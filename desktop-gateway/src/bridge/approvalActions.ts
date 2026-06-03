import { AppServerClient } from "../appServerClient.js";
import type { ClientSnapshot } from "../protocol.js";
import { clearCurrentTurnStarted } from "./runtimeTurnTiming.js";
import { systemStatus } from "./runtimeMessageStore.js";
import { pruneCompletedArtifacts } from "./runtimeSnapshotMessages.js";
import { clearRunningLease, markRunningSignal } from "./runningLease.js";
import {
  markRuntimeApprovalPending,
  markRuntimeApprovalResolved,
  markRuntimeFailed,
  markRuntimeIdle,
  markRuntimeTurnFinished,
  markRuntimeTurnStarted,
  resolveRuntimeStatus,
} from "./runtimeStatusRegistry.js";
import { buildApprovalResponse } from "./summaries.js";
import type {
  PendingApproval,
  ThreadLifecycleStatus,
  ThreadRuntimeState,
} from "./types.js";

type ApprovalActionAppServer = Pick<
  AppServerClient,
  "threadShellCommand" | "respond"
>;

interface SharedApprovalActionDeps {
  appServer: ApprovalActionAppServer;
  threads: Map<string, ThreadRuntimeState>;
  emitChanged(): void;
  getSnapshot(threadId?: string): ClientSnapshot;
  refreshThread(threadId: string): Promise<void>;
  updateSummaryStatus(threadId: string, status: ThreadLifecycleStatus): void;
}

interface ApproveCurrentParams extends SharedApprovalActionDeps {
  threadId: string;
  allow: boolean;
}

export async function handleCurrentApproval({
  allow,
  appServer,
  emitChanged,
  getSnapshot,
  refreshThread,
  threadId,
  threads,
  updateSummaryStatus,
}: ApproveCurrentParams): Promise<ClientSnapshot> {
  const state = threads.get(threadId);
  const pending = state?.pendingApproval;
  if (!state || !pending) {
    return getSnapshot(threadId);
  }

  if (pending.kind === "gatewayShell") {
    state.pendingApproval = null;
    state.snapshot.pendingApproval = null;
    if (!allow) {
      state.snapshot.messages = state.snapshot.messages.concat(systemStatus("审批已拒绝"));
      state.transientOperation = null;
      clearRunningLease(state);
      markRuntimeIdle(state);
      updateSummaryStatus(threadId, resolveRuntimeStatus(state));
      emitChanged();
      return getSnapshot(threadId);
    }

    state.snapshot.messages = state.snapshot.messages.concat(systemStatus("审批已允许"));
    state.snapshot.isGenerating = true;
    markRuntimeApprovalResolved(state);
    markRuntimeTurnStarted(state, `shell-${threadId}`);
    markRunningSignal(state);
    updateSummaryStatus(threadId, resolveRuntimeStatus(state));
    emitChanged();

    void appServer.threadShellCommand(threadId, pending.command ?? "")
      .then(async () => {
        try {
          await refreshThread(threadId);
        } catch {
          // Shell RPC can complete before the thread history is materialized.
          // Keep the live process messages and still close the running state.
        }
        const latest = threads.get(threadId);
        if (!latest) {
          return;
        }

        latest.transientOperation = null;
        markRuntimeTurnFinished(latest, `shell-${threadId}`, "completed");
        latest.snapshot.isGenerating = false;
        latest.currentTurnId = null;
        latest.activeAssistantMessageId = null;
        latest.liveAssistantItemId = null;
        clearCurrentTurnStarted(latest);
        clearRunningLease(latest);
        pruneCompletedArtifacts(latest);
        markRuntimeIdle(latest);
        updateSummaryStatus(threadId, resolveRuntimeStatus(latest));
        emitChanged();
      })
      .catch((error) => {
        const latest = threads.get(threadId);
        if (!latest) {
          return;
        }

        latest.transientOperation = null;
        latest.snapshot.isGenerating = false;
        latest.currentTurnId = null;
        clearCurrentTurnStarted(latest);
        clearRunningLease(latest);
        markRuntimeFailed(latest);
        latest.snapshot.messages = latest.snapshot.messages.concat(
          systemStatus(`shell 命令执行失败: ${error instanceof Error ? error.message : "unknown"}`)
        );
        updateSummaryStatus(threadId, resolveRuntimeStatus(latest));
        emitChanged();
      });
    return getSnapshot(threadId);
  }

  appServer.respond(pending.requestId!, buildApprovalResponse(pending, allow));
  state.pendingApproval = null;
  state.snapshot.pendingApproval = null;
  state.snapshot.messages = state.snapshot.messages.concat(
    systemStatus(allow ? "审批已允许" : "审批已拒绝")
  );
  markRuntimeApprovalResolved(state);
  updateSummaryStatus(threadId, resolveRuntimeStatus(state));
  emitChanged();
  return getSnapshot(threadId);
}

export function applyPendingApprovalState(
  threads: Map<string, ThreadRuntimeState>,
  threadId: string,
  approval: PendingApproval,
  updateSummaryStatus: (threadId: string, status: ThreadLifecycleStatus) => void
): void {
  const state = threads.get(threadId);
  if (!state) {
    return;
  }

  state.pendingApproval = approval;
  state.snapshot.pendingApproval = approval.text;
  markRuntimeApprovalPending(state);
  updateSummaryStatus(threadId, resolveRuntimeStatus(state));
}

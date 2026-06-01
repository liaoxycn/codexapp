import { AppServerClient } from "../appServerClient.js";
import type { ClientSnapshot } from "../protocol.js";
import { systemStatus } from "./runtimeMessages.js";
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
      updateSummaryStatus(threadId, "idle");
      emitChanged();
      return getSnapshot(threadId);
    }

    state.snapshot.messages = state.snapshot.messages.concat(systemStatus("审批已允许"));
    state.snapshot.isGenerating = true;
    updateSummaryStatus(threadId, "running");
    emitChanged();

    void appServer.threadShellCommand(threadId, pending.command ?? "").catch((error) => {
      const latest = threads.get(threadId);
      if (!latest) {
        return;
      }

      latest.snapshot.isGenerating = false;
      latest.snapshot.messages = latest.snapshot.messages.concat(
        systemStatus(`shell 命令执行失败: ${error instanceof Error ? error.message : "unknown"}`)
      );
      updateSummaryStatus(threadId, latest.pendingApproval ? "needs_approval" : "failed");
      emitChanged();
    });
    return getSnapshot(threadId);
  }

  appServer.respond(pending.requestId!, buildApprovalResponse(pending.kind, allow));
  state.pendingApproval = null;
  state.snapshot.pendingApproval = null;
  state.snapshot.messages = state.snapshot.messages.concat(
    systemStatus(allow ? "审批已允许" : "审批已拒绝")
  );
  updateSummaryStatus(threadId, state.snapshot.isGenerating ? "running" : "idle");
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
  updateSummaryStatus(threadId, "needs_approval");
}

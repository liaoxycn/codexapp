import { randomUUID } from "node:crypto";
import process from "node:process";
import { AppServerClient } from "../appServerClient.js";
import type { ClientSnapshot } from "../protocol.js";
import { clearCurrentTurnStarted, markCurrentTurnStarted } from "./runtimeTurnTiming.js";
import { systemStatus, upsertGatewayShellMessage } from "./runtimeMessageStore.js";
import { pruneCompletedArtifacts } from "./runtimeSnapshotMessages.js";
import { clearRunningLease, markRunningSignal } from "./runningLease.js";
import {
  markRuntimeApprovalPending,
  markRuntimeApprovalResolved,
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
  "commandExec" | "respond"
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
    state.gatewayShellSession = null;
    if (!allow) {
      state.snapshot.messages = state.snapshot.messages.concat(systemStatus("审批已拒绝"));
      state.transientOperation = null;
      clearRunningLease(state);
      markRuntimeIdle(state);
      updateSummaryStatus(threadId, resolveRuntimeStatus(state));
      emitChanged();
      return getSnapshot(threadId);
    }

    const command = pending.command?.trim() ?? "";
    if (!command) {
      state.snapshot.messages = state.snapshot.messages.concat(systemStatus("shell 命令为空"));
      state.transientOperation = null;
      clearRunningLease(state);
      markRuntimeIdle(state);
      updateSummaryStatus(threadId, resolveRuntimeStatus(state));
      emitChanged();
      return getSnapshot(threadId);
    }

    const processId = `gateway-shell-${randomUUID()}`;
    const turnId = `shell-${processId}`;
    const startedAtMs = Date.now();
    state.gatewayShellSession = {
      processId,
      messageId: processId,
      command,
      turnId,
      startedAtMs,
    };
    state.currentTurnId = turnId;
    markCurrentTurnStarted(state, startedAtMs, startedAtMs, true);
    state.snapshot.messages = state.snapshot.messages.concat(systemStatus("审批已允许"));
    state.snapshot.isGenerating = true;
    markRuntimeApprovalResolved(state);
    markRuntimeTurnStarted(state, turnId);
    markRunningSignal(state);
    upsertGatewayShellMessage(state, processId, { command });
    updateSummaryStatus(threadId, resolveRuntimeStatus(state));
    emitChanged();

    void appServer.commandExec({
      command: buildShellCommand(command),
      processId,
      streamStdoutStderr: true,
      cwd: state.snapshot.cwd || state.thread?.cwd || null,
      sandboxPolicy: state.sandbox ?? null,
    })
      .then((result) => {
        const latest = threads.get(threadId);
        if (!latest || latest.gatewayShellSession?.processId !== processId) {
          return;
        }

        upsertGatewayShellMessage(latest, processId, {
          command,
          summary: result.exitCode === 0 ? "已运行 1 条命令" : "命令执行失败",
          result: buildCommandResultLabel(result.exitCode, Date.now() - startedAtMs),
          appendOutput: `${result.stdout}${result.stderr}`,
        });
        latest.gatewayShellSession = null;
        latest.transientOperation = null;
        markRuntimeTurnFinished(latest, turnId, result.exitCode === 0 ? "completed" : "failed");
        latest.snapshot.isGenerating = false;
        latest.currentTurnId = null;
        latest.activeAssistantMessageId = null;
        latest.liveAssistantItemId = null;
        clearCurrentTurnStarted(latest);
        clearRunningLease(latest);
        pruneCompletedArtifacts(latest);
        updateSummaryStatus(threadId, resolveRuntimeStatus(latest));
        emitChanged();
      })
      .catch((error) => {
        const latest = threads.get(threadId);
        if (!latest || latest.gatewayShellSession?.processId !== processId) {
          return;
        }

        upsertGatewayShellMessage(latest, processId, {
          command,
          summary: "命令执行失败",
          result: error instanceof Error ? error.message : "unknown",
        });
        latest.gatewayShellSession = null;
        latest.transientOperation = null;
        markRuntimeTurnFinished(latest, turnId, "failed");
        latest.snapshot.isGenerating = false;
        latest.currentTurnId = null;
        latest.activeAssistantMessageId = null;
        latest.liveAssistantItemId = null;
        clearCurrentTurnStarted(latest);
        clearRunningLease(latest);
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

function buildShellCommand(command: string): string[] {
  if (process.platform === "win32") {
    return [process.env.ComSpec || "cmd.exe", "/d", "/s", "/c", command];
  }
  return ["/bin/sh", "-lc", command];
}

function buildCommandResultLabel(exitCode: number, durationMs: number): string {
  const seconds = Math.max(0, Math.round(durationMs / 1000));
  return `退出码 ${exitCode} · ${seconds}s`;
}

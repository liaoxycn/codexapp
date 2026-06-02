import { AppServerClient } from "../appServerClient.js";
import type { ClientSnapshot } from "../protocol.js";
import {
  appendUserMessage,
  hasTrailingSystemStatus,
  systemStatus,
} from "./runtimeMessageStore.js";
import { ensureActiveAssistantMessage } from "./runtimeAssistantMessages.js";
import { upsertThreadState } from "./runtimeState.js";
import { clearRunningLease, markRunningSignal } from "./runningLease.js";
import type {
  ThreadLifecycleStatus,
  ThreadRuntimeState,
} from "./types.js";

type PromptActionAppServer = Pick<
  AppServerClient,
  "threadCompactStart" | "threadRollback" | "turnInterrupt" | "turnStart" | "turnSteer"
>;

interface SharedPromptActionDeps {
  appServer: PromptActionAppServer;
  threads: Map<string, ThreadRuntimeState>;
  emitChanged(): void;
  getSnapshot(threadId?: string): ClientSnapshot;
  updateSummaryStatus(threadId: string, status: ThreadLifecycleStatus): void;
}

interface SendPromptParams extends SharedPromptActionDeps {
  state: ThreadRuntimeState;
  threadId: string;
  text: string;
}

export async function handlePromptSubmission({
  appServer,
  emitChanged,
  getSnapshot,
  state,
  text,
  threadId,
  threads,
  updateSummaryStatus,
}: SendPromptParams): Promise<ClientSnapshot> {
  const trimmed = text.trim();

  appendUserMessage(state, text);
  state.pendingApproval = null;
  state.snapshot.pendingApproval = null;
  state.stopRequested = false;

  if (trimmed === "/compact") {
    state.transientOperation = "compact";
    state.currentTurnId = null;
    state.liveAssistantItemId = null;
    state.activeAssistantMessageId = null;
    state.snapshot.isGenerating = false;
    if (!hasTrailingSystemStatus(state, "已请求压缩上下文")) {
      state.snapshot.messages = state.snapshot.messages.concat(systemStatus("已请求压缩上下文"));
    }

    void appServer.threadCompactStart(threadId).catch((error) => {
      const latest = threads.get(threadId);
      if (!latest) {
        return;
      }

      latest.transientOperation = null;
      latest.snapshot.isGenerating = false;
      clearRunningLease(latest);
      latest.snapshot.messages = latest.snapshot.messages.concat(
        systemStatus(`上下文压缩失败: ${error instanceof Error ? error.message : "unknown"}`)
      );
      updateSummaryStatus(threadId, latest.pendingApproval ? "needs_approval" : "failed");
      emitChanged();
    });

    emitChanged();
    return getSnapshot(threadId);
  }

  if (trimmed === "/rollback") {
    state.transientOperation = "rollback";
    state.currentTurnId = null;
    state.liveAssistantItemId = null;
    state.activeAssistantMessageId = null;
    state.snapshot.isGenerating = false;
    clearRunningLease(state);
    clearRunningLease(state);

    try {
      const rolledBack = await appServer.threadRollback(threadId, 1);
      upsertThreadState({
        threads,
        thread: rolledBack.thread,
        preserveLiveMessages: false,
      });
      const latest = threads.get(threadId);
      if (latest && !hasTrailingSystemStatus(latest, "已回滚最近 1 轮")) {
        latest.snapshot.messages = latest.snapshot.messages.concat(systemStatus("已回滚最近 1 轮"));
      }
      updateSummaryStatus(threadId, "idle");
    } catch (error) {
      const latest = threads.get(threadId) ?? state;
      latest.snapshot.messages = latest.snapshot.messages.concat(
        systemStatus(`回滚失败: ${error instanceof Error ? error.message : "unknown"}`)
      );
      updateSummaryStatus(threadId, "failed");
    } finally {
      const latest = threads.get(threadId) ?? state;
      latest.transientOperation = null;
    }

    emitChanged();
    return getSnapshot(threadId);
  }

  if (trimmed.startsWith("!")) {
    state.transientOperation = "shell";
    state.pendingApproval = {
      kind: "gatewayShell",
      text: `允许执行 shell 命令\n${trimmed.substring(1).trim()}`,
      command: trimmed.substring(1).trim(),
    };
    state.snapshot.pendingApproval = state.pendingApproval.text;
    updateSummaryStatus(threadId, "needs_approval");
    emitChanged();
    return getSnapshot(threadId);
  }

  state.transientOperation = null;
  if (state.currentTurnId && state.snapshot.isGenerating) {
    await appServer.turnSteer(threadId, state.currentTurnId, text);
    emitChanged();
    return getSnapshot(threadId);
  }

  const turnId = await appServer.turnStart(threadId, text);
  state.currentTurnId = turnId;
  state.snapshot.isGenerating = true;
  markRunningSignal(state);
  ensureActiveAssistantMessage(state, turnId);
  updateSummaryStatus(threadId, "running");
  emitChanged();
  return getSnapshot(threadId);
}

export async function rollbackThreadTurns({
  appServer,
  emitChanged,
  getSnapshot,
  state,
  threadId,
  threads,
  updateSummaryStatus,
}: Omit<SendPromptParams, "text">, numTurns: number): Promise<ClientSnapshot> {
  const rollbackCount = normalizeRollbackCount(numTurns);
  state.transientOperation = "rollback";
  state.currentTurnId = null;
  state.liveAssistantItemId = null;
  state.activeAssistantMessageId = null;
  state.pendingApproval = null;
  state.snapshot.pendingApproval = null;
  state.snapshot.isGenerating = false;
  clearRunningLease(state);

  try {
    const rolledBack = await appServer.threadRollback(threadId, rollbackCount);
    upsertThreadState({
      threads,
      thread: rolledBack.thread,
      preserveLiveMessages: false,
    });
    updateSummaryStatus(threadId, "idle");
  } catch (error) {
    const latest = threads.get(threadId) ?? state;
    latest.snapshot.messages = latest.snapshot.messages.concat(
      systemStatus(`回滚失败: ${error instanceof Error ? error.message : "unknown"}`)
    );
    updateSummaryStatus(threadId, "failed");
    throw error;
  } finally {
    const latest = threads.get(threadId) ?? state;
    latest.transientOperation = null;
  }

  emitChanged();
  return getSnapshot(threadId);
}

export async function resendPromptFromTurn(
  params: SendPromptParams,
  rollbackNumTurns: number
): Promise<ClientSnapshot> {
  await rollbackThreadTurns(params, rollbackNumTurns);
  const latest = params.threads.get(params.threadId) ?? params.state;
  return handlePromptSubmission({
    ...params,
    state: latest,
    text: params.text,
  });
}

export async function interruptRunningTurn(
  appServer: Pick<AppServerClient, "turnInterrupt">,
  threads: Map<string, ThreadRuntimeState>,
  getSnapshot: (threadId?: string) => ClientSnapshot,
  threadId: string
): Promise<ClientSnapshot> {
  const state = threads.get(threadId);
  if (state?.currentTurnId) {
    await appServer.turnInterrupt(threadId, state.currentTurnId);
    state.stopRequested = true;
  }
  return getSnapshot(threadId);
}

function normalizeRollbackCount(numTurns: number): number {
  if (!Number.isInteger(numTurns) || numTurns <= 0) {
    return 1;
  }
  return numTurns;
}

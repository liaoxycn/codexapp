import {
  hasTrailingSystemStatus,
  systemStatus,
} from "./runtimeMessageStore.js";
import {
  normalizeCompactMessages,
  pruneCompletedArtifacts,
  rebaseSnapshotMessagesFromThread,
} from "./runtimeSnapshotMessages.js";
import { clearRunningLease, hasRunningLease } from "./runningLease.js";
import type {
  ThreadLifecycleStatus,
  ThreadRuntimeState,
} from "./types.js";

interface SharedTurnFinalizationDeps {
  threads: Map<string, ThreadRuntimeState>;
  emitChanged(): void;
  updateSummaryStatus(threadId: string, status: ThreadLifecycleStatus): void;
}

export async function finalizeTurnRuntimeState({
  emitChanged,
  refreshThread,
  threadId,
  threads,
  updateSummaryStatus,
  turnStatus,
  completedTurnId,
}: SharedTurnFinalizationDeps & {
  refreshThread(threadId: string): Promise<void>;
  threadId: string;
  turnStatus?: string;
  completedTurnId?: string;
}): Promise<void> {
  const existing = threads.get(threadId);
  if (!existing || existing.isFinalizing) {
    return;
  }

  existing.isFinalizing = true;
  const shouldShowStopped = existing.stopRequested;

  try {
    await refreshThread(threadId);
    const state = threads.get(threadId);
    if (!state) {
      return;
    }

    rebaseSnapshotMessagesFromThread(state);
    const nextTurnStarted =
      completedTurnId != null && state.currentTurnId != null && state.currentTurnId !== completedTurnId;
    const keepRunning =
      nextTurnStarted || (!shouldShowStopped && turnStatus !== "failed" && hasRunningLease(state));
    if (!nextTurnStarted) {
      state.currentTurnId = null;
      state.activeAssistantMessageId = null;
      state.liveAssistantItemId = null;
      state.transientOperation = null;
    }
    if (!keepRunning) {
      state.snapshot.isGenerating = false;
      clearRunningLease(state);
    } else {
      state.snapshot.isGenerating = true;
    }
    state.stopRequested = false;

    if (shouldShowStopped && !hasTrailingSystemStatus(state, "已停止，本轮可继续补充输入。")) {
      state.snapshot.messages = state.snapshot.messages.concat(systemStatus("已停止，本轮可继续补充输入。"));
    }

    updateSummaryStatus(
      threadId,
      keepRunning ? "running" : turnStatus === "failed" ? "failed" : state.pendingApproval ? "needs_approval" : "idle"
    );
    emitChanged();
  } finally {
    const state = threads.get(threadId);
    if (state) {
      state.isFinalizing = false;
    }
  }
}

export async function finalizeCompactRuntimeState({
  emitChanged,
  refreshThread,
  threadId,
  threads,
  updateSummaryStatus,
}: SharedTurnFinalizationDeps & {
  refreshThread(threadId: string): Promise<void>;
  threadId: string;
}): Promise<void> {
  const existing = threads.get(threadId);
  if (!existing || existing.isFinalizing) {
    return;
  }

  existing.isFinalizing = true;
  try {
    await refreshThread(threadId);
    const state = threads.get(threadId);
    if (!state) {
      return;
    }

    state.currentTurnId = null;
    state.activeAssistantMessageId = null;
    state.liveAssistantItemId = null;
    state.snapshot.isGenerating = false;
    clearRunningLease(state);
    pruneCompletedArtifacts(state);
    normalizeCompactMessages(state, true);
    updateSummaryStatus(threadId, state.pendingApproval ? "needs_approval" : "idle");
    emitChanged();
  } finally {
    const state = threads.get(threadId);
    if (state) {
      state.isFinalizing = false;
    }
  }
}

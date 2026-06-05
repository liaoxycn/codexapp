import {
  hasTrailingSystemStatus,
  systemStatus,
} from "./runtimeMessageStore.js";
import {
  normalizeCompactMessages,
  pruneCompletedArtifacts,
  rebaseSnapshotMessagesFromThread,
} from "./runtimeSnapshotMessages.js";
import { clearRunningLease, hasRunningActivityLease } from "./runningLease.js";
import {
  hasActiveRuntimeWork,
  markRuntimeIdle,
  resolveRuntimeStatus,
} from "./runtimeStatusRegistry.js";
import { clearCurrentTurnStarted } from "./runtimeTurnTiming.js";
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
    pruneCompletedArtifacts(state);
    const nextTurnStarted =
      completedTurnId != null && state.currentTurnId != null && state.currentTurnId !== completedTurnId;
    const hasOtherActiveWork = Boolean(
      state.transientOperation ||
        state.activeHookIds.length > 0 ||
        state.activeTurnIds.some((id) => id !== completedTurnId) ||
        (completedTurnId == null && state.currentTurnId)
    );
    const keepRunning =
      nextTurnStarted ||
      (!shouldShowStopped && turnStatus !== "failed" && hasOtherActiveWork) ||
      (!shouldShowStopped &&
        turnStatus !== "failed" &&
        resolveRuntimeStatus(state) === "running" &&
        hasRunningActivityLease(state));
    if (!nextTurnStarted) {
      state.currentTurnId = null;
      clearCurrentTurnStarted(state);
      state.activeAssistantMessageId = null;
      state.liveAssistantItemId = null;
      state.transientOperation = null;
    }
    if (!keepRunning) {
      state.snapshot.isGenerating = false;
      clearRunningLease(state);
      if (!state.pendingApproval && turnStatus !== "failed") {
        markRuntimeIdle(state);
      }
    } else {
      state.snapshot.isGenerating = true;
    }
    state.stopRequested = false;

    if (shouldShowStopped && !hasTrailingSystemStatus(state, "已停止，本轮可继续补充输入。")) {
      state.snapshot.messages = state.snapshot.messages.concat(systemStatus("已停止，本轮可继续补充输入。"));
    }

    updateSummaryStatus(
      threadId,
      keepRunning ? "running" : turnStatus === "failed" ? "failed" : resolveRuntimeStatus(state)
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
    clearCurrentTurnStarted(state);
    state.activeAssistantMessageId = null;
    state.liveAssistantItemId = null;
    state.snapshot.isGenerating = false;
    clearRunningLease(state);
    markRuntimeIdle(state);
    pruneCompletedArtifacts(state);
    normalizeCompactMessages(state, true);
    updateSummaryStatus(threadId, resolveRuntimeStatus(state));
    emitChanged();
  } finally {
    const state = threads.get(threadId);
    if (state) {
      state.isFinalizing = false;
    }
  }
}

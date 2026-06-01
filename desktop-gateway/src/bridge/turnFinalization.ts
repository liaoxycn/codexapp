import {
  hasTrailingSystemStatus,
  systemStatus,
} from "./runtimeMessageStore.js";
import {
  normalizeCompactMessages,
  pruneCompletedArtifacts,
  rebaseSnapshotMessagesFromThread,
} from "./runtimeSnapshotMessages.js";
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
}: SharedTurnFinalizationDeps & {
  refreshThread(threadId: string): Promise<void>;
  threadId: string;
  turnStatus?: string;
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
    state.currentTurnId = null;
    state.activeAssistantMessageId = null;
    state.liveAssistantItemId = null;
    state.transientOperation = null;
    state.snapshot.isGenerating = false;
    state.stopRequested = false;

    if (shouldShowStopped && !hasTrailingSystemStatus(state, "已停止，本轮可继续补充输入。")) {
      state.snapshot.messages = state.snapshot.messages.concat(systemStatus("已停止，本轮可继续补充输入。"));
    }

    updateSummaryStatus(threadId, turnStatus === "failed" ? "failed" : state.pendingApproval ? "needs_approval" : "idle");
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

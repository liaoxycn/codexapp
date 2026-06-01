import {
  resolveDisplayedThreadStatus,
  resolveLifecycleStatus,
  resolveThreadSummaryStatus,
} from "../threadState.js";
import type { GatewayThreadPayload } from "../protocol.js";
import { systemStatus } from "./runtimeMessageStore.js";
import {
  dedupeSummaries,
  emptySnapshot,
  isDesktopMainListThread,
  mapThreadToSummary,
} from "./summaries.js";
import {
  INITIAL_HISTORY_WINDOW,
  type ThreadLifecycleStatus,
  type ThreadRuntimeState,
} from "./types.js";

export function buildRuntimeSummaries(
  threads: Map<string, ThreadRuntimeState>
): GatewayThreadPayload[] {
  const items = [...threads.values()]
    .filter((entry) => entry.thread != null || entry.snapshot.threads.length > 0)
    .filter((entry) => entry.thread == null || entry.isLocalCatalogEntry || isDesktopMainListThread(entry.thread))
    .map((entry) =>
      entry.thread
        ? {
            ...mapThreadToSummary(entry.thread, entry.summary.archived, entry.lastActivityAtMs),
            status: resolveDisplayedThreadStatus(resolveThreadSummaryStatus(entry.thread), {
              isGenerating: entry.snapshot.isGenerating,
              currentTurnId: entry.currentTurnId,
              transientOperation: entry.transientOperation,
              pendingApproval: entry.pendingApproval?.text ?? null,
            }),
          }
        : entry.summary
    )
    .filter((value): value is GatewayThreadPayload => value != null);
  return dedupeSummaries(items);
}

export function syncSelectedThreadSnapshots(
  threads: Map<string, ThreadRuntimeState>,
  selectedThreadId: string
): void {
  const summaries = buildRuntimeSummaries(threads);
  for (const [id, state] of threads) {
    state.snapshot.selectedThreadId = selectedThreadId;
    state.snapshot.threads = summaries;
    if (id === selectedThreadId) {
      state.snapshot.selectedThreadId = selectedThreadId;
    }
  }
}

export function updateSummaryStatusForThread(
  threads: Map<string, ThreadRuntimeState>,
  threadId: string,
  status: ThreadLifecycleStatus
): void {
  for (const state of threads.values()) {
    if (state.summary.id === threadId) {
      state.summary = { ...state.summary, status };
    }
    state.snapshot.threads = state.snapshot.threads.map((thread) =>
      thread.id === threadId ? { ...thread, status } : thread
    );
  }
}

export function markAllThreadsFailedState(
  threads: Map<string, ThreadRuntimeState>,
  detail: string
): void {
  for (const state of threads.values()) {
    if (state.snapshot.isGenerating || state.currentTurnId || state.transientOperation) {
      state.snapshot.messages = state.snapshot.messages.concat(systemStatus(detail));
    }

    state.currentTurnId = null;
    state.activeAssistantMessageId = null;
    state.liveAssistantItemId = null;
    state.transientOperation = null;
    state.pendingApproval = null;
    state.snapshot.pendingApproval = null;
    state.snapshot.isGenerating = false;
    state.stopRequested = false;
    state.isFinalizing = false;
    updateSummaryStatusForThread(threads, state.summary.id, state.summary.archived ? "idle" : "failed");
  }
}

export function createPlaceholderThreadRuntimeState(
  summary: GatewayThreadPayload,
  summaries: GatewayThreadPayload[],
  selectedThreadId: string
): ThreadRuntimeState {
  return {
    summary,
    thread: null,
    lastActivityAtMs: summary.updatedAt ?? 0,
    isLocalCatalogEntry: false,
    historyWindow: INITIAL_HISTORY_WINDOW,
    currentTurnId: null,
    activeAssistantMessageId: null,
    liveAssistantItemId: null,
    transientOperation: null,
    pendingApproval: null,
    stopRequested: false,
    isFinalizing: false,
    isSubscribed: false,
    model: null,
    modelProvider: null,
    instructionSources: [],
    approvalPolicy: null,
    approvalsReviewer: null,
    sandbox: null,
    reasoningEffort: null,
    snapshot: {
      ...emptySnapshot(),
      threads: summaries,
      selectedThreadId,
    },
  };
}

export function refreshSummarySnapshotEntry(
  state: ThreadRuntimeState,
  summary: GatewayThreadPayload,
  summaries: GatewayThreadPayload[]
): void {
  state.summary = {
    ...summary,
    status: resolveDisplayedThreadStatus(resolveLifecycleStatus(summary.status, state.pendingApproval != null), {
      isGenerating: state.snapshot.isGenerating,
      currentTurnId: state.currentTurnId,
      transientOperation: state.transientOperation,
      pendingApproval: state.pendingApproval?.text ?? null,
    }),
  };
  state.snapshot.threads = summaries;
}

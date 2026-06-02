import type { AppServerThread, ThreadResumeResult } from "../appServerTypes.js";
import {
  getActiveTurnId,
  resolveThreadSummaryStatus,
  shouldRetainThreadRuntimeOverlay,
} from "../threadState.js";
import type { GatewayThreadPayload } from "../protocol.js";
import { collectThreadMessages, mergeSnapshotMessages } from "./runtimeSnapshotMessages.js";
import {
  getThreadLastActivityAtMs,
  mapThreadToSnapshot,
  mapThreadToSummary,
  toResumeMetadata,
  trimMessagesToWindow,
} from "./summaries.js";
import {
  buildRuntimeSummaries,
  syncSelectedThreadSnapshots,
} from "./runtimeSummaryState.js";
import { hasRunningLease } from "./runningLease.js";
import {
  initializeRuntimeStatus,
  resolveRuntimeStatus,
} from "./runtimeStatusRegistry.js";
import {
  INITIAL_HISTORY_WINDOW,
  type ThreadRuntimeState,
} from "./types.js";

interface UpsertThreadStateParams {
  threads: Map<string, ThreadRuntimeState>;
  thread: AppServerThread;
  summaries?: GatewayThreadPayload[] | null;
  resume?: ThreadResumeResult | null;
  preserveLiveMessages?: boolean;
  archived?: boolean;
  isLocalCatalogEntry?: boolean;
  syncSelection?: boolean;
}

export function upsertThreadState({
  threads,
  thread,
  summaries,
  resume = null,
  preserveLiveMessages = false,
  archived = false,
  isLocalCatalogEntry = false,
  syncSelection = true,
}: UpsertThreadStateParams): void {
  const existing = threads.get(thread.id);
  const mergedSummaries = summaries ?? buildRuntimeSummaries(threads);
  const allThreadMessages = collectThreadMessages(thread);
  const baseSnapshot = mapThreadToSnapshot(
    thread,
    mergedSummaries,
    thread.id,
    resume ?? toResumeMetadata(existing),
    allThreadMessages
  );
  const mergedMessages =
    preserveLiveMessages && existing
      ? mergeSnapshotMessages(allThreadMessages, existing.snapshot.messages)
      : allThreadMessages;
  const historyWindow = existing?.historyWindow ?? INITIAL_HISTORY_WINDOW;
  const resolvedSummaryStatus = resolveThreadSummaryStatus(thread);
  const lastActivityAtMs = Math.max(existing?.lastActivityAtMs ?? 0, getThreadLastActivityAtMs(thread));
  const retainRuntimeOverlay = shouldRetainThreadRuntimeOverlay(thread, existing);
  const activeTurnId = getActiveTurnId(thread);
  const currentTurnId = retainRuntimeOverlay
    ? existing?.currentTurnId ?? activeTurnId
    : null;
  const runningLeaseActive = existing ? hasRunningLease(existing) : false;
  const inferredIsGenerating = retainRuntimeOverlay
    ? Boolean(existing?.snapshot.isGenerating || activeTurnId != null || baseSnapshot.isGenerating || runningLeaseActive)
    : baseSnapshot.isGenerating;
  const initialRuntimeStatus = existing?.runtimeStatus ?? resolvedSummaryStatus;
  const runtimeStatus = existing ? resolveRuntimeStatus(existing) : initialRuntimeStatus;
  const isGenerating = runtimeStatus === "running";

  const nextSummary = existing
    ? {
        ...mapThreadToSummary(thread, archived || existing.summary.archived, lastActivityAtMs),
        status: runtimeStatus,
      }
    : mapThreadToSummary(thread, archived);

  const runtimeState: ThreadRuntimeState = {
    summary: nextSummary,
    thread,
    isSubscribed: resume != null || existing?.isSubscribed === true,
    isLocalCatalogEntry: existing?.isLocalCatalogEntry === true || isLocalCatalogEntry,
    lastActivityAtMs,
    historyWindow,
    currentTurnId,
    activeAssistantMessageId: retainRuntimeOverlay ? existing?.activeAssistantMessageId ?? null : null,
    liveAssistantItemId: retainRuntimeOverlay ? existing?.liveAssistantItemId ?? null : null,
    transientOperation: retainRuntimeOverlay ? existing?.transientOperation ?? null : null,
    pendingApproval: retainRuntimeOverlay ? existing?.pendingApproval ?? null : null,
    stopRequested: retainRuntimeOverlay ? existing?.stopRequested ?? false : false,
    isFinalizing: retainRuntimeOverlay ? existing?.isFinalizing ?? false : false,
    runningSignalUntilMs: retainRuntimeOverlay ? existing?.runningSignalUntilMs ?? 0 : 0,
    turnCompletionGraceUntilMs: retainRuntimeOverlay ? existing?.turnCompletionGraceUntilMs ?? 0 : 0,
    runtimeStatus,
    activeTurnIds: retainRuntimeOverlay ? existing?.activeTurnIds ?? [] : [],
    activeHookIds: retainRuntimeOverlay ? existing?.activeHookIds ?? [] : [],
    runtimeStatusSeq: existing?.runtimeStatusSeq ?? 0,
    runtimeTerminalSeq: existing?.runtimeTerminalSeq ?? 0,
    model: resume?.model ?? existing?.model ?? null,
    modelProvider: resume?.modelProvider ?? existing?.modelProvider ?? thread.modelProvider ?? null,
    instructionSources: resume?.instructionSources ?? existing?.instructionSources ?? [],
    approvalPolicy: resume?.approvalPolicy ?? existing?.approvalPolicy ?? null,
    approvalsReviewer: resume?.approvalsReviewer ?? existing?.approvalsReviewer ?? null,
    sandbox: resume?.sandbox ?? existing?.sandbox ?? null,
    reasoningEffort: resume?.reasoningEffort ?? existing?.reasoningEffort ?? null,
    snapshot: {
      ...baseSnapshot,
      selectedThreadId: thread.id,
      messages: trimMessagesToWindow(mergedMessages, historyWindow),
      hasMoreHistory: mergedMessages.length > historyWindow,
      pendingApproval: existing?.pendingApproval?.text ?? null,
      isGenerating,
    },
  };

  if (!existing) {
    initializeRuntimeStatus(runtimeState, inferredIsGenerating ? "running" : runtimeStatus);
  }
  threads.set(thread.id, runtimeState);
  if (syncSelection) {
    syncSelectedThreadSnapshots(threads, thread.id);
  }
}

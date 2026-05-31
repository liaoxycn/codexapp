import type { ClientSnapshot } from "../protocol.js";
import type { ThreadRuntimeState } from "./types.js";

interface ActivateThreadSelectionParams {
  threadId: string;
  setCurrentThreadId: (threadId: string) => void;
  unsubscribeOtherThreads: (activeThreadId: string) => Promise<void>;
  resumeThread: (threadId: string) => Promise<void>;
  isCurrentSelection: (threadId: string) => boolean;
  syncSelectedThread: (selectedThreadId: string) => void;
  emitChanged: () => void;
  getSnapshot: (selectedThreadId?: string) => ClientSnapshot;
}

interface StaleSelectionRequestParams {
  currentThreadId: string;
  currentSelectionVersion: number;
  requestedThreadId: string;
  requestedSelectionVersion: number;
}

export function getDefaultThreadId(
  threads: Map<string, ThreadRuntimeState>,
  currentThreadId: string
): string {
  const current = currentThreadId ? threads.get(currentThreadId) : null;
  if (current && !current.summary.archived) {
    return currentThreadId;
  }

  const active = [...threads.values()].find((entry) => !entry.summary.archived)?.summary.id;
  return active || [...threads.keys()][0] || "";
}

export function resolveThreadId(
  threads: Map<string, ThreadRuntimeState>,
  currentThreadId: string,
  threadId?: string
): string {
  if (threadId && threads.has(threadId)) {
    return threadId;
  }
  return getDefaultThreadId(threads, currentThreadId);
}

export function findNextActiveThreadId(
  threads: Map<string, ThreadRuntimeState>
): string {
  return [...threads.values()].find((entry) => !entry.summary.archived)?.summary.id ?? "";
}

export function isStaleSelectionRequest({
  currentThreadId,
  currentSelectionVersion,
  requestedThreadId,
  requestedSelectionVersion,
}: StaleSelectionRequestParams): boolean {
  return currentSelectionVersion !== requestedSelectionVersion && requestedThreadId !== currentThreadId;
}

export async function activateThreadSelection({
  threadId,
  setCurrentThreadId,
  unsubscribeOtherThreads,
  resumeThread,
  isCurrentSelection,
  syncSelectedThread,
  emitChanged,
  getSnapshot,
}: ActivateThreadSelectionParams): Promise<ClientSnapshot> {
  setCurrentThreadId(threadId);
  await unsubscribeOtherThreads(threadId);
  if (!isCurrentSelection(threadId)) {
    return getSnapshot();
  }

  await resumeThread(threadId);
  if (!isCurrentSelection(threadId)) {
    return getSnapshot();
  }

  syncSelectedThread(threadId);
  emitChanged();
  return getSnapshot(threadId);
}

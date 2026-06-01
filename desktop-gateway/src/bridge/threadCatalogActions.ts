import type { AppServerClient } from "../appServerClient.js";
import type { ClientSnapshot } from "../protocol.js";
import {
  syncSelectedThreadSnapshots,
} from "./runtimeSummaryState.js";
import { upsertThreadState } from "./runtimeState.js";
import {
  activateThreadSelection,
  isStaleSelectionRequest,
} from "./threadSelection.js";
import {
  HISTORY_WINDOW_STEP,
  type ThreadRuntimeState,
} from "./types.js";

type ThreadCatalogClient = Pick<
  AppServerClient,
  "threadArchive" | "threadFork" | "threadSetName" | "threadStart" | "threadUnarchive"
>;

export interface ThreadCatalogActionDeps {
  appServer: ThreadCatalogClient;
  threads: Map<string, ThreadRuntimeState>;
  getCurrentThreadId: () => string;
  getSelectionVersion: () => number;
  setCurrentThreadId: (threadId: string) => void;
  incrementSelectionVersion: () => number;
  syncSelectedThread: (selectedThreadId: string) => void;
  emitChanged: () => void;
  getSnapshot: (selectedThreadId?: string) => ClientSnapshot;
  hydrateThreads: (options?: { preserveCurrentThread?: boolean }) => Promise<void>;
  refreshThread: (threadId: string) => Promise<void>;
  resumeThread: (threadId: string) => Promise<void>;
  unsubscribeOtherThreads: (activeThreadId: string) => Promise<void>;
  resolveThreadId: (threadId?: string) => string;
  hasThread: (threadId: string) => boolean;
  isCurrentSelection: (threadId: string) => boolean;
}
export {
  archiveCatalogThread,
  createCatalogThread,
  forkCatalogThread,
  renameCatalogThread,
  unarchiveCatalogThread,
} from "./threadCatalogMutations.js";

export async function refreshCatalogThreads(
  deps: ThreadCatalogActionDeps,
  selectedThreadId?: string
): Promise<ClientSnapshot> {
  const requested = deps.resolveThreadId(selectedThreadId);
  const requestedVersion = deps.getSelectionVersion();

  await deps.hydrateThreads({ preserveCurrentThread: true });

  if (isStaleSelectionRequest({
    currentThreadId: deps.getCurrentThreadId(),
    currentSelectionVersion: deps.getSelectionVersion(),
    requestedThreadId: requested,
    requestedSelectionVersion: requestedVersion,
  })) {
    deps.emitChanged();
    return deps.getSnapshot();
  }

  const resolved = deps.resolveThreadId(requested);
  if (!resolved) {
    deps.emitChanged();
    return deps.getSnapshot(resolved);
  }

  if (isStaleSelectionRequest({
    currentThreadId: deps.getCurrentThreadId(),
    currentSelectionVersion: deps.getSelectionVersion(),
    requestedThreadId: resolved,
    requestedSelectionVersion: requestedVersion,
  })) {
    return deps.getSnapshot();
  }

  return activateThreadSelection({
    threadId: resolved,
    currentThreadId: deps.getCurrentThreadId(),
    setCurrentThreadId: (nextThreadId) => {
      deps.setCurrentThreadId(nextThreadId);
    },
    unsubscribeOtherThreads: async (activeThreadId) => deps.unsubscribeOtherThreads(activeThreadId),
    resumeThread: async (activeThreadId) => deps.resumeThread(activeThreadId),
    isCurrentSelection: (activeThreadId) =>
      !isStaleSelectionRequest({
        currentThreadId: deps.getCurrentThreadId(),
        currentSelectionVersion: deps.getSelectionVersion(),
        requestedThreadId: activeThreadId,
        requestedSelectionVersion: requestedVersion,
      }) && deps.isCurrentSelection(activeThreadId),
    syncSelectedThread: (threadIdToSync) => deps.syncSelectedThread(threadIdToSync),
    emitChanged: () => deps.emitChanged(),
    getSnapshot: (threadIdToSnapshot) => deps.getSnapshot(threadIdToSnapshot),
  });
}

export function expandThreadHistoryWindow(
  threads: Map<string, ThreadRuntimeState>,
  threadId: string
): boolean {
  const state = threads.get(threadId);
  if (!state) {
    return false;
  }

  state.historyWindow += HISTORY_WINDOW_STEP;
  if (state.thread != null) {
    upsertThreadState({
      threads,
      thread: state.thread,
      preserveLiveMessages: true,
    });
  } else {
    syncSelectedThreadSnapshots(threads, threadId);
  }
  return true;
}

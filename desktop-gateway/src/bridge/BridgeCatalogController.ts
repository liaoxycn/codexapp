import type { AppServerClient } from "../appServerClient.js";
import type { ClientSnapshot } from "../protocol.js";
import { BridgeRuntimeStore } from "./bridgeRuntimeStore.js";
import { hydrateThreadCatalog } from "./threadHydration.js";
import {
  refreshThreadState,
  resumeThreadSubscription,
  unsubscribeInactiveThreadSubscriptions,
} from "./threadSubscriptions.js";
import { activateThreadSelection } from "./threadSelection.js";
import {
  archiveCatalogThread,
  createCatalogThread,
  forkCatalogThread,
  expandThreadHistoryWindow,
  refreshCatalogThreads,
  renameCatalogThread,
  unarchiveCatalogThread,
} from "./threadCatalogActions.js";

export class BridgeCatalogController {
  constructor(
    private readonly runtime: BridgeRuntimeStore,
    private readonly getAppServer: () => AppServerClient
  ) {}

  get threads() {
    return this.runtime.threads;
  }

  async selectThread(threadId: string): Promise<ClientSnapshot> {
    const resolved = this.runtime.resolveThreadId(threadId);
    this.runtime.incrementSelectionVersion();
    return activateThreadSelection({
      threadId: resolved,
      currentThreadId: this.runtime.currentThreadId,
      setCurrentThreadId: (nextThreadId) => {
        this.runtime.currentThreadId = nextThreadId;
      },
      unsubscribeOtherThreads: async (activeThreadId) => this.unsubscribeOtherThreads(activeThreadId),
      resumeThread: async (activeThreadId) => this.resumeThread(activeThreadId),
      isCurrentSelection: (activeThreadId) => this.runtime.isCurrentSelection(activeThreadId),
      syncSelectedThread: (selectedThreadId) => this.runtime.syncSelectedThread(selectedThreadId),
      emitChanged: () => this.runtime.emitChanged(),
      getSnapshot: (selectedThreadIdToSnapshot) => this.runtime.getSnapshot(selectedThreadIdToSnapshot),
    });
  }

  async createThread(cwd?: string): Promise<ClientSnapshot> {
    return createCatalogThread(this.threadCatalogActionDeps(), cwd);
  }

  async forkThread(threadId: string): Promise<ClientSnapshot> {
    return forkCatalogThread(this.threadCatalogActionDeps(), threadId);
  }

  async renameThread(threadId: string, name: string): Promise<ClientSnapshot> {
    return renameCatalogThread(this.threadCatalogActionDeps(), threadId, name);
  }

  async archiveThread(threadId: string): Promise<ClientSnapshot> {
    return archiveCatalogThread(this.threadCatalogActionDeps(), threadId);
  }

  async unarchiveThread(threadId: string): Promise<ClientSnapshot> {
    return unarchiveCatalogThread(this.threadCatalogActionDeps(), threadId);
  }

  async refreshThreads(selectedThreadId?: string): Promise<ClientSnapshot> {
    return refreshCatalogThreads(this.threadCatalogActionDeps(), selectedThreadId);
  }

  async loadOlderMessages(threadId: string): Promise<ClientSnapshot> {
    const resolved = this.runtime.resolveThreadId(threadId);
    const expanded = expandThreadHistoryWindow(this.threads, resolved);
    if (!expanded) {
      return this.runtime.getSnapshot(resolved);
    }

    this.runtime.syncSelectedThread(resolved);
    this.runtime.emitChanged();
    return this.runtime.getSnapshot(resolved);
  }

  async hydrateThreads(options: { preserveCurrentThread?: boolean } = {}): Promise<void> {
    const hydratedThreadId = await hydrateThreadCatalog({
      appServer: this.getAppServer(),
      threads: this.threads,
      currentThreadId: this.runtime.currentThreadId,
    });
    if (!options.preserveCurrentThread) {
      this.runtime.currentThreadId = hydratedThreadId;
    }
    this.runtime.emitChanged();
  }

  async refreshThread(threadId: string): Promise<void> {
    await refreshThreadState({ appServer: this.getAppServer(), threads: this.threads }, threadId);
  }

  private async resumeThread(threadId: string): Promise<void> {
    await resumeThreadSubscription({ appServer: this.getAppServer(), threads: this.threads }, threadId);
  }

  private async unsubscribeOtherThreads(activeThreadId: string): Promise<void> {
    await unsubscribeInactiveThreadSubscriptions(
      { appServer: this.getAppServer(), threads: this.threads },
      activeThreadId
    );
  }

  private threadCatalogActionDeps() {
    return {
      appServer: this.getAppServer(),
      threads: this.threads,
      getCurrentThreadId: () => this.runtime.currentThreadId,
      getSelectionVersion: () => this.runtime.selectionVersion,
      setCurrentThreadId: (threadId: string) => {
        this.runtime.currentThreadId = threadId;
      },
      incrementSelectionVersion: () => this.runtime.incrementSelectionVersion(),
      syncSelectedThread: (selectedThreadId: string) => this.runtime.syncSelectedThread(selectedThreadId),
      emitChanged: () => this.runtime.emitChanged(),
      getSnapshot: (selectedThreadId?: string) => this.runtime.getSnapshot(selectedThreadId),
      hydrateThreads: async (options?: { preserveCurrentThread?: boolean }) => this.hydrateThreads(options),
      refreshThread: async (threadId: string) => this.refreshThread(threadId),
      resumeThread: async (threadId: string) => this.resumeThread(threadId),
      unsubscribeOtherThreads: async (activeThreadId: string) => this.unsubscribeOtherThreads(activeThreadId),
      resolveThreadId: (threadId?: string) => this.runtime.resolveThreadId(threadId),
      hasThread: (threadId: string) => this.runtime.hasThread(threadId),
      isCurrentSelection: (threadId: string) => this.runtime.isCurrentSelection(threadId),
    };
  }
}

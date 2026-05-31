import { AppServerClient } from "../appServerClient.js";
import type { ClientSnapshot } from "../protocol.js";
import {
  type BridgeBackendLifecycleDeps,
} from "./bridgeBackendLifecycle.js";
import { BridgeCatalogController } from "./BridgeCatalogController.js";
import { BridgeRuntimeStore } from "./bridgeRuntimeStore.js";
import { ensureActiveAssistantMessage } from "./runtimeMessages.js";
import {
  ensureResumedThread,
} from "./threadSubscriptions.js";
import {
  handleCurrentApproval,
  handlePromptSubmission,
  interruptRunningTurn,
} from "./turnActions.js";
import type { ThreadRuntimeState } from "./types.js";

export class BridgeThreadController {
  private readonly catalog: BridgeCatalogController;

  constructor(
    private readonly runtime: BridgeRuntimeStore,
    private readonly getAppServer: () => AppServerClient
  ) {
    this.catalog = new BridgeCatalogController(runtime, getAppServer);
  }

  get threads() {
    return this.runtime.threads;
  }

  async selectThread(threadId: string): Promise<ClientSnapshot> {
    return this.catalog.selectThread(threadId);
  }

  async createThread(cwd?: string): Promise<ClientSnapshot> {
    return this.catalog.createThread(cwd);
  }

  async renameThread(threadId: string, name: string): Promise<ClientSnapshot> {
    return this.catalog.renameThread(threadId, name);
  }

  async archiveThread(threadId: string): Promise<ClientSnapshot> {
    return this.catalog.archiveThread(threadId);
  }

  async unarchiveThread(threadId: string): Promise<ClientSnapshot> {
    return this.catalog.unarchiveThread(threadId);
  }

  async refreshThreads(selectedThreadId?: string): Promise<ClientSnapshot> {
    return this.catalog.refreshThreads(selectedThreadId);
  }

  async loadOlderMessages(threadId: string): Promise<ClientSnapshot> {
    return this.catalog.loadOlderMessages(threadId);
  }

  async sendPrompt(threadId: string, text: string): Promise<ClientSnapshot> {
    const resolved = this.runtime.resolveThreadId(threadId);
    const state = await this.ensureResumed(resolved);
    return handlePromptSubmission({
      appServer: this.getAppServer(),
      emitChanged: () => this.runtime.emitChanged(),
      getSnapshot: (selectedThreadId) => this.runtime.getSnapshot(selectedThreadId),
      state,
      text,
      threadId: resolved,
      threads: this.threads,
      updateSummaryStatus: (targetThreadId, status) => this.runtime.updateSummaryStatus(targetThreadId, status),
    });
  }

  async stopTurn(threadId: string): Promise<ClientSnapshot> {
    const resolved = this.runtime.resolveThreadId(threadId);
    return interruptRunningTurn(
      this.getAppServer(),
      this.threads,
      (selectedThreadId) => this.runtime.getSnapshot(selectedThreadId),
      resolved
    );
  }

  async approveCurrent(threadId: string, allow: boolean): Promise<ClientSnapshot> {
    const resolved = this.runtime.resolveThreadId(threadId);
    return handleCurrentApproval({
      allow,
      appServer: this.getAppServer(),
      emitChanged: () => this.runtime.emitChanged(),
      getSnapshot: (selectedThreadId) => this.runtime.getSnapshot(selectedThreadId),
      threadId: resolved,
      threads: this.threads,
      updateSummaryStatus: (targetThreadId, status) => this.runtime.updateSummaryStatus(targetThreadId, status),
    });
  }

  async hydrateThreads(): Promise<void> {
    await this.catalog.hydrateThreads();
  }

  lifecycleDeps(): BridgeBackendLifecycleDeps {
    return {
      threads: this.threads,
      emitChanged: () => this.runtime.emitChanged(),
      hydrateThreads: async () => this.hydrateThreads(),
      refreshThread: async (threadId: string) => this.catalog.refreshThread(threadId),
      ensureActiveAssistantMessage: (state, turnId) => ensureActiveAssistantMessage(state, turnId),
      updateSummaryStatus: (threadId, status) => this.runtime.updateSummaryStatus(threadId, status),
    };
  }

  private async ensureResumed(threadId: string): Promise<ThreadRuntimeState> {
    return ensureResumedThread({ appServer: this.getAppServer(), threads: this.threads }, threadId);
  }
}

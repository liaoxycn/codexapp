import { AppServerClient } from "../appServerClient.js";
import type { ClientSnapshot, ThreadStartOptions } from "../protocol.js";
import {
  type BridgeBackendLifecycleDeps,
} from "./bridgeBackendLifecycle.js";
import { BridgeCatalogController } from "./BridgeCatalogController.js";
import { BridgeRuntimeStore } from "./bridgeRuntimeStore.js";
import { ensureActiveAssistantMessage } from "./runtimeAssistantMessages.js";
import {
  handleCurrentApproval,
} from "./approvalActions.js";
import {
  handlePromptSubmission,
  interruptRunningTurn,
  resendPromptFromTurn,
  rollbackThreadTurns,
} from "./promptActions.js";
import {
  ensureResumedThread,
} from "./threadSubscriptions.js";
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

  async createThread(cwd?: string, options: ThreadStartOptions = {}): Promise<ClientSnapshot> {
    return this.catalog.createThread(cwd, options);
  }

  async forkThread(threadId: string, numTurns?: number): Promise<ClientSnapshot> {
    return this.catalog.forkThread(threadId, numTurns);
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

  async refreshThreads(selectedThreadId?: string, requestedSelectionVersion?: number): Promise<ClientSnapshot> {
    return this.catalog.refreshThreads(selectedThreadId, requestedSelectionVersion);
  }

  async loadOlderMessages(threadId: string): Promise<ClientSnapshot> {
    return this.catalog.loadOlderMessages(threadId);
  }

  async sendPrompt(threadId: string, text: string, options: ThreadStartOptions = {}): Promise<ClientSnapshot> {
    const resolved = this.runtime.resolveThreadId(threadId);
    const state = await this.ensureResumed(resolved);
    return handlePromptSubmission({
      appServer: this.getAppServer(),
      emitChanged: () => this.runtime.emitChanged(),
      getSnapshot: (selectedThreadId) => this.runtime.getSnapshot(selectedThreadId),
      options,
      state,
      text,
      threadId: resolved,
      threads: this.threads,
      updateSummaryStatus: (targetThreadId, status) => this.runtime.updateSummaryStatus(targetThreadId, status),
    });
  }

  async rollbackThread(threadId: string, numTurns: number): Promise<ClientSnapshot> {
    const resolved = this.runtime.resolveThreadId(threadId);
    const state = await this.ensureResumed(resolved);
    return rollbackThreadTurns({
      appServer: this.getAppServer(),
      emitChanged: () => this.runtime.emitChanged(),
      getSnapshot: (selectedThreadId) => this.runtime.getSnapshot(selectedThreadId),
      state,
      threadId: resolved,
      threads: this.threads,
      updateSummaryStatus: (targetThreadId, status) => this.runtime.updateSummaryStatus(targetThreadId, status),
    }, numTurns);
  }

  async resendPrompt(
    threadId: string,
    text: string,
    rollbackNumTurns: number,
    options: ThreadStartOptions = {}
  ): Promise<ClientSnapshot> {
    const resolved = this.runtime.resolveThreadId(threadId);
    const state = await this.ensureResumed(resolved);
    return resendPromptFromTurn({
      appServer: this.getAppServer(),
      emitChanged: () => this.runtime.emitChanged(),
      getSnapshot: (selectedThreadId) => this.runtime.getSnapshot(selectedThreadId),
      options,
      state,
      text,
      threadId: resolved,
      threads: this.threads,
      updateSummaryStatus: (targetThreadId, status) => this.runtime.updateSummaryStatus(targetThreadId, status),
    }, rollbackNumTurns);
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
      respondToServerRequest: (id, result) => this.getAppServer().respond(id, result),
      respondToServerRequestError: (id, code, message, data) =>
        this.getAppServer().respondError(id, code, message, data),
      emitChanged: () => this.runtime.emitChanged(),
      hydrateThreads: async () => this.hydrateThreads(),
      refreshThread: async (threadId: string) => this.catalog.refreshThread(threadId),
      ensureActiveAssistantMessage: (state, turnId) => ensureActiveAssistantMessage(state, turnId),
      pushOperationalNotice: (notice) => this.runtime.pushOperationalNotice(notice),
      updateSummaryStatus: (threadId, status) => this.runtime.updateSummaryStatus(threadId, status),
    };
  }

  private async ensureResumed(threadId: string): Promise<ThreadRuntimeState> {
    return ensureResumedThread({ appServer: this.getAppServer(), threads: this.threads }, threadId);
  }
}

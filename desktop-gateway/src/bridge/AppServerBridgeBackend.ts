import type {
  JsonRpcNotification,
  JsonRpcServerRequest,
} from "../appServerTypes.js";
import process from "node:process";
import { AppServerClient } from "../appServerClient.js";
import type { ClientSnapshot, GatewayConfigOptionsPayload, ThreadStartOptions } from "../protocol.js";
import {
  handleBridgeBackendNotification,
  handleBridgeBackendServerRequest,
} from "./bridgeBackendLifecycle.js";
import { BridgeThreadController } from "./BridgeThreadController.js";
import { BridgeRuntimeStore } from "./bridgeRuntimeStore.js";

export class AppServerBridgeBackend {
  private readonly runtime = new BridgeRuntimeStore();
  private _appServer = new AppServerClient();
  private readonly controller = new BridgeThreadController(
    this.runtime,
    () => this.appServer
  );
  readonly threads = this.runtime.threads;

  get appServer(): AppServerClient {
    return this._appServer;
  }

  set appServer(value: AppServerClient) {
    this._appServer = value;
  }

  async start(): Promise<void> {
    await this.appServer.start();
    await this.refreshConfigOptions();
    await this.controller.hydrateThreads();

    this.appServer.onNotification((notification) => {
      void this.handleNotification(notification);
    });
    this.appServer.onRequest((request) => {
      this.handleServerRequest(request);
    });
    this.appServer.onExit((event) => {
      this.runtime.markAllThreadsFailed(
        `app-server exited: code=${event.code ?? "null"} signal=${event.signal ?? "null"}`
      );
    });
  }

  subscribe(listener: () => void): () => void {
    return this.runtime.subscribe(listener);
  }

  hasThread(threadId: string): boolean {
    return this.runtime.hasThread(threadId);
  }

  getDefaultThreadId(): string {
    return this.runtime.getDefaultThreadId();
  }

  getSnapshot(selectedThreadId?: string): ClientSnapshot {
    return this.runtime.getSnapshot(selectedThreadId);
  }

  async selectThread(threadId: string): Promise<ClientSnapshot> {
    return this.controller.selectThread(threadId);
  }

  async createThread(cwd?: string, options: ThreadStartOptions = {}): Promise<ClientSnapshot> {
    return this.controller.createThread(cwd, options);
  }

  async forkThread(threadId: string, numTurns?: number): Promise<ClientSnapshot> {
    return this.controller.forkThread(threadId, numTurns);
  }

  async renameThread(threadId: string, name: string): Promise<ClientSnapshot> {
    return this.controller.renameThread(threadId, name);
  }

  async archiveThread(threadId: string): Promise<ClientSnapshot> {
    return this.controller.archiveThread(threadId);
  }

  async unarchiveThread(threadId: string): Promise<ClientSnapshot> {
    return this.controller.unarchiveThread(threadId);
  }

  async refreshThreads(selectedThreadId?: string): Promise<ClientSnapshot> {
    const selectionVersionAtRequest = this.runtime.selectionVersion;
    await this.refreshConfigOptions();
    return this.controller.refreshThreads(selectedThreadId, selectionVersionAtRequest);
  }

  async loadOlderMessages(threadId: string): Promise<ClientSnapshot> {
    return this.controller.loadOlderMessages(threadId);
  }

  async sendPrompt(threadId: string, text: string, options: ThreadStartOptions = {}): Promise<ClientSnapshot> {
    return this.controller.sendPrompt(threadId, text, options);
  }

  async rollbackThread(threadId: string, numTurns: number): Promise<ClientSnapshot> {
    return this.controller.rollbackThread(threadId, numTurns);
  }

  async resendPrompt(
    threadId: string,
    text: string,
    rollbackNumTurns: number,
    options: ThreadStartOptions = {}
  ): Promise<ClientSnapshot> {
    return this.controller.resendPrompt(threadId, text, rollbackNumTurns, options);
  }

  async stopTurn(threadId: string): Promise<ClientSnapshot> {
    return this.controller.stopTurn(threadId);
  }

  async approveCurrent(threadId: string, allow: boolean): Promise<ClientSnapshot> {
    return this.controller.approveCurrent(threadId, allow);
  }

  private async handleNotification(notification: JsonRpcNotification): Promise<void> {
    await handleBridgeBackendNotification(notification, this.controller.lifecycleDeps());
  }

  private handleServerRequest(request: JsonRpcServerRequest): void {
    handleBridgeBackendServerRequest(request, this.controller.lifecycleDeps());
  }

  private async refreshConfigOptions(): Promise<void> {
    try {
      if (typeof this.appServer.configOptions !== "function") {
        return;
      }
      this.runtime.configOptions = await this.appServer.configOptions(process.cwd());
    } catch (error) {
      console.error("[gateway] config options unavailable:", error instanceof Error ? error.message : error);
    }
  }
}

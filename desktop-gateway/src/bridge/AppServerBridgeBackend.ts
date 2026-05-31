import type {
  JsonRpcNotification,
  JsonRpcServerRequest,
} from "../appServerTypes.js";
import { AppServerClient } from "../appServerClient.js";
import type { ClientSnapshot } from "../protocol.js";
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

  async createThread(cwd?: string): Promise<ClientSnapshot> {
    return this.controller.createThread(cwd);
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
    return this.controller.refreshThreads(selectedThreadId);
  }

  async loadOlderMessages(threadId: string): Promise<ClientSnapshot> {
    return this.controller.loadOlderMessages(threadId);
  }

  async sendPrompt(threadId: string, text: string): Promise<ClientSnapshot> {
    return this.controller.sendPrompt(threadId, text);
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
}

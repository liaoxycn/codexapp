import { InMemoryDesktopBackend } from "../backend.js";
import { AppServerBridgeBackend } from "../bridgeBackend.js";
import type { ClientSnapshot } from "../protocol.js";
import {
  refreshSelectedThread,
  refreshThreadList,
  runBackendAction,
  sendSnapshot,
  sendStatus,
} from "./backendActions.js";
import { handleClientMessage } from "./clientMessages.js";
import { restartCodexDesktop } from "./httpPoke.js";
import type { Backend, ClientContext, RefreshSource } from "./types.js";

interface GatewayBackendControllerOptions {
  pairToken?: string;
  workspacePath?: string;
}

interface GatewayBackendControllerDeps {
  appBackend?: RealBackend;
  restartDesktop?: typeof restartCodexDesktop;
  mockBackend?: Backend;
}

type RealBackend = Backend & { start(): Promise<void> };

export class GatewayBackendController {
  private readonly mockBackend: Backend;
  private readonly appBackend: RealBackend;
  private readonly restartDesktopFn: typeof restartCodexDesktop;
  private desktopRestartRequired = false;
  private useRealBackend = false;

  constructor(
    private readonly options: GatewayBackendControllerOptions,
    deps: GatewayBackendControllerDeps = {}
  ) {
    this.mockBackend = deps.mockBackend ?? new InMemoryDesktopBackend(options.workspacePath);
    this.appBackend = deps.appBackend ?? new AppServerBridgeBackend();
    this.restartDesktopFn = deps.restartDesktop ?? restartCodexDesktop;
  }

  async startRealBackend(): Promise<void> {
    try {
      await this.appBackend.start();
      this.useRealBackend = true;
      console.log("[gateway] real app-server backend enabled");
    } catch (error) {
      this.useRealBackend = false;
      console.error("[gateway] real app-server backend unavailable, fallback to mock:", error);
    }
  }

  backend(): Backend {
    return this.useRealBackend ? this.appBackend : this.mockBackend;
  }

  backendLabel(): "app-server" | "mock" {
    return this.useRealBackend ? "app-server" : "mock";
  }

  async handleMessage(context: ClientContext, raw: string): Promise<void> {
    await handleClientMessage(context, raw, {
      backend: () => this.backend(),
      pairToken: this.options.pairToken,
      sendStatus,
      sendSnapshot: (nextContext, snapshot) => this.sendSnapshot(nextContext, snapshot),
      runBackendAction: (nextContext, action) => this.runBackendAction(nextContext, action),
      refreshSelectedThread: (nextContext, source) => this.refreshSelectedThread(nextContext, source),
      markDesktopRestartRequired: (reason) => this.markDesktopRestartRequired(reason),
      restartDesktop: () => this.restartDesktop(),
    });
  }

  sendSnapshot(context: ClientContext, snapshot: ClientSnapshot): void {
    sendSnapshot(context, this.withDesktopRestartState(snapshot), {
      refreshSelectedThread: (nextContext, source) => this.refreshSelectedThread(nextContext, source),
      refreshThreadList: (nextContext) => this.refreshThreadList(nextContext),
    });
  }

  async runBackendAction(
    context: ClientContext,
    action: () => ClientSnapshot | Promise<ClientSnapshot>
  ): Promise<void> {
    await runBackendAction(context, action, this.serverActionHandlers());
  }

  async refreshSelectedThread(
    context: ClientContext,
    source: RefreshSource
  ): Promise<void> {
    await refreshSelectedThread(context, source, this.serverActionHandlers());
  }

  async refreshThreadList(context: ClientContext): Promise<void> {
    await refreshThreadList(context, this.serverActionHandlers());
  }

  private markDesktopRestartRequired(reason: string): void {
    if (!this.desktopRestartRequired) {
      console.log(`[gateway] desktop restart required reason=${reason}`);
    }
    this.desktopRestartRequired = true;
  }

  private async restartDesktop(): Promise<void> {
    try {
      console.log("[gateway] restarting Codex desktop by client request");
      const result = await this.restartDesktopFn({
        reason: "client_restart_desktop",
        source: "desktop-gateway",
        timestamp: Date.now(),
      });
      if (result.ok) {
        this.desktopRestartRequired = false;
      }
      console.log(`[gateway] desktop restart result=${JSON.stringify(result)}`);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      console.warn(`[gateway] desktop restart unavailable: ${detail}`);
      throw error;
    }
  }

  private withDesktopRestartState(snapshot: ClientSnapshot): ClientSnapshot {
    return {
      ...snapshot,
      desktopRestartRequired: this.desktopRestartRequired,
    };
  }

  private serverActionHandlers() {
    return {
      backend: () => this.backend(),
      sendSnapshot: (context: ClientContext, snapshot: ClientSnapshot) => this.sendSnapshot(context, snapshot),
      sendStatus,
      refreshSelectedThread: (context: ClientContext, source: RefreshSource) =>
        this.refreshSelectedThread(context, source),
      refreshThreadList: (context: ClientContext) => this.refreshThreadList(context),
    };
  }
}

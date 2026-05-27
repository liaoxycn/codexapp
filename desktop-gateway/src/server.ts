import process from "node:process";
import { WebSocketServer, type WebSocket } from "ws";
import { InMemoryDesktopBackend } from "./backend.js";
import { AppServerBridgeBackend } from "./bridgeBackend.js";
import type {
  ClientSnapshot,
  GatewayHelloMessage,
  GatewayIncomingMessage,
  GatewaySnapshotMessage,
  GatewayStatusMessage,
} from "./protocol.js";

interface GatewayServerOptions {
  host: string;
  port: number;
  path: string;
  pairToken?: string;
  workspacePath?: string;
}

interface ClientContext {
  socket: WebSocket;
  selectedThreadId: string;
  authenticated: boolean;
  unsubscribe: () => void;
  snapshotTimer: NodeJS.Timeout | null;
  liveRefreshTimer: NodeJS.Timeout | null;
}

type Backend = {
  subscribe(listener: () => void): () => void;
  hasThread(threadId: string): boolean;
  getDefaultThreadId(): string;
  getSnapshot(selectedThreadId?: string): ClientSnapshot;
  createThread(): ClientSnapshot | Promise<ClientSnapshot>;
  selectThread(threadId: string): ClientSnapshot | Promise<ClientSnapshot>;
  renameThread(threadId: string, name: string): ClientSnapshot | Promise<ClientSnapshot>;
  archiveThread(threadId: string): ClientSnapshot | Promise<ClientSnapshot>;
  unarchiveThread(threadId: string): ClientSnapshot | Promise<ClientSnapshot>;
  refreshThreads(selectedThreadId?: string): ClientSnapshot | Promise<ClientSnapshot>;
  loadOlderMessages(threadId: string): ClientSnapshot | Promise<ClientSnapshot>;
  sendPrompt(threadId: string, text: string): ClientSnapshot | Promise<ClientSnapshot>;
  stopTurn(threadId: string): ClientSnapshot | Promise<ClientSnapshot>;
  approveCurrent(threadId: string, allow: boolean): ClientSnapshot | Promise<ClientSnapshot>;
};

export class GatewayServer {
  private readonly mockBackend: Backend;
  private readonly appBackend = new AppServerBridgeBackend();
  private readonly clients = new Set<ClientContext>();
  private useRealBackend = false;

  constructor(private readonly options: GatewayServerOptions) {
    this.mockBackend = new InMemoryDesktopBackend(options.workspacePath);
  }

  async start(): Promise<WebSocketServer> {
    await this.tryStartRealBackend();
    const wss = new WebSocketServer({
      host: this.options.host,
      port: this.options.port,
      path: this.options.path,
    });

    wss.on("connection", (socket) => {
      console.log("[gateway] android client connected");
      const context: ClientContext = {
        socket,
        selectedThreadId: this.backend().getDefaultThreadId(),
        authenticated: false,
        snapshotTimer: null,
        liveRefreshTimer: null,
        unsubscribe: this.backend().subscribe(() => {
          if (context.authenticated && socket.readyState === socket.OPEN) {
            this.scheduleSnapshot(context);
          }
        }),
      };
      this.clients.add(context);
      this.sendStatus(socket, {
        type: "status",
        status: "connecting",
        detail: "desktop gateway 已连接，等待 hello",
      });

      socket.on("message", (payload) => {
        console.log("[gateway] inbound:", payload.toString());
        void this.handleMessage(context, payload.toString());
      });

      socket.on("close", () => {
        console.log("[gateway] android client disconnected");
        if (context.snapshotTimer) {
          clearTimeout(context.snapshotTimer);
          context.snapshotTimer = null;
        }
        if (context.liveRefreshTimer) {
          clearTimeout(context.liveRefreshTimer);
          context.liveRefreshTimer = null;
        }
        context.unsubscribe();
        this.clients.delete(context);
      });

      socket.on("error", (error) => {
        console.error("[gateway] websocket error:", error.message);
      });
    });

    wss.on("listening", () => {
      console.log(`[gateway] listening on ws://${this.options.host}:${this.options.port}${this.options.path}`);
      console.log(`[gateway] pair token ${this.options.pairToken ? "enabled" : "disabled"}`);
      console.log(`[gateway] backend ${this.useRealBackend ? "app-server" : "mock"}`);
    });

    wss.on("error", (error) => {
      console.error("[gateway] server error:", error.message);
    });

    process.on("SIGINT", () => {
      for (const client of this.clients) {
        client.socket.close(1001, "server shutdown");
      }
      wss.close();
      process.exit(0);
    });

    return wss;
  }

  private async handleMessage(context: ClientContext, raw: string) {
    let message: GatewayIncomingMessage;
    try {
      message = JSON.parse(raw) as GatewayIncomingMessage;
    } catch {
      this.sendStatus(context.socket, {
        type: "status",
        status: "error",
        detail: "消息不是合法 JSON",
      });
      return;
    }

    if (message.type === "hello") {
      this.handleHello(context, message);
      return;
    }

    if (!context.authenticated) {
      console.log("[gateway] ignored pre-auth message:", message.type);
      return;
    }

    switch (message.type) {
      case "create_thread":
        await this.runBackendAction(context, async () => {
          const snapshot = await this.backend().createThread();
          context.selectedThreadId = snapshot.selectedThreadId;
          return snapshot;
        });
        break;
      case "select_thread":
        context.selectedThreadId = this.backend().hasThread(message.threadId)
          ? message.threadId
          : this.backend().getDefaultThreadId();
        await this.runBackendAction(context, async () =>
          this.backend().selectThread(context.selectedThreadId)
        );
        break;
      case "rename_thread":
        await this.runBackendAction(context, async () =>
          this.backend().renameThread(message.threadId, message.name)
        );
        break;
      case "archive_thread":
        await this.runBackendAction(context, async () => {
          const snapshot = await this.backend().archiveThread(message.threadId);
          context.selectedThreadId = snapshot.selectedThreadId;
          return snapshot;
        });
        break;
      case "unarchive_thread":
        await this.runBackendAction(context, async () => {
          const snapshot = await this.backend().unarchiveThread(message.threadId);
          context.selectedThreadId = snapshot.selectedThreadId;
          return snapshot;
        });
        break;
      case "refresh_threads":
        await this.runBackendAction(context, async () => {
          const snapshot = await this.backend().refreshThreads(context.selectedThreadId);
          context.selectedThreadId = snapshot.selectedThreadId;
          return snapshot;
        });
        break;
      case "load_older_messages":
        await this.runBackendAction(context, async () =>
          this.backend().loadOlderMessages(context.selectedThreadId)
        );
        break;
      case "send_prompt":
        await this.runBackendAction(context, async () =>
          this.backend().sendPrompt(context.selectedThreadId, message.text)
        );
        break;
      case "stop_turn":
        await this.runBackendAction(context, async () =>
          this.backend().stopTurn(context.selectedThreadId)
        );
        break;
      case "approve_pending":
        await this.runBackendAction(context, async () =>
          this.backend().approveCurrent(context.selectedThreadId, true)
        );
        break;
      case "reject_pending":
        await this.runBackendAction(context, async () =>
          this.backend().approveCurrent(context.selectedThreadId, false)
        );
        break;
    }
  }

  private handleHello(context: ClientContext, message: GatewayHelloMessage) {
    if (this.options.pairToken && message.pairToken !== this.options.pairToken) {
      console.log("[gateway] pair token rejected");
      this.sendStatus(context.socket, {
        type: "status",
        status: "error",
        detail: "pair token 无效",
      });
      context.socket.close(4001, "invalid pair token");
      return;
    }

    context.authenticated = true;
    console.log(`[gateway] paired client=${message.client ?? "android"} version=${message.version ?? "-"}`);
    this.sendStatus(context.socket, {
      type: "status",
      status: "connected",
      detail: `已配对 ${message.client ?? "android"} ${message.version ?? ""}`.trim(),
    });
    this.sendSnapshot(context, this.backend().getSnapshot(context.selectedThreadId));
  }

  private sendStatus(socket: WebSocket, message: GatewayStatusMessage) {
    socket.send(JSON.stringify(message));
  }

  private sendSnapshot(context: ClientContext, snapshot: ClientSnapshot) {
    const message: GatewaySnapshotMessage = {
      type: "snapshot",
      threads: snapshot.threads,
      selectedThreadId: snapshot.selectedThreadId,
      messages: snapshot.messages,
      hasMoreHistory: snapshot.hasMoreHistory ?? false,
      pendingApproval: snapshot.pendingApproval ?? null,
      chips: snapshot.chips,
      slashCommands: snapshot.slashCommands,
      cwd: snapshot.cwd,
      permissionSummary: snapshot.permissionSummary,
      isGenerating: snapshot.isGenerating,
    };
    context.socket.send(JSON.stringify(message));
    this.scheduleLiveRefresh(context, snapshot);
  }

  private scheduleSnapshot(context: ClientContext): void {
    if (context.snapshotTimer) {
      return;
    }
    context.snapshotTimer = setTimeout(() => {
      context.snapshotTimer = null;
      if (context.socket.readyState !== context.socket.OPEN || !context.authenticated) {
        return;
      }
      this.sendSnapshot(context, this.backend().getSnapshot(context.selectedThreadId));
    }, 120);
  }

  private async runBackendAction(
    context: ClientContext,
    action: () => ClientSnapshot | Promise<ClientSnapshot>
  ): Promise<void> {
    try {
      const snapshot = await action();
      this.sendSnapshot(context, snapshot);
    } catch (error) {
      const detail = error instanceof Error ? error.message : "后端操作失败";
      console.error("[gateway] backend action failed:", detail);
      this.sendStatus(context.socket, {
        type: "status",
        status: "error",
        detail,
      });
      this.sendSnapshot(context, this.backend().getSnapshot(context.selectedThreadId));
    }
  }

  private scheduleLiveRefresh(context: ClientContext, snapshot: ClientSnapshot): void {
    const selectedThread = snapshot.threads.find((thread) => thread.id === snapshot.selectedThreadId);
    const shouldPoll =
      snapshot.isGenerating ||
      snapshot.pendingApproval != null ||
      selectedThread?.status === "running";
    if (!shouldPoll) {
      if (context.liveRefreshTimer) {
        clearTimeout(context.liveRefreshTimer);
        context.liveRefreshTimer = null;
      }
      return;
    }

    if (context.liveRefreshTimer) {
      return;
    }

    context.liveRefreshTimer = setTimeout(() => {
      context.liveRefreshTimer = null;
      if (context.socket.readyState !== context.socket.OPEN || !context.authenticated) {
        return;
      }
      void this.refreshSelectedThread(context);
    }, 1200);
  }

  private async refreshSelectedThread(context: ClientContext): Promise<void> {
    try {
      const snapshot = await this.backend().selectThread(context.selectedThreadId);
      context.selectedThreadId = snapshot.selectedThreadId;
      this.sendSnapshot(context, snapshot);
    } catch (error) {
      const detail = error instanceof Error ? error.message : "刷新运行中会话失败";
      console.error("[gateway] live refresh failed:", detail);
      this.sendStatus(context.socket, {
        type: "status",
        status: "error",
        detail,
      });
    }
  }

  private backend(): Backend {
    return this.useRealBackend ? this.appBackend : this.mockBackend;
  }

  private async tryStartRealBackend() {
    try {
      await this.appBackend.start();
      this.useRealBackend = true;
      console.log("[gateway] real app-server backend enabled");
    } catch (error) {
      this.useRealBackend = false;
      console.error("[gateway] real app-server backend unavailable, fallback to mock:", error);
    }
  }
}

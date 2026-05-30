import process from "node:process";
import { spawnSync } from "node:child_process";
import { URL } from "node:url";
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
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
  listRefreshTimer: NodeJS.Timeout | null;
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

const LIST_REFRESH_INTERVAL_MS = 2500;

export class GatewayServer {
  private readonly mockBackend: Backend;
  private readonly appBackend = new AppServerBridgeBackend();
  private readonly clients = new Set<ClientContext>();
  private useRealBackend = false;

  constructor(private readonly options: GatewayServerOptions) {
    this.mockBackend = new InMemoryDesktopBackend(options.workspacePath);
  }

  async start(): Promise<void> {
    await this.tryStartRealBackend();
    const server = createServer((request, response) => {
      void this.handleHttp(request, response);
    });
    const wss = new WebSocketServer({ noServer: true });

    server.on("upgrade", (request, socket, head) => {
      const requestPath = new URL(request.url ?? "/", "ws://localhost").pathname.replace(/\/+$/, "");
      const expectedPath = this.options.path.replace(/\/+$/, "");
      if (requestPath !== expectedPath) {
        socket.destroy();
        return;
      }
      wss.handleUpgrade(request, socket, head, (ws) => {
        wss.emit("connection", ws, request);
      });
    });

    wss.on("connection", (socket, request) => {
      const requestPath = new URL(request.url ?? "/", "ws://localhost").pathname.replace(/\/+$/, "");
      const expectedPath = this.options.path.replace(/\/+$/, "");
      if (requestPath !== expectedPath) {
        console.warn(`[gateway] path mismatch request=${requestPath} expected=${expectedPath}`);
      }
      console.log("[gateway] android client connected");
      const context: ClientContext = {
        socket,
        selectedThreadId: this.backend().getDefaultThreadId(),
        authenticated: false,
        snapshotTimer: null,
        liveRefreshTimer: null,
        listRefreshTimer: null,
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
        if (context.listRefreshTimer) {
          clearTimeout(context.listRefreshTimer);
          context.listRefreshTimer = null;
        }
        context.unsubscribe();
        this.clients.delete(context);
      });

      socket.on("error", (error) => {
        console.error("[gateway] websocket error:", error.message);
      });
    });

    server.listen(this.options.port, this.options.host, () => {
      console.log(`[gateway] listening on ws://0.0.0.0:${this.options.port}${this.options.path}`);
      console.log(`[gateway] listening on http://0.0.0.0:${this.options.port}/poke`);
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
      server.close();
      process.exit(0);
    });

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
        await this.runBackendAction(context, async () => {
          const snapshot = await this.backend().sendPrompt(message.threadId?.trim() || context.selectedThreadId, message.text);
          console.log(`[gateway] send_prompt backend ok thread=${snapshot.selectedThreadId || context.selectedThreadId}`);
          void this.pokeDesktop(snapshot.selectedThreadId || context.selectedThreadId, "send_prompt");
          return snapshot;
        });
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
    this.scheduleListRefresh(context);
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
    this.scheduleListRefresh(context);
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
      selectedThread?.status === "running" ||
      selectedThread?.status === "needs_approval";
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
      const snapshot = await this.backend().refreshThreads(context.selectedThreadId);
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

  private scheduleListRefresh(context: ClientContext): void {
    if (context.listRefreshTimer || !context.authenticated) {
      return;
    }
    context.listRefreshTimer = setTimeout(() => {
      context.listRefreshTimer = null;
      if (context.socket.readyState !== context.socket.OPEN || !context.authenticated) {
        return;
      }
      void this.refreshThreadList(context);
    }, LIST_REFRESH_INTERVAL_MS);
  }

  private async refreshThreadList(context: ClientContext): Promise<void> {
    try {
      const snapshot = await this.backend().refreshThreads(context.selectedThreadId);
      context.selectedThreadId = snapshot.selectedThreadId;
      this.sendSnapshot(context, snapshot);
    } catch (error) {
      const detail = error instanceof Error ? error.message : "刷新会话列表失败";
      console.error("[gateway] list refresh failed:", detail);
      this.sendStatus(context.socket, {
        type: "status",
        status: "error",
        detail,
      });
      this.scheduleListRefresh(context);
    }
  }

  private async pokeDesktop(threadId: string, reason: string): Promise<void> {
    try {
      console.log(`[gateway] desktop poke -> /poke reason=${reason} thread=${threadId}`);
      const result = await this.focusDesktopWindow({
        reason,
        threadId,
        source: "desktop-gateway",
        timestamp: Date.now(),
      });
      console.log(`[gateway] desktop poke sent body=${JSON.stringify(result)}`);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      console.warn(`[gateway] desktop poke unavailable: ${detail}`);
    }
  }

  private async handleHttp(request: IncomingMessage, response: ServerResponse): Promise<void> {
    const url = new URL(request.url ?? "/", "http://localhost");
    if (request.method !== "POST" || url.pathname.replace(/\/+$/, "") !== "/poke") {
      response.statusCode = 404;
      response.end("not found");
      return;
    }

    try {
      const body = await this.readRequestBody(request);
      const payload = body.trim().length > 0 ? JSON.parse(body) as { reason?: string; threadId?: string; source?: string; timestamp?: number } : {};
      console.log(`[gateway] poke received ${body}`);
      const result = await this.focusDesktopWindow(payload);
      response.statusCode = 200;
      response.setHeader("content-type", "application/json; charset=utf-8");
      response.end(JSON.stringify({ ok: result.ok }));
      console.log(`[gateway] poke result ok=${result.ok}`);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      console.warn(`[gateway] poke failed: ${detail}`);
      response.statusCode = 500;
      response.setHeader("content-type", "application/json; charset=utf-8");
      response.end(JSON.stringify({ ok: false, error: detail }));
    }
  }

  private async focusDesktopWindow(payload: { reason?: string; threadId?: string; source?: string; timestamp?: number }): Promise<{ ok: boolean }> {
    if (process.platform !== "win32") {
      return { ok: false };
    }

    const script = `
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
$payload = $env:CODEX_MOBILE_POKE_PAYLOAD_JSON | ConvertFrom-Json
$proc = Get-Process | Where-Object { $_.MainWindowTitle -eq 'Codex' -or $_.MainWindowTitle -like 'Codex*' } | Select-Object -First 1
if (-not $proc) {
  Write-Output (ConvertTo-Json @{ ok = $false; error = 'Codex window not found' })
  exit 0
}
$shell = New-Object -ComObject WScript.Shell
try { $null = $shell.AppActivate($proc.Id) } catch {}
try {
  $sig = @"
using System;
using System.Runtime.InteropServices;
public static class WinApi {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
}
"@
  Add-Type -TypeDefinition $sig -ErrorAction SilentlyContinue | Out-Null
  [WinApi]::ShowWindowAsync($proc.MainWindowHandle, 5) | Out-Null
  [WinApi]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
} catch {}
Start-Sleep -Milliseconds 180
$steps = @(
  @{ Name = 'escape'; Keys = '{ESC}' },
  @{ Name = 'reload'; Keys = '^r' },
  @{ Name = 'hard_reload'; Keys = '^+r' },
  @{ Name = 'f5'; Keys = '{F5}' }
)
foreach ($step in $steps) {
  [System.Windows.Forms.SendKeys]::SendWait($step.Keys)
  Start-Sleep -Milliseconds 180
}
Write-Output (ConvertTo-Json @{ ok = $true; pid = $proc.Id; title = $proc.MainWindowTitle; reason = $payload.reason; threadId = $payload.threadId })
`;

    const result = spawnSync("powershell.exe", [
      "-NoProfile",
      "-ExecutionPolicy",
      "Bypass",
      "-Command",
      script,
    ], {
      encoding: "utf8",
      env: {
        ...process.env,
        CODEX_MOBILE_POKE_PAYLOAD_JSON: JSON.stringify(payload),
      },
      timeout: 4000,
      windowsHide: true,
    });

    if (result.error) {
      throw result.error;
    }
    if (result.status !== 0) {
      throw new Error((result.stderr || result.stdout || `powershell exit ${result.status}`).trim());
    }

    const text = (result.stdout || "").trim();
    if (!text) {
      return { ok: true };
    }
    try {
      return JSON.parse(text) as { ok: boolean };
    } catch {
      return { ok: true };
    }
  }

  private async readRequestBody(request: IncomingMessage): Promise<string> {
    return await new Promise<string>((resolve, reject) => {
      const chunks: Buffer[] = [];
      request.on("data", (chunk) => chunks.push(Buffer.from(chunk)));
      request.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
      request.on("error", reject);
    });
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

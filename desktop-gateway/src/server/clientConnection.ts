import type { IncomingMessage } from "node:http";
import type { WebSocket } from "ws";
import { clearClientTimers, scheduleSnapshot } from "./liveRefresh.js";
import { requestGatewayPath } from "./gatewayPaths.js";
import type { Backend, ClientContext } from "./types.js";

interface CreateGatewayClientContextArgs {
  backend: () => Backend;
  socket: WebSocket;
  sendSnapshot: (context: ClientContext, snapshot: ReturnType<Backend["getSnapshot"]>) => void;
}

interface AttachGatewayClientSocketArgs {
  context: ClientContext;
  handleMessage: (context: ClientContext, raw: string) => Promise<void>;
  onClosed: (context: ClientContext) => void;
}

export function logGatewayConnectionPathMismatch(request: IncomingMessage, expectedPath: string): void {
  const requestPath = requestGatewayPath(request.url);
  if (requestPath !== expectedPath) {
    console.warn(`[gateway] path mismatch request=${requestPath} expected=${expectedPath}`);
  }
}

export function createGatewayClientContext({
  backend,
  socket,
  sendSnapshot,
}: CreateGatewayClientContextArgs): ClientContext {
  const context: ClientContext = {
    socket,
    selectedThreadId: "",
    selectionVersion: 0,
    authenticated: false,
    snapshotTimer: null,
    liveRefreshTimer: null,
    listRefreshTimer: null,
    lastSnapshotPayload: null,
    lastSnapshotMessage: null,
    snapshotRevision: 0,
    supportsSnapshotPatch: false,
    currentAction: null,
    actionTraceType: null,
    unsubscribe: backend().subscribe(() => {
      if (context.authenticated && socket.readyState === socket.OPEN) {
        scheduleSnapshot(context, backend, (nextContext, snapshot) => sendSnapshot(nextContext, snapshot));
      }
    }),
  };
  return context;
}

export function attachGatewayClientSocket({
  context,
  handleMessage,
  onClosed,
}: AttachGatewayClientSocketArgs): void {
  let messageQueue = Promise.resolve();
  context.socket.on("message", (payload) => {
    const raw = payload.toString();
    console.log(`[gateway] inbound ${summarizeInboundMessage(raw)}`);
    messageQueue = messageQueue
      .then(() => handleMessage(context, raw))
      .catch((error) => {
        const detail = error instanceof Error ? error.message : String(error);
        console.error("[gateway] client message failed:", detail);
      });
  });

  context.socket.on("close", () => {
    console.log("[gateway] android client disconnected");
    clearClientTimers(context);
    context.unsubscribe();
    onClosed(context);
  });

  context.socket.on("error", (error) => {
    console.error("[gateway] websocket error:", error.message);
  });
}

export function summarizeInboundMessage(raw: string): string {
  try {
    const message = JSON.parse(raw) as Record<string, unknown>;
    const type = typeof message.type === "string" ? message.type : "unknown";
    const parts = [`type=${type}`];
    if (typeof message.threadId === "string" && message.threadId.trim()) {
      parts.push(`thread=${message.threadId.trim()}`);
    }
    if (message.newThread === true) {
      parts.push("newThread=true");
    }
    if (typeof message.text === "string") {
      parts.push(`textLen=${message.text.length}`);
    }
    if (typeof message.numTurns === "number") {
      parts.push(`numTurns=${message.numTurns}`);
    }
    if (typeof message.rollbackNumTurns === "number") {
      parts.push(`rollbackNumTurns=${message.rollbackNumTurns}`);
    }
    if (Array.isArray(message.capabilities)) {
      parts.push(`capabilities=${message.capabilities.length}`);
    }
    return parts.join(" ");
  } catch {
    return "invalid-json";
  }
}

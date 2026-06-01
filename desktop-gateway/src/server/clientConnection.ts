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
    selectedThreadId: backend().getDefaultThreadId(),
    selectionVersion: 0,
    authenticated: false,
    snapshotTimer: null,
    liveRefreshTimer: null,
    listRefreshTimer: null,
    lastSnapshotPayload: null,
    lastSnapshotMessage: null,
    snapshotRevision: 0,
    supportsSnapshotPatch: false,
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
  context.socket.on("message", (payload) => {
    console.log("[gateway] inbound:", payload.toString());
    void handleMessage(context, payload.toString());
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

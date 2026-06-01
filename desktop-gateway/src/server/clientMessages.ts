import type { WebSocket } from "ws";
import type {
  ClientSnapshot,
  GatewayHelloMessage,
  GatewayIncomingMessage,
  GatewayStatusMessage,
} from "../protocol.js";
import type { Backend, ClientContext, RefreshSource } from "./types.js";
import { handleThreadClientMessage } from "./clientThreadMessages.js";
import { handleTurnClientMessage } from "./clientTurnMessages.js";

export type ClientMessageHandlers = {
  backend: () => Backend;
  pairToken?: string;
  sendStatus: (socket: WebSocket, message: GatewayStatusMessage) => void;
  sendSnapshot: (context: ClientContext, snapshot: ClientSnapshot) => void;
  runBackendAction: (
    context: ClientContext,
    action: () => ClientSnapshot | Promise<ClientSnapshot>
  ) => Promise<void>;
  refreshSelectedThread: (context: ClientContext, source: RefreshSource) => Promise<void>;
  pokeDesktop: (threadId: string, reason: string) => Promise<void>;
};

export async function handleClientMessage(
  context: ClientContext,
  raw: string,
  handlers: ClientMessageHandlers
): Promise<void> {
  let message: GatewayIncomingMessage;
  try {
    message = JSON.parse(raw) as GatewayIncomingMessage;
  } catch {
    handlers.sendStatus(context.socket, {
      type: "status",
      status: "error",
      detail: "消息不是合法 JSON",
    });
    return;
  }

  if (message.type === "hello") {
    handleHello(context, message, handlers);
    return;
  }

  if (!context.authenticated) {
    console.log("[gateway] ignored pre-auth message:", message.type);
    return;
  }

  if (await handleThreadClientMessage(context, message, handlers)) {
    return;
  }

  if (await handleTurnClientMessage(context, message, handlers)) {
    return;
  }
}

function handleHello(
  context: ClientContext,
  message: GatewayHelloMessage,
  handlers: Pick<ClientMessageHandlers, "backend" | "pairToken" | "sendSnapshot" | "sendStatus">
): void {
  if (handlers.pairToken && message.pairToken !== handlers.pairToken) {
    console.log("[gateway] pair token rejected");
    handlers.sendStatus(context.socket, {
      type: "status",
      status: "error",
      detail: "pair token 无效",
    });
    context.socket.close(4001, "invalid pair token");
    return;
  }

  context.authenticated = true;
  context.supportsSnapshotPatch = message.capabilities?.includes("snapshot_patch") ?? false;
  console.log(`[gateway] paired client=${message.client ?? "android"} version=${message.version ?? "-"}`);
  handlers.sendStatus(context.socket, {
    type: "status",
    status: "connected",
    detail: `已配对 ${message.client ?? "android"} ${message.version ?? ""}`.trim(),
  });
  handlers.sendSnapshot(context, handlers.backend().getSnapshot(context.selectedThreadId));
}

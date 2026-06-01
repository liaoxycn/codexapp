import type { WebSocket } from "ws";
import type {
  ClientSnapshot,
  GatewaySnapshotMessage,
  GatewayStatusMessage,
} from "../protocol.js";
import {
  refreshSelectedThread as refreshSelectedThreadState,
  scheduleListRefresh,
  scheduleLiveRefresh,
} from "./liveRefresh.js";
import type { Backend, ClientContext, RefreshSource } from "./types.js";

type RefreshHandlers = {
  backend: () => Backend;
  sendSnapshot: (context: ClientContext, snapshot: ClientSnapshot) => void;
  sendStatus: (socket: WebSocket, message: GatewayStatusMessage) => void;
};

type SnapshotHandlers = {
  refreshSelectedThread: (context: ClientContext, source: RefreshSource) => Promise<void>;
  refreshThreadList: (context: ClientContext) => Promise<void>;
};

export function sendStatus(socket: WebSocket, message: GatewayStatusMessage): void {
  socket.send(JSON.stringify(message));
}

export function buildSnapshotMessage(snapshot: ClientSnapshot): GatewaySnapshotMessage {
  return {
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
}

export function sendSnapshot(
  context: ClientContext,
  snapshot: ClientSnapshot,
  handlers: SnapshotHandlers
): void {
  const payload = JSON.stringify(buildSnapshotMessage(snapshot));
  if (context.lastSnapshotPayload !== payload) {
    context.lastSnapshotPayload = payload;
    context.socket.send(payload);
  }
  scheduleLiveRefresh(context, snapshot, handlers.refreshSelectedThread);
  scheduleListRefresh(context, handlers.refreshThreadList);
}

export async function runBackendAction(
  context: ClientContext,
  action: () => ClientSnapshot | Promise<ClientSnapshot>,
  handlers: RefreshHandlers & SnapshotHandlers
): Promise<void> {
  try {
    const snapshot = await action();
    handlers.sendSnapshot(context, snapshot);
  } catch (error) {
    const detail = error instanceof Error ? error.message : "后端操作失败";
    console.error("[gateway] backend action failed:", detail);
    handlers.sendStatus(context.socket, {
      type: "status",
      status: "error",
      detail,
    });
    handlers.sendSnapshot(context, handlers.backend().getSnapshot(context.selectedThreadId));
  }
}

export async function refreshSelectedThread(
  context: ClientContext,
  source: RefreshSource,
  handlers: RefreshHandlers & SnapshotHandlers
): Promise<void> {
  await refreshSelectedThreadState(context, source, {
    backend: handlers.backend,
    sendSnapshot: handlers.sendSnapshot,
    sendStatus: handlers.sendStatus,
  });
}

export async function refreshThreadList(
  context: ClientContext,
  handlers: RefreshHandlers & SnapshotHandlers
): Promise<void> {
  await handlers.refreshSelectedThread(context, "list");
  scheduleListRefresh(context, handlers.refreshThreadList);
}

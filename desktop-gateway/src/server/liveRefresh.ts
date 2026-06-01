import type { WebSocket } from "ws";
import type { ClientSnapshot, GatewayStatusMessage } from "../protocol.js";
import type { Backend, ClientContext, RefreshSource } from "./types.js";
import { LIST_REFRESH_INTERVAL_MS } from "./types.js";

type RefreshContext = {
  backend: () => Backend;
  sendSnapshot: (context: ClientContext, snapshot: ClientSnapshot) => void;
  sendStatus: (socket: WebSocket, message: GatewayStatusMessage) => void;
};

export function clearClientTimers(context: ClientContext): void {
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
}

export function scheduleSnapshot(
  context: ClientContext,
  backend: () => Backend,
  sendSnapshot: (context: ClientContext, snapshot: ClientSnapshot) => void
): void {
  if (context.snapshotTimer) {
    return;
  }
  context.snapshotTimer = setTimeout(() => {
    context.snapshotTimer = null;
    if (context.socket.readyState !== context.socket.OPEN || !context.authenticated) {
      return;
    }
    sendSnapshot(context, backend().getSnapshot(context.selectedThreadId));
  }, 120);
}

export function scheduleLiveRefresh(
  context: ClientContext,
  snapshot: ClientSnapshot,
  refreshSelectedThread: (context: ClientContext, source: RefreshSource) => Promise<void>
): void {
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
    void refreshSelectedThread(context, "live");
  }, 1200);
}

export function scheduleListRefresh(
  context: ClientContext,
  refreshThreadList: (context: ClientContext) => Promise<void>
): void {
  if (context.listRefreshTimer || !context.authenticated) {
    return;
  }
  context.listRefreshTimer = setTimeout(() => {
    context.listRefreshTimer = null;
    if (context.socket.readyState !== context.socket.OPEN || !context.authenticated) {
      return;
    }
    void refreshThreadList(context);
  }, LIST_REFRESH_INTERVAL_MS);
}

export async function refreshSelectedThread(
  context: ClientContext,
  source: RefreshSource,
  handlers: RefreshContext
): Promise<void> {
  const requestedThreadId = context.selectedThreadId;
  const requestedVersion = context.selectionVersion;
  try {
    const snapshot = await handlers.backend().refreshThreads(requestedThreadId);
    if (isStaleRefresh(context, requestedThreadId, requestedVersion)) {
      console.log(`[gateway] ignored stale ${source} refresh for ${requestedThreadId}`);
      return;
    }
    context.selectedThreadId = snapshot.selectedThreadId;
    handlers.sendSnapshot(context, snapshot);
  } catch (error) {
    if (isStaleRefresh(context, requestedThreadId, requestedVersion)) {
      console.log(`[gateway] ignored stale ${source} refresh error for ${requestedThreadId}`);
      return;
    }
    const detail = error instanceof Error ? error.message : "刷新会话失败";
    console.error(`[gateway] ${source} refresh failed:`, detail);
    handlers.sendStatus(context.socket, {
      type: "status",
      status: "error",
      detail,
    });
  }
}

function isStaleRefresh(
  context: ClientContext,
  requestedThreadId: string,
  requestedVersion: number
): boolean {
  return context.selectionVersion !== requestedVersion || context.selectedThreadId !== requestedThreadId;
}

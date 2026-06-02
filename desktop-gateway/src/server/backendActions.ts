import type { WebSocket } from "ws";
import type {
  ClientSnapshot,
  GatewaySnapshotPatchMessage,
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
    files: snapshot.files,
    slashCommands: snapshot.slashCommands,
    cwd: snapshot.cwd,
    permissionSummary: snapshot.permissionSummary,
    sessionConfig: snapshot.sessionConfig,
    configOptions: snapshot.configOptions,
    operationalNotices: snapshot.operationalNotices ?? [],
    desktopRestartRequired: snapshot.desktopRestartRequired ?? false,
    tokenUsage: snapshot.tokenUsage ?? null,
    diagnostics: snapshot.diagnostics ?? {
      selectedThreadId: snapshot.selectedThreadId,
      isGenerating: snapshot.isGenerating,
      runningThreadIds: snapshot.threads.filter((thread) => thread.status === "running").map((thread) => thread.id),
      snapshotRevision: 0,
    },
    isGenerating: snapshot.isGenerating,
  };
}

type SnapshotPatchField = GatewaySnapshotPatchMessage["changed"][number];

const PATCH_FIELDS: SnapshotPatchField[] = [
  "threads",
  "selectedThreadId",
  "messages",
  "hasMoreHistory",
  "pendingApproval",
  "chips",
  "files",
  "slashCommands",
  "cwd",
  "permissionSummary",
  "sessionConfig",
  "configOptions",
  "operationalNotices",
  "desktopRestartRequired",
  "tokenUsage",
  "diagnostics",
  "isGenerating",
];

export function buildSnapshotPatchMessage(
  previous: GatewaySnapshotMessage,
  next: GatewaySnapshotMessage,
  baseRevision: number,
  revision: number
): GatewaySnapshotPatchMessage | null {
  const changed = PATCH_FIELDS.filter((field) => !sameSnapshotField(previous[field], next[field]));
  if (changed.length === 0) {
    return null;
  }

  const patch: GatewaySnapshotPatchMessage = {
    type: "snapshot_patch",
    baseRevision,
    revision,
    changed,
  };
  for (const field of changed) {
    patch[field] = next[field] as never;
  }
  return patch;
}

function sameSnapshotField(left: unknown, right: unknown): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}

export function sendSnapshot(
  context: ClientContext,
  snapshot: ClientSnapshot,
  handlers: SnapshotHandlers
): void {
  const message = buildSnapshotMessage(snapshot);
  const payload = JSON.stringify(message);
  if (context.lastSnapshotPayload !== payload) {
    const baseRevision = context.snapshotRevision;
    const revision = baseRevision + 1;
    const patch =
      context.supportsSnapshotPatch && context.lastSnapshotMessage
        ? buildSnapshotPatchMessage(context.lastSnapshotMessage, message, baseRevision, revision)
        : null;
    context.lastSnapshotPayload = payload;
    context.lastSnapshotMessage = message;
    context.snapshotRevision = revision;
    if (patch) {
      context.socket.send(JSON.stringify(patch));
    } else if (context.supportsSnapshotPatch) {
      context.socket.send(JSON.stringify({ ...message, revision }));
    } else {
      context.socket.send(payload);
    }
  }
  scheduleLiveRefresh(context, snapshot, handlers.refreshSelectedThread);
  scheduleListRefresh(context, handlers.refreshThreadList);
}

export async function runBackendAction(
  context: ClientContext,
  action: () => ClientSnapshot | Promise<ClientSnapshot>,
  handlers: RefreshHandlers & SnapshotHandlers
): Promise<void> {
  const actionType = context.actionTraceType ?? "backend_action";
  context.actionTraceType = null;
  const actionTrace = startActionTrace(context, actionType);
  try {
    const snapshot = await action();
    finishActionTrace(context, actionTrace, "succeeded");
    handlers.sendSnapshot(context, snapshot);
  } catch (error) {
    finishActionTrace(context, actionTrace, "failed");
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

export function startActionTrace(context: ClientContext, type: string): NonNullable<ClientContext["currentAction"]> {
  const trace = {
    id: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`,
    type,
    status: "started" as const,
    startedAt: Date.now(),
  };
  context.currentAction = trace;
  console.log(`[gateway] action start id=${trace.id} type=${type} selected=${context.selectedThreadId || "-"}`);
  return trace;
}

export function finishActionTrace(
  context: ClientContext,
  trace: NonNullable<ClientContext["currentAction"]>,
  status: "succeeded" | "failed"
): void {
  const finished = {
    ...trace,
    status,
    finishedAt: Date.now(),
  };
  context.currentAction = finished;
  console.log(
    `[gateway] action ${status} id=${finished.id} type=${finished.type} selected=${context.selectedThreadId || "-"} durationMs=${finished.finishedAt - finished.startedAt}`
  );
}

export async function refreshSelectedThread(
  context: ClientContext,
  source: RefreshSource,
  handlers: RefreshHandlers & SnapshotHandlers
): Promise<void> {
  const shouldTrace = source === "manual" && context.actionTraceType != null;
  const trace = shouldTrace ? startActionTrace(context, context.actionTraceType ?? "refresh_threads") : null;
  context.actionTraceType = null;
  try {
    await refreshSelectedThreadState(context, source, {
      backend: handlers.backend,
      sendSnapshot: handlers.sendSnapshot,
      sendStatus: handlers.sendStatus,
    });
    if (trace) {
      finishActionTrace(context, trace, "succeeded");
    }
  } catch (error) {
    if (trace) {
      finishActionTrace(context, trace, "failed");
    }
    throw error;
  }
}

export async function refreshThreadList(
  context: ClientContext,
  handlers: RefreshHandlers & SnapshotHandlers
): Promise<void> {
  await handlers.refreshSelectedThread(context, "list");
  scheduleListRefresh(context, handlers.refreshThreadList);
}

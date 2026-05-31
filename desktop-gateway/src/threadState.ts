import type { AppServerThread, AppServerThreadStatus } from "./appServerTypes.js";

export function getThreadStatusType(status: AppServerThreadStatus): string {
  if (typeof status === "string") {
    return status.toLowerCase();
  }
  return status.type.toLowerCase();
}

export function getThreadActiveFlags(status: AppServerThreadStatus): string[] {
  if (typeof status === "string" || !("activeFlags" in status)) {
    return [];
  }
  return Array.isArray(status.activeFlags) ? status.activeFlags : [];
}

function hasThreadActiveFlag(status: AppServerThreadStatus, flag: string): boolean {
  return getThreadActiveFlags(status).some((value) => value.toLowerCase() === flag.toLowerCase());
}

function isRecentUnfinishedTurn(
  turn: AppServerThread["turns"][number],
  nowMs = Date.now()
): boolean {
  if (turn.completedAt != null) {
    return false;
  }
  const startedAtMs = typeof turn.startedAt === "number" && turn.startedAt > 0 ? turn.startedAt * 1000 : 0;
  if (startedAtMs <= 0 || nowMs - startedAtMs > 120_000) {
    return false;
  }
  return ["inprogress", "running", "interrupted"].includes(turn.status.toLowerCase());
}

export function isTerminalTurnStatus(status?: string): boolean {
  if (!status) {
    return false;
  }
  return [
    "failed",
    "completed",
    "complete",
    "cancelled",
    "canceled",
    "done",
    "idle",
    "stopped",
    "interrupted",
  ].includes(status.toLowerCase());
}

export function isThreadActivelyGenerating(thread: AppServerThread): boolean {
  const type = getThreadStatusType(thread.status);
  if (hasThreadActiveFlag(thread.status, "waitingOnApproval") || hasThreadActiveFlag(thread.status, "waitingOnUserInput")) {
    return false;
  }
  const lastTurn = thread.turns.at(-1);
  if (!lastTurn) {
    return false;
  }
  if (isRecentUnfinishedTurn(lastTurn)) {
    return true;
  }
  if (isTerminalTurnStatus(lastTurn.status)) {
    return false;
  }
  if (type !== "active" && type !== "in_progress" && type !== "inprogress" && type !== "running") {
    return lastTurn.status.toLowerCase() === "inprogress" || lastTurn.status.toLowerCase() === "running";
  }
  return lastTurn.items.some((item) => {
    const itemType = item.type.toLowerCase();
    return itemType === "agentmessage" ||
      itemType === "commandexecution" ||
      itemType === "filechange" ||
      itemType === "reasoning";
  });
}

function hasRecentUnmaterializedActivity(thread: AppServerThread, nowMs = Date.now()): boolean {
  if (thread.turns.length === 0) {
    return false;
  }
  const updatedAtMs = thread.updatedAt > 0 ? thread.updatedAt * 1000 : 0;
  if (updatedAtMs <= 0 || nowMs - updatedAtMs > 120_000) {
    return false;
  }
  const latestTurnActivityMs = thread.turns.reduce((latest, turn) => {
    const startedAtMs = typeof turn.startedAt === "number" && turn.startedAt > 0 ? turn.startedAt * 1000 : 0;
    const completedAtMs = typeof turn.completedAt === "number" && turn.completedAt > 0 ? turn.completedAt * 1000 : 0;
    return Math.max(latest, startedAtMs, completedAtMs);
  }, 0);
  return latestTurnActivityMs + 1000 < updatedAtMs;
}

export function resolveThreadSummaryStatus(
  thread: AppServerThread
): "running" | "idle" | "needs_approval" | "failed" {
  const type = getThreadStatusType(thread.status);
  if (hasThreadActiveFlag(thread.status, "waitingOnApproval") || type === "waitingonapproval") {
    return "needs_approval";
  }
  if (hasThreadActiveFlag(thread.status, "waitingOnUserInput")) {
    return "idle";
  }
  if (type === "systemerror") {
    return "failed";
  }
  if (hasRecentUnmaterializedActivity(thread)) {
    return "running";
  }
  if (isThreadActivelyGenerating(thread)) {
    return "running";
  }
  if (type === "active" || type === "in_progress" || type === "inprogress" || type === "running") {
    const lastTurn = thread.turns.at(-1);
    if (!lastTurn) {
      return type === "active" ? "idle" : "running";
    }
    if (!isTerminalTurnStatus(lastTurn.status)) {
      return "running";
    }
    if (isTerminalTurnStatus(lastTurn.status)) {
      return "idle";
    }
  }
  if (type === "idle" || type === "notloaded") {
    return "idle";
  }
  return "idle";
}

export function resolveDisplayedThreadStatus(
  baseStatus: "running" | "idle" | "needs_approval" | "failed",
  runtime: {
    isGenerating?: boolean;
    currentTurnId?: string | null;
    transientOperation?: string | null;
    pendingApproval?: string | null;
  }
): "running" | "idle" | "needs_approval" | "failed" {
  if (runtime.pendingApproval) {
    return "needs_approval";
  }
  if (runtime.isGenerating || runtime.currentTurnId || runtime.transientOperation) {
    return "running";
  }
  return baseStatus;
}

export function shouldRetainLiveThreadRuntime(thread: AppServerThread): boolean {
  const status = resolveThreadSummaryStatus(thread);
  return status === "running" || status === "needs_approval";
}

export function shouldRetainThreadRuntimeOverlay(
  thread: AppServerThread,
  existingRuntime?: {
    isGenerating?: boolean;
    currentTurnId?: string | null;
    transientOperation?: string | null;
    pendingApproval?: { text?: string | null } | null;
  } | null
): boolean {
  return shouldRetainLiveThreadRuntime(thread);
}

export function resolveLifecycleStatus(
  status: AppServerThreadStatus,
  hasPendingApproval: boolean
): "running" | "idle" | "needs_approval" | "failed" {
  if (hasPendingApproval) {
    return "needs_approval";
  }
  const type = getThreadStatusType(status);
  if (hasThreadActiveFlag(status, "waitingOnApproval") || type === "waitingonapproval") {
    return "needs_approval";
  }
  if (hasThreadActiveFlag(status, "waitingOnUserInput")) {
    return "idle";
  }
  if (type === "active" || type === "in_progress" || type === "inprogress" || type === "running") {
    return "running";
  }
  if (type === "systemerror") {
    return "failed";
  }
  if (type === "idle" || type === "notloaded") {
    return "idle";
  }
  if (type === "active" || type === "in_progress" || type === "inprogress" || type === "running") {
    return "idle";
  }
  return "idle";
}

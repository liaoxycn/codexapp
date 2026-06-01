import type { AppServerThread, AppServerThreadStatus } from "./appServerTypes.js";
import {
  getThreadStatusType,
  hasThreadActiveFlag,
  isRunningStatusType,
  isTerminalTurnStatus,
} from "./threadStatus.js";

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

export function getActiveTurnId(thread: AppServerThread): string | null {
  for (let index = thread.turns.length - 1; index >= 0; index -= 1) {
    const turn = thread.turns[index];
    if (turn.completedAt != null || isTerminalTurnStatus(turn.status)) {
      continue;
    }
    const normalizedStatus = turn.status.toLowerCase();
    if (["inprogress", "running"].includes(normalizedStatus)) {
      return turn.id;
    }
  }
  return null;
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
  if (!isRunningStatusType(type)) {
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
  if (isRunningStatusType(type)) {
    const lastTurn = thread.turns.at(-1);
    if (!lastTurn) {
      return type === "active" ? "idle" : "running";
    }
    return isTerminalTurnStatus(lastTurn.status) ? "idle" : "running";
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
  void existingRuntime;
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
  if (isRunningStatusType(type)) {
    return "running";
  }
  if (type === "systemerror") {
    return "failed";
  }
  if (type === "idle" || type === "notloaded") {
    return "idle";
  }
  return "idle";
}

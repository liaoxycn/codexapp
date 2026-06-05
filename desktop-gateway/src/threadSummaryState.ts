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

function getThreadLatestActivityAtMs(thread: AppServerThread): number {
  let latest = thread.updatedAt > 0 ? thread.updatedAt * 1000 : 0;
  for (const turn of thread.turns) {
    const startedAtMs = typeof turn.startedAt === "number" && turn.startedAt > 0 ? turn.startedAt * 1000 : 0;
    const completedAtMs = typeof turn.completedAt === "number" && turn.completedAt > 0 ? turn.completedAt * 1000 : 0;
    latest = Math.max(latest, startedAtMs, completedAtMs);
  }
  return latest;
}

function hasRecentUnmaterializedActivity(
  thread: AppServerThread,
  statusType: string,
  nowMs = Date.now()
): boolean {
  if (statusType !== "notloaded") {
    return false;
  }
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

function hasActiveUnmaterializedTurn(thread: AppServerThread): boolean {
  if (getThreadStatusType(thread.status) !== "active") {
    return false;
  }
  if (thread.turns.length === 0) {
    return false;
  }
  const latestTurn = thread.turns.at(-1);
  if (!latestTurn || latestTurn.completedAt != null || isTerminalTurnStatus(latestTurn.status)) {
    return false;
  }
  return ["inprogress", "running"].includes(latestTurn.status.toLowerCase());
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
  if (hasRecentUnmaterializedActivity(thread, type)) {
    return "running";
  }
  if (hasActiveUnmaterializedTurn(thread)) {
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
    snapshot?: { isGenerating?: boolean } | null;
    currentTurnId?: string | null;
    activeTurnIds?: string[];
    activeHookIds?: string[];
    runtimeStatus?: string | null;
    transientOperation?: string | null;
    pendingApproval?: { text?: string | null } | null;
    lastActivityAtMs?: number;
    runningSignalUntilMs?: number;
    turnCompletionGraceUntilMs?: number;
  } | null
): boolean {
  if (shouldRetainLiveThreadRuntime(thread)) {
    return true;
  }
  const terminalTurnIds = new Set(
    thread.turns
      .filter((turn) => turn.completedAt != null || isTerminalTurnStatus(turn.status))
      .map((turn) => turn.id)
  );
  const trackedTurnIds = new Set(
    [
      existingRuntime?.currentTurnId,
      ...(existingRuntime?.activeTurnIds ?? []),
    ].filter((value): value is string => typeof value === "string" && value.length > 0)
  );
  const trackedTurnsResolved =
    trackedTurnIds.size > 0 &&
    [...trackedTurnIds].every((turnId) => terminalTurnIds.has(turnId));
  const nowMs = Date.now();
  const runningLeaseActive = Boolean(
    (existingRuntime?.runningSignalUntilMs ?? 0) > nowMs ||
      (existingRuntime?.turnCompletionGraceUntilMs ?? 0) > nowMs
  );
  const hasActiveRuntimeSignal = Boolean(
    (existingRuntime?.activeTurnIds ?? []).some((turnId) => !terminalTurnIds.has(turnId)) ||
      (existingRuntime?.activeHookIds?.length ?? 0) > 0
  );
  if (hasActiveRuntimeSignal) {
    return true;
  }
  if (existingRuntime?.pendingApproval?.text) {
    return true;
  }
  if (
    trackedTurnsResolved &&
    !existingRuntime?.transientOperation &&
    !existingRuntime?.pendingApproval?.text &&
    (existingRuntime?.activeHookIds?.length ?? 0) === 0
  ) {
    return false;
  }
  const hasLiveOverlay = Boolean(
    existingRuntime?.isGenerating ||
      existingRuntime?.snapshot?.isGenerating ||
      existingRuntime?.currentTurnId ||
      existingRuntime?.transientOperation ||
      existingRuntime?.pendingApproval?.text ||
      runningLeaseActive
  );
  if (!hasLiveOverlay) {
    return false;
  }
  const incomingActivityAtMs = getThreadLatestActivityAtMs(thread);
  const localActivityAtMs = existingRuntime?.lastActivityAtMs ?? 0;
  if (runningLeaseActive) {
    return incomingActivityAtMs <= 0 || localActivityAtMs <= 0 || incomingActivityAtMs <= localActivityAtMs;
  }
  return incomingActivityAtMs > 0 && incomingActivityAtMs < localActivityAtMs;
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

import type {
  AppServerThreadStatus,
  JsonRpcNotification,
} from "../appServerTypes.js";
import { getThreadStatusType, isThreadActivelyGenerating, resolveLifecycleStatus } from "../threadState.js";
import { asString } from "./appServerValues.js";
import {
  replaceOrAppendMessage,
  systemStatus,
} from "./runtimeMessages.js";
import { normalizeCompactMessages, pruneCompletedArtifacts } from "./runtimeSnapshotMessages.js";
import { touchThreadActivity } from "./summaries.js";
import type { BridgeNotificationDeps } from "./notifications.js";

export async function handleThreadStatusChanged(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): Promise<void> {
  const { threadId, status } = notification.params as {
    threadId: string;
    status: AppServerThreadStatus;
  };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  const nextType = getThreadStatusType(status);
  if (nextType === "active") {
    const compactInFlight = state.transientOperation === "compact";
    const hasLiveTurn =
      state.currentTurnId != null ||
      state.snapshot.isGenerating ||
      (state.thread != null && isThreadActivelyGenerating(state.thread));
    if (!compactInFlight && !hasLiveTurn) {
      state.snapshot.isGenerating = false;
      deps.updateSummaryStatus(threadId, state.pendingApproval ? "needs_approval" : "idle");
      deps.emitChanged();
      return;
    }

    state.snapshot.isGenerating = !compactInFlight;
    deps.updateSummaryStatus(
      threadId,
      compactInFlight ? (state.pendingApproval ? "needs_approval" : "idle") : "running"
    );
    deps.emitChanged();
    return;
  }

  if (state.transientOperation === "compact") {
    state.snapshot.isGenerating = false;
    deps.updateSummaryStatus(threadId, state.pendingApproval ? "needs_approval" : "idle");
    deps.emitChanged();
    return;
  }

  if (state.currentTurnId || state.snapshot.isGenerating || state.stopRequested) {
    await deps.finalizeTurnState(threadId);
    return;
  }

  state.snapshot.isGenerating = false;
  deps.updateSummaryStatus(threadId, resolveLifecycleStatus(status, state.pendingApproval != null));
  deps.emitChanged();
}

export function handleTurnStarted(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, turn } = notification.params as {
    threadId: string;
    turn: { id: string; startedAt?: number | null };
  };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  state.currentTurnId = turn.id;
  touchThreadActivity(state, turn.startedAt);
  if (state.transientOperation === "compact") {
    return;
  }

  state.transientOperation = null;
  state.snapshot.isGenerating = true;
  deps.ensureActiveAssistantMessage(state, turn.id);
  deps.updateSummaryStatus(threadId, "running");
  deps.emitChanged();
}

export async function handleTurnCompleted(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): Promise<void> {
  const { threadId, turn } = notification.params as {
    threadId: string;
    turn: { id: string; status?: string; startedAt?: number | null; completedAt?: number | null };
  };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  if (state.transientOperation === "compact" && turn.status !== "failed") {
    touchThreadActivity(state, turn.completedAt ?? turn.startedAt);
    await deps.finalizeCompactState(threadId);
    return;
  }

  touchThreadActivity(state, turn.completedAt ?? turn.startedAt);
  await deps.finalizeTurnState(threadId, turn.status);
}

export function handleServerRequestResolved(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId } = notification.params as { threadId: string; requestId: string | number };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  state.pendingApproval = null;
  state.snapshot.pendingApproval = null;
  deps.updateSummaryStatus(threadId, state.snapshot.isGenerating ? "running" : "idle");
  deps.emitChanged();
}

export function handleThreadCompacted(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId } = notification.params as { threadId: string; turnId?: string };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  state.currentTurnId = null;
  state.liveAssistantItemId = null;
  state.activeAssistantMessageId = null;
  state.snapshot.isGenerating = false;
  pruneCompletedArtifacts(state);
  normalizeCompactMessages(state, true);
  deps.updateSummaryStatus(threadId, state.pendingApproval ? "needs_approval" : "idle");
  deps.emitChanged();
}

export function handleThreadGoalUpdated(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, goal } = notification.params as {
    threadId: string;
    goal?: { objective?: string | null; status?: string | null };
  };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  const objective = asString(goal?.objective).trim();
  const status = asString(goal?.status).trim();
  const suffix = status ? ` · ${status}` : "";
  replaceOrAppendMessage(state, systemStatus(`目标: ${objective || "已更新"}${suffix}`, "thread-goal"));
  deps.emitChanged();
}

export function handleThreadGoalCleared(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId } = notification.params as { threadId: string };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  replaceOrAppendMessage(state, systemStatus("目标已清除", "thread-goal"));
  deps.emitChanged();
}

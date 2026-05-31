import type {
  AppServerThreadItem,
  JsonRpcNotification,
} from "../appServerTypes.js";
import { buildFileChangeBlocks } from "./fileChanges.js";
import {
  appendAssistantDelta,
  appendOrMergeMessage,
  mergeThreadItem,
  replaceOrAppendMessage,
} from "./runtimeMessages.js";
import { touchThreadActivity } from "./summaries.js";
import type { BridgeNotificationDeps } from "./notifications.js";

export function handleAgentMessageDelta(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, turnId, itemId, delta } = notification.params as {
    threadId: string;
    turnId: string;
    itemId: string;
    delta: string;
  };
  const state = deps.threads.get(threadId);
  if (!state || state.transientOperation === "compact") {
    return;
  }

  state.currentTurnId = turnId;
  state.snapshot.isGenerating = true;
  appendAssistantDelta(state, itemId, delta);
  deps.updateSummaryStatus(threadId, "running");
  deps.emitChanged();
}

export function handleReasoningSummaryDelta(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, itemId, delta } = notification.params as {
    threadId: string;
    turnId: string;
    itemId: string;
    delta: string;
  };
  const state = deps.threads.get(threadId);
  if (!state || state.transientOperation === "compact") {
    return;
  }

  appendOrMergeMessage(
    state,
    itemId,
    "assistant",
    { kind: "reasoning", value: delta },
    true
  );
  deps.emitChanged();
}

export function handleCommandExecutionOutputDelta(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, itemId, delta } = notification.params as {
    threadId: string;
    turnId: string;
    itemId: string;
    delta: string;
  };
  const state = deps.threads.get(threadId);
  if (!state || state.transientOperation === "compact") {
    return;
  }

  replaceOrAppendMessage(state, {
    id: itemId,
    role: "assistant",
    blocks: [
      { kind: "commandSummary", value: "命令执行中" },
      { kind: "commandMeta", value: "命令输出更新中" },
      { kind: "code", language: "shell", value: delta },
    ],
  });
  deps.emitChanged();
}

export function handleFileChangePatchUpdated(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, itemId, changes } = notification.params as {
    threadId: string;
    turnId: string;
    itemId: string;
    changes: Array<{ path?: string; kind?: string; diff?: string | null }>;
  };
  const state = deps.threads.get(threadId);
  if (!state || state.transientOperation === "compact") {
    return;
  }

  replaceOrAppendMessage(state, {
    id: itemId,
    role: "assistant",
    blocks: buildFileChangeBlocks(changes, "inProgress", state.snapshot.cwd),
  });
  deps.emitChanged();
}

export async function handleItemLifecycle(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): Promise<void> {
  const { threadId, item, turnId } = notification.params as {
    threadId: string;
    turnId: string;
    item: AppServerThreadItem;
    startedAtMs?: number;
    completedAtMs?: number;
  };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  const timestampMs =
    notification.method === "item/started"
      ? (notification.params as { startedAtMs?: number }).startedAtMs
      : (notification.params as { completedAtMs?: number }).completedAtMs;
  touchThreadActivity(state, timestampMs);
  if (state.transientOperation === "compact") {
    state.currentTurnId = turnId;
    if (item.type === "contextCompaction" && notification.method === "item/completed") {
      await deps.finalizeCompactState(threadId);
    }
    return;
  }

  state.currentTurnId = turnId;
  mergeThreadItem(state, item, true);
  deps.emitChanged();
}

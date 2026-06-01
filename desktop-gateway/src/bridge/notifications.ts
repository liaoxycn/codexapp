import type { JsonRpcNotification } from "../appServerTypes.js";
import type { ThreadLifecycleStatus, ThreadRuntimeState } from "./types.js";
import {
  handleAgentMessageDelta,
  handleCommandExecutionOutputDelta,
  handleFileChangePatchUpdated,
  handleItemLifecycle,
  handleReasoningSummaryDelta,
} from "./itemNotifications.js";
import {
  handleServerRequestResolved,
  handleThreadGoalCleared,
  handleThreadGoalUpdated,
  handleThreadCompacted,
  handleThreadStatusChanged,
  handleTurnCompleted,
  handleTurnStarted,
} from "./threadNotifications.js";

export interface BridgeNotificationDeps {
  threads: Map<string, ThreadRuntimeState>;
  emitChanged(): void;
  finalizeCompactState(threadId: string): Promise<void>;
  finalizeTurnState(threadId: string, turnStatus?: string): Promise<void>;
  hydrateThreads(): Promise<void>;
  ensureActiveAssistantMessage(state: ThreadRuntimeState, turnId: string): void;
  updateSummaryStatus(threadId: string, status: ThreadLifecycleStatus): void;
}

export async function handleBridgeNotification(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): Promise<void> {
  switch (notification.method) {
    case "thread/status/changed":
      await handleThreadStatusChanged(notification, deps);
      return;
    case "turn/started":
      handleTurnStarted(notification, deps);
      return;
    case "turn/completed":
      await handleTurnCompleted(notification, deps);
      return;
    case "item/agentMessage/delta":
      handleAgentMessageDelta(notification, deps);
      return;
    case "item/reasoning/summaryTextDelta":
      handleReasoningSummaryDelta(notification, deps);
      return;
    case "item/commandExecution/outputDelta":
      handleCommandExecutionOutputDelta(notification, deps);
      return;
    case "item/fileChange/patchUpdated":
      handleFileChangePatchUpdated(notification, deps);
      return;
    case "item/started":
    case "item/completed":
      await handleItemLifecycle(notification, deps);
      return;
    case "serverRequest/resolved":
      handleServerRequestResolved(notification, deps);
      return;
    case "thread/started":
    case "thread/archived":
    case "thread/unarchived":
      await deps.hydrateThreads();
      return;
    case "thread/compacted":
      handleThreadCompacted(notification, deps);
      return;
    case "thread/goal/updated":
      handleThreadGoalUpdated(notification, deps);
      return;
    case "thread/goal/cleared":
      handleThreadGoalCleared(notification, deps);
      return;
    default:
      return;
  }
}

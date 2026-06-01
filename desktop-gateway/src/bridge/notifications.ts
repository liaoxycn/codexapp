import type { JsonRpcNotification } from "../appServerTypes.js";
import type { ThreadLifecycleStatus, ThreadRuntimeState } from "./types.js";
import {
  handleAgentMessageDelta,
  handleCommandExecutionOutputDelta,
  handleFileChangeOutputDelta,
  handleFileChangePatchUpdated,
  handleGuardianApprovalReview,
  handleItemLifecycle,
  handleMcpToolCallProgress,
  handlePlanDelta,
  handleReasoningSummaryPartAdded,
  handleReasoningSummaryDelta,
  handleReasoningTextDelta,
  handleTerminalInteraction,
} from "./itemNotifications.js";
import {
  handleErrorNotification,
  handleModelRerouted,
  handleModelVerification,
  handleServerRequestResolved,
  handleGlobalNotice,
  handleThreadGoalCleared,
  handleThreadGoalUpdated,
  handleThreadCompacted,
  handleThreadLevelWarning,
  handleTurnDiffUpdated,
  handleTurnPlanUpdated,
  handleThreadTokenUsageUpdated,
  handleThreadStatusChanged,
  handleTurnCompleted,
  handleTurnStarted,
  handleHookRunUpdated,
  handleOperationalNotice,
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
    case "hook/started":
    case "hook/completed":
      handleHookRunUpdated(notification, deps);
      return;
    case "item/agentMessage/delta":
      handleAgentMessageDelta(notification, deps);
      return;
    case "item/reasoning/summaryTextDelta":
      handleReasoningSummaryDelta(notification, deps);
      return;
    case "item/reasoning/summaryPartAdded":
      handleReasoningSummaryPartAdded(notification, deps);
      return;
    case "item/reasoning/textDelta":
      handleReasoningTextDelta(notification, deps);
      return;
    case "item/plan/delta":
      handlePlanDelta(notification, deps);
      return;
    case "item/commandExecution/outputDelta":
      handleCommandExecutionOutputDelta(notification, deps);
      return;
    case "item/commandExecution/terminalInteraction":
      handleTerminalInteraction(notification, deps);
      return;
    case "item/fileChange/outputDelta":
      handleFileChangeOutputDelta(notification, deps);
      return;
    case "item/fileChange/patchUpdated":
      handleFileChangePatchUpdated(notification, deps);
      return;
    case "item/mcpToolCall/progress":
      handleMcpToolCallProgress(notification, deps);
      return;
    case "item/started":
    case "item/completed":
      await handleItemLifecycle(notification, deps);
      return;
    case "item/autoApprovalReview/started":
    case "item/autoApprovalReview/completed":
      handleGuardianApprovalReview(notification, deps);
      return;
    case "serverRequest/resolved":
      handleServerRequestResolved(notification, deps);
      return;
    case "thread/started":
    case "thread/archived":
    case "thread/unarchived":
    case "thread/name/updated":
    case "thread/closed":
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
    case "thread/tokenUsage/updated":
      handleThreadTokenUsageUpdated(notification, deps);
      return;
    case "turn/plan/updated":
      handleTurnPlanUpdated(notification, deps);
      return;
    case "turn/diff/updated":
      handleTurnDiffUpdated(notification, deps);
      return;
    case "model/rerouted":
      handleModelRerouted(notification, deps);
      return;
    case "model/verification":
      handleModelVerification(notification, deps);
      return;
    case "error":
      handleErrorNotification(notification, deps);
      return;
    case "warning":
    case "guardianWarning":
      handleThreadLevelWarning(notification, deps);
      return;
    case "configWarning":
    case "deprecationNotice":
      handleGlobalNotice(notification, deps);
      return;
    case "mcpServer/oauthLogin/completed":
    case "mcpServer/startupStatus/updated":
    case "skills/changed":
    case "account/updated":
    case "account/rateLimits/updated":
    case "remoteControl/status/changed":
    case "externalAgentConfig/import/completed":
    case "windows/worldWritableWarning":
    case "windowsSandbox/setupCompleted":
    case "account/login/completed":
      handleOperationalNotice(notification, deps);
      return;
    default:
      return;
  }
}

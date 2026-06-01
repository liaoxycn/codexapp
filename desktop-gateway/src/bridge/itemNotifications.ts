import type {
  AppServerThreadItem,
  JsonRpcNotification,
} from "../appServerTypes.js";
import { buildFileChangeBlocks } from "./fileChanges.js";
import {
  appendOrMergeCodeMessage,
  appendAssistantDelta,
  appendOrMergeMessage,
  mergeThreadItem,
  replaceOrAppendMessage,
} from "./runtimeMessages.js";
import { touchThreadActivity } from "./summaries.js";
import type { BridgeNotificationDeps } from "./notifications.js";
import { asString } from "./appServerValues.js";

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

export function handleReasoningSummaryPartAdded(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, itemId, summaryIndex } = notification.params as {
    threadId: string;
    turnId: string;
    itemId: string;
    summaryIndex?: number;
  };
  const state = deps.threads.get(threadId);
  if (!state || state.transientOperation === "compact") {
    return;
  }

  appendOrMergeMessage(
    state,
    itemId,
    "assistant",
    { kind: "reasoning", value: `思考摘要 ${summaryIndex == null ? "" : summaryIndex + 1}`.trim() },
    false
  );
  deps.emitChanged();
}

export function handleReasoningTextDelta(
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

export function handlePlanDelta(
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
    { kind: "text", value: delta },
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

  appendOrMergeCodeMessage(state, itemId, delta, "shell", "命令执行中");
  deps.emitChanged();
}

export function handleTerminalInteraction(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, itemId, stdin } = notification.params as {
    threadId: string;
    turnId: string;
    itemId: string;
    processId: string;
    stdin: string;
  };
  const state = deps.threads.get(threadId);
  if (!state || state.transientOperation === "compact") {
    return;
  }

  appendOrMergeCodeMessage(state, itemId, `\nstdin> ${stdin}`, "shell", "终端交互");
  deps.emitChanged();
}

export function handleFileChangeOutputDelta(
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

  appendOrMergeCodeMessage(state, itemId, delta, "diff", "文件改动中");
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

export function handleMcpToolCallProgress(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, itemId, message } = notification.params as {
    threadId: string;
    turnId: string;
    itemId: string;
    message: string;
  };
  const state = deps.threads.get(threadId);
  if (!state || state.transientOperation === "compact") {
    return;
  }

  replaceOrAppendMessage(state, {
    id: itemId,
    role: "assistant",
    blocks: [{ kind: "text", value: `MCP 进度: ${message}` }],
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

export function handleGuardianApprovalReview(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, reviewId, review, action } = notification.params as {
    threadId: string;
    turnId: string;
    reviewId: string;
    review?: { status?: string | null; riskLevel?: string | null; rationale?: string | null } | null;
    action?: { type?: string | null } | null;
  };
  const state = deps.threads.get(threadId);
  if (!state || !reviewId) {
    return;
  }

  const status = asString(review?.status, notification.method === "item/autoApprovalReview/started" ? "inProgress" : "completed");
  const risk = asString(review?.riskLevel).trim();
  const rationale = asString(review?.rationale).trim();
  const actionLabel = formatReviewAction(action);
  const lines = [
    `自动审批审查 ${formatReviewStatus(status)}: ${actionLabel}`,
    risk ? `风险: ${formatRiskLevel(risk)}` : "",
    rationale,
  ].filter(Boolean);
  replaceOrAppendMessage(state, {
    id: `auto-approval-review-${reviewId}`,
    role: "system",
    blocks: [{ kind: "status", value: lines.join("\n") }],
  });
  deps.emitChanged();
}

export function handleRawResponseItemCompleted(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const params = notification.params as Record<string, unknown>;
  const threadId = asString(params.threadId);
  const turnId = asString(params.turnId, "turn");
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  const message = mapRawResponseItem(turnId, params.item);
  if (!message) {
    return;
  }
  replaceOrAppendMessage(state, message);
  deps.emitChanged();
}

export function handleRealtimeNotification(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const params = notification.params as Record<string, unknown>;
  const threadId = asString(params.threadId);
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  if (notification.method === "thread/realtime/itemAdded") {
    const item = asThreadItem(params.item);
    if (!item) {
      return;
    }
    mergeThreadItem(state, item, true);
    deps.emitChanged();
    return;
  }

  if (notification.method === "thread/realtime/transcript/delta") {
    appendOrMergeMessage(
      state,
      `realtime-transcript-${asString(params.role, "assistant")}`,
      params.role === "user" ? "user" : "assistant",
      { kind: "text", value: asString(params.delta) },
      true
    );
    deps.emitChanged();
    return;
  }

  if (notification.method === "thread/realtime/transcript/done") {
    const role = params.role === "user" ? "user" : "assistant";
    replaceOrAppendMessage(state, {
      id: `realtime-transcript-${asString(params.role, "assistant")}`,
      role,
      blocks: [{ kind: "text", value: asString(params.text, "实时转写已完成") }],
    });
    deps.emitChanged();
    return;
  }

  const status = formatRealtimeStatus(notification);
  if (!status) {
    return;
  }
  replaceOrAppendMessage(state, {
    id: "thread-realtime-status",
    role: "system",
    blocks: [{ kind: "status", value: status }],
  });
  deps.emitChanged();
}

function mapRawResponseItem(turnId: string, value: unknown) {
  if (typeof value !== "object" || value == null) {
    return null;
  }
  const item = value as Record<string, unknown>;
  const type = asString(item.type);
  const itemId = asString(item.id, `raw-response-${turnId}-${type || "item"}`);

  if (type === "message") {
    const role = item.role === "user" ? "user" as const : "assistant" as const;
    const text = textFromContentItems(item.content);
    if (!text) {
      return null;
    }
    return {
      id: itemId,
      role,
      blocks: [{ kind: "text" as const, value: text }],
    };
  }

  if (type === "reasoning") {
    const text = [
      textFromContentItems(item.summary),
      textFromContentItems(item.content),
    ].filter(Boolean).join("\n");
    if (!text) {
      return null;
    }
    return {
      id: itemId,
      role: "assistant" as const,
      blocks: [{ kind: "reasoning" as const, value: text }],
    };
  }

  if (type === "local_shell_call") {
    const action = item.action as Record<string, unknown> | null;
    const command = Array.isArray(action?.command)
      ? action.command.filter((part): part is string => typeof part === "string").join(" ")
      : "";
    return {
      id: itemId,
      role: "assistant" as const,
      blocks: [{ kind: "text" as const, value: `命令 ${asString(item.status, "updated")}: ${command}`.trim() }],
    };
  }

  if (type === "function_call" || type === "custom_tool_call" || type === "tool_search_call") {
    const name = asString(item.name, type);
    const status = asString(item.status, "completed");
    return {
      id: itemId,
      role: "assistant" as const,
      blocks: [{ kind: "text" as const, value: `工具: ${name} · ${status}` }],
    };
  }

  if (type === "web_search_call") {
    return {
      id: itemId,
      role: "assistant" as const,
      blocks: [{ kind: "text" as const, value: `Web search · ${asString(item.status, "completed")}` }],
    };
  }

  if (type === "image_generation_call") {
    return {
      id: itemId,
      role: "assistant" as const,
      blocks: [{ kind: "text" as const, value: `生成图片 ${asString(item.status, "completed")}: ${asString(item.result)}` }],
    };
  }

  return null;
}

function textFromContentItems(value: unknown): string {
  if (!Array.isArray(value)) {
    return "";
  }
  return value
    .map((entry) => {
      if (typeof entry !== "object" || entry == null) {
        return "";
      }
      const item = entry as Record<string, unknown>;
      return asString(item.text).trim();
    })
    .filter(Boolean)
    .join("\n");
}

function asThreadItem(value: unknown): AppServerThreadItem | null {
  if (typeof value !== "object" || value == null) {
    return null;
  }
  const item = value as Record<string, unknown>;
  return typeof item.type === "string" && typeof item.id === "string"
    ? (item as AppServerThreadItem)
    : null;
}

function formatReviewAction(action?: { type?: string | null } | null): string {
  const type = asString(action?.type, "request");
  switch (type) {
    case "command":
      return "命令";
    case "execve":
      return "进程执行";
    case "applyPatch":
      return "文件修改";
    case "networkAccess":
      return "网络访问";
    case "mcpToolCall":
      return "MCP 工具";
    case "requestPermissions":
      return "权限请求";
    default:
      return type;
  }
}

function formatReviewStatus(status: string): string {
  switch (status) {
    case "inProgress":
      return "进行中";
    case "approved":
      return "已允许";
    case "denied":
      return "已拒绝";
    case "timedOut":
      return "超时";
    case "aborted":
      return "已中止";
    default:
      return status || "已更新";
  }
}

function formatRiskLevel(risk: string): string {
  switch (risk) {
    case "low":
      return "低";
    case "medium":
      return "中";
    case "high":
      return "高";
    case "critical":
      return "严重";
    default:
      return risk;
  }
}

function formatRealtimeStatus(notification: JsonRpcNotification): string | null {
  const params = notification.params as Record<string, unknown>;
  switch (notification.method) {
    case "thread/realtime/started":
      return `实时会话已开始${asString(params.realtimeSessionId).trim() ? `\n${asString(params.realtimeSessionId).trim()}` : ""}`;
    case "thread/realtime/outputAudio/delta":
      return "实时音频输出中";
    case "thread/realtime/sdp":
      return "实时连接参数已更新";
    case "thread/realtime/error":
      return `实时会话错误: ${asString(params.message, "unknown")}`;
    case "thread/realtime/closed":
      return `实时会话已关闭${asString(params.reason).trim() ? `\n${asString(params.reason).trim()}` : ""}`;
    default:
      return null;
  }
}

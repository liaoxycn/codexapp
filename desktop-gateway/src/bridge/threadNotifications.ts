import type {
  AppServerThreadStatus,
  JsonRpcNotification,
} from "../appServerTypes.js";
import { getThreadStatusType, resolveLifecycleStatus } from "../threadState.js";
import { asString } from "./appServerValues.js";
import {
  replaceOrAppendMessage,
  systemStatus,
} from "./runtimeMessageStore.js";
import { normalizeCompactMessages, pruneCompletedArtifacts } from "./runtimeSnapshotMessages.js";
import { clearRunningLease, hasRunningLease, markRunningSignal, markTurnCompletionGrace } from "./runningLease.js";
import {
  applyRuntimeStatusToSnapshot,
  markRuntimeApprovalResolved,
  markRuntimeFailed,
  markRuntimeHookFinished,
  markRuntimeHookStarted,
  markRuntimeIdle,
  markRuntimeTurnFinished,
  markRuntimeTurnStarted,
  resolveRuntimeStatus,
} from "./runtimeStatusRegistry.js";
import { clearCurrentTurnStarted, markCurrentTurnStarted } from "./runtimeTurnTiming.js";
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

  if (state.transientOperation === "compact") {
    markRuntimeIdle(state);
    deps.updateSummaryStatus(threadId, resolveRuntimeStatus(state));
    deps.emitChanged();
    return;
  }

  if (state.currentTurnId || state.stopRequested || hasRunningLease(state)) {
    await deps.finalizeTurnState(threadId);
    return;
  }

  const nextStatus = resolveLifecycleStatus(status, state.pendingApproval != null);
  if (nextStatus === "failed") {
    markRuntimeFailed(state);
  } else if (nextStatus === "idle") {
    markRuntimeIdle(state);
  }
  applyRuntimeStatusToSnapshot(state);
  deps.updateSummaryStatus(threadId, resolveRuntimeStatus(state));
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
  markCurrentTurnStarted(state, turn.startedAt, Date.now(), true);
  markRuntimeTurnStarted(state, turn.id);
  markRunningSignal(state);
  touchThreadActivity(state, turn.startedAt);
  if (state.transientOperation === "compact") {
    return;
  }

  state.transientOperation = null;
  state.snapshot.isGenerating = true;
  deps.ensureActiveAssistantMessage(state, turn.id);
  deps.updateSummaryStatus(threadId, resolveRuntimeStatus(state));
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
  markTurnCompletionGrace(state);
  markRuntimeTurnFinished(state, turn.id, turn.status);
  await deps.finalizeTurnState(threadId, turn.status, turn.id);
}

export function handleHookRunUpdated(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, turnId, run } = notification.params as {
    threadId: string;
    turnId?: string | null;
    run?: HookRunSummary | null;
  };
  const state = deps.threads.get(threadId);
  if (!state || !run?.id) {
    return;
  }

  if (notification.method === "hook/started") {
    if (turnId) {
      state.currentTurnId = turnId;
      markCurrentTurnStarted(state, null, Date.now(), true);
    }
    markRuntimeHookStarted(state, run.id);
    markRunningSignal(state);
  } else {
    markRuntimeHookFinished(state, run.id, run.status ?? undefined);
  }
  const label = [asString(run.eventName, "hook"), asString(run.handlerType)].filter(Boolean).join(" ");
  const status = asString(run.status, notification.method === "hook/started" ? "running" : "completed");
  const detail = [
    `Hook ${label}: ${formatHookStatus(status)}`,
    asString(run.statusMessage).trim(),
    ...formatHookEntries(run.entries),
  ].filter(Boolean).join("\n");
  replaceOrAppendMessage(state, systemStatus(detail, `hook-run-${run.id}`));
  deps.updateSummaryStatus(threadId, resolveRuntimeStatus(state));
  deps.emitChanged();
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
  markRuntimeApprovalResolved(state);
  deps.updateSummaryStatus(threadId, resolveRuntimeStatus(state));
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
  clearCurrentTurnStarted(state);
  state.liveAssistantItemId = null;
  state.activeAssistantMessageId = null;
  state.snapshot.isGenerating = false;
  clearRunningLease(state);
  markRuntimeIdle(state);
  pruneCompletedArtifacts(state);
  normalizeCompactMessages(state, true);
  deps.updateSummaryStatus(threadId, resolveRuntimeStatus(state));
  deps.emitChanged();
}

export function handleThreadGoalUpdated(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, turnId, goal } = notification.params as {
    threadId: string;
    turnId?: string | null;
    goal?: { objective?: string | null; status?: string | null; updatedAt?: number | null };
  };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  const objective = asString(goal?.objective).trim();
  const status = asString(goal?.status).trim();
  const suffix = status ? ` · ${status}` : "";
  replaceOrAppendMessage(state, systemStatus(`目标: ${objective || "已更新"}${suffix}`, "thread-goal"));
  if (isActiveGoalStatus(status)) {
    if (turnId) {
      state.currentTurnId = turnId;
      markCurrentTurnStarted(state, goal?.updatedAt ?? Date.now(), Date.now(), true);
    }
    state.snapshot.isGenerating = true;
    markRuntimeTurnStarted(state, turnId ?? `goal-${threadId}`);
    markRunningSignal(state);
    touchThreadActivity(state, goal?.updatedAt ?? Date.now());
    deps.updateSummaryStatus(threadId, resolveRuntimeStatus(state));
  }
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

export function handleThreadTokenUsageUpdated(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, tokenUsage } = notification.params as {
    threadId: string;
    turnId: string;
    tokenUsage?: {
      total?: TokenUsageBreakdown | null;
      last?: TokenUsageBreakdown | null;
      modelContextWindow?: number | null;
    } | null;
  };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  const total = tokenUsage?.total;
  const window = Number.isFinite(tokenUsage?.modelContextWindow)
    ? Number(tokenUsage?.modelContextWindow)
    : null;
  const totalTokens = finiteTokenCount(total?.totalTokens);
  const contextPercent = window && totalTokens > 0
    ? Math.round((totalTokens / window) * 100)
    : undefined;
  state.tokenUsage = {
    totalTokens,
    inputTokens: finiteTokenCount(total?.inputTokens),
    outputTokens: finiteTokenCount(total?.outputTokens),
    reasoningTokens: finiteTokenCount(total?.reasoningOutputTokens),
    ...(contextPercent != null ? { contextPercent } : {}),
  };
  state.snapshot.tokenUsage = state.tokenUsage;
  deps.emitChanged();
}

export function handleTurnPlanUpdated(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, turnId, explanation, plan } = notification.params as {
    threadId: string;
    turnId: string;
    explanation?: string | null;
    plan?: Array<{ step?: string; status?: string }>;
  };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  const planLines = (plan ?? [])
    .map((entry) => `${entry.status ?? "pending"}: ${entry.step ?? ""}`.trim())
    .filter((line) => line.length > 0);
  replaceOrAppendMessage(
    state,
    systemStatus([asString(explanation), ...planLines].filter(Boolean).join("\n") || "计划已更新", `turn-plan-${turnId}`)
  );
  deps.emitChanged();
}

export function handleTurnDiffUpdated(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, turnId, diff } = notification.params as {
    threadId: string;
    turnId: string;
    diff?: string | null;
  };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  const value = asString(diff).trim();
  if (!value) {
    return;
  }
  replaceOrAppendMessage(state, {
    id: `turn-diff-${turnId}`,
    role: "assistant",
    blocks: [{ kind: "fileChangeDiff", value, language: "diff" }],
  });
  deps.emitChanged();
}

export function handleModelRerouted(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, turnId, fromModel, toModel, reason } = notification.params as {
    threadId: string;
    turnId: string;
    fromModel?: string | null;
    toModel?: string | null;
    reason?: string | null;
  };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  replaceOrAppendMessage(
    state,
    systemStatus(
      `模型已切换: ${asString(fromModel, "unknown")} -> ${asString(toModel, "unknown")}${
        reason ? ` · ${reason}` : ""
      }`,
      `model-rerouted-${turnId}`
    )
  );
  deps.emitChanged();
}

export function handleModelVerification(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, turnId, verifications } = notification.params as {
    threadId: string;
    turnId: string;
    verifications?: string[];
  };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  const value = (verifications ?? []).filter(Boolean).join(", ");
  replaceOrAppendMessage(
    state,
    systemStatus(`模型验证: ${value || "已更新"}`, `model-verification-${turnId}`)
  );
  deps.emitChanged();
}

export function handleErrorNotification(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const { threadId, error, willRetry } = notification.params as {
    threadId: string;
    turnId?: string;
    error?: { message?: string | null; additionalDetails?: string | null } | null;
    willRetry?: boolean;
  };
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  const message = [error?.message, error?.additionalDetails, willRetry ? "将重试" : null]
    .filter(Boolean)
    .join("\n");
  state.snapshot.messages = state.snapshot.messages.concat(systemStatus(`错误: ${message || "unknown"}`));
  state.snapshot.isGenerating = Boolean(willRetry);
  if (willRetry) {
    markRuntimeTurnStarted(state, asString((notification.params as Record<string, unknown>).turnId, `retry-${threadId}`));
    markCurrentTurnStarted(state, null, Date.now(), true);
    markRunningSignal(state);
  } else {
    markRuntimeFailed(state);
    clearCurrentTurnStarted(state);
    clearRunningLease(state);
  }
  deps.updateSummaryStatus(threadId, resolveRuntimeStatus(state));
  deps.emitChanged();
}

export function handleThreadLevelWarning(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const params = notification.params as {
    threadId?: string | null;
    message?: string | null;
  };
  const threadId = params.threadId;
  if (!threadId) {
    return;
  }
  const state = deps.threads.get(threadId);
  if (!state) {
    return;
  }

  state.snapshot.messages = state.snapshot.messages.concat(
    systemStatus(`警告: ${params.message || "unknown"}`)
  );
  deps.emitChanged();
}

export function handleGlobalNotice(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const state = resolveNoticeTargetState(deps);
  if (!state) {
    return;
  }

  const params = notification.params as {
    summary?: string | null;
    details?: string | null;
    path?: string | null;
  };
  const prefix = notification.method === "configWarning" ? "配置警告" : "废弃提示";
  const text = [
    `${prefix}: ${asString(params.summary, "已更新")}`,
    asString(params.details).trim(),
    asString(params.path).trim(),
  ].filter(Boolean).join("\n");
  replaceOrAppendMessage(state, systemStatus(text, `${notification.method}-notice`));
  deps.emitChanged();
}

export function handleOperationalNotice(
  notification: JsonRpcNotification,
  deps: BridgeNotificationDeps
): void {
  const notice = formatOperationalNotice(notification);
  if (!notice) {
    return;
  }
  deps.pushOperationalNotice?.({
    ...notice,
    createdAt: Date.now(),
  });
  deps.emitChanged();
}

function resolveNoticeTargetState(deps: BridgeNotificationDeps) {
  for (const state of deps.threads.values()) {
    const selectedThreadId = state.snapshot.selectedThreadId;
    if (selectedThreadId && deps.threads.has(selectedThreadId)) {
      return deps.threads.get(selectedThreadId);
    }
  }
  return deps.threads.values().next().value;
}

interface TokenUsageBreakdown {
  totalTokens?: number | null;
  inputTokens?: number | null;
  outputTokens?: number | null;
  reasoningOutputTokens?: number | null;
}

interface HookRunSummary {
  id?: string | null;
  eventName?: string | null;
  handlerType?: string | null;
  status?: string | null;
  statusMessage?: string | null;
  entries?: Array<{ kind?: string | null; text?: string | null }> | null;
}

function finiteTokenCount(value: unknown): number {
  return Number.isFinite(value) ? Math.max(0, Math.round(Number(value))) : 0;
}

function isActiveGoalStatus(status: string): boolean {
  return status.toLowerCase() === "active";
}

function formatHookStatus(status: string): string {
  switch (status) {
    case "running":
      return "运行中";
    case "completed":
      return "已完成";
    case "failed":
      return "失败";
    case "blocked":
      return "已阻止";
    case "stopped":
      return "已停止";
    default:
      return status || "已更新";
  }
}

function formatHookEntries(entries: HookRunSummary["entries"]): string[] {
  return (entries ?? [])
    .map((entry) => {
      const text = asString(entry.text).trim();
      if (!text) {
        return "";
      }
      const kind = asString(entry.kind).trim();
      return kind ? `${kind}: ${text}` : text;
    })
    .filter(Boolean);
}

function formatOperationalNotice(notification: JsonRpcNotification): { id: string; text: string } | null {
  const params = notification.params as Record<string, unknown>;
  switch (notification.method) {
    case "mcpServer/oauthLogin/completed": {
      const name = asString(params.name, "MCP");
      const success = params.success === true;
      const error = asString(params.error).trim();
      return {
        id: `mcp-oauth-${name}`,
        text: `MCP 授权 ${name}: ${success ? "已完成" : "失败"}${error ? `\n${error}` : ""}`,
      };
    }
    case "mcpServer/startupStatus/updated": {
      const name = asString(params.name, "MCP");
      const status = asString(params.status, "updated");
      const error = asString(params.error).trim();
      return {
        id: `mcp-startup-${name}`,
        text: `MCP 服务 ${name}: ${formatMcpStartupStatus(status)}${error ? `\n${error}` : ""}`,
      };
    }
    case "skills/changed":
      return {
        id: "skills-changed",
        text: "技能列表已变更",
      };
    case "account/updated": {
      const authMode = asString(params.authMode, "unknown");
      const planType = asString(params.planType, "unknown");
      return {
        id: "account-updated",
        text: `账号状态已更新: ${authMode} · ${planType}`,
      };
    }
    case "account/rateLimits/updated": {
      const rateLimits = (params.rateLimits ?? {}) as Record<string, unknown>;
      const primary = (rateLimits.primary ?? {}) as Record<string, unknown>;
      const usedPercent = Number.isFinite(primary.usedPercent) ? `${Math.round(Number(primary.usedPercent))}%` : "未知";
      const limitName = asString(rateLimits.limitName, asString(rateLimits.limitId, "额度"));
      const reachedType = asString(rateLimits.rateLimitReachedType).trim();
      return {
        id: "account-rate-limits",
        text: `额度状态 ${limitName}: ${usedPercent}${reachedType ? `\n${reachedType}` : ""}`,
      };
    }
    case "app/list/updated": {
      const apps = Array.isArray(params.data) ? params.data : [];
      return {
        id: "app-list-updated",
        text: `应用列表已更新: ${apps.length} 个`,
      };
    }
    case "remoteControl/status/changed": {
      const status = asString(params.status, "unknown");
      const environmentId = asString(params.environmentId).trim();
      return {
        id: "remote-control-status",
        text: `远程控制: ${formatRemoteControlStatus(status)}${environmentId ? `\n${environmentId}` : ""}`,
      };
    }
    case "externalAgentConfig/import/completed":
      return {
        id: "external-agent-config-import",
        text: "外部代理配置已导入",
      };
    case "fs/changed": {
      const changedPaths = Array.isArray(params.changedPaths)
        ? params.changedPaths.filter((entry): entry is string => typeof entry === "string")
        : [];
      const watchId = asString(params.watchId, "watch");
      return {
        id: `fs-changed-${watchId}`,
        text: [`文件变更: ${changedPaths.length} 项`, ...changedPaths.slice(0, 3)].join("\n"),
      };
    }
    case "fuzzyFileSearch/sessionUpdated": {
      const query = asString(params.query);
      const files = Array.isArray(params.files) ? params.files : [];
      const sessionId = asString(params.sessionId, "session");
      return {
        id: `fuzzy-file-search-${sessionId}`,
        text: `文件搜索: ${query || "输入中"} · ${files.length} 个结果`,
      };
    }
    case "fuzzyFileSearch/sessionCompleted": {
      const sessionId = asString(params.sessionId, "session");
      return {
        id: `fuzzy-file-search-${sessionId}`,
        text: "文件搜索已完成",
      };
    }
    case "windows/worldWritableWarning": {
      const samplePaths = Array.isArray(params.samplePaths)
        ? params.samplePaths.filter((entry): entry is string => typeof entry === "string")
        : [];
      const extraCount = Number.isFinite(params.extraCount) ? Number(params.extraCount) : 0;
      const failedScan = params.failedScan === true;
      const suffix = extraCount > 0 ? ` 等 ${extraCount + samplePaths.length} 项` : "";
      return {
        id: "windows-world-writable-warning",
        text: [
          `Windows 权限警告: ${failedScan ? "扫描未完成" : "发现可被其他用户写入的路径"}${suffix}`,
          ...samplePaths.slice(0, 3),
        ].filter(Boolean).join("\n"),
      };
    }
    case "windowsSandbox/setupCompleted": {
      const mode = asString(params.mode, "sandbox");
      const success = params.success === true;
      const error = asString(params.error).trim();
      return {
        id: "windows-sandbox-setup",
        text: `Windows Sandbox ${mode}: ${success ? "已就绪" : "设置失败"}${error ? `\n${error}` : ""}`,
      };
    }
    case "account/login/completed": {
      const success = params.success === true;
      const error = asString(params.error).trim();
      return {
        id: "account-login-completed",
        text: `账号登录: ${success ? "已完成" : "失败"}${error ? `\n${error}` : ""}`,
      };
    }
    default:
      return null;
  }
}

function formatMcpStartupStatus(status: string): string {
  switch (status) {
    case "starting":
      return "启动中";
    case "ready":
      return "已就绪";
    case "failed":
      return "启动失败";
    case "cancelled":
      return "已取消";
    default:
      return status || "已更新";
  }
}

function formatRemoteControlStatus(status: string): string {
  switch (status) {
    case "disabled":
      return "已停用";
    case "connecting":
      return "连接中";
    case "connected":
      return "已连接";
    case "errored":
      return "异常";
    default:
      return status || "已更新";
  }
}

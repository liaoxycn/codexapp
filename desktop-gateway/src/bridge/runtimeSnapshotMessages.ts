import type { AppServerThread } from "../appServerTypes.js";
import type { GatewayMessagePayload } from "../protocol.js";
import { isTerminalTurnStatus } from "../threadStatus.js";
import { mapItemToMessages } from "./messageMapping.js";
import {
  isOptimisticUserMessage,
  isSameUserMessage,
  mergeMessageBlocks,
  normalizeMessageText,
} from "./messageMerging.js";
import {
  systemStatus,
} from "./runtimeMessageStore.js";
import { trimMessagesToWindow } from "./summaries.js";
import type { ThreadRuntimeState } from "./types.js";

export function pruneCompletedArtifacts(state: ThreadRuntimeState): void {
  const assistantTextIds = new Set(
    state.snapshot.messages
      .filter((message) => message.role === "assistant")
      .filter((message) => message.blocks.some((block) => block.kind === "text" && block.value.trim().length > 0))
      .map((message) => message.id)
  );

  state.snapshot.messages = state.snapshot.messages.filter((message) => {
    if (message.role !== "assistant") {
      return true;
    }
    if (!message.blocks.every((block) => block.kind === "status")) {
      return true;
    }

    const onlyStatus = message.blocks.map((block) => block.value.trim()).join("\n");
    if (onlyStatus !== "思考中") {
      return true;
    }

    return assistantTextIds.has(message.id);
  });

  state.snapshot.messages = state.snapshot.messages.filter((message) => {
    if (!message.id.startsWith("assistant-live-")) {
      return true;
    }

    const onlyStatus = message.blocks.every((block) => block.kind === "status");
    const onlyThinking = onlyStatus && message.blocks.every((block) => block.value.trim() === "思考中");
    return !onlyThinking;
  });
}

export function normalizeCompactMessages(state: ThreadRuntimeState, includeCompletedStatus: boolean): void {
  const isCompactStatus = (message: GatewayMessagePayload): boolean =>
    message.role === "system" &&
    message.blocks.length === 1 &&
    message.blocks[0]?.kind === "status" &&
    ["已请求压缩上下文", "上下文已压缩"].includes(message.blocks[0].value.trim());

  let requestedSeen = false;
  let compactedSeen = false;
  let end = state.snapshot.messages.length;
  while (end > 0 && isCompactStatus(state.snapshot.messages[end - 1]!)) {
    const value = state.snapshot.messages[end - 1]!.blocks[0]!.value.trim();
    if (value === "已请求压缩上下文") {
      requestedSeen = true;
    }
    if (value === "上下文已压缩") {
      compactedSeen = true;
    }
    end -= 1;
  }

  state.snapshot.messages = state.snapshot.messages.slice(0, end);
  if (requestedSeen) {
    state.snapshot.messages = state.snapshot.messages.concat(systemStatus("已请求压缩上下文"));
  }
  if (includeCompletedStatus) {
    compactedSeen = true;
  }
  if (compactedSeen) {
    state.snapshot.messages = state.snapshot.messages.concat(systemStatus("上下文已压缩"));
  }
  state.transientOperation = null;
}

export function normalizeAllCompactMessages(state: ThreadRuntimeState): void {
  const nextMessages: GatewayMessagePayload[] = [];
  let pendingRequested: GatewayMessagePayload | null = null;
  let pendingCompacted: GatewayMessagePayload | null = null;

  const flush = () => {
    if (pendingRequested) {
      nextMessages.push(pendingRequested);
    }
    if (pendingCompacted) {
      nextMessages.push(pendingCompacted);
    }
    pendingRequested = null;
    pendingCompacted = null;
  };

  for (const message of state.snapshot.messages) {
    const compactStatus = getCompactStatusValue(message);
    if (!compactStatus) {
      flush();
      nextMessages.push(message);
      continue;
    }
    if (compactStatus === "已请求压缩上下文") {
      pendingRequested = systemStatus("已请求压缩上下文");
      continue;
    }
    pendingCompacted = systemStatus("上下文已压缩");
  }

  flush();
  state.snapshot.messages = nextMessages;
}

export function rebaseSnapshotMessagesFromThread(state: ThreadRuntimeState): void {
  if (!state.thread) {
    return;
  }

  const allMessages = collectThreadMessages(state.thread);
  state.snapshot.messages = trimMessagesToWindow(allMessages, state.historyWindow);
  state.snapshot.hasMoreHistory = allMessages.length > state.historyWindow;
  normalizeAllCompactMessages(state);
}

export function collectThreadMessages(thread: AppServerThread): GatewayMessagePayload[] {
  const nowMs = Date.now();
  return thread.turns.flatMap((turn, turnIndex) =>
    turn.items.flatMap((item) => {
      const turnCompleted = turn.completedAt != null || isTerminalTurnStatus(turn.status);
      const durationMs = getTurnDurationMs(turn, nowMs);
      return mapItemToMessages(item, thread.cwd).map((message) => {
        const isAssistantText =
          message.role === "assistant" &&
          message.blocks.some((block) => block.kind === "text" && block.value.trim().length > 0);
        return {
          ...message,
          ...(message.role === "assistant" ? { forkNumTurns: turnIndex + 1 } : {}),
          ...(message.role === "assistant" && durationMs != null ? { durationMs } : {}),
          ...(isAssistantText && turnCompleted ? { isFinal: true } : {}),
          ...(message.role === "user" ? { rollbackNumTurns: thread.turns.length - turnIndex } : {}),
        };
      });
    })
  );
}

function getTurnDurationMs(
  turn: AppServerThread["turns"][number],
  nowMs: number
): number | null {
  if (typeof turn.durationMs === "number" && turn.durationMs > 0) {
    return Math.round(turn.durationMs);
  }

  const startedAtMs = toMillisTimestamp(turn.startedAt);
  if (startedAtMs == null) {
    return null;
  }

  const completedAtMs = toMillisTimestamp(turn.completedAt);
  const endMs = completedAtMs ?? nowMs;
  return Math.max(1000, Math.round(endMs - startedAtMs));
}

function toMillisTimestamp(value?: number | null): number | null {
  if (typeof value !== "number" || value <= 0) {
    return null;
  }
  return value > 10_000_000_000 ? value : value * 1000;
}

function getCompactStatusValue(message: GatewayMessagePayload): string | null {
  if (
    message.role !== "system" ||
    message.blocks.length !== 1 ||
    message.blocks[0]?.kind !== "status"
  ) {
    return null;
  }

  const value = message.blocks[0].value.trim();
  return value === "已请求压缩上下文" || value === "上下文已压缩" ? value : null;
}

export function mergeSnapshotMessages(
  baseMessages: GatewayMessagePayload[],
  liveMessages: GatewayMessagePayload[]
): GatewayMessagePayload[] {
  const merged = [...baseMessages];
  const baseHasAnyRealAssistant = baseMessages.some(
    (entry) => entry.role === "assistant" && entry.blocks.some((block) => block.kind === "text" && block.value.trim().length > 0)
  );

  for (const message of liveMessages) {
    const existingIndex = merged.findIndex((entry) => entry.id === message.id);
    if (existingIndex >= 0) {
      merged[existingIndex] = mergeMessageBlocks(merged[existingIndex], message);
      continue;
    }

    if (isOptimisticUserMessage(message) && merged.some((entry) => isSameUserMessage(entry, message))) {
      continue;
    }

    if (message.id.startsWith("assistant-live-") && baseHasAnyRealAssistant) {
      continue;
    }

    if (message.role === "assistant" && message.blocks.every((block) => block.kind === "status")) {
      if (baseHasAnyRealAssistant) {
        continue;
      }
      if (merged.some((entry) => entry.role === "assistant" && normalizeMessageText(entry).length === 0)) {
        continue;
      }
    }

    merged.push(message);
  }

  return merged;
}

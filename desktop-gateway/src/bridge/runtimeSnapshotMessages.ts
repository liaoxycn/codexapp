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

    const onlyThinking = message.blocks.every((block) =>
      (block.kind === "status" && block.value.trim() === "思考中") ||
      (block.kind === "reasoning" && block.value.trim() === "正在思考")
    );
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
  const mergedMessages = mergeSnapshotMessages(allMessages, state.snapshot.messages);
  state.snapshot.messages = trimMessagesToWindow(mergedMessages, state.historyWindow);
  state.snapshot.hasMoreHistory = mergedMessages.length > state.historyWindow;
  normalizeAllCompactMessages(state);
}

export function collectThreadMessages(thread: AppServerThread): GatewayMessagePayload[] {
  const nowMs = Date.now();
  return thread.turns.flatMap((turn, turnIndex) => {
    const turnCompleted = turn.completedAt != null || isTerminalTurnStatus(turn.status);
    const durationMs = getTurnDurationMs(turn, nowMs);
    const messages = turn.items.flatMap((item) => mapItemToMessages(item, thread.cwd));
    const finalAssistantMessageId = turnCompleted ? resolveFinalAssistantMessageId(messages) : null;
    return messages.map((message) => {
      const normalizedMessage =
        turnCompleted && message.role === "assistant" && message.id !== finalAssistantMessageId
          ? convertAssistantTextToCommentary(message)
          : message;
      const isFinalAssistantMessage =
        normalizedMessage.role === "assistant" &&
        normalizedMessage.id === finalAssistantMessageId &&
        normalizedMessage.blocks.some((block) => hasRenderableAssistantBlock(block));
      return {
        ...normalizedMessage,
        ...(normalizedMessage.role === "assistant" ? { forkNumTurns: turnIndex + 1 } : {}),
        ...(normalizedMessage.role === "assistant" && durationMs != null ? { durationMs } : {}),
        ...(isFinalAssistantMessage ? { isFinal: true } : {}),
        ...(normalizedMessage.role === "user" ? { rollbackNumTurns: thread.turns.length - turnIndex } : {}),
      };
    });
  });
}

function resolveFinalAssistantMessageId(messages: GatewayMessagePayload[]): string | null {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index]!;
    if (
      message.role === "assistant" &&
      message.blocks.some((block) => block.kind === "text" && block.value.trim().length > 0)
    ) {
      return message.id;
    }
  }
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index]!;
    if (message.role === "assistant" && message.blocks.some((block) => hasRenderableAssistantBlock(block))) {
      return message.id;
    }
  }
  return null;
}

function convertAssistantTextToCommentary(message: GatewayMessagePayload): GatewayMessagePayload {
  if (!message.blocks.some((block) => block.kind === "text")) {
    return message;
  }
  return {
    ...message,
    blocks: message.blocks.map((block) =>
      block.kind === "text" ? { ...block, kind: "commentary" as const } : block
    ),
  };
}

function hasRenderableAssistantBlock(block: GatewayMessagePayload["blocks"][number]): boolean {
  switch (block.kind) {
    case "text":
    case "code":
    case "status":
    case "reasoning":
    case "commentary":
    case "plan":
    case "commandSummary":
    case "commandMeta":
    case "toolCall":
    case "webSearch":
    case "image":
    case "collab":
    case "review":
    case "hook":
    case "context":
    case "fileChangeSummary":
    case "fileChangeDiff":
      return block.value.trim().length > 0;
    case "fileChangeMeta":
      return block.value.trim().length > 0 || (block.path?.trim().length ?? 0) > 0;
    default:
      return false;
  }
}

function isAssistantProcessBlock(block: GatewayMessagePayload["blocks"][number]): boolean {
  return block.kind !== "text" && hasRenderableAssistantBlock(block);
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
  const merged: GatewayMessagePayload[] = [];
  const consumedBaseIndexes = new Set<number>();
  let baseCursor = 0;
  const baseHasAnyRealAssistant = baseMessages.some(
    (entry) => entry.role === "assistant" && entry.blocks.some((block) => block.kind === "text" && block.value.trim().length > 0)
  );
  const appendBaseUntil = (targetIndex: number) => {
    while (baseCursor < targetIndex) {
      if (!consumedBaseIndexes.has(baseCursor)) {
        merged.push(baseMessages[baseCursor]!);
        consumedBaseIndexes.add(baseCursor);
      }
      baseCursor += 1;
    }
  };

  for (const message of liveMessages) {
    const baseIndex = baseMessages.findIndex((entry) => entry.id === message.id);
    if (baseIndex >= 0) {
      appendBaseUntil(baseIndex);
      merged.push(mergeMessageBlocks(baseMessages[baseIndex]!, message));
      consumedBaseIndexes.add(baseIndex);
      baseCursor = Math.max(baseCursor, baseIndex + 1);
      continue;
    }

    const matchingBaseUserIndex = isOptimisticUserMessage(message)
      ? baseMessages.findIndex((entry, index) => !consumedBaseIndexes.has(index) && isSameUserMessage(entry, message))
      : -1;
    if (matchingBaseUserIndex >= 0) {
      appendBaseUntil(matchingBaseUserIndex);
      merged.push(baseMessages[matchingBaseUserIndex]!);
      consumedBaseIndexes.add(matchingBaseUserIndex);
      baseCursor = Math.max(baseCursor, matchingBaseUserIndex + 1);
      continue;
    }

    if (message.id.startsWith("assistant-live-") && baseHasAnyRealAssistant) {
      const liveProcessBlocks = message.blocks.filter(isAssistantProcessBlock);
      if (liveProcessBlocks.length > 0) {
        const targetBaseIndex = findFinalAssistantBaseIndex(baseMessages, consumedBaseIndexes);
        if (targetBaseIndex >= 0) {
          appendBaseUntil(targetBaseIndex);
          merged.push(mergeMessageBlocks(baseMessages[targetBaseIndex]!, {
            ...message,
            id: baseMessages[targetBaseIndex]!.id,
            blocks: liveProcessBlocks,
          }));
          consumedBaseIndexes.add(targetBaseIndex);
          baseCursor = Math.max(baseCursor, targetBaseIndex + 1);
        }
      }
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

  for (const [index, message] of baseMessages.entries()) {
    if (!consumedBaseIndexes.has(index)) {
      merged.push(message);
    }
  }

  return merged;
}

function findFinalAssistantBaseIndex(
  baseMessages: GatewayMessagePayload[],
  consumedBaseIndexes: Set<number>
): number {
  for (let index = baseMessages.length - 1; index >= 0; index -= 1) {
    const message = baseMessages[index]!;
    if (
      !consumedBaseIndexes.has(index) &&
      message.role === "assistant" &&
      message.blocks.some((block) => block.kind === "text" && block.value.trim().length > 0)
    ) {
      return index;
    }
  }
  return -1;
}

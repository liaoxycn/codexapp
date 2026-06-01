import type { AppServerThread } from "../appServerTypes.js";
import type { GatewayMessagePayload } from "../protocol.js";
import { mapItemToMessages } from "./messageMapping.js";
import {
  isOptimisticUserMessage,
  isSameUserMessage,
  mergeMessageBlocks,
  normalizeMessageText,
} from "./messageMerging.js";
import {
  systemStatus,
} from "./runtimeMessages.js";
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

export function rebaseSnapshotMessagesFromThread(state: ThreadRuntimeState): void {
  if (!state.thread) {
    return;
  }

  const allMessages = collectThreadMessages(state.thread);
  state.snapshot.messages = trimMessagesToWindow(allMessages, state.historyWindow);
  state.snapshot.hasMoreHistory = allMessages.length > state.historyWindow;
}

export function collectThreadMessages(thread: AppServerThread): GatewayMessagePayload[] {
  return thread.turns.flatMap((turn) => turn.items).flatMap((item) => mapItemToMessages(item, thread.cwd));
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

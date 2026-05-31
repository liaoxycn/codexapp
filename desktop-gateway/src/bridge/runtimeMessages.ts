import type { AppServerThreadItem } from "../appServerTypes.js";
import type { GatewayMessagePayload } from "../protocol.js";
import { asFileChanges, asString } from "./appServerValues.js";
import { buildFileChangeBlocks } from "./fileChanges.js";
import {
  isOptimisticUserMessage,
  isSameUserMessage,
} from "./messageMerging.js";
import { buildCommandExecutionBlocks, mapItemToMessages } from "./messageMapping.js";
import {
  appendAssistantDelta,
  collapseLiveAssistantMessage,
  ensureActiveAssistantMessage,
} from "./runtimeAssistantMessages.js";
import {
  appendOrMergeCodeMessage,
  appendOrMergeMessage,
  appendUserMessage,
  hasTrailingSystemStatus,
  replaceOrAppendMessage,
  systemStatus,
} from "./runtimeMessageStore.js";
import type { ThreadRuntimeState } from "./types.js";

export {
  appendAssistantDelta,
  appendOrMergeCodeMessage,
  appendOrMergeMessage,
  appendUserMessage,
  collapseLiveAssistantMessage,
  ensureActiveAssistantMessage,
  hasTrailingSystemStatus,
  replaceOrAppendMessage,
  systemStatus,
};

export function mergeThreadItem(state: ThreadRuntimeState, item: AppServerThreadItem, preferLiveAssistantId = false): void {
  if (item.type === "agentMessage") {
    const messageId = preferLiveAssistantId ? resolveLiveAssistantMessageId(state, item.id) : item.id;
    state.activeAssistantMessageId = messageId;
    state.liveAssistantItemId = item.id;
    replaceOrAppendMessage(state, {
      id: messageId,
      role: "assistant",
      blocks: [{ kind: "text", value: asString(item.text) }],
    });
    return;
  }

  if (item.type === "commandExecution") {
    replaceOrAppendMessage(state, {
      id: item.id,
      role: "assistant",
      blocks: buildCommandExecutionBlocks(item as Extract<AppServerThreadItem, { type: "commandExecution" }>),
    });
    return;
  }

  if (item.type === "fileChange") {
    replaceOrAppendMessage(state, {
      id: item.id,
      role: "assistant",
      blocks: buildFileChangeBlocks(
        asFileChanges((item as Extract<AppServerThreadItem, { type: "fileChange" }>).changes),
        asString((item as Extract<AppServerThreadItem, { type: "fileChange" }>).status),
        state.snapshot.cwd
      ),
    });
    return;
  }

  const messages = mapItemToMessages(item, state.snapshot.cwd);
  if (messages.length === 0) {
    return;
  }
  for (const message of messages) {
    if (replaceOptimisticUserMessage(state, message)) {
      continue;
    }
    replaceOrAppendMessage(state, message);
  }
}

function replaceOptimisticUserMessage(state: ThreadRuntimeState, message: GatewayMessagePayload): boolean {
  if (message.role !== "user") {
    return false;
  }
  const index = state.snapshot.messages.findIndex(
    (entry) => isOptimisticUserMessage(entry) && isSameUserMessage(entry, message)
  );
  if (index < 0) {
    return false;
  }
  replaceMessageAt(state, index, message);
  return true;
}

function resolveLiveAssistantMessageId(state: ThreadRuntimeState, itemId: string): string {
  const current = state.activeAssistantMessageId;
  if (!current) {
    return itemId;
  }
  if (current === itemId || current.startsWith("assistant-live-")) {
    return current;
  }
  return itemId;
}

function replaceMessageAt(state: ThreadRuntimeState, index: number, message: GatewayMessagePayload): void {
  state.snapshot.messages = state.snapshot.messages.map((entry, current) => (current === index ? message : entry));
}

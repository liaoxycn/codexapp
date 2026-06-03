import type { AppServerThreadItem } from "../appServerTypes.js";
import type { GatewayBlockPayload, GatewayMessagePayload } from "../protocol.js";
import { asFileChanges, asString } from "./appServerValues.js";
import { buildFileChangeBlocks } from "./fileChanges.js";
import {
  isOptimisticUserMessage,
  isSameUserMessage,
} from "./messageMerging.js";
import { buildCommandExecutionBlocks, mapItemToMessages } from "./messageMapping.js";
import {
  renameMessageId,
  replaceOrAppendMessage,
} from "./runtimeMessageStore.js";
import type { ThreadRuntimeState } from "./types.js";

export function mergeThreadItem(state: ThreadRuntimeState, item: AppServerThreadItem, preferLiveAssistantId = false): void {
  if (item.type === "agentMessage") {
    const messageId = item.id;
    const textBlock: GatewayBlockPayload = {
      kind: item.phase === "commentary" ? "commentary" : "text",
      value: asString(item.text),
    };
    if (item.phase === "commentary") {
      replaceOrAppendMessage(state, {
        id: messageId,
        role: "assistant",
        blocks: [textBlock],
      });
      if (state.activeAssistantMessageId === messageId) {
        state.activeAssistantMessageId = null;
      }
      if (state.liveAssistantItemId === messageId) {
        state.liveAssistantItemId = null;
      }
      return;
    }
    if (preferLiveAssistantId) {
      const liveId = resolveLiveAssistantMessageId(state, item.id);
      if (liveId !== item.id) {
        renameMessageId(state, liveId, item.id);
      }
    }
    state.activeAssistantMessageId = item.id;
    state.liveAssistantItemId = item.id;
    replaceOrAppendMessage(state, {
      id: messageId,
      role: "assistant",
      blocks: [textBlock],
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
    if (message.role === "assistant" && preferLiveAssistantId) {
      replaceOrReuseLiveAssistantMessage(state, message);
      continue;
    }
    replaceOrAppendMessage(state, message);
  }
}

export function replaceOrReuseLiveAssistantMessage(
  state: ThreadRuntimeState,
  message: GatewayMessagePayload
): void {
  const canReuseLiveAssistant =
    message.role === "assistant" &&
    message.blocks.some((block) => block.kind === "text" || block.kind === "reasoning");
  if (!canReuseLiveAssistant) {
    replaceOrAppendMessage(state, message);
    return;
  }

  const liveId = resolveLiveAssistantMessageId(state, message.id);
  if (liveId !== message.id) {
    renameMessageId(state, liveId, message.id);
  }
  state.activeAssistantMessageId = message.id;
  state.liveAssistantItemId = message.id;
  replaceOrAppendMessage(state, mergeWithExistingAssistantMessage(state, message.id, message.blocks));
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
  if (current === itemId || current.startsWith("assistant-live-") || canReuseAssistantProcessMessage(state, current)) {
    return current;
  }
  return itemId;
}

function canReuseAssistantProcessMessage(state: ThreadRuntimeState, messageId: string): boolean {
  const message = state.snapshot.messages.find((entry) => entry.id === messageId);
  return (
    message?.role === "assistant" &&
    message.blocks.length > 0 &&
    message.blocks.every((block) => block.kind !== "text")
  );
}

function mergeWithExistingAssistantMessage(
  state: ThreadRuntimeState,
  messageId: string,
  nextBlocks: GatewayMessagePayload["blocks"]
): GatewayMessagePayload {
  const existing = state.snapshot.messages.find((entry) => entry.id === messageId);
  const nextKinds = new Set(nextBlocks.map((block) => block.kind));
  const existingProcessBlocks = existing?.blocks.filter((block) => block.kind !== "text" && !nextKinds.has(block.kind)) ?? [];
  return {
    id: messageId,
    role: "assistant",
    blocks: [...existingProcessBlocks, ...nextBlocks],
  };
}

function replaceMessageAt(state: ThreadRuntimeState, index: number, message: GatewayMessagePayload): void {
  state.snapshot.messages = state.snapshot.messages.map((entry, current) => (current === index ? message : entry));
}

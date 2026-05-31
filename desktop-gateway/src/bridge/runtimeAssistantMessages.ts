import { mergeMessageBlocks } from "./messageMerging.js";
import { renameMessageId, replaceOrAppendMessage } from "./runtimeMessageStore.js";
import type { ThreadRuntimeState } from "./types.js";

export function ensureActiveAssistantMessage(state: ThreadRuntimeState, turnId: string): void {
  const id = `assistant-live-${turnId}`;
  state.activeAssistantMessageId = id;
  state.liveAssistantItemId = null;
  if (state.snapshot.messages.some((message) => message.id === id)) {
    return;
  }
  state.snapshot.messages = state.snapshot.messages.concat({
    id,
    role: "assistant",
    blocks: [{ kind: "status", value: "思考中" }],
  });
}

export function appendAssistantDelta(state: ThreadRuntimeState, itemId: string, delta: string): void {
  const messageId = normalizeLiveAssistantMessageId(state, itemId);
  const current = state.snapshot.messages.find((message) => message.id === messageId);
  const currentText = current?.blocks.find((block) => block.kind === "text")?.value ?? "";
  replaceOrAppendMessage(state, {
    id: messageId,
    role: "assistant",
    blocks: [{ kind: "text", value: currentText + delta }],
  });
}

export function collapseLiveAssistantMessage(state: ThreadRuntimeState): void {
  const liveId = state.activeAssistantMessageId;
  const itemId = state.liveAssistantItemId;
  if (!liveId || !itemId || liveId === itemId) {
    return;
  }

  const existing = state.snapshot.messages.find((message) => message.id === itemId);
  const live = state.snapshot.messages.find((message) => message.id === liveId);
  if (existing && live) {
    replaceOrAppendMessage(state, mergeMessageBlocks(existing, live));
    state.snapshot.messages = state.snapshot.messages.filter((message) => message.id !== liveId);
    state.activeAssistantMessageId = itemId;
    return;
  }

  renameMessageId(state, liveId, itemId);
  state.activeAssistantMessageId = itemId;
}

function normalizeLiveAssistantMessageId(state: ThreadRuntimeState, itemId: string): string {
  const current = state.activeAssistantMessageId;
  if (!current) {
    state.activeAssistantMessageId = itemId;
    state.liveAssistantItemId = itemId;
    return itemId;
  }
  if (current === itemId) {
    state.liveAssistantItemId = itemId;
    return current;
  }
  if (current.startsWith("assistant-live-")) {
    renameMessageId(state, current, itemId);
    state.activeAssistantMessageId = itemId;
    state.liveAssistantItemId = itemId;
    return itemId;
  }

  state.liveAssistantItemId = itemId;
  return current;
}

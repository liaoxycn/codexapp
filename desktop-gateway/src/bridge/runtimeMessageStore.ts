import { randomUUID } from "node:crypto";
import type { GatewayMessagePayload } from "../protocol.js";
import { mergeMessageBlocks } from "./messageMerging.js";
import type { ThreadRuntimeState } from "./types.js";
import { withLiveAssistantDuration } from "./runtimeTurnTiming.js";

export function systemStatus(text: string, id: string = randomUUID()): GatewayMessagePayload {
  return {
    id,
    role: "system",
    blocks: [{ kind: "status", value: text }],
  };
}

export function appendUserMessage(state: ThreadRuntimeState, text: string): void {
  state.snapshot.messages = state.snapshot.messages.concat({
    id: `user-live-${randomUUID()}`,
    role: "user",
    blocks: [{ kind: "text", value: text }],
  });
}

export function appendOrMergeCodeMessage(
  state: ThreadRuntimeState,
  itemId: string,
  delta: string,
  language: string,
  title: string
): void {
  const existing = state.snapshot.messages.find((message) => message.id === itemId);
  if (!existing) {
    replaceOrAppendMessage(state, {
      id: itemId,
      role: "assistant",
      blocks: [
        { kind: "text", value: title },
        { kind: "code", language, value: delta },
      ],
    });
    return;
  }

  const code = existing.blocks.find((block) => block.kind === "code");
  replaceOrAppendMessage(state, {
    ...existing,
    blocks: existing.blocks.map((block) =>
      block.kind === "code" ? { ...block, value: `${code?.value ?? ""}${delta}` } : block
    ),
  });
}

export function appendOrMergeMessage(
  state: ThreadRuntimeState,
  itemId: string,
  role: GatewayMessagePayload["role"],
  block: GatewayMessagePayload["blocks"][number],
  appendToSameKind: boolean
): void {
  const existing = state.snapshot.messages.find((message) => message.id === itemId);
  if (!existing) {
    replaceOrAppendMessage(state, { id: itemId, role, blocks: [block] });
    return;
  }

  const mergedBlocks = appendToSameKind
    ? existing.blocks.map((entry) =>
        entry.kind === block.kind ? { ...entry, value: entry.value + block.value } : entry
      )
    : existing.blocks.concat(block);
  replaceOrAppendMessage(state, { ...existing, blocks: mergedBlocks });
}

export function replaceOrAppendMessage(state: ThreadRuntimeState, message: GatewayMessagePayload): void {
  const nextMessage = withLiveAssistantDuration(state, message);
  const index = state.snapshot.messages.findIndex((entry) => entry.id === message.id);
  if (index < 0) {
    state.snapshot.messages = state.snapshot.messages.concat(nextMessage);
    return;
  }
  replaceMessageAt(state, index, nextMessage);
}

export function renameMessageId(state: ThreadRuntimeState, fromId: string, toId: string): void {
  if (fromId === toId) {
    return;
  }
  const fromIndex = state.snapshot.messages.findIndex((message) => message.id === fromId);
  if (fromIndex < 0) {
    return;
  }

  const toIndex = state.snapshot.messages.findIndex((message) => message.id === toId);
  const fromMessage = state.snapshot.messages[fromIndex];
  if (toIndex >= 0) {
    replaceMessageAt(state, toIndex, withLiveAssistantDuration(state, mergeMessageBlocks(state.snapshot.messages[toIndex], fromMessage)));
    state.snapshot.messages = state.snapshot.messages.filter((message) => message.id !== fromId);
    return;
  }

  replaceMessageAt(state, fromIndex, withLiveAssistantDuration(state, { ...fromMessage, id: toId }));
}

export function hasTrailingSystemStatus(state: ThreadRuntimeState, value: string): boolean {
  const last = [...state.snapshot.messages].reverse().find((message) => message.role === "system");
  return (last?.blocks ?? []).some((block) => block.kind === "status" && block.value === value);
}

function replaceMessageAt(state: ThreadRuntimeState, index: number, message: GatewayMessagePayload): void {
  state.snapshot.messages = state.snapshot.messages.map((entry, current) => (current === index ? message : entry));
}

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

export function upsertGatewayShellMessage(
  state: ThreadRuntimeState,
  messageId: string,
  update: {
    command: string;
    summary?: string;
    result?: string | null;
    output?: string;
    appendOutput?: string;
  }
): void {
  const existing = state.snapshot.messages.find((message) => message.id === messageId);
  const existingSummary =
    existing?.blocks.find((block) => block.kind === "commandSummary")?.value ?? "";
  const existingResult =
    existing?.blocks
      .filter((block): block is { kind: "commandMeta"; value: string } => block.kind === "commandMeta")
      .find((block) => block.value.startsWith("结果: "))
      ?.value.replace(/^结果:\s*/, "") ?? null;
  const nextOutput =
    update.output ??
    `${readShellOutput(existing)}${update.appendOutput ?? ""}`;
  const nextResult = update.result === undefined ? existingResult : update.result;
  const summary = (update.summary ?? existingSummary) || buildRunningCommandSummary(update.command);

  replaceOrAppendMessage(state, {
    id: messageId,
    role: "assistant",
    blocks: [
      {
        kind: "commandSummary",
        value: summary,
      },
      {
        kind: "commandMeta",
        value: `命令: ${formatCommandForDisplay(update.command) || "(empty)"}`,
      },
      ...(nextResult
        ? [
            {
              kind: "commandMeta" as const,
              value: `结果: ${nextResult}`,
            },
          ]
        : []),
      ...(nextOutput.length > 0
        ? [
            {
              kind: "code" as const,
              language: "shell",
              value: nextOutput,
            },
          ]
        : []),
    ],
  });
}

export function appendOrMergeCodeMessage(
  state: ThreadRuntimeState,
  itemId: string,
  delta: string,
  language: string,
  title: string,
  titleKind: "commandSummary" | "fileChangeSummary" = "commandSummary"
): void {
  const existing = state.snapshot.messages.find((message) => message.id === itemId);
  if (!existing) {
    replaceOrAppendMessage(state, {
      id: itemId,
      role: "assistant",
      blocks: [
        { kind: titleKind, value: title },
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

function readShellOutput(message: GatewayMessagePayload | undefined): string {
  const block = message?.blocks.find((entry) => entry.kind === "code" && entry.language === "shell");
  return block?.kind === "code" ? block.value : "";
}

function buildRunningCommandSummary(command: string): string {
  const display = formatCommandForDisplay(command);
  return display ? `正在运行 ${display}` : "正在运行命令";
}

function formatCommandForDisplay(command: string): string {
  const singleLine = command.trim().replace(/\s+/g, " ");
  if (singleLine.length <= 80) {
    return singleLine;
  }
  return `${singleLine.slice(0, 77)}...`;
}

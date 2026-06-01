import type { TurnStartResult } from "./appServerTypes.js";

export interface MentionInput {
  type: "mention";
  name: string;
  path: string;
}

type RequestFn = <TParams extends object, TResult = unknown>(
  method: string,
  params: TParams
) => Promise<TResult>;

export function buildUserInput(text: string) {
  const mentions = extractMentionInputs(text);
  return [
    {
      type: "text",
      text,
      text_elements: [],
    },
    ...mentions,
  ];
}

export async function startTurn(
  request: RequestFn,
  threadId: string,
  text: string
): Promise<string> {
  const result = (await request("turn/start", {
    threadId,
    input: buildUserInput(text),
  })) as TurnStartResult;
  return result.turn.id;
}

export async function interruptTurn(
  request: RequestFn,
  threadId: string,
  turnId: string
): Promise<void> {
  await request("turn/interrupt", {
    threadId,
    turnId,
  });
}

export async function steerTurn(
  request: RequestFn,
  threadId: string,
  expectedTurnId: string,
  text: string
): Promise<void> {
  await request("turn/steer", {
    threadId,
    expectedTurnId,
    input: buildUserInput(text),
  });
}

export async function startThreadCompaction(
  request: RequestFn,
  threadId: string
): Promise<void> {
  await request("thread/compact/start", {
    threadId,
  });
}

export async function sendThreadShellCommand(
  request: RequestFn,
  threadId: string,
  command: string
): Promise<void> {
  await request("thread/shellCommand", {
    threadId,
    command,
  });
}

function extractMentionInputs(text: string): MentionInput[] {
  const mentions: MentionInput[] = [];
  const seenPaths = new Set<string>();
  const matcher = /@\{([^}]+)\}/g;
  let match: RegExpExecArray | null;
  while ((match = matcher.exec(text)) !== null) {
    const path = match[1]?.trim();
    if (!path || seenPaths.has(path)) {
      continue;
    }
    seenPaths.add(path);
    mentions.push({
      type: "mention",
      name: mentionNameFromPath(path),
      path,
    });
  }
  return mentions;
}

function mentionNameFromPath(path: string): string {
  const normalized = path.replaceAll("\\", "/");
  const segments = normalized.split("/").filter(Boolean);
  return segments.at(-1) ?? path;
}

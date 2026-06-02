import type { AppServerApprovalPolicy, AppServerSandboxPolicy, TurnStartResult } from "./appServerTypes.js";
import type { ThreadStartOptions } from "./protocol.js";

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
  text: string,
  options: ThreadStartOptions = {}
): Promise<string> {
  const params: Record<string, unknown> = {
    threadId,
    input: buildUserInput(text),
  };
  if (options.cwd) {
    params.cwd = options.cwd;
  }
  if (options.model) {
    params.model = options.model;
  }
  if (options.reasoningEffort) {
    params.effort = options.reasoningEffort;
  }
  if (options.approvalPolicy) {
    params.approvalPolicy = mapApprovalPolicy(options.approvalPolicy);
  }
  if (options.approvalsReviewer) {
    params.approvalsReviewer = options.approvalsReviewer;
  }
  if (options.sandboxMode) {
    params.sandboxPolicy = mapSandboxPolicy(options.sandboxMode);
  }
  const result = (await request("turn/start", params)) as TurnStartResult;
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

function mapApprovalPolicy(value: string): AppServerApprovalPolicy {
  switch (value) {
    case "untrusted":
    case "on-failure":
    case "on-request":
    case "never":
      return value;
    default:
      return "never";
  }
}

function mapSandboxPolicy(value: string): AppServerSandboxPolicy {
  switch (value) {
    case "danger-full-access":
      return { type: "dangerFullAccess" };
    case "read-only":
      return { type: "readOnly", networkAccess: false };
    case "workspace-write":
      return {
        type: "workspaceWrite",
        writableRoots: [],
        networkAccess: false,
        excludeTmpdirEnvVar: false,
        excludeSlashTmp: false,
      };
    default:
      return {
        type: "workspaceWrite",
        writableRoots: [],
        networkAccess: value.includes("+net"),
        excludeTmpdirEnvVar: false,
        excludeSlashTmp: false,
      };
  }
}

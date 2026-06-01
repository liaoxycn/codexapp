import type { TurnStartResult } from "./appServerTypes.js";

type RequestFn = <TParams extends object, TResult = unknown>(
  method: string,
  params: TParams
) => Promise<TResult>;

function buildTextInput(text: string) {
  return [
    {
      type: "text",
      text,
      text_elements: [],
    },
  ];
}

export async function startTurn(
  request: RequestFn,
  threadId: string,
  text: string
): Promise<string> {
  const result = (await request("turn/start", {
    threadId,
    input: buildTextInput(text),
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
    input: buildTextInput(text),
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

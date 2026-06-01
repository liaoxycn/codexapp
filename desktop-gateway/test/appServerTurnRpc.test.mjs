import assert from "node:assert/strict";
import test from "node:test";
import {
  sendThreadShellCommand,
  startThreadCompaction,
  startTurn,
  steerTurn,
} from "../dist/appServerTurnRpc.js";

test("startTurn sends normalized text input payload and returns turn id", async () => {
  const calls = [];
  const request = async (method, params) => {
    calls.push({ method, params });
    return { turn: { id: "turn-1" } };
  };

  const turnId = await startTurn(request, "thread-1", "hello");

  assert.equal(turnId, "turn-1");
  assert.deepEqual(calls, [
    {
      method: "turn/start",
      params: {
        threadId: "thread-1",
        input: [{ type: "text", text: "hello", text_elements: [] }],
      },
    },
  ]);
});

test("steerTurn reuses normalized text input payload", async () => {
  const calls = [];
  const request = async (method, params) => {
    calls.push({ method, params });
    return null;
  };

  await steerTurn(request, "thread-2", "turn-9", "continue");

  assert.deepEqual(calls, [
    {
      method: "turn/steer",
      params: {
        threadId: "thread-2",
        expectedTurnId: "turn-9",
        input: [{ type: "text", text: "continue", text_elements: [] }],
      },
    },
  ]);
});

test("thread compaction and shell command RPCs forward exact params", async () => {
  const calls = [];
  const request = async (method, params) => {
    calls.push({ method, params });
    return null;
  };

  await startThreadCompaction(request, "thread-3");
  await sendThreadShellCommand(request, "thread-3", "dir");

  assert.deepEqual(calls, [
    {
      method: "thread/compact/start",
      params: { threadId: "thread-3" },
    },
    {
      method: "thread/shellCommand",
      params: { threadId: "thread-3", command: "dir" },
    },
  ]);
});

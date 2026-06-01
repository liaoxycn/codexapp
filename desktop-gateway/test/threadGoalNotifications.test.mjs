import assert from "node:assert/strict";
import test from "node:test";
import {
  handleThreadGoalCleared,
  handleThreadGoalUpdated,
} from "../dist/bridge/threadNotifications.js";

function createState() {
  return {
    snapshot: {
      messages: [],
    },
  };
}

test("thread goal notifications upsert a stable mobile status message", () => {
  const state = createState();
  const threads = new Map([["thread-1", state]]);
  let emitCount = 0;
  const deps = {
    threads,
    emitChanged: () => {
      emitCount += 1;
    },
  };

  handleThreadGoalUpdated(
    {
      method: "thread/goal/updated",
      params: {
        threadId: "thread-1",
        goal: { objective: "finish protocol work", status: "active" },
      },
    },
    deps
  );
  handleThreadGoalCleared(
    {
      method: "thread/goal/cleared",
      params: { threadId: "thread-1" },
    },
    deps
  );

  assert.equal(emitCount, 2);
  assert.deepEqual(state.snapshot.messages, [
    {
      id: "thread-goal",
      role: "system",
      blocks: [{ kind: "status", value: "目标已清除" }],
    },
  ]);
});

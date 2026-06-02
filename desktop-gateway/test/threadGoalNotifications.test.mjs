import assert from "node:assert/strict";
import test from "node:test";
import {
  handleThreadGoalCleared,
  handleThreadGoalUpdated,
} from "../dist/bridge/threadNotifications.js";

function createState() {
  return {
    summary: { id: "thread-1", status: "idle", updatedAt: 1 },
    lastActivityAtMs: 1,
    currentTurnId: null,
    pendingApproval: null,
    runningSignalUntilMs: 0,
    turnCompletionGraceUntilMs: 0,
    snapshot: {
      messages: [],
      threads: [{ id: "thread-1", status: "idle", updatedAt: 1 }],
      isGenerating: false,
    },
  };
}

test("thread goal notifications upsert a stable mobile status message", () => {
  const state = createState();
  const threads = new Map([["thread-1", state]]);
  let emitCount = 0;
  const deps = {
    threads,
    updateSummaryStatus: () => {},
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

test("active thread goal update renews the mobile running state", () => {
  const state = createState();
  const threads = new Map([["thread-1", state]]);
  const statuses = [];
  const before = Date.now();

  handleThreadGoalUpdated(
    {
      method: "thread/goal/updated",
      params: {
        threadId: "thread-1",
        turnId: "turn-goal-2",
        goal: {
          objective: "finish protocol work",
          status: "active",
          updatedAt: Math.floor(before / 1000),
        },
      },
    },
    {
      threads,
      updateSummaryStatus: (threadId, status) => {
        statuses.push(status);
        state.summary = { ...state.summary, status };
        state.snapshot.threads = state.snapshot.threads.map((thread) =>
          thread.id === threadId ? { ...thread, status } : thread
        );
      },
      emitChanged: () => {},
    }
  );

  assert.equal(state.snapshot.isGenerating, true);
  assert.equal(state.currentTurnId, "turn-goal-2");
  assert.equal(state.summary.updatedAt >= before - 1000, true);
  assert.equal(state.snapshot.threads[0].status, "running");
  assert.equal(state.runningSignalUntilMs > before, true);
  assert.deepEqual(statuses, ["running"]);
});

test("non-active thread goal update does not force a stopped goal to running", () => {
  const state = createState();
  const threads = new Map([["thread-1", state]]);
  const statuses = [];

  handleThreadGoalUpdated(
    {
      method: "thread/goal/updated",
      params: {
        threadId: "thread-1",
        goal: { objective: "finish protocol work", status: "complete" },
      },
    },
    {
      threads,
      updateSummaryStatus: (_threadId, status) => {
        statuses.push(status);
      },
      emitChanged: () => {},
    }
  );

  assert.equal(state.snapshot.isGenerating, false);
  assert.equal(state.currentTurnId, null);
  assert.deepEqual(statuses, []);
});

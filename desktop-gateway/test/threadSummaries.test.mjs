import assert from "node:assert/strict";
import test from "node:test";
import {
  dedupeSummaries,
  mapThreadToSummary,
  touchThreadActivity,
} from "../dist/bridge/threadSummaries.js";

function thread(id, overrides = {}) {
  return {
    id,
    preview: id,
    status: "idle",
    cwd: "D:/Projects/TestApp",
    updatedAt: 100,
    name: null,
    turns: [],
    modelProvider: "openai",
    ...overrides,
  };
}

test("mapThreadToSummary uses latest message text as subtitle and cwd leaf as group", () => {
  const summary = mapThreadToSummary(thread("thread-1", {
    preview: "preview text",
    turns: [
      {
        id: "turn-1",
        startedAt: 100,
        completedAt: 120,
        status: "completed",
        items: [{ type: "agentMessage", text: "final answer" }],
        itemsView: "full",
      },
    ],
  }));

  assert.equal(summary.subtitle, "final answer");
  assert.equal(summary.groupKind, "project");
  assert.equal(summary.groupLabel, "TestApp");
});

test("dedupeSummaries keeps the last payload for the same thread id", () => {
  const deduped = dedupeSummaries([
    { id: "thread-1", title: "old" },
    { id: "thread-1", title: "new" },
  ]);

  assert.deepEqual(deduped, [{ id: "thread-1", title: "new" }]);
});

test("touchThreadActivity updates summary and snapshot thread timestamps", () => {
  const state = {
    summary: { id: "thread-1", updatedAt: 1000 },
    lastActivityAtMs: 1000,
    snapshot: {
      threads: [
        { id: "thread-1", updatedAt: 1000 },
        { id: "thread-2", updatedAt: 500 },
      ],
    },
  };

  touchThreadActivity(state, 2500);

  assert.equal(state.lastActivityAtMs, 2500);
  assert.equal(state.summary.updatedAt, 2500);
  assert.deepEqual(state.snapshot.threads.map((item) => item.updatedAt), [2500, 500]);
});

import assert from "node:assert/strict";
import test from "node:test";
import { buildVisibleThreadSummaries } from "../dist/bridgeBackend.js";

function thread(id, overrides = {}) {
  return {
    id,
    preview: id,
    status: "idle",
    cwd: "C:/work",
    updatedAt: 100,
    name: null,
    turns: [],
    modelProvider: "openai",
    ...overrides,
  };
}

test("visible thread summaries skip excluded self-test items", () => {
  const summaries = buildVisibleThreadSummaries([
    thread("live-1"),
    thread("skip-me", { name: "调研 Codex 安卓壳方案" }),
    thread("live-2"),
  ]);

  assert.deepEqual(
    summaries.map((item) => item.id),
    ["live-1", "live-2"]
  );
});

test("visible thread summaries convert thread and turn timestamps to milliseconds", () => {
  const summaries = buildVisibleThreadSummaries([
    thread("live-1", {
      updatedAt: 100,
      turns: [
        { id: "turn-1", startedAt: 120, completedAt: 180, status: "completed", items: [], itemsView: "full" },
        { id: "turn-2", startedAt: 220, completedAt: null, status: "running", items: [], itemsView: "summary" },
      ],
    }),
  ]);

  assert.equal(summaries[0].updatedAt, 220000);
});

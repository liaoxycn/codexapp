import assert from "node:assert/strict";
import test from "node:test";
import {
  getDefaultThreadId,
  isStaleSelectionRequest,
  resolveThreadId,
} from "../dist/bridge/threadSelection.js";

function state(id, archived = false) {
  return {
    summary: { id, archived },
  };
}

test("getDefaultThreadId prefers current unarchived thread", () => {
  const threads = new Map([
    ["thread-1", state("thread-1")],
    ["thread-2", state("thread-2")],
  ]);

  assert.equal(getDefaultThreadId(threads, "thread-2"), "thread-2");
});

test("getDefaultThreadId falls back to first active thread", () => {
  const threads = new Map([
    ["archived", state("archived", true)],
    ["active", state("active")],
  ]);

  assert.equal(getDefaultThreadId(threads, "archived"), "active");
});

test("resolveThreadId falls back to default when requested thread is missing", () => {
  const threads = new Map([
    ["thread-1", state("thread-1")],
  ]);

  assert.equal(resolveThreadId(threads, "thread-1", "missing"), "thread-1");
});

test("isStaleSelectionRequest detects changed selection version for different thread", () => {
  assert.equal(
    isStaleSelectionRequest({
      currentThreadId: "thread-2",
      currentSelectionVersion: 2,
      requestedThreadId: "thread-1",
      requestedSelectionVersion: 1,
    }),
    true
  );

  assert.equal(
    isStaleSelectionRequest({
      currentThreadId: "thread-1",
      currentSelectionVersion: 2,
      requestedThreadId: "thread-1",
      requestedSelectionVersion: 1,
    }),
    false
  );
});

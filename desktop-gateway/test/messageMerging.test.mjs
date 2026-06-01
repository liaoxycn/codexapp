import assert from "node:assert/strict";
import test from "node:test";
import {
  isOptimisticUserMessage,
  isSameUserMessage,
  mergeMessageBlocks,
  normalizeMessageText,
} from "../dist/bridge/messageMerging.js";

test("normalizeMessageText trims and joins text blocks only", () => {
  assert.equal(
    normalizeMessageText({
      id: "msg-1",
      role: "user",
      blocks: [
        { kind: "text", value: "  hello " },
        { kind: "status", value: "ignored" },
        { kind: "text", value: " world  " },
      ],
    }),
    "hello\nworld"
  );
});

test("isOptimisticUserMessage only matches live user placeholders", () => {
  assert.equal(
    isOptimisticUserMessage({
      id: "user-live-1",
      role: "user",
      blocks: [{ kind: "text", value: "hello" }],
    }),
    true
  );
  assert.equal(
    isOptimisticUserMessage({
      id: "assistant-1",
      role: "assistant",
      blocks: [{ kind: "text", value: "hello" }],
    }),
    false
  );
});

test("isSameUserMessage compares normalized user text", () => {
  const left = {
    id: "user-live-1",
    role: "user",
    blocks: [{ kind: "text", value: " hello " }],
  };
  const right = {
    id: "user-1",
    role: "user",
    blocks: [{ kind: "text", value: "hello" }],
  };

  assert.equal(isSameUserMessage(left, right), true);
});

test("mergeMessageBlocks keeps richer block per kind and appends missing kinds", () => {
  const merged = mergeMessageBlocks(
    {
      id: "assistant-1",
      role: "assistant",
      blocks: [{ kind: "text", value: "short" }],
    },
    {
      id: "assistant-1",
      role: "assistant",
      blocks: [
        { kind: "text", value: "longer text" },
        { kind: "status", value: "done" },
      ],
    }
  );

  assert.deepEqual(merged.blocks, [
    { kind: "text", value: "longer text" },
    { kind: "status", value: "done" },
  ]);
});

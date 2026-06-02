import assert from "node:assert/strict";
import test from "node:test";
import {
  appendAssistantDelta,
  collapseLiveAssistantMessage,
  ensureActiveAssistantMessage,
} from "../dist/bridge/runtimeAssistantMessages.js";
import { renameMessageId } from "../dist/bridge/runtimeMessageStore.js";

function createState(messages = []) {
  return {
    activeAssistantMessageId: null,
    liveAssistantItemId: null,
    currentTurnStartedAtMs: null,
    snapshot: {
      messages,
    },
  };
}

test("ensureActiveAssistantMessage inserts stable reasoning placeholder", () => {
  const state = createState();

  ensureActiveAssistantMessage(state, "turn-1");

  assert.equal(state.activeAssistantMessageId, "assistant-live-turn-1");
  assert.equal(state.liveAssistantItemId, null);
  assert.deepEqual(state.snapshot.messages, [
    {
      id: "assistant-live-turn-1",
      role: "assistant",
      blocks: [{ kind: "reasoning", value: "正在思考" }],
    },
  ]);
});

test("ensureActiveAssistantMessage attaches live turn duration", () => {
  const state = createState();
  state.currentTurnStartedAtMs = Date.now() - 5000;

  ensureActiveAssistantMessage(state, "turn-1");

  assert.equal(state.snapshot.messages[0]?.durationMs >= 1000, true);
});

test("appendAssistantDelta reuses live placeholder and keeps reasoning block", () => {
  const state = createState();

  ensureActiveAssistantMessage(state, "turn-1");
  appendAssistantDelta(state, "item-1", "hello");

  assert.equal(state.activeAssistantMessageId, "item-1");
  assert.equal(state.liveAssistantItemId, "item-1");
  assert.deepEqual(state.snapshot.messages, [
    {
      id: "item-1",
      role: "assistant",
      blocks: [
        { kind: "reasoning", value: "正在思考" },
        { kind: "text", value: "hello" },
      ],
    },
  ]);
});

test("appendAssistantDelta keeps live turn duration on reused message", () => {
  const state = createState();
  state.currentTurnStartedAtMs = Date.now() - 5000;

  ensureActiveAssistantMessage(state, "turn-1");
  appendAssistantDelta(state, "item-1", "hello");

  assert.equal(state.snapshot.messages[0]?.id, "item-1");
  assert.equal(state.snapshot.messages[0]?.durationMs >= 1000, true);
});

test("collapseLiveAssistantMessage keeps richer merged message when both ids exist", () => {
  const state = createState([
    {
      id: "assistant-live-turn-1",
      role: "assistant",
      blocks: [{ kind: "text", value: "partial answer" }],
    },
    {
      id: "item-1",
      role: "assistant",
      blocks: [
        { kind: "text", value: "final answer with more detail" },
        { kind: "status", value: "done" },
      ],
    },
  ]);
  state.activeAssistantMessageId = "assistant-live-turn-1";
  state.liveAssistantItemId = "item-1";

  collapseLiveAssistantMessage(state);

  assert.equal(state.activeAssistantMessageId, "item-1");
  assert.deepEqual(state.snapshot.messages, [
    {
      id: "item-1",
      role: "assistant",
      blocks: [
        { kind: "text", value: "final answer with more detail" },
        { kind: "status", value: "done" },
      ],
    },
  ]);
});

test("renameMessageId merges duplicate message ids without dropping richer blocks", () => {
  const state = createState([
    {
      id: "assistant-live-turn-1",
      role: "assistant",
      blocks: [{ kind: "text", value: "partial answer" }],
    },
    {
      id: "item-1",
      role: "assistant",
      blocks: [
        { kind: "text", value: "final answer with more detail" },
        { kind: "status", value: "done" },
      ],
    },
  ]);

  renameMessageId(state, "assistant-live-turn-1", "item-1");

  assert.deepEqual(state.snapshot.messages, [
    {
      id: "item-1",
      role: "assistant",
      blocks: [
        { kind: "text", value: "final answer with more detail" },
        { kind: "status", value: "done" },
      ],
    },
  ]);
});

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
    snapshot: {
      messages,
    },
  };
}

test("appendAssistantDelta replaces live placeholder text instead of prefixing thinking status", () => {
  const state = createState();

  ensureActiveAssistantMessage(state, "turn-1");
  appendAssistantDelta(state, "item-1", "hello");

  assert.equal(state.activeAssistantMessageId, "item-1");
  assert.equal(state.liveAssistantItemId, "item-1");
  assert.deepEqual(state.snapshot.messages, [
    {
      id: "item-1",
      role: "assistant",
      blocks: [{ kind: "text", value: "hello" }],
    },
  ]);
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

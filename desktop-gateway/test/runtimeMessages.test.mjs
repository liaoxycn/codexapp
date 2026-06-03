import assert from "node:assert/strict";
import test from "node:test";
import {
  mergeThreadItem,
  replaceOrReuseLiveAssistantMessage,
} from "../dist/bridge/runtimeMessages.js";

function createState(messages = []) {
  return {
    activeAssistantMessageId: null,
    liveAssistantItemId: null,
    snapshot: {
      cwd: "D:/repo",
      messages,
    },
  };
}

test("mergeThreadItem reuses live thinking placeholder for completed agent message", () => {
  const state = createState([
    {
      id: "assistant-live-turn-1",
      role: "assistant",
      blocks: [{ kind: "reasoning", value: "正在思考" }],
    },
  ]);
  state.activeAssistantMessageId = "assistant-live-turn-1";

  mergeThreadItem(
    state,
    { type: "agentMessage", id: "assistant-1", text: "final answer" },
    true
  );

  assert.equal(state.activeAssistantMessageId, "assistant-1");
  assert.equal(state.liveAssistantItemId, "assistant-1");
  assert.deepEqual(state.snapshot.messages, [
    {
      id: "assistant-1",
      role: "assistant",
      blocks: [{ kind: "text", value: "final answer" }],
    },
  ]);
});

test("mergeThreadItem clears completed commentary from active live slot", () => {
  const state = createState([
    {
      id: "assistant-progress",
      role: "assistant",
      blocks: [{ kind: "commentary", value: "checking" }],
    },
  ]);
  state.activeAssistantMessageId = "assistant-progress";
  state.liveAssistantItemId = "assistant-progress";

  mergeThreadItem(
    state,
    { type: "agentMessage", id: "assistant-progress", text: "checking", phase: "commentary" },
    true
  );

  assert.equal(state.activeAssistantMessageId, null);
  assert.equal(state.liveAssistantItemId, null);
  assert.deepEqual(state.snapshot.messages, [
    {
      id: "assistant-progress",
      role: "assistant",
      blocks: [{ kind: "commentary", value: "checking" }],
    },
  ]);
});

test("mergeThreadItem replaces live commentary when item becomes final answer", () => {
  const state = createState([
    {
      id: "assistant-final",
      role: "assistant",
      blocks: [{ kind: "commentary", value: "partial final" }],
    },
  ]);
  state.activeAssistantMessageId = "assistant-final";
  state.liveAssistantItemId = "assistant-final";

  mergeThreadItem(
    state,
    { type: "agentMessage", id: "assistant-final", text: "final answer", phase: "final_answer" },
    true
  );

  assert.deepEqual(state.snapshot.messages, [
    {
      id: "assistant-final",
      role: "assistant",
      blocks: [{ kind: "text", value: "final answer" }],
    },
  ]);
});

test("replaceOrReuseLiveAssistantMessage replaces thinking placeholder with real reasoning", () => {
  const state = createState([
    {
      id: "assistant-live-turn-1",
      role: "assistant",
      blocks: [{ kind: "reasoning", value: "正在思考" }],
    },
  ]);
  state.activeAssistantMessageId = "assistant-live-turn-1";

  replaceOrReuseLiveAssistantMessage(state, {
    id: "reasoning-1",
    role: "assistant",
    blocks: [{ kind: "reasoning", value: "actual reasoning" }],
  });

  assert.deepEqual(state.snapshot.messages, [
    {
      id: "reasoning-1",
      role: "assistant",
      blocks: [{ kind: "reasoning", value: "actual reasoning" }],
    },
  ]);
});

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
      blocks: [
        { kind: "reasoning", value: "正在思考" },
        { kind: "text", value: "final answer" },
      ],
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

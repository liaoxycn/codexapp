import assert from "node:assert/strict";
import test from "node:test";
import {
  normalizeAllCompactMessages,
  normalizeCompactMessages,
  collectThreadMessages,
  mergeSnapshotMessages,
} from "../dist/bridge/runtimeSnapshotMessages.js";

function statusMessage(id, value) {
  return {
    id,
    role: "system",
    blocks: [{ kind: "status", value }],
  };
}

test("normalizeCompactMessages keeps one requested and one completed compact status", () => {
  const state = {
    transientOperation: "compact",
    snapshot: {
      messages: [
        { id: "assistant-1", role: "assistant", blocks: [{ kind: "text", value: "done" }] },
        statusMessage("status-1", "已请求压缩上下文"),
        statusMessage("status-2", "已请求压缩上下文"),
        statusMessage("status-3", "上下文已压缩"),
      ],
    },
  };

  normalizeCompactMessages(state, true);

  assert.deepEqual(
    state.snapshot.messages.map((message) => message.blocks[0].value),
    ["done", "已请求压缩上下文", "上下文已压缩"]
  );
  assert.equal(state.transientOperation, null);
});

test("normalizeAllCompactMessages cleans historical duplicate compact status runs", () => {
  const state = {
    snapshot: {
      messages: [
        statusMessage("status-1", "已请求压缩上下文"),
        statusMessage("status-2", "已请求压缩上下文"),
        statusMessage("status-3", "上下文已压缩"),
        { id: "assistant-1", role: "assistant", blocks: [{ kind: "text", value: "done" }] },
        statusMessage("status-4", "上下文已压缩"),
        statusMessage("status-5", "上下文已压缩"),
      ],
    },
  };

  normalizeAllCompactMessages(state);

  assert.deepEqual(
    state.snapshot.messages.map((message) => message.blocks[0].value),
    ["已请求压缩上下文", "上下文已压缩", "done", "上下文已压缩"]
  );
});

test("mergeSnapshotMessages drops live assistant placeholder after real assistant text arrives", () => {
  const baseMessages = [
    {
      id: "assistant-real",
      role: "assistant",
      blocks: [{ kind: "text", value: "final answer" }],
    },
  ];
  const liveMessages = [
    {
      id: "assistant-live-turn-1",
      role: "assistant",
      blocks: [{ kind: "reasoning", value: "正在思考" }],
    },
  ];

  const merged = mergeSnapshotMessages(baseMessages, liveMessages);

  assert.deepEqual(merged, baseMessages);
});

test("collectThreadMessages marks only completed assistant text as final", () => {
  const messages = collectThreadMessages({
    id: "thread-1",
    preview: "",
    status: "active",
    cwd: "D:/Projects/Test",
    updatedAt: 1,
    name: null,
    modelProvider: "openai",
    turns: [
      {
        id: "turn-running",
        status: "running",
        completedAt: null,
        items: [{ type: "agentMessage", id: "assistant-running", text: "working" }],
      },
      {
        id: "turn-done",
        status: "completed",
        completedAt: 2,
        durationMs: 61000,
        items: [{ type: "agentMessage", id: "assistant-final", text: "done" }],
      },
    ],
  });

  assert.equal(messages.find((message) => message.id === "assistant-running")?.isFinal, undefined);
  assert.equal(messages.find((message) => message.id === "assistant-final")?.isFinal, true);
  assert.equal(messages.find((message) => message.id === "assistant-final")?.durationMs, 61000);
});

test("collectThreadMessages computes running turn duration from startedAt", () => {
  const realNow = Date.now;
  Date.now = () => 125000;
  try {
    const messages = collectThreadMessages({
      id: "thread-1",
      preview: "",
      status: "active",
      cwd: "D:/Projects/Test",
      updatedAt: 1,
      name: null,
      modelProvider: "openai",
      turns: [
        {
          id: "turn-running",
          status: "running",
          startedAt: 120,
          completedAt: null,
          items: [
            { type: "reasoning", id: "reasoning-running", summary: ["thinking"] },
            { type: "agentMessage", id: "assistant-running", text: "working" },
          ],
        },
      ],
    });

    assert.equal(messages.find((message) => message.id === "reasoning-running")?.durationMs, 5000);
    assert.equal(messages.find((message) => message.id === "assistant-running")?.durationMs, 5000);
    assert.equal(messages.find((message) => message.id === "assistant-running")?.isFinal, undefined);
  } finally {
    Date.now = realNow;
  }
});

import assert from "node:assert/strict";
import test from "node:test";
import {
  normalizeAllCompactMessages,
  normalizeCompactMessages,
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
      blocks: [{ kind: "status", value: "思考中" }],
    },
  ];

  const merged = mergeSnapshotMessages(baseMessages, liveMessages);

  assert.deepEqual(merged, baseMessages);
});

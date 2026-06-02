import assert from "node:assert/strict";
import test from "node:test";
import {
  finalizeCompactRuntimeState,
  finalizeTurnRuntimeState,
} from "../dist/bridge/turnFinalization.js";

function createState(overrides = {}) {
  return {
    summary: { id: "thread-1", updatedAt: 1, status: "running" },
    thread: null,
    isSubscribed: false,
    lastActivityAtMs: 1,
    historyWindow: 24,
    currentTurnId: "turn-1",
    activeAssistantMessageId: "assistant-live-turn-1",
    liveAssistantItemId: "assistant-item-1",
    transientOperation: null,
    pendingApproval: null,
    stopRequested: false,
    isFinalizing: false,
    runningSignalUntilMs: 0,
    turnCompletionGraceUntilMs: 0,
    model: null,
    instructionSources: [],
    approvalPolicy: null,
    approvalsReviewer: null,
    sandbox: null,
    reasoningEffort: null,
    snapshot: {
      threads: [],
      selectedThreadId: "thread-1",
      messages: [],
      hasMoreHistory: false,
      pendingApproval: null,
      chips: [],
    files: [],
      slashCommands: [],
      cwd: "D:/Projects/Test",
      permissionSummary: "",
      isGenerating: true,
    },
    ...overrides,
  };
}

test("finalizeTurnRuntimeState appends stopped status once and clears running state", async () => {
  const state = createState({
    stopRequested: true,
    snapshot: {
      threads: [],
      selectedThreadId: "thread-1",
      messages: [],
      hasMoreHistory: false,
      pendingApproval: null,
      chips: [],
    files: [],
      slashCommands: [],
      cwd: "D:/Projects/Test",
      permissionSummary: "",
      isGenerating: true,
    },
  });
  const threads = new Map([["thread-1", state]]);
  const statuses = [];
  let emitCount = 0;

  await finalizeTurnRuntimeState({
    threads,
    threadId: "thread-1",
    turnStatus: "completed",
    emitChanged: () => {
      emitCount += 1;
    },
    refreshThread: async () => {},
    updateSummaryStatus: (_threadId, status) => {
      statuses.push(status);
    },
  });

  assert.equal(state.snapshot.isGenerating, false);
  assert.equal(state.stopRequested, false);
  assert.equal(state.currentTurnId, null);
  assert.equal(state.transientOperation, null);
  assert.equal(emitCount, 1);
  assert.deepEqual(statuses, ["idle"]);
  assert.equal(
    state.snapshot.messages.at(-1)?.blocks?.[0]?.value,
    "已停止，本轮可继续补充输入。"
  );
});

test("finalizeTurnRuntimeState keeps running during completion grace", async () => {
  const state = createState({
    turnCompletionGraceUntilMs: Date.now() + 5000,
  });
  const threads = new Map([["thread-1", state]]);
  const statuses = [];

  await finalizeTurnRuntimeState({
    threads,
    threadId: "thread-1",
    turnStatus: "completed",
    completedTurnId: "turn-1",
    emitChanged: () => {},
    refreshThread: async () => {},
    updateSummaryStatus: (_threadId, status) => {
      statuses.push(status);
    },
  });

  assert.equal(state.snapshot.isGenerating, true);
  assert.equal(state.currentTurnId, null);
  assert.deepEqual(statuses, ["running"]);
});

test("finalizeTurnRuntimeState does not clear a newer turn started during refresh", async () => {
  const state = createState();
  const threads = new Map([["thread-1", state]]);
  const statuses = [];

  await finalizeTurnRuntimeState({
    threads,
    threadId: "thread-1",
    turnStatus: "completed",
    completedTurnId: "turn-1",
    emitChanged: () => {},
    refreshThread: async () => {
      state.currentTurnId = "turn-2";
      state.snapshot.isGenerating = true;
      state.runningSignalUntilMs = Date.now() + 5000;
    },
    updateSummaryStatus: (_threadId, status) => {
      statuses.push(status);
    },
  });

  assert.equal(state.snapshot.isGenerating, true);
  assert.equal(state.currentTurnId, "turn-2");
  assert.deepEqual(statuses, ["running"]);
});

test("finalizeCompactRuntimeState normalizes compact status and clears transient state", async () => {
  const state = createState({
    transientOperation: "compact",
    snapshot: {
      threads: [],
      selectedThreadId: "thread-1",
      messages: [
        { id: "assistant-1", role: "assistant", blocks: [{ kind: "text", value: "done" }] },
        { id: "status-1", role: "system", blocks: [{ kind: "status", value: "已请求压缩上下文" }] },
        { id: "status-2", role: "system", blocks: [{ kind: "status", value: "已请求压缩上下文" }] },
        { id: "status-3", role: "system", blocks: [{ kind: "status", value: "上下文已压缩" }] },
      ],
      hasMoreHistory: false,
      pendingApproval: null,
      chips: [],
    files: [],
      slashCommands: [],
      cwd: "D:/Projects/Test",
      permissionSummary: "",
      isGenerating: true,
    },
  });
  const threads = new Map([["thread-1", state]]);
  const statuses = [];

  await finalizeCompactRuntimeState({
    threads,
    threadId: "thread-1",
    emitChanged: () => {},
    refreshThread: async () => {},
    updateSummaryStatus: (_threadId, status) => {
      statuses.push(status);
    },
  });

  assert.equal(state.snapshot.isGenerating, false);
  assert.equal(state.currentTurnId, null);
  assert.equal(state.activeAssistantMessageId, null);
  assert.equal(state.liveAssistantItemId, null);
  assert.equal(state.transientOperation, null);
  assert.deepEqual(statuses, ["idle"]);
  assert.deepEqual(
    state.snapshot.messages.map((message) => message.blocks[0].value),
    ["done", "已请求压缩上下文", "上下文已压缩"]
  );
});

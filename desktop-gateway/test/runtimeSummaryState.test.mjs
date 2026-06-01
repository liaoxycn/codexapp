import assert from "node:assert/strict";
import test from "node:test";
import {
  createPlaceholderThreadRuntimeState,
  markAllThreadsFailedState,
  refreshSummarySnapshotEntry,
  syncSelectedThreadSnapshots,
} from "../dist/bridge/runtimeSummaryState.js";

function summary(id, overrides = {}) {
  return {
    id,
    title: id,
    preview: `${id} preview`,
    status: "idle",
    updatedAt: 1000,
    groupKind: "project",
    groupLabel: "TestApp",
    archived: false,
    ...overrides,
  };
}

function runtimeState(id, overrides = {}) {
  const threadSummary = summary(id, overrides.summary);
  return {
    summary: threadSummary,
    thread: overrides.thread ?? null,
    isSubscribed: false,
    lastActivityAtMs: threadSummary.updatedAt,
    historyWindow: 24,
    currentTurnId: overrides.currentTurnId ?? null,
    activeAssistantMessageId: null,
    liveAssistantItemId: null,
    transientOperation: overrides.transientOperation ?? null,
    pendingApproval: overrides.pendingApproval ?? null,
    stopRequested: false,
    isFinalizing: false,
    model: null,
    instructionSources: [],
    approvalPolicy: null,
    approvalsReviewer: null,
    sandbox: null,
    reasoningEffort: null,
    snapshot: {
      threads: overrides.snapshotThreads ?? [threadSummary],
      selectedThreadId: overrides.selectedThreadId ?? id,
      messages: overrides.messages ?? [],
      hasMoreHistory: false,
      pendingApproval: overrides.pendingApproval?.text ?? null,
      chips: [],
    files: [],
      slashCommands: [],
      cwd: "",
      permissionSummary: "",
      isGenerating: overrides.isGenerating ?? false,
    },
  };
}

test("createPlaceholderThreadRuntimeState builds empty snapshot with selected thread", () => {
  const summaries = [summary("thread-1"), summary("thread-2")];
  const state = createPlaceholderThreadRuntimeState(summaries[1], summaries, "thread-2");

  assert.equal(state.thread, null);
  assert.equal(state.snapshot.selectedThreadId, "thread-2");
  assert.deepEqual(state.snapshot.threads, summaries);
  assert.deepEqual(state.snapshot.messages, []);
});

test("syncSelectedThreadSnapshots rewrites selected thread id across runtime snapshots", () => {
  const threads = new Map([
    ["thread-1", runtimeState("thread-1", { selectedThreadId: "thread-1" })],
    ["thread-2", runtimeState("thread-2", { selectedThreadId: "thread-1" })],
  ]);

  syncSelectedThreadSnapshots(threads, "thread-2");

  assert.equal(threads.get("thread-1").snapshot.selectedThreadId, "thread-2");
  assert.equal(threads.get("thread-2").snapshot.selectedThreadId, "thread-2");
  assert.deepEqual(
    threads.get("thread-2").snapshot.threads.map((item) => item.id),
    ["thread-1", "thread-2"]
  );
});

test("refreshSummarySnapshotEntry keeps runtime approval overlay in summary status", () => {
  const summaries = [summary("thread-1")];
  const state = runtimeState("thread-1", {
    pendingApproval: { kind: "command", text: "allow command" },
  });

  refreshSummarySnapshotEntry(state, summary("thread-1", { status: "idle" }), summaries);

  assert.equal(state.summary.status, "needs_approval");
  assert.deepEqual(state.snapshot.threads, summaries);
});

test("markAllThreadsFailedState clears live runtime state and preserves archived idle state", () => {
  const active = runtimeState("thread-active", {
    currentTurnId: "turn-1",
    transientOperation: "compact",
    isGenerating: true,
    messages: [{ id: "assistant-1", role: "assistant", blocks: [{ kind: "text", value: "working" }] }],
  });
  const archived = runtimeState("thread-archived", {
    summary: { archived: true, status: "running" },
    currentTurnId: "turn-2",
    isGenerating: true,
  });
  const threads = new Map([
    ["thread-active", active],
    ["thread-archived", archived],
  ]);

  markAllThreadsFailedState(threads, "backend failed");

  assert.equal(active.snapshot.isGenerating, false);
  assert.equal(active.currentTurnId, null);
  assert.equal(active.transientOperation, null);
  assert.equal(active.summary.status, "failed");
  assert.equal(active.snapshot.messages.at(-1).blocks[0].value, "backend failed");
  assert.equal(archived.summary.status, "idle");
});

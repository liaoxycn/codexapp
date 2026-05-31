import assert from "node:assert/strict";
import test from "node:test";
import { handleBridgeBackendServerRequest } from "../dist/bridge/bridgeBackendLifecycle.js";

function createState() {
  return {
    summary: { id: "thread-1", title: "thread-1", preview: "preview", status: "idle", archived: false },
    thread: null,
    isSubscribed: false,
    lastActivityAtMs: 1000,
    historyWindow: 24,
    currentTurnId: null,
    activeAssistantMessageId: null,
    liveAssistantItemId: null,
    transientOperation: null,
    pendingApproval: null,
    stopRequested: false,
    isFinalizing: false,
    model: null,
    instructionSources: [],
    approvalPolicy: null,
    approvalsReviewer: null,
    sandbox: null,
    reasoningEffort: null,
    snapshot: {
      threads: [{ id: "thread-1", title: "thread-1", preview: "preview", status: "idle", archived: false }],
      selectedThreadId: "thread-1",
      messages: [],
      hasMoreHistory: false,
      pendingApproval: null,
      chips: [],
      slashCommands: [],
      cwd: "D:/Projects/Test",
      permissionSummary: "",
      isGenerating: false,
    },
  };
}

test("handleBridgeBackendServerRequest stores pending approval and emits change", () => {
  const state = createState();
  const threads = new Map([["thread-1", state]]);
  const statuses = [];
  let emitCount = 0;

  handleBridgeBackendServerRequest(
    {
      id: "request-1",
      method: "item/commandExecution/requestApproval",
      params: {
        threadId: "thread-1",
        reason: "需要执行命令",
        command: "dir",
      },
    },
    {
      threads,
      emitChanged: () => {
        emitCount += 1;
      },
      hydrateThreads: async () => {},
      refreshThread: async () => {},
      ensureActiveAssistantMessage: () => "assistant-live",
      updateSummaryStatus: (_threadId, status) => {
        statuses.push(status);
      },
    }
  );

  assert.equal(state.pendingApproval?.requestId, "request-1");
  assert.equal(state.pendingApproval?.kind, "command");
  assert.equal(state.pendingApproval?.text, "需要执行命令\ndir");
  assert.equal(state.snapshot.pendingApproval, "需要执行命令\ndir");
  assert.deepEqual(statuses, ["needs_approval"]);
  assert.equal(emitCount, 1);
});

import assert from "node:assert/strict";
import test from "node:test";
import { BridgeCatalogController } from "../dist/bridge/BridgeCatalogController.js";
import { BridgeRuntimeStore } from "../dist/bridge/bridgeRuntimeStore.js";

function existingState(id, overrides = {}) {
  return {
    summary: {
      id,
      title: id,
      subtitle: null,
      updatedAt: 100000,
      status: "idle",
      cwd: "C:/work",
      groupKind: "recent",
      groupLabel: "最近",
      projectPath: null,
      projectName: null,
      archived: false,
    },
    thread: {
      id,
      preview: id,
      status: "idle",
      cwd: "C:/work",
      updatedAt: 100,
      name: null,
      turns: [],
      modelProvider: "openai",
    },
    isSubscribed: false,
    lastActivityAtMs: 100000,
    historyWindow: 24,
    currentTurnId: null,
    activeAssistantMessageId: null,
    liveAssistantItemId: null,
    transientOperation: null,
    pendingApproval: null,
    stopRequested: false,
    isFinalizing: false,
    model: "gpt-5",
    instructionSources: ["C:/work"],
    approvalPolicy: "never",
    approvalsReviewer: "user",
    sandbox: { type: "workspaceWrite", networkAccess: false },
    reasoningEffort: null,
    snapshot: {
      threads: [{ id, title: id, preview: id, status: "idle", cwd: "C:/work", updatedAt: 100000, groupKind: "recent", groupLabel: "最近", archived: false }],
      selectedThreadId: id,
      messages: [],
      hasMoreHistory: false,
      pendingApproval: null,
      chips: [],
      slashCommands: [],
      cwd: "C:/work",
      permissionSummary: "workspace-write · never",
      isGenerating: false,
    },
    ...overrides,
  };
}

test("BridgeCatalogController loadOlderMessages expands window and returns updated snapshot", async () => {
  const runtime = new BridgeRuntimeStore();
  runtime.currentThreadId = "thread-a";
  runtime.threads.set("thread-a", existingState("thread-a"));
  const controller = new BridgeCatalogController(runtime, () => ({}));

  const snapshot = await controller.loadOlderMessages("thread-a");

  assert.equal(runtime.threads.get("thread-a")?.historyWindow, 48);
  assert.equal(snapshot.selectedThreadId, "thread-a");
});

test("BridgeCatalogController loadOlderMessages falls back when thread is missing", async () => {
  const runtime = new BridgeRuntimeStore();
  const controller = new BridgeCatalogController(runtime, () => ({}));

  const snapshot = await controller.loadOlderMessages("missing");

  assert.equal(snapshot.selectedThreadId, "");
  assert.deepEqual(snapshot.messages, []);
});

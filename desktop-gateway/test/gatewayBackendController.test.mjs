import assert from "node:assert/strict";
import test from "node:test";
import { GatewayBackendController } from "../dist/server/GatewayBackendController.js";

function createSnapshot(overrides = {}) {
  return {
    threads: [],
    selectedThreadId: "thread-1",
    messages: [],
    hasMoreHistory: false,
    pendingApproval: null,
    chips: [],
    slashCommands: [],
    cwd: "D:/Projects/Test",
    permissionSummary: "workspace-write · never",
    isGenerating: false,
    ...overrides,
  };
}

function createBackend(overrides = {}) {
  return {
    subscribe: () => () => {},
    hasThread: () => true,
    getDefaultThreadId: () => "thread-1",
    getSnapshot: () => createSnapshot(),
    createThread: async () => createSnapshot(),
    selectThread: async () => createSnapshot(),
    renameThread: async () => createSnapshot(),
    archiveThread: async () => createSnapshot(),
    unarchiveThread: async () => createSnapshot(),
    refreshThreads: async () => createSnapshot(),
    loadOlderMessages: async () => createSnapshot(),
    sendPrompt: async () => createSnapshot(),
    stopTurn: async () => createSnapshot(),
    approveCurrent: async () => createSnapshot(),
    ...overrides,
  };
}

test("GatewayBackendController switches to app backend after successful start", async () => {
  const appBackend = {
    ...createBackend(),
    startCalls: 0,
    async start() {
      this.startCalls += 1;
    },
  };
  const mockBackend = createBackend({ getDefaultThreadId: () => "mock-thread" });
  const controller = new GatewayBackendController(
    { workspacePath: "D:/Projects/Test" },
    { appBackend, mockBackend }
  );

  await controller.startRealBackend();

  assert.equal(appBackend.startCalls, 1);
  assert.equal(controller.backend(), appBackend);
  assert.equal(controller.backendLabel(), "app-server");
});

test("GatewayBackendController falls back to mock backend when app backend start fails", async () => {
  const appBackend = {
    ...createBackend(),
    async start() {
      throw new Error("boom");
    },
  };
  const mockBackend = createBackend({ getDefaultThreadId: () => "mock-thread" });
  const controller = new GatewayBackendController(
    { workspacePath: "D:/Projects/Test" },
    { appBackend, mockBackend }
  );

  await controller.startRealBackend();

  assert.equal(controller.backend(), mockBackend);
  assert.equal(controller.backendLabel(), "mock");
});

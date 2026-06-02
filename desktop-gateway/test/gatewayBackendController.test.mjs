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
    files: [],
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

function createSocket() {
  return {
    OPEN: 1,
    readyState: 1,
    sent: [],
    send(payload) {
      this.sent.push(JSON.parse(payload));
    },
  };
}

function createContext(overrides = {}) {
  return {
    socket: createSocket(),
    selectedThreadId: "thread-1",
    selectionVersion: 0,
    authenticated: false,
    unsubscribe: () => {},
    snapshotTimer: null,
    liveRefreshTimer: null,
    listRefreshTimer: null,
    lastSnapshotPayload: null,
    lastSnapshotMessage: null,
    snapshotRevision: 0,
    supportsSnapshotPatch: false,
    currentAction: null,
    actionTraceType: null,
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

test("GatewayBackendController serializes backend actions across clients", async () => {
  const controller = new GatewayBackendController(
    { workspacePath: "D:/Projects/Test" },
    { mockBackend: createBackend() }
  );
  const calls = [];
  let releaseFirst;
  const firstDone = new Promise((resolve) => {
    releaseFirst = resolve;
  });

  const first = controller.runBackendAction(createContext(), async () => {
    calls.push("start:first");
    await firstDone;
    calls.push("end:first");
    return createSnapshot({ selectedThreadId: "thread-1" });
  });
  const second = controller.runBackendAction(createContext(), async () => {
    calls.push("start:second");
    calls.push("end:second");
    return createSnapshot({ selectedThreadId: "thread-2" });
  });

  await Promise.resolve();
  await Promise.resolve();

  assert.deepEqual(calls, ["start:first"]);

  releaseFirst();
  await Promise.all([first, second]);

  assert.deepEqual(calls, ["start:first", "end:first", "start:second", "end:second"]);
});

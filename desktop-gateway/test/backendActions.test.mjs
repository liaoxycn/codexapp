import assert from "node:assert/strict";
import test from "node:test";
import {
  buildSnapshotMessage,
  runBackendAction,
  sendSnapshot,
} from "../dist/server/backendActions.js";

function createSnapshot(overrides = {}) {
  return {
    threads: [],
    selectedThreadId: "thread-1",
    messages: [],
    hasMoreHistory: undefined,
    pendingApproval: undefined,
    chips: [],
    slashCommands: [],
    cwd: "D:/Projects/Test",
    permissionSummary: null,
    isGenerating: false,
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
    ...overrides,
  };
}

test("buildSnapshotMessage fills gateway defaults for optional fields", () => {
  const message = buildSnapshotMessage(createSnapshot());

  assert.equal(message.type, "snapshot");
  assert.equal(message.hasMoreHistory, false);
  assert.equal(message.pendingApproval, null);
});

test("sendSnapshot serializes snapshot payload for client socket", () => {
  const context = createContext();
  sendSnapshot(context, createSnapshot({ pendingApproval: "审批中" }), {
    refreshSelectedThread: async () => {},
    refreshThreadList: async () => {},
  });

  assert.equal(context.socket.sent.length, 1);
  assert.equal(context.socket.sent[0].type, "snapshot");
  assert.equal(context.socket.sent[0].pendingApproval, "审批中");
  assert.equal(context.listRefreshTimer, null);
});

test("sendSnapshot skips duplicate payloads for the same client", () => {
  const context = createContext();
  const handlers = {
    refreshSelectedThread: async () => {},
    refreshThreadList: async () => {},
  };

  sendSnapshot(context, createSnapshot(), handlers);
  sendSnapshot(context, createSnapshot(), handlers);

  assert.equal(context.socket.sent.length, 1);
});

test("runBackendAction sends snapshot on success", async () => {
  const context = createContext();
  const snapshots = [];
  const statuses = [];
  const snapshot = createSnapshot({ selectedThreadId: "thread-2" });

  await runBackendAction(context, async () => snapshot, {
    backend: () => ({
      getSnapshot: () => createSnapshot(),
    }),
    sendSnapshot: (_context, nextSnapshot) => snapshots.push(nextSnapshot),
    sendStatus: (_socket, message) => statuses.push(message),
    refreshSelectedThread: async () => {},
    refreshThreadList: async () => {},
  });

  assert.deepEqual(statuses, []);
  assert.equal(snapshots.length, 1);
  assert.equal(snapshots[0].selectedThreadId, "thread-2");
});

test("runBackendAction sends error status and fallback snapshot on failure", async () => {
  const context = createContext({ selectedThreadId: "thread-fallback" });
  const fallback = createSnapshot({ selectedThreadId: "thread-fallback" });
  const snapshots = [];
  const statuses = [];

  await runBackendAction(context, async () => {
    throw new Error("boom");
  }, {
    backend: () => ({
      getSnapshot: () => fallback,
    }),
    sendSnapshot: (_context, nextSnapshot) => snapshots.push(nextSnapshot),
    sendStatus: (_socket, message) => statuses.push(message),
    refreshSelectedThread: async () => {},
    refreshThreadList: async () => {},
  });

  assert.equal(statuses.length, 1);
  assert.equal(statuses[0].status, "error");
  assert.equal(statuses[0].detail, "boom");
  assert.equal(snapshots.length, 1);
  assert.equal(snapshots[0].selectedThreadId, "thread-fallback");
});

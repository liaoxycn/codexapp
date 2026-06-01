import assert from "node:assert/strict";
import test from "node:test";
import {
  buildSnapshotPatchMessage,
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
    files: [],
    slashCommands: [],
    cwd: "D:/Projects/Test",
    permissionSummary: null,
    sessionConfig: {},
    configOptions: { models: [], reasoningEfforts: [], sandboxModes: [], defaults: {} },
    desktopRestartRequired: false,
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
    lastSnapshotMessage: null,
    snapshotRevision: 0,
    supportsSnapshotPatch: false,
    ...overrides,
  };
}

test("buildSnapshotMessage fills gateway defaults for optional fields", () => {
  const message = buildSnapshotMessage(createSnapshot());

  assert.equal(message.type, "snapshot");
  assert.equal(message.hasMoreHistory, false);
  assert.equal(message.pendingApproval, null);
  assert.deepEqual(message.sessionConfig, {});
  assert.equal(message.desktopRestartRequired, false);
});

test("sendSnapshot serializes snapshot payload for client socket", () => {
  const context = createContext();
  sendSnapshot(context, createSnapshot({ pendingApproval: "审批中" }), {
    refreshSelectedThread: async () => {},
    refreshThreadList: async () => {},
  });

  assert.equal(context.socket.sent.length, 1);
  assert.equal(context.socket.sent[0].type, "snapshot");
  assert.equal("revision" in context.socket.sent[0], false);
  assert.equal(context.socket.sent[0].pendingApproval, "审批中");
  assert.equal(context.listRefreshTimer, null);
});

test("buildSnapshotPatchMessage includes only changed snapshot fields", () => {
  const previous = buildSnapshotMessage(createSnapshot());
  const next = buildSnapshotMessage(createSnapshot({
    messages: [{ id: "msg-1", role: "assistant", blocks: [{ kind: "text", value: "done" }] }],
    isGenerating: true,
  }));

  const patch = buildSnapshotPatchMessage(previous, next, 1, 2);

  assert.equal(patch.type, "snapshot_patch");
  assert.equal(patch.baseRevision, 1);
  assert.equal(patch.revision, 2);
  assert.deepEqual(patch.changed, ["messages", "isGenerating"]);
  assert.equal(patch.messages.length, 1);
  assert.equal(patch.isGenerating, true);
  assert.equal("threads" in patch, false);
});

test("buildSnapshotPatchMessage includes changed project file list", () => {
  const previous = buildSnapshotMessage(createSnapshot());
  const next = buildSnapshotMessage(createSnapshot({
    files: [{ label: "src/App.ts", path: "D:/Projects/Test/src/App.ts" }],
  }));

  const patch = buildSnapshotPatchMessage(previous, next, 1, 2);

  assert.deepEqual(patch.changed, ["files"]);
  assert.deepEqual(patch.files, [{ label: "src/App.ts", path: "D:/Projects/Test/src/App.ts" }]);
});

test("buildSnapshotPatchMessage includes changed desktop restart prompt state", () => {
  const previous = buildSnapshotMessage(createSnapshot());
  const next = buildSnapshotMessage(createSnapshot({ desktopRestartRequired: true }));

  const patch = buildSnapshotPatchMessage(previous, next, 1, 2);

  assert.deepEqual(patch.changed, ["desktopRestartRequired"]);
  assert.equal(patch.desktopRestartRequired, true);
});

test("buildSnapshotPatchMessage includes changed session config", () => {
  const previous = buildSnapshotMessage(createSnapshot());
  const next = buildSnapshotMessage(createSnapshot({
    sessionConfig: {
      permissionMode: "workspace-write",
      provider: "openai",
      model: "gpt-5",
      reasoningEffort: "high",
    },
  }));

  const patch = buildSnapshotPatchMessage(previous, next, 1, 2);

  assert.deepEqual(patch.changed, ["sessionConfig"]);
  assert.deepEqual(patch.sessionConfig, {
    permissionMode: "workspace-write",
    provider: "openai",
    model: "gpt-5",
    reasoningEffort: "high",
  });
});

test("sendSnapshot sends patch after a negotiated baseline snapshot", () => {
  const context = createContext({ supportsSnapshotPatch: true });
  const handlers = {
    refreshSelectedThread: async () => {},
    refreshThreadList: async () => {},
  };

  sendSnapshot(context, createSnapshot(), handlers);
  sendSnapshot(context, createSnapshot({ isGenerating: true }), handlers);

  assert.equal(context.socket.sent.length, 2);
  assert.equal(context.socket.sent[0].type, "snapshot");
  assert.equal(context.socket.sent[0].revision, 1);
  assert.equal(context.socket.sent[1].type, "snapshot_patch");
  assert.equal(context.socket.sent[1].baseRevision, 1);
  assert.equal(context.socket.sent[1].revision, 2);
  assert.deepEqual(context.socket.sent[1].changed, ["isGenerating"]);
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

import assert from "node:assert/strict";
import test from "node:test";
import { handleClientMessage } from "../dist/server/clientMessages.js";

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
    closed: null,
    send() {},
    close(code, reason) {
      this.closed = { code, reason };
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

function createBackend(overrides = {}) {
  return {
    hasThread: () => true,
    getDefaultThreadId: () => "thread-default",
    getSnapshot: (selectedThreadId = "thread-1") => createSnapshot({ selectedThreadId }),
    createThread: async () => createSnapshot({ selectedThreadId: "thread-created" }),
    forkThread: async () => createSnapshot({ selectedThreadId: "thread-forked" }),
    selectThread: async (threadId) => createSnapshot({ selectedThreadId: threadId }),
    renameThread: async (threadId) => createSnapshot({ selectedThreadId: threadId }),
    archiveThread: async () => createSnapshot({ selectedThreadId: "thread-next" }),
    unarchiveThread: async () => createSnapshot({ selectedThreadId: "thread-restored" }),
    refreshThreads: async (threadId) => createSnapshot({ selectedThreadId: threadId ?? "thread-1" }),
    loadOlderMessages: async (threadId) => createSnapshot({ selectedThreadId: threadId }),
    sendPrompt: async (threadId) => createSnapshot({ selectedThreadId: threadId }),
    rollbackThread: async (threadId) => createSnapshot({ selectedThreadId: threadId }),
    resendPrompt: async (threadId) => createSnapshot({ selectedThreadId: threadId }),
    stopTurn: async (threadId) => createSnapshot({ selectedThreadId: threadId }),
    approveCurrent: async (threadId) => createSnapshot({ selectedThreadId: threadId }),
    ...overrides,
  };
}

function createHandlers(overrides = {}) {
  const statuses = [];
  const snapshots = [];
  const {
    backend: backendOverride,
    ...handlerOverrides
  } = overrides;
  const backend = backendOverride ?? createBackend();
  const handlers = {
    backend: () => backend,
    pairToken: undefined,
    sendStatus: (_socket, message) => statuses.push(message),
    sendSnapshot: (_context, snapshot) => snapshots.push(snapshot),
    runBackendAction: async (context, action) => {
      const snapshot = await action();
      snapshots.push(snapshot);
      context.selectedThreadId = snapshot.selectedThreadId;
    },
    refreshSelectedThread: async () => {},
    markDesktopRestartRequired: () => {},
    restartDesktop: async () => {},
    ...handlerOverrides,
  };
  return {
    backend,
    handlers,
    statuses,
    snapshots,
  };
}

test("handleClientMessage reports invalid JSON", async () => {
  const context = createContext();
  const { handlers, statuses } = createHandlers();

  await handleClientMessage(context, "{bad json", handlers);

  assert.equal(statuses.length, 1);
  assert.equal(statuses[0].status, "error");
  assert.equal(statuses[0].detail, "消息不是合法 JSON");
});

test("handleClientMessage rejects hello with invalid pair token", async () => {
  const context = createContext();
  const { handlers, statuses } = createHandlers({ pairToken: "expected-token" });

  await handleClientMessage(
    context,
    JSON.stringify({ type: "hello", pairToken: "wrong-token" }),
    handlers
  );

  assert.equal(context.authenticated, false);
  assert.equal(statuses.length, 1);
  assert.equal(statuses[0].detail, "pair token 无效");
  assert.deepEqual(context.socket.closed, { code: 4001, reason: "invalid pair token" });
});

test("handleClientMessage records negotiated snapshot patch support", async () => {
  const context = createContext();
  const { handlers } = createHandlers();

  await handleClientMessage(
    context,
    JSON.stringify({ type: "hello", client: "android", capabilities: ["snapshot_patch"] }),
    handlers
  );

  assert.equal(context.authenticated, true);
  assert.equal(context.supportsSnapshotPatch, true);
});

test("handleClientMessage restores requested selected thread from hello", async () => {
  const context = createContext({ selectedThreadId: "thread-default" });
  const selectCalls = [];
  const { handlers, snapshots } = createHandlers({
    backend: createBackend({
      hasThread: (threadId) => threadId === "thread-2",
      selectThread: async (selectedThreadId) => {
        selectCalls.push(selectedThreadId);
        return createSnapshot({ selectedThreadId });
      },
    }),
  });

  await handleClientMessage(
    context,
    JSON.stringify({ type: "hello", client: "android", selectedThreadId: "thread-2" }),
    handlers
  );

  assert.equal(context.selectedThreadId, "thread-2");
  assert.deepEqual(selectCalls, ["thread-2"]);
  assert.equal(snapshots.at(-1).selectedThreadId, "thread-2");
});

test("handleClientMessage resumes requested thread on hello so live status is restored", async () => {
  const context = createContext({ selectedThreadId: "thread-default" });
  const { handlers, snapshots } = createHandlers({
    backend: createBackend({
      hasThread: (threadId) => threadId === "thread-running",
      getSnapshot: () => createSnapshot({
        selectedThreadId: "thread-running",
        threads: [{ id: "thread-running", status: "idle" }],
      }),
      selectThread: async (selectedThreadId) => createSnapshot({
        selectedThreadId,
        threads: [{ id: selectedThreadId, status: "running" }],
        isGenerating: true,
      }),
    }),
  });

  await handleClientMessage(
    context,
    JSON.stringify({ type: "hello", client: "android", selectedThreadId: "thread-running" }),
    handlers
  );

  assert.equal(context.selectedThreadId, "thread-running");
  assert.equal(snapshots.at(-1).threads[0].status, "running");
  assert.equal(snapshots.at(-1).isGenerating, true);
});

test("handleClientMessage falls back to default thread when select target is missing", async () => {
  const context = createContext({ authenticated: true, selectedThreadId: "thread-1" });
  const { handlers, backend, snapshots } = createHandlers({
    backend: createBackend({
      hasThread: () => false,
    }),
  });
  const selected = [];
  backend.selectThread = async (threadId) => {
    selected.push(threadId);
    return createSnapshot({ selectedThreadId: threadId });
  };

  await handleClientMessage(
    context,
    JSON.stringify({ type: "select_thread", threadId: "thread-missing" }),
    handlers
  );

  assert.equal(context.selectionVersion, 1);
  assert.equal(context.selectedThreadId, "thread-default");
  assert.deepEqual(selected, ["thread-default"]);
  assert.equal(snapshots.at(-1).selectedThreadId, "thread-default");
});

test("handleClientMessage routes refresh_threads to manual refresh", async () => {
  const context = createContext({ authenticated: true });
  const calls = [];
  const { handlers } = createHandlers({
    refreshSelectedThread: async (_context, source) => {
      calls.push(source);
    },
  });

  await handleClientMessage(
    context,
    JSON.stringify({ type: "refresh_threads" }),
    handlers
  );

  assert.deepEqual(calls, ["manual"]);
});

test("handleClientMessage force refresh resets snapshot patch baseline", async () => {
  const context = createContext({
    authenticated: true,
    supportsSnapshotPatch: true,
    lastSnapshotPayload: "{\"type\":\"snapshot\"}",
    lastSnapshotMessage: createSnapshot(),
    snapshotRevision: 9,
  });
  const calls = [];
  const { handlers } = createHandlers({
    refreshSelectedThread: async (nextContext, source) => {
      calls.push(source);
      assert.equal(nextContext.lastSnapshotPayload, null);
      assert.equal(nextContext.lastSnapshotMessage, null);
      assert.equal(nextContext.snapshotRevision, 0);
    },
  });

  await handleClientMessage(
    context,
    JSON.stringify({ type: "refresh_threads", forceSnapshot: true }),
    handlers
  );

  assert.deepEqual(calls, ["manual"]);
});

test("handleClientMessage switches selected thread after fork_thread", async () => {
  const context = createContext({ authenticated: true, selectedThreadId: "thread-1" });
  const forkCalls = [];
  const { handlers, backend, snapshots } = createHandlers({ backend: createBackend() });
  backend.forkThread = async (threadId, numTurns) => {
    forkCalls.push([threadId, numTurns]);
    return createSnapshot({ selectedThreadId: "thread-forked" });
  };

  await handleClientMessage(
    context,
    JSON.stringify({ type: "fork_thread", threadId: "thread-1", numTurns: 2 }),
    handlers
  );

  assert.equal(context.selectionVersion, 1);
  assert.equal(context.selectedThreadId, "thread-forked");
  assert.deepEqual(forkCalls, [["thread-1", 2]]);
  assert.equal(snapshots.at(-1).selectedThreadId, "thread-forked");
});

test("handleClientMessage switches selected thread for send_prompt and marks desktop restart", async () => {
  const context = createContext({ authenticated: true, selectedThreadId: "thread-1" });
  const sendCalls = [];
  const restartReasons = [];
  const { handlers, backend, snapshots } = createHandlers({
    backend: createBackend(),
    markDesktopRestartRequired: (reason) => restartReasons.push(reason),
  });
  backend.sendPrompt = async (threadId, text) => {
    sendCalls.push([threadId, text]);
    return createSnapshot({ selectedThreadId: threadId });
  };

  await handleClientMessage(
    context,
    JSON.stringify({ type: "send_prompt", threadId: "thread-2", text: "hello" }),
    handlers
  );

  assert.equal(context.selectionVersion, 1);
  assert.equal(context.selectedThreadId, "thread-2");
  assert.deepEqual(sendCalls, [["thread-2", "hello"]]);
  assert.deepEqual(restartReasons, ["send_prompt"]);
  assert.equal(snapshots.at(-1).selectedThreadId, "thread-2");
});

test("handleClientMessage restarts desktop on explicit request", async () => {
  const context = createContext({ authenticated: true, selectedThreadId: "thread-1" });
  let restarted = 0;
  const { handlers, snapshots } = createHandlers({
    restartDesktop: async () => {
      restarted += 1;
    },
  });

  await handleClientMessage(
    context,
    JSON.stringify({ type: "restart_desktop" }),
    handlers
  );

  assert.equal(restarted, 1);
  assert.equal(snapshots.at(-1).selectedThreadId, "thread-1");
});

test("handleClientMessage rolls back a thread from a user message action", async () => {
  const context = createContext({ authenticated: true, selectedThreadId: "thread-1" });
  const rollbackCalls = [];
  const restartReasons = [];
  const { handlers, backend, snapshots } = createHandlers({
    backend: createBackend(),
    markDesktopRestartRequired: (reason) => restartReasons.push(reason),
  });
  backend.rollbackThread = async (threadId, numTurns) => {
    rollbackCalls.push([threadId, numTurns]);
    return createSnapshot({ selectedThreadId: threadId });
  };

  await handleClientMessage(
    context,
    JSON.stringify({ type: "rollback_thread", threadId: "thread-1", numTurns: 3 }),
    handlers
  );

  assert.deepEqual(rollbackCalls, [["thread-1", 3]]);
  assert.deepEqual(restartReasons, ["rollback_thread"]);
  assert.equal(snapshots.at(-1).selectedThreadId, "thread-1");
});

test("handleClientMessage resends by rolling back before prompt submission", async () => {
  const context = createContext({ authenticated: true, selectedThreadId: "thread-1" });
  const resendCalls = [];
  const restartReasons = [];
  const { handlers, backend, snapshots } = createHandlers({
    backend: createBackend(),
    markDesktopRestartRequired: (reason) => restartReasons.push(reason),
  });
  backend.resendPrompt = async (threadId, text, rollbackNumTurns) => {
    resendCalls.push([threadId, text, rollbackNumTurns]);
    return createSnapshot({ selectedThreadId: threadId });
  };

  await handleClientMessage(
    context,
    JSON.stringify({ type: "resend_prompt", threadId: "thread-1", text: "hello again", rollbackNumTurns: 2 }),
    handlers
  );

  assert.deepEqual(resendCalls, [["thread-1", "hello again", 2]]);
  assert.deepEqual(restartReasons, ["resend_prompt"]);
  assert.equal(snapshots.at(-1).selectedThreadId, "thread-1");
});

test("handleClientMessage creates thread before first draft prompt", async () => {
  const context = createContext({ authenticated: true, selectedThreadId: "" });
  const createCalls = [];
  const sendCalls = [];
  const { handlers, backend, snapshots } = createHandlers({ backend: createBackend() });
  backend.createThread = async (cwd, options) => {
    createCalls.push([cwd, options]);
    return createSnapshot({ selectedThreadId: "thread-created" });
  };
  backend.sendPrompt = async (threadId, text) => {
    sendCalls.push([threadId, text]);
    return createSnapshot({ selectedThreadId: threadId });
  };

  await handleClientMessage(
    context,
    JSON.stringify({
      type: "send_prompt",
      text: "hello draft",
      newThread: true,
      cwd: "D:/Projects/codexapp",
      model: "gpt-5",
      reasoningEffort: "medium",
      sandboxMode: "workspace-write",
    }),
    handlers
  );

  assert.equal(context.selectedThreadId, "thread-created");
  assert.deepEqual(createCalls, [[
    "D:/Projects/codexapp",
    {
      cwd: "D:/Projects/codexapp",
      model: "gpt-5",
      reasoningEffort: "medium",
      sandboxMode: "workspace-write",
    },
  ]]);
  assert.deepEqual(sendCalls, [["thread-created", "hello draft"]]);
  assert.equal(snapshots.at(-1).selectedThreadId, "thread-created");
});

test("handleClientMessage invalidates stale selection before creating draft thread", async () => {
  const context = createContext({ authenticated: true, selectedThreadId: "thread-old", selectionVersion: 4 });
  const createCalls = [];
  const selectedDuringCreate = [];
  const { handlers, backend, snapshots } = createHandlers({ backend: createBackend() });
  backend.createThread = async (cwd, options) => {
    selectedDuringCreate.push(context.selectedThreadId);
    createCalls.push([cwd, options]);
    return createSnapshot({ selectedThreadId: "thread-created" });
  };
  backend.sendPrompt = async (threadId) => createSnapshot({ selectedThreadId: threadId });

  await handleClientMessage(
    context,
    JSON.stringify({
      type: "send_prompt",
      text: "hello draft",
      newThread: true,
    }),
    handlers
  );

  assert.equal(context.selectionVersion, 6);
  assert.deepEqual(selectedDuringCreate, [""]);
  assert.equal(context.selectedThreadId, "thread-created");
  assert.equal(snapshots.at(-1).selectedThreadId, "thread-created");
  assert.equal(createCalls.length, 1);
});

test("handleClientMessage clears selection after archive_thread", async () => {
  const context = createContext({ authenticated: true, selectedThreadId: "thread-1" });
  const { handlers, backend, snapshots } = createHandlers({ backend: createBackend() });
  backend.archiveThread = async () => createSnapshot({
    selectedThreadId: "thread-next",
    messages: [{ id: "m1", role: "user", blocks: [] }],
    cwd: "D:/Projects/Next",
  });

  await handleClientMessage(
    context,
    JSON.stringify({ type: "archive_thread", threadId: "thread-1" }),
    handlers
  );

  assert.equal(context.selectedThreadId, "");
  assert.equal(snapshots.at(-1).selectedThreadId, "");
  assert.deepEqual(snapshots.at(-1).messages, []);
  assert.equal(snapshots.at(-1).cwd, "");
});

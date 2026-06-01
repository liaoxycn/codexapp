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
    slashCommands: [],
    cwd: "D:/Projects/Test",
    permissionSummary: "workspace-write · never",
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
    ...overrides,
  };
}

function createBackend(overrides = {}) {
  return {
    hasThread: () => true,
    getDefaultThreadId: () => "thread-default",
    getSnapshot: (selectedThreadId = "thread-1") => createSnapshot({ selectedThreadId }),
    createThread: async () => createSnapshot({ selectedThreadId: "thread-created" }),
    selectThread: async (threadId) => createSnapshot({ selectedThreadId: threadId }),
    renameThread: async (threadId) => createSnapshot({ selectedThreadId: threadId }),
    archiveThread: async () => createSnapshot({ selectedThreadId: "thread-next" }),
    unarchiveThread: async () => createSnapshot({ selectedThreadId: "thread-restored" }),
    refreshThreads: async (threadId) => createSnapshot({ selectedThreadId: threadId ?? "thread-1" }),
    loadOlderMessages: async (threadId) => createSnapshot({ selectedThreadId: threadId }),
    sendPrompt: async (threadId) => createSnapshot({ selectedThreadId: threadId }),
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
    pokeDesktop: async () => {},
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

test("handleClientMessage switches selected thread for send_prompt and pokes desktop", async () => {
  const context = createContext({ authenticated: true, selectedThreadId: "thread-1" });
  const sendCalls = [];
  const pokeCalls = [];
  const { handlers, backend, snapshots } = createHandlers({
    backend: createBackend(),
    pokeDesktop: async (threadId, reason) => {
      pokeCalls.push([threadId, reason]);
    },
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
  assert.deepEqual(pokeCalls, [["thread-2", "send_prompt"]]);
  assert.equal(snapshots.at(-1).selectedThreadId, "thread-2");
});

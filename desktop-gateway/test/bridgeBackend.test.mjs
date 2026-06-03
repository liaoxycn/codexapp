import assert from "node:assert/strict";
import test from "node:test";
import { InMemoryDesktopBackend } from "../dist/backend.js";
import { AppServerBridgeBackend, buildFileChangeBlocks, buildVisibleThreadSummaries } from "../dist/bridgeBackend.js";

function thread(id, overrides = {}) {
  return {
    id,
    preview: id,
    status: "idle",
    cwd: "C:/work",
    updatedAt: 100,
    name: null,
    turns: [],
    modelProvider: "openai",
    ...overrides,
  };
}

function startedThreadResponse(id, cwd, overrides = {}) {
  return {
    thread: thread(id, {
      cwd,
      preview: id,
      updatedAt: 200,
      ...overrides,
    }),
    model: "gpt-5",
    modelProvider: "openai",
    serviceTier: null,
    cwd,
    instructionSources: [cwd],
    approvalPolicy: "never",
    approvalsReviewer: "user",
    sandbox: { type: "workspaceWrite", networkAccess: false },
    reasoningEffort: null,
  };
}

function fakeConfigOptions() {
  return {
    models: [{ label: "gpt-5", value: "gpt-5" }],
    reasoningEfforts: [{ label: "high", value: "high" }],
    sandboxModes: [{ label: "workspace-write", value: "workspace-write" }],
    defaults: {
      model: "gpt-5",
      reasoningEffort: "high",
      sandboxMode: "workspace-write",
    },
  };
}

test("visible thread summaries do not hide threads by fixed title", () => {
  const summaries = buildVisibleThreadSummaries([
    thread("live-1"),
    thread("previously-hidden", { name: "调研 Codex 安卓壳方案" }),
    thread("live-2"),
  ]);

  assert.deepEqual(
    summaries.map((item) => item.id),
    ["live-1", "previously-hidden", "live-2"]
  );
});

test("visible thread summaries convert thread and turn timestamps to milliseconds", () => {
  const summaries = buildVisibleThreadSummaries([
    thread("live-1", {
      updatedAt: 100,
      turns: [
        { id: "turn-1", startedAt: 120, completedAt: 180, status: "completed", items: [], itemsView: "full" },
        { id: "turn-2", startedAt: 220, completedAt: null, status: "running", items: [], itemsView: "summary" },
      ],
    }),
  ]);

  assert.equal(summaries[0].updatedAt, 220000);
});

test("file change blocks include file rows and expandable diffs", () => {
  const blocks = buildFileChangeBlocks([
    {
      path: "D:/Projects/codexapp/app/src/main/App.kt",
      kind: "update",
      diff: "diff --git a/app/src/main/App.kt b/app/src/main/App.kt\n--- a/app/src/main/App.kt\n+++ b/app/src/main/App.kt\n-old\n+new",
    },
  ], "complete", "D:/Projects/codexapp");

  assert.deepEqual(
    blocks.map((block) => block.kind),
    ["fileChangeSummary", "fileChangeMeta", "fileChangeDiff"]
  );
  assert.equal(blocks[0].value, "已编辑 1 个文件");
  assert.equal(blocks[1].value, "已编辑 App.kt");
  assert.equal(blocks[1].path, "app/src/main/App.kt");
  assert.equal(blocks[2].language, "diff");
  assert.match(blocks[2].value, /\+new/);
});

test("visible thread summaries keep short OK project threads under their cwd project", () => {
  const summaries = buildVisibleThreadSummaries([
    thread("ok-thread", {
      preview: "OK",
      cwd: "D:/Data/Documents/md2html",
    }),
  ]);

  assert.equal(summaries[0].groupKind, "project");
  assert.equal(summaries[0].groupLabel, "md2html");
  assert.equal(summaries[0].cwd, "D:/Data/Documents/md2html");
});

test("visible thread summaries keep project names that look like chat folders", () => {
  const summaries = buildVisibleThreadSummaries([
    thread("codex-project", { cwd: "D:/Projects/Codex" }),
    thread("chat-project", { cwd: "D:/Projects/chat" }),
  ]);

  assert.deepEqual(
    summaries.map((item) => [item.groupKind, item.groupLabel]),
    [
      ["project", "Codex"],
      ["project", "chat"],
    ]
  );
});

test("mock backend creates a project thread in the requested cwd", () => {
  const backend = new InMemoryDesktopBackend("D:/Projects/default");
  const snapshot = backend.createThread("D:/Projects/Project A");
  const created = snapshot.threads.find((item) => item.id === snapshot.selectedThreadId);

  assert.equal(snapshot.cwd, "D:/Projects/Project A");
  assert.equal(created.cwd, "D:/Projects/Project A");
  assert.equal(created.groupKind, "project");
  assert.equal(created.groupLabel, "Project A");
});

test("bridge backend passes requested project cwd to app-server thread start", async () => {
  const backend = new AppServerBridgeBackend();
  const calls = [];
  backend.appServer = {
    threadStart: async (cwd) => {
      calls.push(cwd);
      return {
        thread: thread("new-project-thread", {
          cwd,
          preview: "new project thread",
          updatedAt: 200,
        }),
        model: "gpt-5",
        modelProvider: "openai",
        serviceTier: null,
        cwd,
        instructionSources: [cwd],
        approvalPolicy: "never",
        approvalsReviewer: "user",
        sandbox: { type: "workspaceWrite", networkAccess: false },
        reasoningEffort: null,
      };
    },
  };

  const snapshot = await backend.createThread("D:/Projects/Project B");
  const created = snapshot.threads.find((item) => item.id === "new-project-thread");

  assert.deepEqual(calls, ["D:/Projects/Project B"]);
  assert.equal(snapshot.cwd, "D:/Projects/Project B");
  assert.equal(created.cwd, "D:/Projects/Project B");
  assert.equal(created.groupLabel, "Project B");
});

test("bridge backend starts default new thread without inheriting selected project cwd", async () => {
  const backend = new AppServerBridgeBackend();
  const calls = [];
  backend.threads.set("selected-project-thread", {
    summary: {
      id: "selected-project-thread",
      title: "Project",
      preview: "Project",
      status: "idle",
      updatedAt: 100,
      groupKind: "project",
      groupLabel: "SelectedProject",
      cwd: "D:/Projects/SelectedProject",
      archived: false,
    },
    snapshot: {
      threads: [],
      selectedThreadId: "selected-project-thread",
      messages: [],
      hasMoreHistory: false,
      pendingApproval: null,
      chips: [],
      files: [],
      slashCommands: [],
      cwd: "D:/Projects/SelectedProject",
      permissionSummary: "",
      sessionConfig: {},
      configOptions: fakeConfigOptions(),
      desktopRestartRequired: false,
      operationalNotices: [],
      isGenerating: false,
    },
    thread: thread("selected-project-thread", { cwd: "D:/Projects/SelectedProject" }),
    historyWindow: 25,
    isLocalCatalogEntry: false,
    subscribed: false,
    lastActivityAtMs: 100000,
  });
  backend.currentThreadId = "selected-project-thread";
  backend.appServer = {
    threadStart: async (cwd, options) => {
      calls.push([cwd, options]);
      return startedThreadResponse("new-chat-thread", cwd, {
        preview: "new chat thread",
      });
    },
  };

  const snapshot = await backend.createThread();
  const created = snapshot.threads.find((item) => item.id === "new-chat-thread");

  assert.equal(calls.length, 1);
  assert.match(calls[0][0].replaceAll("\\", "/"), /\/Documents\/Codex\/\d{4}-\d{2}-\d{2}\/new-chat$/);
  assert.equal(calls[0][1].cwd, calls[0][0]);
  assert.notEqual(calls[0][0], "D:/Projects/SelectedProject");
  assert.match(snapshot.cwd.replaceAll("\\", "/"), /\/Documents\/Codex\/\d{4}-\d{2}-\d{2}\/new-chat$/);
  assert.equal(created.cwd, calls[0][0]);
  assert.equal(created.groupKind, "chat");
  assert.equal(created.groupLabel, "普通会话");
});

test("bridge backend exposes selected thread session config from app-server", async () => {
  const backend = new AppServerBridgeBackend();
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("configured-session-thread", cwd),
  };

  const snapshot = await backend.createThread("D:/Projects/SessionConfig");

  assert.deepEqual(snapshot.sessionConfig, {
    permissionMode: "workspace-write",
    provider: "openai",
    model: "gpt-5",
    reasoningEffort: undefined,
  });
});

test("bridge backend leaves unknown selected thread session config fields empty", async () => {
  const backend = new AppServerBridgeBackend();
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("provider-only-thread", cwd, {
      modelProvider: "local",
    }),
    threadList: async () => [thread("provider-only-thread", { cwd: "D:/Projects/SessionConfig", modelProvider: "local" })],
    threadRead: async () => thread("provider-only-thread", { cwd: "D:/Projects/SessionConfig", modelProvider: "local" }),
  };

  await backend.createThread("D:/Projects/SessionConfig");
  backend.threads.get("provider-only-thread").model = null;
  backend.threads.get("provider-only-thread").modelProvider = null;
  backend.threads.get("provider-only-thread").sandbox = null;
  backend.threads.get("provider-only-thread").reasoningEffort = null;

  const snapshot = await backend.refreshThreads("provider-only-thread");

  assert.deepEqual(snapshot.sessionConfig, {
    permissionMode: undefined,
    provider: "local",
    model: undefined,
    reasoningEffort: undefined,
  });
});

test("bridge backend keeps a newly started project selected until it appears in thread list", async () => {
  const backend = new AppServerBridgeBackend();
  backend.appServer = {
    threadStart: async (cwd) => ({
      thread: thread("new-project-thread", {
        cwd,
        preview: "new project thread",
        updatedAt: 200,
      }),
      model: "gpt-5",
      modelProvider: "openai",
      serviceTier: null,
      cwd,
      instructionSources: [cwd],
      approvalPolicy: "never",
      approvalsReviewer: "user",
      sandbox: { type: "workspaceWrite", networkAccess: false },
      reasoningEffort: null,
    }),
    threadList: async () => [],
    threadRead: async (threadId) => thread(threadId),
  };

  await backend.createThread("D:/Projects/Project C");
  await backend.handleNotification({ method: "thread/started", params: { threadId: "new-project-thread" } });
  const snapshot = backend.getSnapshot();
  const created = snapshot.threads.find((item) => item.id === "new-project-thread");

  assert.equal(snapshot.selectedThreadId, "new-project-thread");
  assert.equal(snapshot.cwd, "D:/Projects/Project C");
  assert.equal(created.cwd, "D:/Projects/Project C");
  assert.equal(created.groupLabel, "Project C");
});

test("bridge backend keeps an empty newly created thread idle", async () => {
  const backend = new AppServerBridgeBackend();
  backend.appServer = {
    threadStart: async (cwd) => ({
      thread: thread("empty-project-thread", {
        cwd,
        preview: "",
        status: { type: "active", activeFlags: [] },
        updatedAt: Math.floor(Date.now() / 1000),
        turns: [],
      }),
      model: "gpt-5",
      modelProvider: "openai",
      serviceTier: null,
      cwd,
      instructionSources: [cwd],
      approvalPolicy: "never",
      approvalsReviewer: "user",
      sandbox: { type: "workspaceWrite", networkAccess: false },
      reasoningEffort: null,
    }),
  };

  const snapshot = await backend.createThread("D:/Projects/Project D");
  assert.equal(snapshot.isGenerating, false);
  assert.equal(snapshot.threads.find((item) => item.id === "empty-project-thread")?.status, "idle");

  await backend.handleNotification({
    method: "thread/status/changed",
    params: { threadId: "empty-project-thread", status: { type: "active", activeFlags: [] } },
  });
  const afterStatusUpdate = backend.getSnapshot();

  assert.equal(afterStatusUpdate.isGenerating, false);
  assert.equal(afterStatusUpdate.threads.find((item) => item.id === "empty-project-thread")?.status, "idle");
});

test("bridge backend annotates messages with turn-scoped action counts", async () => {
  const backend = new AppServerBridgeBackend();
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("turn-count-thread", cwd, {
      turns: [
        { id: "turn-1", status: "completed", items: [{ type: "userMessage", id: "user-1", content: [{ type: "text", text: "one" }] }] },
        { id: "turn-2", status: "completed", items: [{ type: "agentMessage", id: "assistant-2", text: "two" }] },
        { id: "turn-3", status: "completed", items: [{ type: "userMessage", id: "user-3", content: [{ type: "text", text: "three" }] }] },
      ],
    }),
  };

  const snapshot = await backend.createThread("D:/Projects/ForkProject");

  assert.deepEqual(
    snapshot.messages.map((message) => [message.id, message.forkNumTurns, message.rollbackNumTurns]),
    [["user-1", undefined, 3], ["assistant-2", 2, undefined], ["user-3", undefined, 1]]
  );
});

test("bridge backend starts compact request from prompt command", async () => {
  const backend = new AppServerBridgeBackend();
  const compactCalls = [];
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("compact-thread", cwd),
    threadRead: async (threadId) => thread(threadId, { cwd: "D:/Projects/CompactProject" }),
    threadCompactStart: async (threadId) => {
      compactCalls.push(threadId);
    },
  };

  await backend.createThread("D:/Projects/CompactProject");
  const snapshot = await backend.sendPrompt("compact-thread", "/compact");

  assert.deepEqual(compactCalls, ["compact-thread"]);
  assert.equal(snapshot.isGenerating, true);
  assert.equal(snapshot.messages.some((message) =>
    message.blocks.some((block) => block.kind === "status" && block.value === "已请求压缩上下文")
  ), true);
});

test("bridge backend turns shell prompt into pending approval", async () => {
  const backend = new AppServerBridgeBackend();
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("shell-thread", cwd),
    threadRead: async (threadId) => thread(threadId, { cwd: "D:/Projects/ShellProject" }),
  };

  await backend.createThread("D:/Projects/ShellProject");
  const snapshot = await backend.sendPrompt("shell-thread", "!echo hi");

  assert.equal(snapshot.pendingApproval, "允许执行 shell 命令\necho hi");
  assert.equal(snapshot.threads.find((item) => item.id === "shell-thread")?.status, "needs_approval");
});

test("bridge backend executes approved shell command through app-server", async () => {
  const backend = new AppServerBridgeBackend();
  const shellCalls = [];
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("approved-shell-thread", cwd),
    threadRead: async (threadId) => thread(threadId, { cwd: "D:/Projects/ApprovedShell" }),
    threadShellCommand: async (threadId, command) => {
      shellCalls.push([threadId, command]);
    },
  };

  await backend.createThread("D:/Projects/ApprovedShell");
  await backend.sendPrompt("approved-shell-thread", "!dir");
  const snapshot = await backend.approveCurrent("approved-shell-thread", true);

  assert.deepEqual(shellCalls, [["approved-shell-thread", "dir"]]);
  assert.equal(snapshot.pendingApproval, null);
  assert.equal(snapshot.isGenerating, true);
  assert.equal(snapshot.messages.some((message) =>
    message.blocks.some((block) => block.kind === "status" && block.value === "审批已允许")
  ), true);
});

test("bridge backend merges assistant delta from notification stream", async () => {
  const backend = new AppServerBridgeBackend();
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("delta-thread", cwd),
  };

  await backend.createThread("D:/Projects/DeltaProject");
  await backend.handleNotification({
    method: "turn/started",
    params: { threadId: "delta-thread", turn: { id: "turn-1", startedAt: 200 } },
  });
  await backend.handleNotification({
    method: "item/agentMessage/delta",
    params: { threadId: "delta-thread", turnId: "turn-1", itemId: "assistant-item-1", delta: "hello" },
  });

  const snapshot = backend.getSnapshot("delta-thread");
  assert.equal(snapshot.isGenerating, true);
  assert.equal(
    snapshot.messages.some((message) =>
      message.blocks.some((block) => block.kind === "commentary" && block.value.includes("hello"))
    ),
    true
  );
});

test("bridge backend keeps subscribed running status across stale refresh", async () => {
  const backend = new AppServerBridgeBackend();
  const staleThread = thread("long-running-thread", {
    cwd: "D:/Projects/LongRun",
    status: "idle",
    turns: [
      {
        id: "turn-old",
        status: "completed",
        completedAt: 100,
        items: [
          { type: "agentMessage", id: "assistant-old", text: "old" },
        ],
      },
    ],
  });
  backend.appServer = {
    configOptions: async () => fakeConfigOptions(),
    threadStart: async (cwd) => startedThreadResponse("long-running-thread", cwd, staleThread),
    threadList: async () => [staleThread],
    threadRead: async () => staleThread,
  };

  await backend.createThread("D:/Projects/LongRun");
  await backend.handleNotification({
    method: "turn/started",
    params: { threadId: "long-running-thread", turn: { id: "turn-live", startedAt: 200 } },
  });
  await backend.handleNotification({
    method: "item/agentMessage/delta",
    params: { threadId: "long-running-thread", turnId: "turn-live", itemId: "assistant-live", delta: "working" },
  });

  const snapshot = await backend.refreshThreads("long-running-thread");

  assert.equal(snapshot.isGenerating, true);
  assert.equal(snapshot.threads.find((item) => item.id === "long-running-thread")?.status, "running");
});

test("bridge backend reconnect refresh clears stale running overlay after turn already completed", async () => {
  const backend = new AppServerBridgeBackend();
  const initialThread = thread("finished-thread", {
    cwd: "D:/Projects/Finished",
    status: "idle",
    turns: [],
  });
  const completedThread = thread("finished-thread", {
    cwd: "D:/Projects/Finished",
    status: "idle",
    updatedAt: 210,
    turns: [
      {
        id: "turn-live",
        status: "completed",
        startedAt: 200,
        completedAt: 210,
        items: [
          { type: "agentMessage", id: "assistant-live", text: "done" },
        ],
      },
    ],
  });
  backend.appServer = {
    configOptions: async () => fakeConfigOptions(),
    threadStart: async (cwd) => startedThreadResponse("finished-thread", cwd, initialThread),
    threadList: async () => [completedThread],
    threadRead: async () => completedThread,
  };

  await backend.createThread("D:/Projects/Finished");
  await backend.handleNotification({
    method: "turn/started",
    params: { threadId: "finished-thread", turn: { id: "turn-live", startedAt: 200 } },
  });
  await backend.handleNotification({
    method: "item/agentMessage/delta",
    params: { threadId: "finished-thread", turnId: "turn-live", itemId: "assistant-live", delta: "working" },
  });

  const snapshot = await backend.refreshThreads("finished-thread");

  assert.equal(snapshot.isGenerating, false);
  assert.equal(snapshot.threads.find((item) => item.id === "finished-thread")?.status, "idle");
});

test("bridge backend keeps active hook running across idle status refresh", async () => {
  const backend = new AppServerBridgeBackend();
  const staleThread = thread("hook-running-thread", {
    cwd: "D:/Projects/HookRun",
    status: "idle",
    turns: [
      {
        id: "turn-live",
        status: "completed",
        completedAt: 100,
        items: [
          { type: "agentMessage", id: "assistant-old", text: "old" },
        ],
      },
    ],
  });
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("hook-running-thread", cwd, staleThread),
    threadRead: async () => staleThread,
  };

  await backend.createThread("D:/Projects/HookRun");
  await backend.handleNotification({
    method: "hook/started",
    params: {
      threadId: "hook-running-thread",
      turnId: "turn-live",
      run: { id: "hook-live", eventName: "preToolUse", handlerType: "command", status: "running" },
    },
  });
  await backend.handleNotification({
    method: "thread/status/changed",
    params: { threadId: "hook-running-thread", status: "idle" },
  });

  const snapshot = backend.getSnapshot("hook-running-thread");
  assert.equal(snapshot.isGenerating, true);
  assert.equal(snapshot.threads.find((item) => item.id === "hook-running-thread")?.status, "running");
});

test("bridge backend does not let stale turn completion clear a newer loop turn", async () => {
  const backend = new AppServerBridgeBackend();
  const staleThread = thread("loop-thread", {
    cwd: "D:/Projects/Loop",
    status: "idle",
    turns: [
      { id: "turn-1", status: "completed", completedAt: 210, items: [] },
    ],
  });
  let releaseRead = () => {};
  const readBlocked = new Promise((resolve) => {
    releaseRead = resolve;
  });
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("loop-thread", cwd, staleThread),
    threadRead: async () => {
      await readBlocked;
      return staleThread;
    },
  };

  await backend.createThread("D:/Projects/Loop");
  await backend.handleNotification({
    method: "turn/started",
    params: { threadId: "loop-thread", turn: { id: "turn-1", startedAt: 200 } },
  });

  const completion = backend.handleNotification({
    method: "turn/completed",
    params: { threadId: "loop-thread", turn: { id: "turn-1", status: "completed", completedAt: 210 } },
  });
  await backend.handleNotification({
    method: "turn/started",
    params: { threadId: "loop-thread", turn: { id: "turn-2", startedAt: 211 } },
  });
  releaseRead();
  await completion;

  const state = backend.threads.get("loop-thread");
  const snapshot = backend.getSnapshot("loop-thread");
  assert.equal(state.currentTurnId, "turn-2");
  assert.equal(snapshot.isGenerating, true);
  assert.equal(snapshot.threads.find((item) => item.id === "loop-thread")?.status, "running");
});

test("bridge backend passes draft options to app-server thread start", async () => {
  const backend = new AppServerBridgeBackend();
  const calls = [];
  backend.appServer = {
    threadStart: async (cwd, options) => {
      calls.push([cwd, options]);
      return startedThreadResponse("configured-thread", cwd);
    },
  };

  await backend.createThread("D:/Projects/Configured", {
    model: "gpt-5",
    reasoningEffort: "high",
    approvalPolicy: "on-request",
    sandboxMode: "workspace-write",
  });

  assert.deepEqual(calls, [[
    "D:/Projects/Configured",
    {
      cwd: "D:/Projects/Configured",
      model: "gpt-5",
      reasoningEffort: "high",
      approvalPolicy: "on-request",
      sandboxMode: "workspace-write",
    },
  ]]);
});

test("bridge backend forks a thread and selects the fork", async () => {
  const backend = new AppServerBridgeBackend();
  const forkCalls = [];
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("source-thread", cwd),
    threadFork: async (threadId) => {
      forkCalls.push(threadId);
      return startedThreadResponse("forked-thread", "D:/Projects/ForkProject", {
        preview: "forked preview",
      });
    },
  };

  await backend.createThread("D:/Projects/ForkProject");
  const snapshot = await backend.forkThread("source-thread");

  assert.deepEqual(forkCalls, ["source-thread"]);
  assert.equal(snapshot.selectedThreadId, "forked-thread");
  assert.equal(snapshot.cwd, "D:/Projects/ForkProject");
  assert.equal(snapshot.threads.some((item) => item.id === "forked-thread"), true);
});

test("bridge backend keeps a newly forked vscode thread visible before desktop index refreshes", async () => {
  const backend = new AppServerBridgeBackend();
  const source = thread("source-thread", {
    cwd: "D:/Projects/ForkProject",
    source: "vscode",
  });
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("source-thread", cwd, source),
    threadFork: async () => startedThreadResponse("forked-thread", "D:/Projects/ForkProject", {
      ...source,
      id: "forked-thread",
      preview: "forked preview",
    }),
  };

  await backend.createThread("D:/Projects/ForkProject");
  const snapshot = await backend.forkThread("source-thread");

  assert.equal(snapshot.selectedThreadId, "forked-thread");
  assert.equal(snapshot.threads.some((item) => item.id === "forked-thread"), true);
});

test("bridge backend keeps a local fork visible across refresh before desktop index refreshes", async () => {
  const backend = new AppServerBridgeBackend();
  const source = thread("source-thread", {
    cwd: "D:/Projects/ForkProject",
    source: "vscode",
  });
  const other = thread("other-thread", {
    cwd: "D:/Projects/Other",
    source: "appServer",
  });
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("source-thread", cwd, source),
    threadFork: async () => startedThreadResponse("forked-thread", "D:/Projects/ForkProject", {
      ...source,
      id: "forked-thread",
      preview: "forked preview",
    }),
    threadList: async () => [source, other],
    threadRead: async (threadId) => (threadId === "other-thread" ? other : source),
  };

  await backend.createThread("D:/Projects/ForkProject");
  await backend.forkThread("source-thread");
  const snapshot = await backend.refreshThreads("other-thread");

  assert.equal(snapshot.threads.some((item) => item.id === "forked-thread"), true);
  assert.equal(snapshot.threads.some((item) => item.id === "other-thread"), true);
});

test("bridge backend forks from a specific turn by rolling back the fork", async () => {
  const backend = new AppServerBridgeBackend();
  const rollbackCalls = [];
  const source = thread("source-thread", {
    cwd: "D:/Projects/ForkProject",
    turns: [
      { id: "turn-1", status: "completed", items: [{ type: "userMessage", id: "user-1", content: [{ type: "text", text: "one" }] }] },
      { id: "turn-2", status: "completed", items: [{ type: "userMessage", id: "user-2", content: [{ type: "text", text: "two" }] }] },
      { id: "turn-3", status: "completed", items: [{ type: "userMessage", id: "user-3", content: [{ type: "text", text: "three" }] }] },
    ],
  });
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("source-thread", cwd, source),
    threadFork: async () => startedThreadResponse("forked-thread", "D:/Projects/ForkProject", {
      ...source,
      id: "forked-thread",
    }),
    threadRollback: async (threadId, numTurns) => {
      rollbackCalls.push([threadId, numTurns]);
      return startedThreadResponse(threadId, "D:/Projects/ForkProject", {
        ...source,
        id: threadId,
        turns: source.turns.slice(0, 2),
      });
    },
  };

  await backend.createThread("D:/Projects/ForkProject");
  const snapshot = await backend.forkThread("source-thread", 2);

  assert.deepEqual(rollbackCalls, [["forked-thread", 1]]);
  assert.equal(snapshot.selectedThreadId, "forked-thread");
  assert.deepEqual(snapshot.messages.map((message) => message.id), ["user-1", "user-2"]);
});

test("bridge backend reads source turns before turn-scoped fork when cache is partial", async () => {
  const backend = new AppServerBridgeBackend();
  const readCalls = [];
  const rollbackCalls = [];
  const source = thread("source-thread", {
    cwd: "D:/Projects/ForkProject",
    turns: [
      { id: "turn-1", status: "completed", items: [{ type: "userMessage", id: "user-1", content: [{ type: "text", text: "one" }] }] },
      { id: "turn-2", status: "completed", items: [{ type: "userMessage", id: "user-2", content: [{ type: "text", text: "two" }] }] },
      { id: "turn-3", status: "completed", items: [{ type: "userMessage", id: "user-3", content: [{ type: "text", text: "three" }] }] },
    ],
  });
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("source-thread", cwd, source),
    threadList: async () => [thread("source-thread", { cwd: "D:/Projects/ForkProject", turns: [] })],
    threadRead: async (threadId, includeTurns) => {
      readCalls.push([threadId, includeTurns]);
      return source;
    },
    threadFork: async () => startedThreadResponse("forked-thread", "D:/Projects/ForkProject", {
      ...source,
      id: "forked-thread",
    }),
    threadRollback: async (threadId, numTurns) => {
      rollbackCalls.push([threadId, numTurns]);
      return {
        thread: thread(threadId, {
          cwd: "D:/Projects/ForkProject",
          turns: source.turns.slice(0, 2),
        }),
      };
    },
  };

  await backend.createThread("D:/Projects/ForkProject");
  backend.threads.get("source-thread").thread.turns = [];
  await backend.forkThread("source-thread", 2);

  assert.deepEqual(readCalls, [["source-thread", true]]);
  assert.deepEqual(rollbackCalls, [["forked-thread", 1]]);
});

test("bridge backend rolls back last turn from prompt command", async () => {
  const backend = new AppServerBridgeBackend();
  const rollbackCalls = [];
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("rollback-thread", cwd),
    threadRead: async (threadId) => thread(threadId, { cwd: "D:/Projects/RollbackProject" }),
    threadRollback: async (threadId, numTurns) => {
      rollbackCalls.push([threadId, numTurns]);
      return {
        thread: thread(threadId, {
          cwd: "D:/Projects/RollbackProject",
          turns: [],
        }),
      };
    },
  };

  await backend.createThread("D:/Projects/RollbackProject");
  const snapshot = await backend.sendPrompt("rollback-thread", "/rollback");

  assert.deepEqual(rollbackCalls, [["rollback-thread", 1]]);
  assert.equal(snapshot.isGenerating, false);
  assert.equal(snapshot.messages.some((message) =>
    message.blocks.some((block) => block.kind === "status" && block.value === "已回滚最近 1 轮")
  ), true);
});

test("bridge backend resends prompt after rolling back to selected user turn", async () => {
  const backend = new AppServerBridgeBackend();
  const calls = [];
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("resend-thread", cwd),
    threadRead: async (threadId) => thread(threadId, { cwd: "D:/Projects/ResendProject" }),
    threadRollback: async (threadId, numTurns) => {
      calls.push(["rollback", threadId, numTurns]);
      return {
        thread: thread(threadId, {
          cwd: "D:/Projects/ResendProject",
          turns: [{ id: "turn-1", status: "completed", items: [] }],
        }),
      };
    },
    turnStart: async (threadId, text) => {
      calls.push(["turnStart", threadId, text]);
      return "turn-resend";
    },
  };

  await backend.createThread("D:/Projects/ResendProject");
  const snapshot = await backend.resendPrompt("resend-thread", "edited prompt", 2);

  assert.deepEqual(calls, [
    ["rollback", "resend-thread", 2],
    ["turnStart", "resend-thread", "edited prompt"],
  ]);
  assert.equal(snapshot.isGenerating, true);
});

test("bridge backend can stop a resumed in-progress turn", async () => {
  const backend = new AppServerBridgeBackend();
  const interruptCalls = [];
  const liveThread = thread("running-thread", {
    status: { type: "active", activeFlags: [] },
    turns: [
      {
        id: "turn-live",
        status: "inProgress",
        completedAt: null,
        items: [
          { type: "agentMessage", id: "assistant-live", text: "working" },
        ],
      },
    ],
  });
  backend.appServer = {
    threadResume: async () => startedThreadResponse("running-thread", "D:/Projects/Running", liveThread),
    turnInterrupt: async (threadId, turnId) => {
      interruptCalls.push([threadId, turnId]);
    },
    threadUnsubscribe: async () => {},
  };

  await backend.selectThread("running-thread");
  const snapshot = await backend.stopTurn("running-thread");

  assert.deepEqual(interruptCalls, [["running-thread", "turn-live"]]);
  assert.equal(snapshot.isGenerating, true);
});

test("bridge backend steers a resumed in-progress turn instead of starting another one", async () => {
  const backend = new AppServerBridgeBackend();
  const steerCalls = [];
  const startCalls = [];
  const liveThread = thread("steer-thread", {
    status: { type: "active", activeFlags: [] },
    turns: [
      {
        id: "turn-steer",
        status: "running",
        completedAt: null,
        items: [
          { type: "agentMessage", id: "assistant-steer", text: "working" },
        ],
      },
    ],
  });
  backend.appServer = {
    threadResume: async () => startedThreadResponse("steer-thread", "D:/Projects/Steer", liveThread),
    threadRead: async () => liveThread,
    turnSteer: async (threadId, turnId, text) => {
      steerCalls.push([threadId, turnId, text]);
    },
    turnStart: async (threadId, text) => {
      startCalls.push([threadId, text]);
      return "turn-new";
    },
    threadUnsubscribe: async () => {},
  };

  await backend.selectThread("steer-thread");
  await backend.sendPrompt("steer-thread", "继续");

  assert.deepEqual(steerCalls, [["steer-thread", "turn-steer", "继续"]]);
  assert.deepEqual(startCalls, []);
});

test("bridge backend accumulates command output deltas from notification stream", async () => {
  const backend = new AppServerBridgeBackend();
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("command-delta-thread", cwd),
  };

  await backend.createThread("D:/Projects/CommandDeltaProject");
  await backend.handleNotification({
    method: "item/commandExecution/outputDelta",
    params: { threadId: "command-delta-thread", turnId: "turn-1", itemId: "command-1", delta: "one" },
  });
  await backend.handleNotification({
    method: "item/commandExecution/outputDelta",
    params: { threadId: "command-delta-thread", turnId: "turn-1", itemId: "command-1", delta: "\ntwo" },
  });

  const snapshot = backend.getSnapshot("command-delta-thread");
  assert.equal(
    snapshot.messages.find((message) => message.id === "command-1")?.blocks.find((block) => block.kind === "code")?.value,
    "one\ntwo"
  );
});

test("bridge backend archives current thread and returns to draft state", async () => {
  const backend = new AppServerBridgeBackend();
  const threadsById = {
    "thread-a": thread("thread-a", { cwd: "D:/Projects/A", updatedAt: 100 }),
    "thread-b": thread("thread-b", { cwd: "D:/Projects/B", updatedAt: 200 }),
  };
  let startIndex = 0;
  let activeIds = ["thread-a", "thread-b"];
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse(
      startIndex++ === 0 ? "thread-a" : "thread-b",
      cwd
    ),
    threadArchive: async (threadId) => {
      activeIds = activeIds.filter((id) => id !== threadId);
    },
    threadList: async (archived = false) => {
      assert.equal(archived, false);
      return activeIds.map((id) => threadsById[id]);
    },
    threadRead: async (threadId) => threadsById[threadId],
  };

  await backend.createThread("D:/Projects/A");
  await backend.createThread("D:/Projects/B");
  const snapshot = await backend.archiveThread("thread-b");

  assert.equal(snapshot.selectedThreadId, "");
  assert.equal(snapshot.cwd, "");
  assert.deepEqual(snapshot.messages, []);
  assert.deepEqual(snapshot.threads.map((item) => item.id), ["thread-a"]);
});

test("bridge backend selects a thread again after unarchive refresh", async () => {
  const backend = new AppServerBridgeBackend();
  const threadsById = {
    "thread-a": thread("thread-a", { cwd: "D:/Projects/A", updatedAt: 100 }),
    "thread-b": thread("thread-b", { cwd: "D:/Projects/B", updatedAt: 200 }),
  };
  let activeIds = ["thread-a"];
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("thread-a", cwd),
    threadUnarchive: async (threadId) => {
      if (!activeIds.includes(threadId)) {
        activeIds = [...activeIds, threadId];
      }
    },
    threadList: async (archived = false) =>
      archived
        ? Object.values(threadsById).filter((entry) => !activeIds.includes(entry.id))
        : activeIds.map((id) => threadsById[id]),
    threadRead: async (threadId) => threadsById[threadId],
  };

  await backend.createThread("D:/Projects/A");
  const snapshot = await backend.unarchiveThread("thread-b");

  assert.equal(snapshot.selectedThreadId, "thread-b");
  assert.equal(snapshot.cwd, "D:/Projects/B");
});

test("bridge backend does not query or include archived threads in refreshed snapshots", async () => {
  const backend = new AppServerBridgeBackend();
  const threadsById = {
    "thread-a": thread("thread-a", { cwd: "D:/Projects/A", updatedAt: 100 }),
    "thread-archived": thread("thread-archived", { cwd: "D:/Projects/Old", updatedAt: 50 }),
  };
  const threadListArchivedFlags = [];
  backend.appServer = {
    configOptions: async () => fakeConfigOptions(),
    threadStart: async (cwd) => startedThreadResponse("thread-a", cwd),
    threadList: async (archived = false) => {
      threadListArchivedFlags.push(archived);
      return archived ? [threadsById["thread-archived"]] : [threadsById["thread-a"]];
    },
    threadRead: async (threadId) => threadsById[threadId],
  };

  await backend.createThread("D:/Projects/A");
  const snapshot = await backend.refreshThreads("thread-a");

  assert.deepEqual(
    snapshot.threads.map((item) => [item.id, item.archived]),
    [["thread-a", false]]
  );
  assert.deepEqual(threadListArchivedFlags, [false]);
  assert.equal(snapshot.configOptions.defaults.model, "gpt-5");
});

test("bridge backend refreshThreads does not override a newer manual selection", async () => {
  const backend = new AppServerBridgeBackend();
  const threadsById = {
    "thread-a": thread("thread-a", { cwd: "D:/Projects/A", updatedAt: 100 }),
    "thread-b": thread("thread-b", { cwd: "D:/Projects/B", updatedAt: 200 }),
  };
  let startIndex = 0;
  let releaseThreadList = () => {};
  const threadListBlocked = new Promise((resolve) => {
    releaseThreadList = resolve;
  });
  backend.appServer = {
    configOptions: async () => fakeConfigOptions(),
    threadStart: async (cwd) => startedThreadResponse(
      startIndex++ === 0 ? "thread-a" : "thread-b",
      cwd
    ),
    threadList: async (archived = false) => {
      await threadListBlocked;
      if (archived) {
        return [];
      }
      return [threadsById["thread-a"], threadsById["thread-b"]];
    },
    threadRead: async (threadId) => threadsById[threadId],
    threadResume: async (threadId) => startedThreadResponse(
      threadId,
      threadsById[threadId]?.cwd,
      threadsById[threadId]
    ),
    threadUnsubscribe: async () => {},
  };

  await backend.createThread("D:/Projects/A");
  await backend.createThread("D:/Projects/B");

  const refreshPromise = backend.refreshThreads("thread-a");
  await backend.selectThread("thread-b");
  releaseThreadList();

  const snapshot = await refreshPromise;
  assert.equal(snapshot.selectedThreadId, "thread-b");
  assert.equal(snapshot.cwd, "D:/Projects/B");
});

test("bridge backend restores previous selection when resume fails", async () => {
  const backend = new AppServerBridgeBackend();
  backend.appServer = {
    configOptions: async () => fakeConfigOptions(),
    threadStart: async (cwd) => startedThreadResponse("thread-a", cwd),
    threadList: async () => [
      thread("thread-a", { cwd: "D:/Projects/A" }),
      thread("thread-b", { cwd: "D:/Projects/B" }),
    ],
    threadRead: async (threadId) => thread(threadId, { cwd: `D:/Projects/${threadId}` }),
    threadResume: async (threadId) => {
      if (threadId === "thread-b") {
        throw new Error("resume failed");
      }
      return startedThreadResponse(threadId, `D:/Projects/${threadId}`);
    },
    threadUnsubscribe: async () => {},
  };

  await backend.createThread("D:/Projects/A");
  await backend.refreshThreads("thread-a");
  await assert.rejects(() => backend.selectThread("thread-b"), /resume failed/);

  const snapshot = backend.getSnapshot();
  assert.equal(snapshot.selectedThreadId, "thread-a");
});

test("bridge backend loadOlderMessages expands history window", async () => {
  const backend = new AppServerBridgeBackend();
  backend.appServer = {
    threadStart: async (cwd) => startedThreadResponse("history-thread", cwd),
  };

  await backend.createThread("D:/Projects/History");
  const before = backend.threads.get("history-thread").historyWindow;

  await backend.loadOlderMessages("history-thread");

  assert.equal(backend.threads.get("history-thread").historyWindow, before + 80);
});

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

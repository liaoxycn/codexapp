import assert from "node:assert/strict";
import test from "node:test";
import {
  buildVisibleThreadSummaries,
  dedupeSummaries,
  mapThreadToSummary,
  touchThreadActivity,
} from "../dist/bridge/threadSummaries.js";

function thread(id, overrides = {}) {
  return {
    id,
    preview: id,
    status: "idle",
    cwd: "D:/Projects/TestApp",
    updatedAt: 100,
    name: null,
    turns: [],
    modelProvider: "openai",
    ...overrides,
  };
}

test("mapThreadToSummary uses latest message text as subtitle and cwd leaf as project group", () => {
  const summary = mapThreadToSummary(thread("thread-1", {
    preview: "preview text",
    turns: [
      {
        id: "turn-1",
        startedAt: 100,
        completedAt: 120,
        status: "completed",
        items: [{ type: "agentMessage", text: "final answer" }],
        itemsView: "full",
      },
    ],
  }));

  assert.equal(summary.subtitle, "final answer");
  assert.equal(summary.groupKind, "project");
  assert.equal(summary.groupLabel, "TestApp");
});

test("mapThreadToSummary keeps desktop synthetic chat cwd in chat group", () => {
  const byLeaf = mapThreadToSummary(thread("weather-chat", {
    preview: "查看深圳天气",
    cwd: "C:/Users/lxy/Documents/Codex/2026-05-30/new-chat",
  }));
  const byDateFolder = mapThreadToSummary(thread("dated-chat", {
    preview: "普通聊天",
    cwd: "C:/Users/lxy/Documents/Codex/2026-05-30/session-123",
  }));
  const projectNamedNewProject = mapThreadToSummary(thread("real-project", {
    preview: "调研 Codex 网页客户端可行性",
    cwd: "D:/Data/Documents/New project",
  }));

  assert.equal(byLeaf.groupKind, "chat");
  assert.equal(byLeaf.groupLabel, "普通会话");
  assert.equal(byDateFolder.groupKind, "chat");
  assert.equal(projectNamedNewProject.groupKind, "project");
  assert.equal(projectNamedNewProject.groupLabel, "New project");
});

test("visible thread summaries match Desktop main list visibility", () => {
  const summaries = buildVisibleThreadSummaries(
    [
      thread("desktop-project", {
        name: "项目会话测试",
        cwd: "D:/Data/Documents/md2html",
        source: "vscode",
      }),
      thread("desktop-project-second", {
        name: "写一个1",
        cwd: "D:/Data/Documents/md2html",
        source: "vscode",
      }),
      thread("desktop-codexapp-short", {
        name: "hello",
        cwd: "D:/Projects/home/codexapp",
        source: "vscode",
      }),
      thread("desktop-chat", {
        name: "查看深圳天气",
        cwd: "C:/Users/lxy/Documents/Codex/2026-05-30/new-chat",
        source: "vscode",
      }),
      thread("desktop-projectless", {
        name: "回复一下",
        cwd: "C:/Users/lxy/Documents/Codex/2026-06-01/codex-desktop",
        source: "vscode",
      }),
      thread("mobile-thread", {
        name: "手机新建",
        source: "appServer",
      }),
      thread("imported-openmanus", {
        cwd: "D:/Projects/home/OpenManus",
        source: "vscode",
      }),
      thread("unindexed-trtr", {
        cwd: "D:/Projects/home/trtr",
        source: "vscode",
      }),
      thread("cli-thread", { source: "cli" }),
      thread("placeholder", {
        name: "<environment_context>",
        source: "vscode",
      }),
    ],
    {
      desktopVisibility: {
        workspaceRoots: new Set([
          "d:/data/documents/md2html",
          "d:/projects/home/codexapp",
          "d:/data/documents/new project",
        ]),
        projectlessThreadIds: new Set(["desktop-projectless"]),
        importedThreadIds: new Set(["imported-openmanus"]),
      },
    }
  );

  assert.deepEqual(
    summaries.map((item) => item.id),
    [
      "desktop-project",
      "desktop-project-second",
      "desktop-codexapp-short",
      "desktop-chat",
      "desktop-projectless",
      "mobile-thread",
    ]
  );
});

test("mapThreadToSummary includes branch and short git sha when present", () => {
  const summary = mapThreadToSummary(thread("thread-git", {
    gitInfo: {
      branch: "feature/mobile-shell",
      sha: "1234567890abcdef",
      originUrl: "git@example.com:repo.git",
    },
  }));

  assert.equal(summary.gitBranch, "feature/mobile-shell");
  assert.equal(summary.gitSha, "1234567");
});

test("dedupeSummaries keeps the last payload for the same thread id", () => {
  const deduped = dedupeSummaries([
    { id: "thread-1", title: "old" },
    { id: "thread-1", title: "new" },
  ]);

  assert.deepEqual(deduped, [{ id: "thread-1", title: "new" }]);
});

test("touchThreadActivity updates summary and snapshot thread timestamps", () => {
  const state = {
    summary: { id: "thread-1", updatedAt: 1000 },
    lastActivityAtMs: 1000,
    snapshot: {
      threads: [
        { id: "thread-1", updatedAt: 1000 },
        { id: "thread-2", updatedAt: 500 },
      ],
    },
  };

  touchThreadActivity(state, 2_500_000_000_000);

  assert.equal(state.lastActivityAtMs, 2_500_000_000_000);
  assert.equal(state.summary.updatedAt, 2_500_000_000_000);
  assert.deepEqual(state.snapshot.threads.map((item) => item.updatedAt), [2_500_000_000_000, 500]);
});

test("touchThreadActivity normalizes app-server seconds to milliseconds", () => {
  const state = {
    summary: { id: "thread-1", updatedAt: 1000 },
    lastActivityAtMs: 1000,
    snapshot: {
      threads: [
        { id: "thread-1", updatedAt: 1000 },
      ],
    },
  };

  touchThreadActivity(state, 2500);

  assert.equal(state.lastActivityAtMs, 2_500_000);
  assert.equal(state.summary.updatedAt, 2_500_000);
  assert.equal(state.snapshot.threads[0].updatedAt, 2_500_000);
});

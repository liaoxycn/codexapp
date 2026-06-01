import assert from "node:assert/strict";
import test from "node:test";
import {
  ensureResumedThread,
  refreshThreadState,
  unsubscribeInactiveThreadSubscriptions,
} from "../dist/bridge/threadSubscriptions.js";

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

function resumeResult(id, overrides = {}) {
  return {
    thread: thread(id, overrides.thread),
    model: "gpt-5",
    modelProvider: "openai",
    serviceTier: null,
    cwd: overrides.thread?.cwd ?? "C:/work",
    instructionSources: [overrides.thread?.cwd ?? "C:/work"],
    approvalPolicy: "never",
    approvalsReviewer: "user",
    sandbox: { type: "workspaceWrite", networkAccess: false },
    reasoningEffort: null,
  };
}

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
    thread: thread(id),
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
      threads: [],
      selectedThreadId: id,
      messages: [],
      hasMoreHistory: false,
      pendingApproval: null,
      chips: [],
    files: [],
      slashCommands: [],
      cwd: "C:/work",
      permissionSummary: "workspace-write · never",
      isGenerating: false,
    },
    ...overrides,
  };
}

test("ensureResumedThread reuses materialized thread when rollout is already gone", async () => {
  const threads = new Map([["thread-a", existingState("thread-a")]]);

  const state = await ensureResumedThread(
    {
      appServer: {
        threadResume: async () => {
          throw new Error("no rollout found for thread id thread-a");
        },
      },
      threads,
    },
    "thread-a"
  );

  assert.equal(state.isSubscribed, true);
  assert.equal(threads.get("thread-a")?.isSubscribed, true);
});

test("refreshThreadState retries non-materialized reads without dropping subscription flag", async () => {
  const threads = new Map([["thread-a", existingState("thread-a", { isSubscribed: true })]]);
  const calls = [];

  await refreshThreadState(
    {
      appServer: {
        threadRead: async (threadId, materialized) => {
          calls.push(materialized ?? true);
          if (calls.length === 1) {
            throw new Error(`thread ${threadId} not materialized yet`);
          }
          return thread(threadId, { preview: "updated", updatedAt: 200 });
        },
      },
      threads,
    },
    "thread-a"
  );

  assert.deepEqual(calls, [true, false]);
  assert.equal(threads.get("thread-a")?.isSubscribed, true);
  assert.equal(threads.get("thread-a")?.thread?.preview, "updated");
});

test("unsubscribeInactiveThreadSubscriptions only clears stale subscribed threads", async () => {
  const threads = new Map([
    ["thread-a", existingState("thread-a", { isSubscribed: true })],
    ["thread-b", existingState("thread-b", { isSubscribed: true })],
    ["thread-c", existingState("thread-c", { isSubscribed: false })],
  ]);
  const unsubscribed = [];

  await unsubscribeInactiveThreadSubscriptions(
    {
      appServer: {
        threadUnsubscribe: async (threadId) => {
          unsubscribed.push(threadId);
        },
      },
      threads,
    },
    "thread-b"
  );

  assert.deepEqual(unsubscribed, ["thread-a"]);
  assert.equal(threads.get("thread-a")?.isSubscribed, false);
  assert.equal(threads.get("thread-b")?.isSubscribed, true);
  assert.equal(threads.get("thread-c")?.isSubscribed, false);
});

import assert from "node:assert/strict";
import test from "node:test";
import {
  getActiveTurnId,
  isThreadActivelyGenerating,
  isTerminalTurnStatus,
  getThreadActiveFlags,
  resolveDisplayedThreadStatus,
  resolveLifecycleStatus,
  resolveThreadSummaryStatus,
  shouldRetainLiveThreadRuntime,
  shouldRetainThreadRuntimeOverlay,
} from "../dist/threadState.js";

function thread(overrides = {}) {
  return {
    id: "thread-1",
    preview: "preview",
    status: "active",
    cwd: "C:/work",
    updatedAt: 0,
    name: null,
    modelProvider: "openai",
    turns: [],
    ...overrides,
  };
}

test("terminal turn status is not generating", () => {
  assert.equal(isTerminalTurnStatus("completed"), true);
  assert.equal(isTerminalTurnStatus("failed"), true);
  assert.equal(isTerminalTurnStatus("running"), false);
});

test("active thread with completed compaction is not generating", () => {
  assert.equal(
    isThreadActivelyGenerating(
      thread({
        turns: [
          {
            id: "turn-1",
            status: "completed",
            items: [
              { type: "contextCompaction", id: "item-1" },
            ],
          },
        ],
      })
    ),
    false
  );
});

test("active thread with unfinished assistant item is generating", () => {
  assert.equal(
    isThreadActivelyGenerating(
      thread({
        turns: [
          {
            id: "turn-2",
            status: "running",
            items: [
              { type: "agentMessage", id: "item-2", text: "hi" },
            ],
          },
        ],
      })
    ),
    true
  );
});

test("persisted in-progress turn is generating even when thread status is notLoaded", () => {
  assert.equal(
    resolveThreadSummaryStatus(
      thread({
        status: { type: "notLoaded" },
        turns: [
          {
            id: "turn-cross-process",
            status: "inProgress",
            items: [
              { type: "agentMessage", id: "item-cross-process", text: "working" },
            ],
          },
        ],
      })
    ),
    "running"
  );
});

test("recent app-server activity without materialized turns is treated as running", () => {
  const nowSeconds = Math.floor(Date.now() / 1000);
  assert.equal(
    resolveThreadSummaryStatus(
      thread({
        status: { type: "notLoaded" },
        updatedAt: nowSeconds,
        turns: [
          {
            id: "turn-old",
            status: "completed",
            startedAt: nowSeconds - 90,
            completedAt: nowSeconds - 80,
            items: [
              { type: "agentMessage", id: "item-old", text: "done" },
            ],
          },
        ],
      })
    ),
    "running"
  );
});

test("recent unfinished interrupted turn is treated as running", () => {
  const nowSeconds = Math.floor(Date.now() / 1000);
  assert.equal(
    resolveThreadSummaryStatus(
      thread({
        status: { type: "notLoaded" },
        updatedAt: nowSeconds,
        turns: [
          {
            id: "turn-interrupted-live",
            status: "interrupted",
            startedAt: nowSeconds - 5,
            completedAt: null,
            items: [],
          },
        ],
      })
    ),
    "running"
  );
});

test("active turn id is recovered from latest non-terminal in-progress turn", () => {
  assert.equal(
    getActiveTurnId(
      thread({
        turns: [
          {
            id: "turn-done",
            status: "completed",
            completedAt: 100,
            items: [],
          },
          {
            id: "turn-live",
            status: "inProgress",
            completedAt: null,
            items: [],
          },
        ],
      })
    ),
    "turn-live"
  );
});

test("active turn id ignores completed and interrupted terminal turns", () => {
  assert.equal(
    getActiveTurnId(
      thread({
        turns: [
          {
            id: "turn-completed",
            status: "running",
            completedAt: 100,
            items: [],
          },
          {
            id: "turn-interrupted",
            status: "interrupted",
            completedAt: null,
            items: [],
          },
        ],
      })
    ),
    null
  );
});

test("old unmaterialized app-server activity stays idle", () => {
  assert.equal(
    resolveThreadSummaryStatus(
      thread({
        status: { type: "notLoaded" },
        updatedAt: 100,
        turns: [
          {
            id: "turn-old",
            status: "completed",
            startedAt: 80,
            completedAt: 90,
            items: [
              { type: "agentMessage", id: "item-old", text: "done" },
            ],
          },
        ],
      })
    ),
    "idle"
  );
});

test("new empty active thread stays idle", () => {
  const nowSeconds = Math.floor(Date.now() / 1000);
  assert.equal(
    resolveThreadSummaryStatus(
      thread({
        status: { type: "active", activeFlags: [] },
        updatedAt: nowSeconds,
        turns: [],
      })
    ),
    "idle"
  );
});

test("runtime generating state keeps thread listed as running", () => {
  assert.equal(
    resolveDisplayedThreadStatus("idle", {
      isGenerating: true,
      currentTurnId: "turn-9",
      transientOperation: null,
      pendingApproval: null,
    }),
    "running"
  );
});

test("pending approval wins over idle summary", () => {
  assert.equal(
    resolveDisplayedThreadStatus("idle", {
      isGenerating: false,
      currentTurnId: null,
      transientOperation: null,
      pendingApproval: "需要审批",
    }),
    "needs_approval"
  );
});

test("finished active thread resolves to idle summary", () => {
  assert.equal(
    resolveThreadSummaryStatus(
      thread({
        status: "active",
        turns: [
          {
            id: "turn-3",
            status: "completed",
            items: [
              { type: "agentMessage", id: "item-3", text: "done" },
            ],
          },
        ],
      })
    ),
    "idle"
  );
});

test("string based backend states map correctly", () => {
  assert.equal(resolveLifecycleStatus("active", false), "running");
  assert.equal(resolveLifecycleStatus("inProgress", false), "running");
  assert.equal(resolveLifecycleStatus("waitingOnApproval", false), "needs_approval");
  assert.equal(resolveLifecycleStatus("waitingOnUserInput", false), "idle");
  assert.equal(resolveLifecycleStatus("systemError", false), "failed");
  assert.equal(resolveLifecycleStatus("notLoaded", false), "idle");
  assert.equal(resolveThreadSummaryStatus(thread({ status: "waitingOnApproval" })), "needs_approval");
  assert.equal(resolveThreadSummaryStatus(thread({ status: "inProgress" })), "running");
});

test("active flags map to the expected lifecycle state", () => {
  assert.deepEqual(getThreadActiveFlags({ type: "active", activeFlags: ["waitingOnUserInput"] }), ["waitingOnUserInput"]);
  assert.equal(
    resolveLifecycleStatus({ type: "active", activeFlags: ["waitingOnUserInput"] }, false),
    "idle"
  );
  assert.equal(
    resolveThreadSummaryStatus(
      thread({ status: { type: "active", activeFlags: ["waitingOnApproval"] } })
    ),
    "needs_approval"
  );
});

test("runtime state keeps active thread running after refresh", () => {
  assert.equal(
    resolveDisplayedThreadStatus("idle", {
      isGenerating: true,
      currentTurnId: "turn-11",
      transientOperation: null,
      pendingApproval: null,
    }),
    "running"
  );
});

test("app-server active status is preserved when runtime is idle", () => {
  assert.equal(
    resolveDisplayedThreadStatus("running", {
      isGenerating: false,
      currentTurnId: null,
      transientOperation: null,
      pendingApproval: null,
    }),
    "running"
  );
});

test("idle active thread with historical turns stays idle unless runtime says otherwise", () => {
  assert.equal(
    resolveThreadSummaryStatus(
      thread({
        status: { type: "active", activeFlags: [] },
        turns: [
          {
            id: "turn-old",
            status: "completed",
            items: [
              { type: "agentMessage", id: "item-old", text: "done" },
            ],
          },
        ],
      })
    ),
    "idle"
  );
});

test("only live threads retain runtime overlays across refresh", () => {
  assert.equal(
    shouldRetainLiveThreadRuntime(
      thread({
        status: { type: "active", activeFlags: ["waitingOnApproval"] },
        turns: [
          {
            id: "turn-live",
            status: "inProgress",
            items: [
              { type: "agentMessage", id: "item-live", text: "working" },
            ],
          },
        ],
      })
    ),
    true
  );
  assert.equal(
    shouldRetainLiveThreadRuntime(
      thread({
        status: "idle",
        turns: [
          {
            id: "turn-done",
            status: "completed",
            items: [
              { type: "agentMessage", id: "item-done", text: "done" },
            ],
          },
        ],
      })
    ),
    false
  );
});

test("stale idle refresh drops an existing live runtime overlay", () => {
  assert.equal(
    shouldRetainThreadRuntimeOverlay(
      thread({
        status: "idle",
        turns: [
          {
            id: "turn-live",
            status: "completed",
            items: [
              { type: "agentMessage", id: "item-live", text: "done" },
            ],
          },
        ],
      }),
      {
        isGenerating: true,
        currentTurnId: "turn-live",
        transientOperation: null,
        pendingApproval: null,
      }
    ),
    false
  );
});

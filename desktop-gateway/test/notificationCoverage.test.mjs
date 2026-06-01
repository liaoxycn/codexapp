import assert from "node:assert/strict";
import test from "node:test";
import { handleBridgeNotification } from "../dist/bridge/notifications.js";

function createState() {
  return {
    pendingApproval: null,
    transientOperation: null,
    currentTurnId: null,
    snapshot: {
      messages: [],
      cwd: "D:/repo",
      isGenerating: false,
    },
  };
}

function createDeps(state) {
  const statuses = [];
  let emitCount = 0;
  return {
    deps: {
      threads: new Map([["thread-1", state]]),
      emitChanged: () => {
        emitCount += 1;
      },
      finalizeCompactState: async () => {},
      finalizeTurnState: async () => {},
      hydrateThreads: async () => {},
      ensureActiveAssistantMessage: () => {},
      updateSummaryStatus: (_threadId, status) => {
        statuses.push(status);
      },
    },
    get emitCount() {
      return emitCount;
    },
    statuses,
  };
}

test("handleBridgeNotification surfaces plan, diff and model reroute notifications", async () => {
  const state = createState();
  const context = createDeps(state);

  await handleBridgeNotification(
    {
      method: "turn/plan/updated",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        explanation: "执行计划",
        plan: [{ step: "补协议", status: "in_progress" }],
      },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "turn/diff/updated",
      params: { threadId: "thread-1", turnId: "turn-1", diff: "diff --git a/a b/a" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "model/rerouted",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        fromModel: "gpt-5",
        toModel: "gpt-5-high",
        reason: "highRiskCyberActivity",
      },
    },
    context.deps
  );

  assert.equal(context.emitCount, 3);
  assert.deepEqual(
    state.snapshot.messages.map((message) => message.blocks[0].value),
    [
      "执行计划\nin_progress: 补协议",
      "diff --git a/a b/a",
      "模型已切换: gpt-5 -> gpt-5-high · highRiskCyberActivity",
    ]
  );
});

test("handleBridgeNotification surfaces streaming item notifications", async () => {
  const state = createState();
  const context = createDeps(state);

  await handleBridgeNotification(
    {
      method: "item/reasoning/textDelta",
      params: { threadId: "thread-1", turnId: "turn-1", itemId: "reasoning-1", delta: "思考" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "item/plan/delta",
      params: { threadId: "thread-1", turnId: "turn-1", itemId: "plan-1", delta: "步骤" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "item/fileChange/outputDelta",
      params: { threadId: "thread-1", turnId: "turn-1", itemId: "patch-1", delta: "+line" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "item/mcpToolCall/progress",
      params: { threadId: "thread-1", turnId: "turn-1", itemId: "mcp-1", message: "连接中" },
    },
    context.deps
  );

  assert.equal(context.emitCount, 4);
  assert.deepEqual(
    state.snapshot.messages.map((message) => message.blocks.at(-1).value),
    ["思考", "步骤", "+line", "MCP 进度: 连接中"]
  );
});

test("handleBridgeNotification surfaces errors and thread warnings", async () => {
  const state = createState();
  const context = createDeps(state);

  await handleBridgeNotification(
    {
      method: "warning",
      params: { threadId: "thread-1", message: "配置有风险" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "error",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        willRetry: false,
        error: { message: "失败", additionalDetails: "详情", codexErrorInfo: null },
      },
    },
    context.deps
  );

  assert.deepEqual(context.statuses, ["failed"]);
  assert.equal(state.snapshot.isGenerating, false);
  assert.deepEqual(
    state.snapshot.messages.map((message) => message.blocks[0].value),
    ["警告: 配置有风险", "错误: 失败\n详情"]
  );
});

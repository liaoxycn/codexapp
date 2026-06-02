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
  const operationalNotices = [];
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
      pushOperationalNotice: (notice) => {
        operationalNotices.push(notice);
      },
      updateSummaryStatus: (_threadId, status) => {
        statuses.push(status);
      },
    },
    get emitCount() {
      return emitCount;
    },
    statuses,
    operationalNotices,
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
      method: "item/reasoning/summaryPartAdded",
      params: { threadId: "thread-1", turnId: "turn-1", itemId: "summary-1", summaryIndex: 0 },
    },
    context.deps
  );
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
  await handleBridgeNotification(
    {
      method: "item/commandExecution/terminalInteraction",
      params: { threadId: "thread-1", turnId: "turn-1", itemId: "cmd-1", processId: "p1", stdin: "y\n" },
    },
    context.deps
  );

  assert.equal(context.emitCount, 6);
  assert.deepEqual(
    state.snapshot.messages.map((message) => message.blocks.at(-1).value),
    ["思考摘要 1", "思考", "步骤", "+line", "MCP 进度: 连接中", "\nstdin> y\n"]
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

test("handleBridgeNotification surfaces global notices on the selected thread", async () => {
  const state = createState();
  state.snapshot.selectedThreadId = "thread-1";
  const context = createDeps(state);

  await handleBridgeNotification(
    {
      method: "configWarning",
      params: { summary: "配置项无效", details: "请检查 config.toml", path: "D:/repo/config.toml" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "deprecationNotice",
      params: { summary: "旧模型将停用", details: "请切换模型" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "model/verification",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        verifications: ["trustedAccessForCyber"],
      },
    },
    context.deps
  );

  assert.equal(context.emitCount, 3);
  assert.deepEqual(
    state.snapshot.messages.map((message) => message.blocks[0].value),
    [
      "配置警告: 配置项无效\n请检查 config.toml\nD:/repo/config.toml",
      "废弃提示: 旧模型将停用\n请切换模型",
      "模型验证: trustedAccessForCyber",
    ]
  );
});

test("handleBridgeNotification surfaces thread token usage updates", async () => {
  const state = createState();
  const context = createDeps(state);

  await handleBridgeNotification(
    {
      method: "thread/tokenUsage/updated",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        tokenUsage: {
          total: {
            totalTokens: 12345,
            inputTokens: 10000,
            cachedInputTokens: 5000,
            outputTokens: 2345,
            reasoningOutputTokens: 345,
          },
          last: {
            totalTokens: 123,
            inputTokens: 100,
            cachedInputTokens: 50,
            outputTokens: 23,
            reasoningOutputTokens: 3,
          },
          modelContextWindow: 20000,
        },
      },
    },
    context.deps
  );

  assert.equal(context.emitCount, 1);
  assert.deepEqual(state.snapshot.messages, []);
  assert.deepEqual(state.snapshot.tokenUsage, {
    totalTokens: 12345,
    inputTokens: 10000,
    outputTokens: 2345,
    reasoningTokens: 345,
    contextPercent: 62,
  });
});

test("handleBridgeNotification surfaces hook run updates", async () => {
  const state = createState();
  const context = createDeps(state);

  await handleBridgeNotification(
    {
      method: "hook/started",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        run: {
          id: "hook-1",
          eventName: "preToolUse",
          handlerType: "command",
          status: "running",
          statusMessage: "检查权限",
          entries: [],
        },
      },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "hook/completed",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        run: {
          id: "hook-1",
          eventName: "preToolUse",
          handlerType: "command",
          status: "completed",
          statusMessage: "允许执行",
          entries: [{ kind: "feedback", text: "ok" }],
        },
      },
    },
    context.deps
  );

  assert.equal(context.emitCount, 2);
  assert.deepEqual(
    state.snapshot.messages.map((message) => message.blocks[0].value),
    ["Hook preToolUse command: 已完成\n允许执行\nfeedback: ok"]
  );
});

test("handleBridgeNotification surfaces auto approval review updates", async () => {
  const state = createState();
  const context = createDeps(state);

  await handleBridgeNotification(
    {
      method: "item/autoApprovalReview/started",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        reviewId: "review-1",
        startedAtMs: 1,
        targetItemId: "cmd-1",
        review: { status: "inProgress", riskLevel: "medium", userAuthorization: null, rationale: "检查命令" },
        action: { type: "command", source: "shell", command: "npm test", cwd: "D:/repo" },
      },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "item/autoApprovalReview/completed",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        reviewId: "review-1",
        startedAtMs: 1,
        completedAtMs: 2,
        targetItemId: "cmd-1",
        decisionSource: "agent",
        review: { status: "approved", riskLevel: "low", userAuthorization: "high", rationale: "风险可控" },
        action: { type: "command", source: "shell", command: "npm test", cwd: "D:/repo" },
      },
    },
    context.deps
  );

  assert.equal(context.emitCount, 2);
  assert.deepEqual(
    state.snapshot.messages.map((message) => message.blocks[0].value),
    ["自动审批审查 已允许: 命令\n风险: 低\n风险可控"]
  );
});

test("handleBridgeNotification surfaces raw response item completions", async () => {
  const state = createState();
  const context = createDeps(state);

  await handleBridgeNotification(
    {
      method: "rawResponseItem/completed",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        item: {
          id: "raw-message-1",
          type: "message",
          role: "assistant",
          content: [{ type: "output_text", text: "原始回复" }],
        },
      },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "rawResponseItem/completed",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        item: {
          type: "reasoning",
          summary: [{ type: "summary_text", text: "摘要" }],
          content: [{ type: "reasoning_text", text: "推理" }],
          encrypted_content: null,
        },
      },
    },
    context.deps
  );

  assert.equal(context.emitCount, 2);
  assert.deepEqual(
    state.snapshot.messages.map((message) => message.blocks[0].value),
    ["原始回复", "摘要\n推理"]
  );
});

test("handleBridgeNotification surfaces realtime thread notifications", async () => {
  const state = createState();
  const context = createDeps(state);

  await handleBridgeNotification(
    {
      method: "thread/realtime/started",
      params: { threadId: "thread-1", realtimeSessionId: "rt-1", version: 1 },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "thread/realtime/transcript/delta",
      params: { threadId: "thread-1", role: "assistant", delta: "你" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "thread/realtime/transcript/delta",
      params: { threadId: "thread-1", role: "assistant", delta: "好" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "thread/realtime/transcript/done",
      params: { threadId: "thread-1", role: "assistant", text: "你好" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "thread/realtime/itemAdded",
      params: {
        threadId: "thread-1",
        item: { type: "agentMessage", id: "rt-item-1", text: "实时 item" },
      },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "thread/realtime/error",
      params: { threadId: "thread-1", message: "network" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "thread/realtime/closed",
      params: { threadId: "thread-1", reason: "done" },
    },
    context.deps
  );

  assert.equal(context.emitCount, 7);
  assert.deepEqual(
    state.snapshot.messages.map((message) => message.blocks[0].value),
    ["实时会话已关闭\ndone", "你好", "实时 item"]
  );
});

test("handleBridgeNotification does not inject global operational notices into thread messages", async () => {
  const state = createState();
  state.snapshot.selectedThreadId = "thread-1";
  const context = createDeps(state);

  await handleBridgeNotification(
    {
      method: "mcpServer/oauthLogin/completed",
      params: { name: "github", success: false, error: "denied" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "mcpServer/startupStatus/updated",
      params: { name: "playwright", status: "ready", error: null },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "skills/changed",
      params: {},
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "account/updated",
      params: { authMode: "chatgpt", planType: "pro" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "account/rateLimits/updated",
      params: {
        rateLimits: {
          limitId: "primary",
          limitName: "主额度",
          primary: { usedPercent: 72, windowDurationMins: 300, resetsAt: null },
          secondary: null,
          credits: null,
          planType: "pro",
          rateLimitReachedType: null,
        },
      },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "remoteControl/status/changed",
      params: { status: "connected", environmentId: "env-1" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "windowsSandbox/setupCompleted",
      params: { mode: "unelevated", success: true, error: null },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "windows/worldWritableWarning",
      params: { samplePaths: ["C:/tmp/a", "C:/tmp/b"], extraCount: 1, failedScan: false },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "account/login/completed",
      params: { loginId: "login-1", success: true, error: null },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "externalAgentConfig/import/completed",
      params: {},
    },
    context.deps
  );

  assert.equal(context.emitCount, 10);
  assert.deepEqual(state.snapshot.messages, []);
  assert.deepEqual(
    context.operationalNotices.map((notice) => notice.text),
    [
      "MCP 授权 github: 失败\ndenied",
      "MCP 服务 playwright: 已就绪",
      "技能列表已变更",
      "账号状态已更新: chatgpt · pro",
      "额度状态 主额度: 72%",
      "远程控制: 已连接\nenv-1",
      "Windows Sandbox unelevated: 已就绪",
      "Windows 权限警告: 发现可被其他用户写入的路径 等 3 项\nC:/tmp/a\nC:/tmp/b",
      "账号登录: 已完成",
      "外部代理配置已导入",
    ]
  );
});

test("handleBridgeNotification does not inject connection scoped operational updates into thread messages", async () => {
  const state = createState();
  state.snapshot.selectedThreadId = "thread-1";
  const context = createDeps(state);

  await handleBridgeNotification(
    {
      method: "app/list/updated",
      params: { data: [{ id: "app-1", name: "App One" }, { id: "app-2", name: "App Two" }] },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "fs/changed",
      params: { watchId: "watch-1", changedPaths: ["D:/repo/a.ts", "D:/repo/b.ts"] },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "fuzzyFileSearch/sessionUpdated",
      params: { sessionId: "search-1", query: "main", files: [{ path: "Main.kt" }] },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "fuzzyFileSearch/sessionCompleted",
      params: { sessionId: "search-1" },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "command/exec/outputDelta",
      params: { processId: "cmd-1", stream: "stdout", deltaBase64: "b2s=", capReached: false },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "process/outputDelta",
      params: { processHandle: "proc-1", stream: "stderr", deltaBase64: "ZXJy", capReached: false },
    },
    context.deps
  );
  await handleBridgeNotification(
    {
      method: "process/exited",
      params: {
        processHandle: "proc-1",
        exitCode: 0,
        stdout: "",
        stderr: "",
        stdoutCapReached: false,
        stderrCapReached: false,
      },
    },
    context.deps
  );

  assert.equal(context.emitCount, 4);
  assert.deepEqual(state.snapshot.messages, []);
  assert.deepEqual(
    context.operationalNotices.map((notice) => notice.text),
    [
      "应用列表已更新: 2 个",
      "文件变更: 2 项\nD:/repo/a.ts\nD:/repo/b.ts",
      "文件搜索: main · 1 个结果",
      "文件搜索已完成",
    ]
  );
});

test("handleBridgeNotification refreshes catalog for thread lifecycle notifications", async () => {
  const state = createState();
  const context = createDeps(state);
  const hydrated = [];
  context.deps.hydrateThreads = async () => {
    hydrated.push("hydrate");
  };

  for (const method of [
    "thread/started",
    "thread/archived",
    "thread/unarchived",
    "thread/name/updated",
    "thread/closed",
  ]) {
    await handleBridgeNotification(
      {
        method,
        params: { threadId: "thread-1" },
      },
      context.deps
    );
  }

  assert.equal(hydrated.length, 5);
});

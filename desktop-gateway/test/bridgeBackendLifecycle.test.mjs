import assert from "node:assert/strict";
import test from "node:test";
import { handleBridgeBackendServerRequest } from "../dist/bridge/bridgeBackendLifecycle.js";

function createState() {
  return {
    summary: { id: "thread-1", title: "thread-1", preview: "preview", status: "idle", archived: false },
    thread: null,
    isSubscribed: false,
    lastActivityAtMs: 1000,
    historyWindow: 24,
    currentTurnId: null,
    activeAssistantMessageId: null,
    liveAssistantItemId: null,
    transientOperation: null,
    pendingApproval: null,
    stopRequested: false,
    isFinalizing: false,
    model: null,
    instructionSources: [],
    approvalPolicy: null,
    approvalsReviewer: null,
    sandbox: null,
    reasoningEffort: null,
    snapshot: {
      threads: [{ id: "thread-1", title: "thread-1", preview: "preview", status: "idle", archived: false }],
      selectedThreadId: "thread-1",
      messages: [],
      hasMoreHistory: false,
      pendingApproval: null,
      chips: [],
    files: [],
      slashCommands: [],
      cwd: "D:/Projects/Test",
      permissionSummary: "",
      isGenerating: false,
    },
  };
}

test("handleBridgeBackendServerRequest stores pending approval and emits change", () => {
  const state = createState();
  const threads = new Map([["thread-1", state]]);
  const statuses = [];
  let emitCount = 0;

  handleBridgeBackendServerRequest(
    {
      id: "request-1",
      method: "item/commandExecution/requestApproval",
      params: {
        threadId: "thread-1",
        reason: "需要执行命令",
        command: "dir",
      },
    },
    {
      threads,
      respondToServerRequest: () => {},
      respondToServerRequestError: () => {},
      emitChanged: () => {
        emitCount += 1;
      },
      hydrateThreads: async () => {},
      refreshThread: async () => {},
      ensureActiveAssistantMessage: () => "assistant-live",
      updateSummaryStatus: (_threadId, status) => {
        statuses.push(status);
      },
    }
  );

  assert.equal(state.pendingApproval?.requestId, "request-1");
  assert.equal(state.pendingApproval?.kind, "command");
  assert.equal(state.pendingApproval?.text, "需要执行命令\ndir");
  assert.equal(state.snapshot.pendingApproval, "需要执行命令\ndir");
  assert.deepEqual(statuses, ["needs_approval"]);
  assert.equal(emitCount, 1);
});

test("handleBridgeBackendServerRequest stores MCP elicitation approval", () => {
  const state = createState();
  const threads = new Map([["thread-1", state]]);

  handleBridgeBackendServerRequest(
    {
      id: "request-2",
      method: "mcpServer/elicitation/request",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        serverName: "calendar",
        mode: "url",
        message: "需要打开授权页面",
        url: "https://example.test/auth",
        elicitationId: "elicitation-1",
      },
    },
    {
      threads,
      respondToServerRequest: () => {},
      respondToServerRequestError: () => {},
      emitChanged: () => {},
      hydrateThreads: async () => {},
      refreshThread: async () => {},
      ensureActiveAssistantMessage: () => "assistant-live",
      updateSummaryStatus: () => {},
    }
  );

  assert.equal(state.pendingApproval?.requestId, "request-2");
  assert.equal(state.pendingApproval?.kind, "mcpElicitation");
  assert.equal(
    state.pendingApproval?.text,
    "MCP 追问请求\nserver: calendar\n需要打开授权页面\nhttps://example.test/auth"
  );
});

test("handleBridgeBackendServerRequest stores requested permissions", () => {
  const state = createState();
  const threads = new Map([["thread-1", state]]);

  handleBridgeBackendServerRequest(
    {
      id: "request-permissions",
      method: "item/permissions/requestApproval",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        itemId: "permissions-1",
        startedAtMs: 1,
        cwd: "D:/repo",
        reason: "需要网络",
        permissions: {
          fileSystem: null,
          network: { enabled: true },
        },
      },
    },
    {
      threads,
      respondToServerRequest: () => {},
      respondToServerRequestError: () => {},
      emitChanged: () => {},
      hydrateThreads: async () => {},
      refreshThread: async () => {},
      ensureActiveAssistantMessage: () => "assistant-live",
      updateSummaryStatus: () => {},
    }
  );

  assert.equal(state.pendingApproval?.kind, "permissions");
  assert.deepEqual(state.pendingApproval?.permissions, {
    fileSystem: null,
    network: { enabled: true },
  });
});

test("handleBridgeBackendServerRequest stores tool user input questions", () => {
  const state = createState();
  const threads = new Map([["thread-1", state]]);

  handleBridgeBackendServerRequest(
    {
      id: "request-3",
      method: "item/tool/requestUserInput",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        itemId: "tool-1",
        questions: [
          {
            id: "permission",
            header: "授权",
            question: "允许继续？",
            isOther: false,
            isSecret: false,
            options: [{ label: "允许", description: "继续执行" }],
          },
        ],
      },
    },
    {
      threads,
      respondToServerRequest: () => {},
      respondToServerRequestError: () => {},
      emitChanged: () => {},
      hydrateThreads: async () => {},
      refreshThread: async () => {},
      ensureActiveAssistantMessage: () => "assistant-live",
      updateSummaryStatus: () => {},
    }
  );

  assert.equal(state.pendingApproval?.requestId, "request-3");
  assert.equal(state.pendingApproval?.kind, "toolUserInput");
  assert.equal(state.pendingApproval?.text, "工具追问请求\n授权\n允许继续？\n允许: 继续执行");
  assert.deepEqual(state.pendingApproval?.questions, [
    { id: "permission", options: [{ label: "允许", description: "继续执行" }] },
  ]);
});

test("handleBridgeBackendServerRequest resolves unsupported dynamic tool calls", () => {
  const state = createState();
  const threads = new Map([["thread-1", state]]);
  const responses = [];

  handleBridgeBackendServerRequest(
    {
      id: "request-4",
      method: "item/tool/call",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        callId: "call-1",
        namespace: null,
        tool: "unsupported",
        arguments: {},
      },
    },
    {
      threads,
      respondToServerRequest: (id, result) => responses.push([id, result]),
      respondToServerRequestError: () => {},
      emitChanged: () => {},
      hydrateThreads: async () => {},
      refreshThread: async () => {},
      ensureActiveAssistantMessage: () => "assistant-live",
      updateSummaryStatus: () => {},
    }
  );

  assert.deepEqual(responses, [
    [
      "request-4",
      {
        contentItems: [
          {
            type: "inputText",
            text: "Mobile gateway does not execute dynamic tool calls.",
          },
        ],
        success: false,
      },
    ],
  ]);
  assert.equal(state.pendingApproval, null);
});

test("handleBridgeBackendServerRequest rejects auth token refresh requests", () => {
  const state = createState();
  const threads = new Map([["thread-1", state]]);
  const errors = [];

  handleBridgeBackendServerRequest(
    {
      id: "request-5",
      method: "account/chatgptAuthTokens/refresh",
      params: { reason: "unauthorized" },
    },
    {
      threads,
      respondToServerRequest: () => {},
      respondToServerRequestError: (id, code, message) => errors.push([id, code, message]),
      emitChanged: () => {},
      hydrateThreads: async () => {},
      refreshThread: async () => {},
      ensureActiveAssistantMessage: () => "assistant-live",
      updateSummaryStatus: () => {},
    }
  );

  assert.deepEqual(errors, [
    ["request-5", -32001, "Mobile gateway cannot refresh ChatGPT auth tokens"],
  ]);
  assert.equal(state.pendingApproval, null);
});

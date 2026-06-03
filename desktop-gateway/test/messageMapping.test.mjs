import assert from "node:assert/strict";
import test from "node:test";
import {
  buildCommandExecutionBlocks,
  mapItemToMessages,
} from "../dist/bridge/messageMapping.js";

test("command execution uses desktop-like running and completed summaries", () => {
  assert.equal(
    buildCommandExecutionBlocks({
      type: "commandExecution",
      id: "cmd-running",
      command: "npm run build",
      status: "inProgress",
    })[0].value,
    "正在运行 npm run build"
  );

  assert.equal(
    buildCommandExecutionBlocks({
      type: "commandExecution",
      id: "cmd-done",
      command: "npm test",
      status: "completed",
      exitCode: 0,
    })[0].value,
    "已运行命令"
  );
});

test("tool and web search items map to process status blocks", () => {
  const messages = [
    ...mapItemToMessages({
      type: "dynamicToolCall",
      id: "tool-1",
      namespace: "web",
      tool: "search",
      status: "inProgress",
    }),
    ...mapItemToMessages({
      type: "webSearch",
      id: "search-1",
      query: "深圳天气",
      action: { status: "completed" },
    }),
  ];

  assert.deepEqual(
    messages.map((message) => message.blocks[0]),
    [
      { kind: "toolCall", value: "正在调用工具 web/search" },
      { kind: "webSearch", value: "已搜索网页: 深圳天气" },
    ]
  );
});

test("plan and context compaction remain process messages", () => {
  assert.deepEqual(
    mapItemToMessages({
      type: "plan",
      id: "plan-1",
      text: "1. 检查\n2. 修改",
    })[0].blocks,
    [{ kind: "plan", value: "1. 检查\n2. 修改" }]
  );

  assert.deepEqual(
    mapItemToMessages({
      type: "contextCompaction",
      id: "compact-1",
    })[0].blocks,
    [{ kind: "context", value: "上下文压缩" }]
  );
});

test("empty reasoning maps to active thinking text", () => {
  const [message] = mapItemToMessages({
    type: "reasoning",
    id: "reasoning-1",
    summary: [],
    content: [],
  });

  assert.deepEqual(message.blocks, [{ kind: "reasoning", value: "正在思考" }]);
});

test("agent commentary phase maps to process commentary block", () => {
  assert.deepEqual(
    mapItemToMessages({
      type: "agentMessage",
      id: "assistant-progress",
      text: "我先检查分组逻辑。",
      phase: "commentary",
    })[0].blocks,
    [{ kind: "commentary", value: "我先检查分组逻辑。" }]
  );
});

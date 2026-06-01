import type { AppServerThreadItem } from "../appServerTypes.js";
import type { GatewayBlockPayload, GatewayMessagePayload } from "../protocol.js";
import { buildFileChangeBlocks } from "./fileChanges.js";
import { asHookPromptFragments, asString, asStringArray, asTextInputEntries, flattenHookPrompt } from "./appServerValues.js";

export function mapItemToMessages(item: AppServerThreadItem, cwd = ""): GatewayMessagePayload[] {
  switch (item.type) {
    case "userMessage":
      return [
        {
          id: item.id,
          role: "user",
          blocks: asTextInputEntries(item.content)
            .filter((entry) => entry.type === "text")
            .map((entry) => ({ kind: "text", value: entry.text })),
        },
      ];
    case "agentMessage":
      return [{ id: item.id, role: "assistant", blocks: [{ kind: "text", value: asString(item.text) }] }];
    case "reasoning":
      return [
        {
          id: item.id,
          role: "assistant",
          blocks: [
            {
              kind: "reasoning",
              value: [...asStringArray(item.summary), ...asStringArray(item.content)].join("\n") || "思考中",
            },
          ],
        },
      ];
    case "commandExecution":
      return [
        {
          id: item.id,
          role: "assistant",
          blocks: buildCommandExecutionBlocks(item as Extract<AppServerThreadItem, { type: "commandExecution" }>),
        },
      ];
    case "fileChange": {
      const fileChangeItem = item as Extract<AppServerThreadItem, { type: "fileChange" }>;
      return [
        {
          id: fileChangeItem.id,
          role: "assistant",
          blocks: buildFileChangeBlocks(fileChangeItem.changes, fileChangeItem.status, cwd),
        },
      ];
    }
    case "plan":
      return [{ id: item.id, role: "assistant", blocks: [{ kind: "text", value: asString(item.text) }] }];
    case "mcpToolCall":
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "text", value: `MCP: ${item.server}/${item.tool} · ${item.status}` }],
      }];
    case "dynamicToolCall":
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "text", value: `工具: ${item.namespace ? `${item.namespace}/` : ""}${item.tool} · ${item.status}` }],
      }];
    case "webSearch":
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "text", value: `检索: ${item.query}` }],
      }];
    case "imageView":
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "text", value: `查看图片: ${item.path}` }],
      }];
    case "imageGeneration":
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "text", value: `生成图片 ${item.status}: ${item.savedPath ?? item.result}` }],
      }];
    case "collabAgentToolCall":
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "text", value: `协作代理: ${item.tool} · ${item.status}` }],
      }];
    case "hookPrompt":
      return [{
        id: item.id,
        role: "system",
        blocks: [{ kind: "status", value: flattenHookPrompt(asHookPromptFragments(item.fragments)) || "Hook 提示" }],
      }];
    case "enteredReviewMode":
      return [{ id: item.id, role: "system", blocks: [{ kind: "status", value: `进入 review: ${asString(item.review)}` }] }];
    case "exitedReviewMode":
      return [{ id: item.id, role: "system", blocks: [{ kind: "status", value: `退出 review: ${asString(item.review)}` }] }];
    case "contextCompaction":
      return [];
    default:
      return [];
  }
}

export function buildCommandExecutionBlocks(item: Extract<AppServerThreadItem, { type: "commandExecution" }>): GatewayBlockPayload[] {
  return [
    {
      kind: "commandSummary",
      value: Array.isArray(item.commandActions) && item.commandActions.length > 0
        ? `已运行 ${item.commandActions.length} 条命令`
        : asString(item.status) === "inProgress"
          ? "命令执行中"
          : `命令 ${asString(item.status)}`,
    },
    { kind: "commandMeta", value: `命令: ${asString(item.command)}` },
    { kind: "commandMeta", value: `结果: ${formatCommandResult(item)}` },
    { kind: "code" as const, language: "shell", value: asString(item.aggregatedOutput, asString(item.status)) },
  ];
}

function formatCommandResult(item: {
  [key: string]: unknown;
  status?: string;
  exitCode?: number | null;
  durationMs?: number | null;
}): string {
  const parts: string[] = [];
  if (typeof item.exitCode === "number") {
    parts.push(`退出码 ${item.exitCode}`);
  }
  if (typeof item.durationMs === "number") {
    parts.push(`${Math.max(0, Math.round(item.durationMs / 1000))}s`);
  }
  if (parts.length > 0) {
    return parts.join(" · ");
  }
  return item.status ?? "unknown";
}

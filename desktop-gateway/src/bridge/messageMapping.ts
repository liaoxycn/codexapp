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
    case "reasoning": {
      const reasoningText = [...asStringArray(item.summary), ...asStringArray(item.content)].join("\n");
      return [
        {
          id: item.id,
          role: "assistant",
          blocks: [
            {
              kind: "reasoning",
              value: reasoningText || "正在思考",
            },
          ],
        },
      ];
    }
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
    case "mcpToolCall": {
      const status = asString(item.status);
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "status", value: formatToolStatus("MCP", `${asString(item.server)}/${asString(item.tool)}`, status) }],
      }];
    }
    case "dynamicToolCall": {
      const status = asString(item.status);
      const name = `${item.namespace ? `${asString(item.namespace)}/` : ""}${asString(item.tool)}`;
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "status", value: formatToolStatus("工具", name, status) }],
      }];
    }
    case "webSearch": {
      const action = typeof item.action === "object" && item.action != null ? item.action as Record<string, unknown> : {};
      const status = asString(action.status, asString(action.type, "completed"));
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "status", value: formatSearchStatus(asString(item.query), status) }],
      }];
    }
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
      value: formatCommandSummary(item),
    },
    { kind: "commandMeta", value: `命令: ${formatCommandForDisplay(asString(item.command))}` },
    { kind: "commandMeta", value: `结果: ${formatCommandResult(item)}` },
    { kind: "code" as const, language: "shell", value: asString(item.aggregatedOutput, asString(item.status)) },
  ];
}

function formatCommandSummary(item: Extract<AppServerThreadItem, { type: "commandExecution" }>): string {
  const command = formatCommandForDisplay(asString(item.command));
  const actionCount = Array.isArray(item.commandActions) ? item.commandActions.length : 0;
  const status = normalizeStatus(asString(item.status));
  if (status === "running") {
    return command ? `正在运行 ${command}` : "正在运行命令";
  }
  if (actionCount > 0) {
    return `已运行 ${actionCount} 条命令`;
  }
  if (status === "completed") {
    return "已运行命令";
  }
  if (status === "failed") {
    return "命令执行失败";
  }
  return "命令已更新";
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

function formatCommandForDisplay(command: string): string {
  const singleLine = command.trim().replace(/\s+/g, " ");
  if (singleLine.length <= 80) {
    return singleLine;
  }
  return `${singleLine.slice(0, 77)}...`;
}

function formatToolStatus(prefix: string, name: string, status: string): string {
  const normalized = normalizeStatus(status);
  const cleanName = name.trim() || "未知工具";
  if (normalized === "running") {
    return `正在调用${prefix} ${cleanName}`;
  }
  if (normalized === "completed") {
    return `已调用${prefix} ${cleanName}`;
  }
  if (normalized === "failed") {
    return `${prefix} ${cleanName} 调用失败`;
  }
  return `${prefix} ${cleanName} · ${status || "已更新"}`;
}

function formatSearchStatus(query: string, status: string): string {
  const normalized = normalizeStatus(status);
  const cleanQuery = query.trim();
  if (normalized === "running") {
    return cleanQuery ? `正在搜索网页: ${cleanQuery}` : "正在搜索网页";
  }
  if (normalized === "completed") {
    return cleanQuery ? `已搜索网页: ${cleanQuery}` : "已搜索网页";
  }
  if (normalized === "failed") {
    return cleanQuery ? `网页搜索失败: ${cleanQuery}` : "网页搜索失败";
  }
  return cleanQuery ? `网页搜索 ${status || "已更新"}: ${cleanQuery}` : `网页搜索 ${status || "已更新"}`;
}

function normalizeStatus(status: string): "running" | "completed" | "failed" | "other" {
  switch (status.toLowerCase()) {
    case "inprogress":
    case "in_progress":
    case "running":
    case "started":
    case "pending":
      return "running";
    case "completed":
    case "complete":
    case "done":
    case "succeeded":
    case "success":
      return "completed";
    case "failed":
    case "error":
    case "errored":
    case "cancelled":
    case "canceled":
      return "failed";
    default:
      return "other";
  }
}

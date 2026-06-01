import type { JsonRpcServerRequest } from "../appServerTypes.js";
import type { PendingApproval } from "./types.js";

type SetPendingApproval = (threadId: string, approval: PendingApproval) => void;
type ServerResponder = {
  respond(id: string | number, result: unknown): void;
  respondError(id: string | number, code: number, message: string, data?: unknown): void;
};

export function applyServerRequest(
  request: JsonRpcServerRequest,
  setPendingApproval: SetPendingApproval,
  responder?: ServerResponder
): void {
  switch (request.method) {
    case "item/commandExecution/requestApproval": {
      const params = request.params as {
        threadId: string;
        command?: string | null;
        reason?: string | null;
        cwd?: string | null;
      };
      setPendingApproval(params.threadId, {
        requestId: request.id,
        kind: "command",
        text: [params.reason, params.command, params.cwd].filter(Boolean).join("\n") || "命令执行请求审批",
      });
      return;
    }
    case "item/fileChange/requestApproval": {
      const params = request.params as {
        threadId: string;
        reason?: string | null;
        grantRoot?: string | null;
      };
      setPendingApproval(params.threadId, {
        requestId: request.id,
        kind: "file",
        text: [params.reason, params.grantRoot].filter(Boolean).join("\n") || "文件改动请求审批",
      });
      return;
    }
    case "item/permissions/requestApproval": {
      const params = request.params as {
        threadId: string;
        reason?: string | null;
        cwd?: string | null;
        permissions?: {
          fileSystem?: unknown | null;
          network?: unknown | null;
        } | null;
      };
      setPendingApproval(params.threadId, {
        requestId: request.id,
        kind: "permissions",
        text: [params.reason, params.cwd].filter(Boolean).join("\n") || "权限请求审批",
        permissions: params.permissions ?? undefined,
      });
      return;
    }
    case "mcpServer/elicitation/request": {
      const params = request.params as {
        threadId: string;
        serverName?: string | null;
        message?: string | null;
        mode?: string | null;
        url?: string | null;
      };
      setPendingApproval(params.threadId, {
        requestId: request.id,
        kind: "mcpElicitation",
        text:
          [
            "MCP 追问请求",
            params.serverName ? `server: ${params.serverName}` : null,
            params.message,
            params.mode === "url" && params.url ? params.url : null,
          ]
            .filter(Boolean)
            .join("\n") || "MCP 追问请求",
      });
      return;
    }
    case "item/tool/requestUserInput": {
      const params = request.params as {
        threadId: string;
        questions?: Array<{
          id: string;
          header?: string | null;
          question?: string | null;
          options?: Array<{ label: string; description?: string | null }> | null;
        }> | null;
      };
      const questionLines = (params.questions ?? []).flatMap((question) => [
        question.header || null,
        question.question || null,
        ...(question.options ?? []).map((option) =>
          option.description ? `${option.label}: ${option.description}` : option.label
        ),
      ]);
      setPendingApproval(params.threadId, {
        requestId: request.id,
        kind: "toolUserInput",
        text: ["工具追问请求", ...questionLines].filter(Boolean).join("\n") || "工具追问请求",
        questions: (params.questions ?? []).map((question) => ({
          id: question.id,
          options: question.options?.map((option) => ({
            label: option.label,
            description: option.description ?? undefined,
          })),
        })),
      });
      return;
    }
    case "execCommandApproval": {
      const params = request.params as {
        conversationId: string;
        reason?: string | null;
        command?: string[] | null;
        cwd?: string | null;
      };
      setPendingApproval(params.conversationId, {
        requestId: request.id,
        kind: "legacyCommand",
        text:
          [params.reason, params.command?.join(" "), params.cwd].filter(Boolean).join("\n") ||
          "命令执行请求审批",
      });
      return;
    }
    case "applyPatchApproval": {
      const params = request.params as {
        conversationId: string;
        reason?: string | null;
        grantRoot?: string | null;
        fileChanges?: Record<string, unknown> | null;
      };
      setPendingApproval(params.conversationId, {
        requestId: request.id,
        kind: "legacyPatch",
        text:
          [
            params.reason,
            params.grantRoot,
            ...Object.keys(params.fileChanges ?? {}).map((path) => `file: ${path}`),
          ]
            .filter(Boolean)
            .join("\n") || "文件改动请求审批",
      });
      return;
    }
    case "item/tool/call": {
      responder?.respond(request.id, {
        contentItems: [
          {
            type: "inputText",
            text: "Mobile gateway does not execute dynamic tool calls.",
          },
        ],
        success: false,
      });
      return;
    }
    case "account/chatgptAuthTokens/refresh": {
      responder?.respondError(
        request.id,
        -32001,
        "Mobile gateway cannot refresh ChatGPT auth tokens"
      );
      return;
    }
    default:
      return;
  }
}

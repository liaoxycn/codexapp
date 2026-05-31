import type { JsonRpcServerRequest } from "../appServerTypes.js";
import type { PendingApproval } from "./types.js";

type SetPendingApproval = (threadId: string, approval: PendingApproval) => void;

export function applyServerRequest(
  request: JsonRpcServerRequest,
  setPendingApproval: SetPendingApproval
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
      };
      setPendingApproval(params.threadId, {
        requestId: request.id,
        kind: "permissions",
        text: [params.reason, params.cwd].filter(Boolean).join("\n") || "权限请求审批",
      });
      return;
    }
    default:
      return;
  }
}

import type {
  AppServerApprovalPolicy,
  AppServerSandboxPolicy,
  AppServerThread,
  ThreadResumeResult,
} from "../appServerTypes.js";
import { isThreadActivelyGenerating } from "../threadState.js";
import type { ClientSnapshot, GatewayChipPayload, GatewayMessagePayload, GatewayThreadPayload } from "../protocol.js";
import type { PendingApproval, ThreadRuntimeState } from "./types.js";
import { INITIAL_HISTORY_WINDOW } from "./types.js";
export {
  buildVisibleThreadSummaries,
  dedupeSummaries,
  getThreadLastActivityAtMs,
  mapThreadToSummary,
  touchThreadActivity,
} from "./threadSummaries.js";

export function mapThreadToSnapshot(
  thread: AppServerThread,
  threads: GatewayThreadPayload[],
  selectedThreadId: string,
  resume: Pick<ThreadResumeResult, "model" | "approvalPolicy" | "approvalsReviewer" | "sandbox" | "reasoningEffort" | "instructionSources"> | null,
  allMessages: GatewayMessagePayload[]
): ClientSnapshot {
  const threadIsGenerating = isThreadActivelyGenerating(thread);
  return {
    threads,
    selectedThreadId,
    messages: allMessages,
    hasMoreHistory: allMessages.length > INITIAL_HISTORY_WINDOW,
    pendingApproval: null,
    chips: buildChips(thread, resume),
    slashCommands: ["/compact  压缩上下文", "/rollback  回滚上轮", "! ls  运行 shell 命令"],
    cwd: thread.cwd,
    permissionSummary: buildPermissionSummary(resume?.approvalPolicy ?? null, resume?.sandbox ?? null),
    isGenerating: threadIsGenerating,
  };
}

export function buildApprovalResponse(approval: PendingApproval, allow: boolean): unknown {
  const { kind } = approval;
  if (kind === "command" || kind === "file") {
    return { decision: allow ? "accept" : "decline" };
  }
  if (kind === "legacyCommand" || kind === "legacyPatch") {
    return { decision: allow ? "approved" : "denied" };
  }
  if (kind === "mcpElicitation") {
    return {
      action: allow ? "accept" : "decline",
      content: null,
      _meta: null,
    };
  }
  if (kind === "toolUserInput") {
    if (!allow) {
      return { answers: {} };
    }
    return {
      answers: Object.fromEntries(
        (approval.questions ?? []).map((question) => [
          question.id,
          { answers: [question.options?.[0]?.label ?? ""] },
        ])
      ),
    };
  }
  if (kind === "permissions") {
    return {
      permissions: allow
        ? {
            fileSystem: approval.permissions?.fileSystem ?? null,
            network: approval.permissions?.network ?? null,
          }
        : {
            fileSystem: null,
            network: null,
          },
      scope: "turn",
    };
  }
  return {
    permissions: {
      fileSystem: null,
      network: null,
    },
    scope: "turn",
  };
}

export function toResumeMetadata(
  state: ThreadRuntimeState | undefined
): Pick<ThreadResumeResult, "model" | "approvalPolicy" | "approvalsReviewer" | "sandbox" | "reasoningEffort" | "instructionSources"> | null {
  if (!state) {
    return null;
  }
  return {
    model: state.model ?? state.thread?.modelProvider ?? "openai",
    approvalPolicy: state.approvalPolicy ?? "never",
    approvalsReviewer: (state.approvalsReviewer as ThreadResumeResult["approvalsReviewer"]) ?? "user",
    sandbox: state.sandbox ?? { type: "dangerFullAccess" },
    reasoningEffort: state.reasoningEffort,
    instructionSources: state.instructionSources,
  };
}

export function buildPermissionSummary(
  approvalPolicy: AppServerApprovalPolicy | null,
  sandbox: AppServerSandboxPolicy | null
): string {
  const sandboxLabel = sandbox ? mapSandboxLabel(sandbox) : "sandbox:unknown";
  const approvalLabel = approvalPolicy ? mapApprovalLabel(approvalPolicy) : "approval:unknown";
  return `${sandboxLabel} · ${approvalLabel}`;
}

export function isThreadNotMaterializedError(error: unknown): boolean {
  return error instanceof Error && error.message.includes("not materialized yet");
}

export function isNoRolloutFoundError(error: unknown): boolean {
  return error instanceof Error && error.message.includes("no rollout found for thread id");
}

export function emptySnapshot(): ClientSnapshot {
  return {
    threads: [],
    selectedThreadId: "",
    messages: [],
    hasMoreHistory: false,
    pendingApproval: null,
    chips: [],
    slashCommands: [],
    cwd: "",
    permissionSummary: "",
    isGenerating: false,
  };
}

export function trimMessagesToWindow(messages: GatewayMessagePayload[], historyWindow: number): GatewayMessagePayload[] {
  if (historyWindow <= 0 || messages.length <= historyWindow) {
    return messages;
  }
  return messages.slice(messages.length - historyWindow);
}

function buildChips(
  thread: AppServerThread,
  resume: Pick<ThreadResumeResult, "model" | "reasoningEffort" | "instructionSources"> | null
): GatewayChipPayload[] {
  const chips: GatewayChipPayload[] = [];
  chips.push({ label: resume?.model || thread.modelProvider || "openai", icon: "context" });
  if (resume?.reasoningEffort) {
    chips.push({ label: `reasoning:${resume.reasoningEffort}`, icon: "context" });
  }
  const fileChip = resume?.instructionSources?.[0] ?? thread.cwd;
  chips.push({ label: shrinkPathLabel(fileChip), icon: "file", path: fileChip });
  return chips.slice(0, 3);
}

function mapSandboxLabel(sandbox: AppServerSandboxPolicy): string {
  switch (sandbox.type) {
    case "dangerFullAccess":
      return "danger-full-access";
    case "readOnly":
      return sandbox.networkAccess ? "read-only+net" : "read-only";
    case "externalSandbox":
      return sandbox.networkAccess === "enabled" ? "external+net" : "external";
    case "workspaceWrite":
      return sandbox.networkAccess ? "workspace-write+net" : "workspace-write";
    default:
      return "sandbox";
  }
}

function mapApprovalLabel(policy: AppServerApprovalPolicy): string {
  if (typeof policy === "string") {
    return policy;
  }
  return "granular";
}

function shrinkPathLabel(value: string): string {
  const normalized = value.replaceAll("\\", "/");
  const segments = normalized.split("/").filter(Boolean);
  if (segments.length === 0) {
    return value;
  }
  return segments.slice(-2).join("/");
}

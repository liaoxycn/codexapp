import type {
  AppServerApprovalPolicy,
  AppServerSandboxPolicy,
  AppServerThread,
  ThreadResumeResult,
} from "../appServerTypes.js";
import { isThreadActivelyGenerating } from "../threadState.js";
import {
  emptyConfigOptions,
  type ClientSnapshot,
  type GatewayChipPayload,
  type GatewayMessagePayload,
  type GatewaySessionConfigPayload,
  type GatewayThreadPayload,
} from "../protocol.js";
import { listProjectFiles } from "./projectFiles.js";
import type { PendingApproval, ThreadRuntimeState } from "./types.js";
import { INITIAL_HISTORY_WINDOW } from "./types.js";
export {
  buildVisibleThreadSummaries,
  dedupeSummaries,
  getThreadLastActivityAtMs,
  isDesktopMainListThread,
  mapThreadToSummary,
  touchThreadActivity,
} from "./threadSummaries.js";

type SnapshotResumeMetadata = {
  model: string | null;
  modelProvider: string | null;
  approvalPolicy: AppServerApprovalPolicy | null;
  approvalsReviewer: ThreadResumeResult["approvalsReviewer"] | string | null;
  sandbox: AppServerSandboxPolicy | null;
  reasoningEffort: string | null;
  instructionSources: string[];
};

export function mapThreadToSnapshot(
  thread: AppServerThread,
  threads: GatewayThreadPayload[],
  selectedThreadId: string,
  resume: SnapshotResumeMetadata | null,
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
    files: listProjectFiles(thread.cwd),
    slashCommands: ["/compact  压缩上下文", "/rollback  回滚上轮", "! ls  运行 shell 命令"],
    cwd: thread.cwd,
    permissionSummary: buildPermissionSummary(resume?.approvalPolicy ?? null, resume?.sandbox ?? null),
    sessionConfig: buildSessionConfig(thread, resume),
    configOptions: emptyConfigOptions(),
    operationalNotices: [],
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
): SnapshotResumeMetadata | null {
  if (!state) {
    return null;
  }
  return {
    model: state.model ?? null,
    modelProvider: state.modelProvider ?? state.thread?.modelProvider ?? null,
    approvalPolicy: state.approvalPolicy ?? null,
    approvalsReviewer: (state.approvalsReviewer as ThreadResumeResult["approvalsReviewer"]) ?? null,
    sandbox: state.sandbox ?? null,
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

export function buildSessionConfig(
  thread: AppServerThread,
  resume: SnapshotResumeMetadata | null
): GatewaySessionConfigPayload {
  return {
    permissionMode: resume?.sandbox ? mapSandboxLabel(resume.sandbox) : undefined,
    provider: resume?.modelProvider || thread.modelProvider || undefined,
    model: resume?.model || undefined,
    reasoningEffort: resume?.reasoningEffort || undefined,
  };
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
    files: [],
    slashCommands: [],
    cwd: "",
    permissionSummary: "",
    sessionConfig: {},
    configOptions: emptyConfigOptions(),
    operationalNotices: [],
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
  resume: SnapshotResumeMetadata | null
): GatewayChipPayload[] {
  const chips: GatewayChipPayload[] = [];
  const modelChip = resume?.model || thread.modelProvider;
  if (modelChip) {
    chips.push({ label: modelChip, icon: "context" });
  }
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

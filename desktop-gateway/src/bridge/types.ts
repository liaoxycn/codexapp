import type { AppServerApprovalPolicy, AppServerSandboxPolicy, AppServerThread } from "../appServerTypes.js";
import type { ClientSnapshot, GatewayThreadPayload } from "../protocol.js";

export const INITIAL_HISTORY_WINDOW = 24;
export const HISTORY_WINDOW_STEP = 24;

export interface PendingApproval {
  requestId?: string | number;
  kind:
    | "command"
    | "file"
    | "permissions"
    | "mcpElicitation"
    | "toolUserInput"
    | "legacyCommand"
    | "legacyPatch"
    | "gatewayShell";
  text: string;
  command?: string;

  permissions?: {
    fileSystem?: unknown | null;
    network?: unknown | null;
  };

  questions?: Array<{
    id: string;
    options?: Array<{ label: string; description?: string }> | null;
  }>;
}

export interface ThreadRuntimeState {
  summary: GatewayThreadPayload;
  thread: AppServerThread | null;
  isSubscribed: boolean;
  lastActivityAtMs: number;
  historyWindow: number;
  currentTurnId: string | null;
  activeAssistantMessageId: string | null;
  liveAssistantItemId: string | null;
  transientOperation: "compact" | "rollback" | "shell" | null;
  pendingApproval: PendingApproval | null;
  stopRequested: boolean;
  isFinalizing: boolean;
  model: string | null;
  instructionSources: string[];
  approvalPolicy: AppServerApprovalPolicy | null;
  approvalsReviewer: string | null;
  sandbox: AppServerSandboxPolicy | null;
  reasoningEffort: string | null;
  snapshot: ClientSnapshot;
}

export type ThreadLifecycleStatus = "running" | "idle" | "failed" | "needs_approval";


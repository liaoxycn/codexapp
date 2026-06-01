import type { AppServerApprovalPolicy, AppServerSandboxPolicy, AppServerThread } from "../appServerTypes.js";
import type { ClientSnapshot, GatewayThreadPayload } from "../protocol.js";

export const INITIAL_HISTORY_WINDOW = 24;
export const HISTORY_WINDOW_STEP = 24;

export interface PendingApproval {
  requestId?: string | number;
  kind: "command" | "file" | "permissions" | "gatewayShell";
  text: string;
  command?: string;
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


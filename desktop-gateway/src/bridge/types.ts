import type { AppServerApprovalPolicy, AppServerSandboxPolicy, AppServerThread } from "../appServerTypes.js";
import type { ClientSnapshot, GatewayThreadPayload } from "../protocol.js";

export const INITIAL_HISTORY_WINDOW = 80;
export const HISTORY_WINDOW_STEP = 80;
export const RUNNING_SIGNAL_LEASE_MS = 90_000;
export const TURN_COMPLETION_GRACE_MS = 8_000;

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

export interface ThreadTokenUsage {
  totalTokens: number;
  inputTokens: number;
  outputTokens: number;
  reasoningTokens: number;
  contextPercent?: number;
}

export interface GatewayShellSession {
  processId: string;
  messageId: string;
  command: string;
  turnId: string;
  startedAtMs: number;
}

export interface ThreadRuntimeState {
  summary: GatewayThreadPayload;
  thread: AppServerThread | null;
  isSubscribed: boolean;
  isLocalCatalogEntry: boolean;
  lastActivityAtMs: number;
  historyWindow: number;
  currentTurnId: string | null;
  currentTurnStartedAtMs: number | null;
  activeAssistantMessageId: string | null;
  liveAssistantItemId: string | null;
  transientOperation: "compact" | "rollback" | "shell" | null;
  gatewayShellSession: GatewayShellSession | null;
  pendingApproval: PendingApproval | null;
  stopRequested: boolean;
  isFinalizing: boolean;
  runningSignalUntilMs: number;
  turnCompletionGraceUntilMs: number;
  runtimeStatus: ThreadLifecycleStatus;
  activeTurnIds: string[];
  activeHookIds: string[];
  runtimeStatusSeq: number;
  runtimeTerminalSeq: number;
  model: string | null;
  modelProvider: string | null;
  instructionSources: string[];
  approvalPolicy: AppServerApprovalPolicy | null;
  approvalsReviewer: string | null;
  sandbox: AppServerSandboxPolicy | null;
  reasoningEffort: string | null;
  tokenUsage: ThreadTokenUsage | null;
  snapshot: ClientSnapshot;
}

export type ThreadLifecycleStatus = "running" | "idle" | "failed" | "needs_approval";


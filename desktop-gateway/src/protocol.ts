export type GatewayIncomingMessage =
  | GatewayHelloMessage
  | GatewaySelectThreadMessage
  | GatewayCreateThreadMessage
  | GatewayForkThreadMessage
  | GatewayRenameThreadMessage
  | GatewayArchiveThreadMessage
  | GatewayUnarchiveThreadMessage
  | GatewayRefreshThreadsMessage
  | GatewayLoadOlderMessagesMessage
  | GatewaySendPromptMessage
  | GatewayRollbackThreadMessage
  | GatewayResendPromptMessage
  | GatewayStopTurnMessage
  | GatewayApprovePendingMessage
  | GatewayRejectPendingMessage
  | GatewayRestartDesktopMessage;

export interface GatewayHelloMessage {
  type: "hello";
  client?: string;
  version?: string;
  pairToken?: string | null;
  selectedThreadId?: string | null;
  capabilities?: string[];
}

export interface GatewaySelectThreadMessage {
  type: "select_thread";
  threadId: string;
}

export interface GatewayCreateThreadMessage {
  type: "create_thread";
  cwd?: string;
  model?: string;
  reasoningEffort?: string;
  approvalPolicy?: string;
  approvalsReviewer?: string;
  sandboxMode?: string;
}

export interface GatewayForkThreadMessage {
  type: "fork_thread";
  threadId: string;
  numTurns?: number;
}

export interface GatewayRenameThreadMessage {
  type: "rename_thread";
  threadId: string;
  name: string;
}

export interface GatewayArchiveThreadMessage {
  type: "archive_thread";
  threadId: string;
}

export interface GatewayUnarchiveThreadMessage {
  type: "unarchive_thread";
  threadId: string;
}

export interface GatewayRefreshThreadsMessage {
  type: "refresh_threads";
  forceSnapshot?: boolean;
}

export interface GatewayLoadOlderMessagesMessage {
  type: "load_older_messages";
}

export interface GatewaySendPromptMessage {
  type: "send_prompt";
  text: string;
  threadId?: string;
  newThread?: boolean;
  cwd?: string;
  model?: string;
  reasoningEffort?: string;
  approvalPolicy?: string;
  approvalsReviewer?: string;
  sandboxMode?: string;
}

export interface GatewayRollbackThreadMessage {
  type: "rollback_thread";
  threadId?: string;
  numTurns: number;
}

export interface GatewayResendPromptMessage {
  type: "resend_prompt";
  text: string;
  threadId?: string;
  rollbackNumTurns: number;
  model?: string;
  reasoningEffort?: string;
  approvalPolicy?: string;
  approvalsReviewer?: string;
  sandboxMode?: string;
}

export interface GatewayStopTurnMessage {
  type: "stop_turn";
}

export interface GatewayApprovePendingMessage {
  type: "approve_pending";
}

export interface GatewayRejectPendingMessage {
  type: "reject_pending";
}

export interface GatewayRestartDesktopMessage {
  type: "restart_desktop";
}

export interface GatewayStatusMessage {
  type: "status";
  status: "connected" | "connecting" | "disconnected" | "error";
  detail?: string;
}

export interface GatewaySnapshotMessage {
  type: "snapshot";
  revision?: number;
  threads: GatewayThreadPayload[];
  selectedThreadId?: string;
  messages: GatewayMessagePayload[];
  hasMoreHistory?: boolean;
  pendingApproval?: string | null;
  chips: GatewayChipPayload[];
  files: GatewayFilePayload[];
  slashCommands: string[];
  cwd?: string;
  permissionSummary?: string;
  sessionConfig?: GatewaySessionConfigPayload;
  configOptions: GatewayConfigOptionsPayload;
  operationalNotices?: GatewayOperationalNoticePayload[];
  desktopRestartRequired?: boolean;
  diagnostics?: GatewayDiagnosticsPayload;
  isGenerating: boolean;
}

export interface GatewaySnapshotPatchMessage {
  type: "snapshot_patch";
  baseRevision: number;
  revision: number;
  changed: Array<keyof Omit<GatewaySnapshotMessage, "type" | "revision">>;
  threads?: GatewayThreadPayload[];
  selectedThreadId?: string;
  messages?: GatewayMessagePayload[];
  hasMoreHistory?: boolean;
  pendingApproval?: string | null;
  chips?: GatewayChipPayload[];
  files?: GatewayFilePayload[];
  slashCommands?: string[];
  cwd?: string;
  permissionSummary?: string | null;
  sessionConfig?: GatewaySessionConfigPayload;
  configOptions?: GatewayConfigOptionsPayload;
  operationalNotices?: GatewayOperationalNoticePayload[];
  desktopRestartRequired?: boolean;
  diagnostics?: GatewayDiagnosticsPayload;
  isGenerating?: boolean;
}

export interface GatewayOperationalNoticePayload {
  id: string;
  text: string;
  createdAt: number;
}

export interface GatewayDiagnosticsPayload {
  selectedThreadId?: string;
  pendingSelectionThreadId?: string;
  isGenerating: boolean;
  runningThreadIds: string[];
  snapshotRevision: number;
  actionTraceId?: string;
  actionType?: string;
  actionStatus?: string;
  actionStartedAt?: number;
  actionFinishedAt?: number;
}

export interface GatewayConfigOptionPayload {
  label: string;
  value: string;
  description?: string;
}

export interface GatewayConfigOptionsPayload {
  models: GatewayConfigOptionPayload[];
  reasoningEfforts: GatewayConfigOptionPayload[];
  sandboxModes: GatewayConfigOptionPayload[];
  defaults: {
    model?: string;
    reasoningEffort?: string;
    sandboxMode?: string;
  };
}

export interface GatewaySessionConfigPayload {
  permissionMode?: string;
  provider?: string;
  model?: string;
  reasoningEffort?: string;
}

export interface GatewayThreadPayload {
  id: string;
  title: string;
  preview: string;
  subtitle?: string;
  cwd?: string;
  status: "running" | "idle" | "needs_approval" | "failed";
  updatedAt?: number;
  groupKind?: "project" | "chat";
  groupLabel?: string;
  archived?: boolean;
  gitBranch?: string;
  gitSha?: string;
}

export interface GatewayMessagePayload {
  id: string;
  role: "user" | "assistant" | "system";
  blocks: GatewayBlockPayload[];
  forkNumTurns?: number;
  rollbackNumTurns?: number;
  durationMs?: number;
  isFinal?: boolean;
}

export interface GatewayBlockPayload {
  kind:
    | "text"
    | "code"
    | "status"
    | "reasoning"
    | "commandSummary"
    | "commandMeta"
    | "fileChangeSummary"
    | "fileChangeMeta"
    | "fileChangeDiff";
  value: string;
  language?: string;
  path?: string;
}

export interface GatewayChipPayload {
  label: string;
  icon: "file" | "context";
  path?: string;
}

export interface GatewayFilePayload {
  label: string;
  path: string;
}

export interface ClientSnapshot {
  threads: GatewayThreadPayload[];
  selectedThreadId: string;
  messages: GatewayMessagePayload[];
  hasMoreHistory?: boolean;
  pendingApproval?: string | null;
  chips: GatewayChipPayload[];
  files: GatewayFilePayload[];
  slashCommands: string[];
  cwd: string;
  permissionSummary: string;
  sessionConfig: GatewaySessionConfigPayload;
  configOptions: GatewayConfigOptionsPayload;
  operationalNotices?: GatewayOperationalNoticePayload[];
  desktopRestartRequired?: boolean;
  diagnostics?: GatewayDiagnosticsPayload;
  isGenerating: boolean;
}

export interface ThreadStartOptions {
  cwd?: string;
  model?: string;
  reasoningEffort?: string;
  approvalPolicy?: string;
  approvalsReviewer?: string;
  sandboxMode?: string;
}

export function emptyConfigOptions(): GatewayConfigOptionsPayload {
  return {
    models: [],
    reasoningEfforts: [],
    sandboxModes: [],
    defaults: {},
  };
}

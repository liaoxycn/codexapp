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
  | GatewayStopTurnMessage
  | GatewayApprovePendingMessage
  | GatewayRejectPendingMessage;

export interface GatewayHelloMessage {
  type: "hello";
  client?: string;
  version?: string;
  pairToken?: string | null;
  capabilities?: string[];
}

export interface GatewaySelectThreadMessage {
  type: "select_thread";
  threadId: string;
}

export interface GatewayCreateThreadMessage {
  type: "create_thread";
  cwd?: string;
}

export interface GatewayForkThreadMessage {
  type: "fork_thread";
  threadId: string;
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
}

export interface GatewayLoadOlderMessagesMessage {
  type: "load_older_messages";
}

export interface GatewaySendPromptMessage {
  type: "send_prompt";
  text: string;
  threadId?: string;
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
  slashCommands: string[];
  cwd?: string;
  permissionSummary?: string;
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
  slashCommands?: string[];
  cwd?: string;
  permissionSummary?: string | null;
  isGenerating?: boolean;
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
}

export interface GatewayMessagePayload {
  id: string;
  role: "user" | "assistant" | "system";
  blocks: GatewayBlockPayload[];
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
}

export interface ClientSnapshot {
  threads: GatewayThreadPayload[];
  selectedThreadId: string;
  messages: GatewayMessagePayload[];
  hasMoreHistory?: boolean;
  pendingApproval?: string | null;
  chips: GatewayChipPayload[];
  slashCommands: string[];
  cwd: string;
  permissionSummary: string;
  isGenerating: boolean;
}

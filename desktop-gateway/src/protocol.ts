export type GatewayIncomingMessage =
  | GatewayHelloMessage
  | GatewaySelectThreadMessage
  | GatewayCreateThreadMessage
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
}

export interface GatewaySelectThreadMessage {
  type: "select_thread";
  threadId: string;
}

export interface GatewayCreateThreadMessage {
  type: "create_thread";
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

export interface GatewayThreadPayload {
  id: string;
  title: string;
  preview: string;
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
  kind: "text" | "code" | "status" | "reasoning";
  value: string;
  language?: string;
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

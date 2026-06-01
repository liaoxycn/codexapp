import type { WebSocket } from "ws";
import type { ClientSnapshot, GatewaySnapshotMessage } from "../protocol.js";

export interface GatewayServerOptions {
  host: string;
  port: number;
  path: string;
  pairToken?: string;
  workspacePath?: string;
}

export interface ClientContext {
  socket: WebSocket;
  selectedThreadId: string;
  selectionVersion: number;
  authenticated: boolean;
  unsubscribe: () => void;
  snapshotTimer: NodeJS.Timeout | null;
  liveRefreshTimer: NodeJS.Timeout | null;
  listRefreshTimer: NodeJS.Timeout | null;
  lastSnapshotPayload: string | null;
  lastSnapshotMessage: GatewaySnapshotMessage | null;
  snapshotRevision: number;
  supportsSnapshotPatch: boolean;
}

export type Backend = {
  subscribe(listener: () => void): () => void;
  hasThread(threadId: string): boolean;
  getDefaultThreadId(): string;
  getSnapshot(selectedThreadId?: string): ClientSnapshot;
  createThread(cwd?: string): ClientSnapshot | Promise<ClientSnapshot>;
  forkThread(threadId: string): ClientSnapshot | Promise<ClientSnapshot>;
  selectThread(threadId: string): ClientSnapshot | Promise<ClientSnapshot>;
  renameThread(threadId: string, name: string): ClientSnapshot | Promise<ClientSnapshot>;
  archiveThread(threadId: string): ClientSnapshot | Promise<ClientSnapshot>;
  unarchiveThread(threadId: string): ClientSnapshot | Promise<ClientSnapshot>;
  refreshThreads(selectedThreadId?: string): ClientSnapshot | Promise<ClientSnapshot>;
  loadOlderMessages(threadId: string): ClientSnapshot | Promise<ClientSnapshot>;
  sendPrompt(threadId: string, text: string): ClientSnapshot | Promise<ClientSnapshot>;
  stopTurn(threadId: string): ClientSnapshot | Promise<ClientSnapshot>;
  approveCurrent(threadId: string, allow: boolean): ClientSnapshot | Promise<ClientSnapshot>;
};

export type RefreshSource = "manual" | "live" | "list";

export const LIST_REFRESH_INTERVAL_MS = 2500;

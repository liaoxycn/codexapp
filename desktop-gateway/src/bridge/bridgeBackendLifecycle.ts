import type {
  JsonRpcNotification,
  JsonRpcServerRequest,
} from "../appServerTypes.js";
import type { GatewayOperationalNoticePayload } from "../protocol.js";
import { handleBridgeNotification } from "./notifications.js";
import { applyServerRequest } from "./serverRequests.js";
import { applyPendingApprovalState } from "./approvalActions.js";
import {
  finalizeCompactRuntimeState,
  finalizeTurnRuntimeState,
} from "./turnFinalization.js";
import type {
  PendingApproval,
  ThreadLifecycleStatus,
  ThreadRuntimeState,
} from "./types.js";

export interface BridgeBackendLifecycleDeps {
  threads: Map<string, ThreadRuntimeState>;
  respondToServerRequest(id: string | number, result: unknown): void;
  respondToServerRequestError(id: string | number, code: number, message: string, data?: unknown): void;
  emitChanged(): void;
  hydrateThreads(): Promise<void>;
  refreshThread(threadId: string): Promise<void>;
  ensureActiveAssistantMessage(state: ThreadRuntimeState, turnId: string): void;
  pushOperationalNotice?(notice: GatewayOperationalNoticePayload): void;
  updateSummaryStatus(threadId: string, status: ThreadLifecycleStatus): void;
}

export async function handleBridgeBackendNotification(
  notification: JsonRpcNotification,
  deps: BridgeBackendLifecycleDeps
): Promise<void> {
  await handleBridgeNotification(notification, {
    threads: deps.threads,
    emitChanged: deps.emitChanged,
    finalizeCompactState: async (threadId) => finalizeBridgeCompactState(threadId, deps),
    finalizeTurnState: async (threadId, turnStatus, completedTurnId) =>
      finalizeBridgeTurnState(threadId, turnStatus, completedTurnId, deps),
    hydrateThreads: deps.hydrateThreads,
    ensureActiveAssistantMessage: deps.ensureActiveAssistantMessage,
    pushOperationalNotice: deps.pushOperationalNotice,
    updateSummaryStatus: deps.updateSummaryStatus,
  });
}

export function handleBridgeBackendServerRequest(
  request: JsonRpcServerRequest,
  deps: BridgeBackendLifecycleDeps
): void {
  applyServerRequest(
    request,
    (threadId, approval) => {
      setBridgePendingApproval(threadId, approval, deps);
    },
    {
      respond: deps.respondToServerRequest,
      respondError: deps.respondToServerRequestError,
    }
  );
}

export function setBridgePendingApproval(
  threadId: string,
  approval: PendingApproval,
  deps: Pick<BridgeBackendLifecycleDeps, "threads" | "emitChanged" | "updateSummaryStatus">
): void {
  applyPendingApprovalState(deps.threads, threadId, approval, deps.updateSummaryStatus);
  deps.emitChanged();
}

export async function finalizeBridgeTurnState(
  threadId: string,
  turnStatus: string | undefined,
  completedTurnId: string | undefined,
  deps: Pick<BridgeBackendLifecycleDeps, "threads" | "emitChanged" | "refreshThread" | "updateSummaryStatus">
): Promise<void> {
  await finalizeTurnRuntimeState({
    emitChanged: deps.emitChanged,
    refreshThread: deps.refreshThread,
    threadId,
    threads: deps.threads,
    turnStatus,
    completedTurnId,
    updateSummaryStatus: deps.updateSummaryStatus,
  });
}

export async function finalizeBridgeCompactState(
  threadId: string,
  deps: Pick<BridgeBackendLifecycleDeps, "threads" | "emitChanged" | "refreshThread" | "updateSummaryStatus">
): Promise<void> {
  await finalizeCompactRuntimeState({
    emitChanged: deps.emitChanged,
    refreshThread: deps.refreshThread,
    threadId,
    threads: deps.threads,
    updateSummaryStatus: deps.updateSummaryStatus,
  });
}

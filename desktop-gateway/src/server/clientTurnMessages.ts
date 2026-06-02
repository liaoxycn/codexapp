import type {
  GatewayApprovePendingMessage,
  GatewayIncomingMessage,
  GatewayRejectPendingMessage,
  GatewayResendPromptMessage,
  GatewayRollbackThreadMessage,
  GatewaySendPromptMessage,
  GatewayStopTurnMessage,
} from "../protocol.js";
import type { ClientContext } from "./types.js";
import type { ClientMessageHandlers } from "./clientMessages.js";

export async function handleTurnClientMessage(
  context: ClientContext,
  message: GatewayIncomingMessage,
  handlers: ClientMessageHandlers
): Promise<boolean> {
  switch (message.type) {
    case "send_prompt":
      await handleSendPrompt(context, message, handlers);
      return true;
    case "rollback_thread":
      await handleRollbackThread(context, message, handlers);
      return true;
    case "resend_prompt":
      await handleResendPrompt(context, message, handlers);
      return true;
    case "stop_turn":
      await handleStopTurn(context, message, handlers);
      return true;
    case "approve_pending":
      await handleApprovePending(context, message, handlers);
      return true;
    case "reject_pending":
      await handleRejectPending(context, message, handlers);
      return true;
    default:
      return false;
  }
}

async function handleRollbackThread(
  context: ClientContext,
  message: GatewayRollbackThreadMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  context.actionTraceType = message.type;
  await handlers.runBackendAction(context, async () => {
    const requestedThreadId = message.threadId?.trim() || context.selectedThreadId;
    const snapshot = await handlers.backend().rollbackThread(requestedThreadId, message.numTurns);
    context.selectedThreadId = snapshot.selectedThreadId || requestedThreadId;
    handlers.markDesktopRestartRequired("rollback_thread");
    return snapshot;
  });
}

async function handleResendPrompt(
  context: ClientContext,
  message: GatewayResendPromptMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  context.actionTraceType = message.type;
  await handlers.runBackendAction(context, async () => {
    const requestedThreadId = message.threadId?.trim() || context.selectedThreadId;
    const snapshot = await handlers.backend().resendPrompt(
      requestedThreadId,
      message.text,
      message.rollbackNumTurns
    );
    context.selectedThreadId = snapshot.selectedThreadId || requestedThreadId;
    handlers.markDesktopRestartRequired("resend_prompt");
    return snapshot;
  });
}

async function handleSendPrompt(
  context: ClientContext,
  message: GatewaySendPromptMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  if (message.newThread) {
    context.selectionVersion += 1;
    context.selectedThreadId = "";
  }
  context.actionTraceType = message.type;
  await handlers.runBackendAction(context, async () => {
    let requestedThreadId = message.threadId?.trim() || context.selectedThreadId;
    if (message.newThread || !requestedThreadId) {
      const created = await handlers.backend().createThread(message.cwd, {
        cwd: message.cwd,
        model: message.model,
        reasoningEffort: message.reasoningEffort,
        sandboxMode: message.sandboxMode,
      });
      requestedThreadId = created.selectedThreadId;
    }
    if (requestedThreadId && requestedThreadId !== context.selectedThreadId) {
      context.selectionVersion += 1;
      context.selectedThreadId = requestedThreadId;
    }
    const snapshot = await handlers.backend().sendPrompt(requestedThreadId, message.text);
    context.selectedThreadId = snapshot.selectedThreadId || requestedThreadId;
    console.log(`[gateway] send_prompt backend ok thread=${context.selectedThreadId}`);
    handlers.markDesktopRestartRequired("send_prompt");
    return snapshot;
  });
}

async function handleStopTurn(
  context: ClientContext,
  message: GatewayStopTurnMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  context.actionTraceType = message.type;
  await handlers.runBackendAction(context, async () =>
    handlers.backend().stopTurn(context.selectedThreadId)
  );
}

async function handleApprovePending(
  context: ClientContext,
  message: GatewayApprovePendingMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  context.actionTraceType = message.type;
  await handlers.runBackendAction(context, async () =>
    handlers.backend().approveCurrent(context.selectedThreadId, true)
  );
}

async function handleRejectPending(
  context: ClientContext,
  message: GatewayRejectPendingMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  context.actionTraceType = message.type;
  await handlers.runBackendAction(context, async () =>
    handlers.backend().approveCurrent(context.selectedThreadId, false)
  );
}

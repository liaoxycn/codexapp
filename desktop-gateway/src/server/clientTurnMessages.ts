import type {
  GatewayApprovePendingMessage,
  GatewayIncomingMessage,
  GatewayRejectPendingMessage,
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

async function handleSendPrompt(
  context: ClientContext,
  message: GatewaySendPromptMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  await handlers.runBackendAction(context, async () => {
    const requestedThreadId = message.threadId?.trim() || context.selectedThreadId;
    if (requestedThreadId && requestedThreadId !== context.selectedThreadId) {
      context.selectionVersion += 1;
      context.selectedThreadId = requestedThreadId;
    }
    const snapshot = await handlers.backend().sendPrompt(requestedThreadId, message.text);
    context.selectedThreadId = snapshot.selectedThreadId || requestedThreadId;
    console.log(`[gateway] send_prompt backend ok thread=${context.selectedThreadId}`);
    void handlers.pokeDesktop(context.selectedThreadId, "send_prompt");
    return snapshot;
  });
}

async function handleStopTurn(
  context: ClientContext,
  _message: GatewayStopTurnMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  await handlers.runBackendAction(context, async () =>
    handlers.backend().stopTurn(context.selectedThreadId)
  );
}

async function handleApprovePending(
  context: ClientContext,
  _message: GatewayApprovePendingMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  await handlers.runBackendAction(context, async () =>
    handlers.backend().approveCurrent(context.selectedThreadId, true)
  );
}

async function handleRejectPending(
  context: ClientContext,
  _message: GatewayRejectPendingMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  await handlers.runBackendAction(context, async () =>
    handlers.backend().approveCurrent(context.selectedThreadId, false)
  );
}

import type {
  GatewayArchiveThreadMessage,
  GatewayCreateThreadMessage,
  GatewayForkThreadMessage,
  GatewayIncomingMessage,
  GatewayLoadOlderMessagesMessage,
  GatewayRefreshThreadsMessage,
  GatewayRenameThreadMessage,
  GatewaySelectThreadMessage,
  GatewayUnarchiveThreadMessage,
} from "../protocol.js";
import type { ClientContext } from "./types.js";
import type { ClientMessageHandlers } from "./clientMessages.js";

export async function handleThreadClientMessage(
  context: ClientContext,
  message: GatewayIncomingMessage,
  handlers: ClientMessageHandlers
): Promise<boolean> {
  switch (message.type) {
    case "create_thread":
      await handleCreateThread(context, message, handlers);
      return true;
    case "fork_thread":
      await handleForkThread(context, message, handlers);
      return true;
    case "select_thread":
      await handleSelectThread(context, message, handlers);
      return true;
    case "rename_thread":
      await handleRenameThread(context, message, handlers);
      return true;
    case "archive_thread":
      await handleArchiveThread(context, message, handlers);
      return true;
    case "unarchive_thread":
      await handleUnarchiveThread(context, message, handlers);
      return true;
    case "refresh_threads":
      await handleRefreshThreads(context, message, handlers);
      return true;
    case "load_older_messages":
      await handleLoadOlderMessages(context, message, handlers);
      return true;
    default:
      return false;
  }
}

async function handleForkThread(
  context: ClientContext,
  message: GatewayForkThreadMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  context.selectionVersion += 1;
  await handlers.runBackendAction(context, async () => {
    const snapshot = await handlers.backend().forkThread(message.threadId);
    context.selectedThreadId = snapshot.selectedThreadId;
    return snapshot;
  });
}

async function handleCreateThread(
  context: ClientContext,
  message: GatewayCreateThreadMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  context.selectionVersion += 1;
  await handlers.runBackendAction(context, async () => {
    const snapshot = await handlers.backend().createThread(message.cwd);
    context.selectedThreadId = snapshot.selectedThreadId;
    return snapshot;
  });
}

async function handleSelectThread(
  context: ClientContext,
  message: GatewaySelectThreadMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  context.selectionVersion += 1;
  context.selectedThreadId = handlers.backend().hasThread(message.threadId)
    ? message.threadId
    : handlers.backend().getDefaultThreadId();
  await handlers.runBackendAction(context, async () =>
    handlers.backend().selectThread(context.selectedThreadId)
  );
}

async function handleRenameThread(
  context: ClientContext,
  message: GatewayRenameThreadMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  await handlers.runBackendAction(context, async () =>
    handlers.backend().renameThread(message.threadId, message.name)
  );
}

async function handleArchiveThread(
  context: ClientContext,
  message: GatewayArchiveThreadMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  context.selectionVersion += 1;
  await handlers.runBackendAction(context, async () => {
    const snapshot = await handlers.backend().archiveThread(message.threadId);
    context.selectedThreadId = snapshot.selectedThreadId;
    return snapshot;
  });
}

async function handleUnarchiveThread(
  context: ClientContext,
  message: GatewayUnarchiveThreadMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  context.selectionVersion += 1;
  await handlers.runBackendAction(context, async () => {
    const snapshot = await handlers.backend().unarchiveThread(message.threadId);
    context.selectedThreadId = snapshot.selectedThreadId;
    return snapshot;
  });
}

async function handleRefreshThreads(
  context: ClientContext,
  _message: GatewayRefreshThreadsMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  await handlers.refreshSelectedThread(context, "manual");
}

async function handleLoadOlderMessages(
  context: ClientContext,
  _message: GatewayLoadOlderMessagesMessage,
  handlers: ClientMessageHandlers
): Promise<void> {
  await handlers.runBackendAction(context, async () =>
    handlers.backend().loadOlderMessages(context.selectedThreadId)
  );
}

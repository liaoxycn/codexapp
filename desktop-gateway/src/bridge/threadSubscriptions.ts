import type { AppServerThread } from "../appServerTypes.js";
import { AppServerClient } from "../appServerClient.js";
import { upsertThreadState } from "./runtimeState.js";
import {
  isNoRolloutFoundError,
  isThreadNotMaterializedError,
} from "./summaries.js";
import type { ThreadRuntimeState } from "./types.js";

type SubscriptionClient = Pick<
  AppServerClient,
  "threadRead" | "threadResume" | "threadUnsubscribe"
>;

interface ThreadSubscriptionDeps {
  appServer: SubscriptionClient;
  threads: Map<string, ThreadRuntimeState>;
}

export async function ensureResumedThread(
  deps: ThreadSubscriptionDeps,
  threadId: string
): Promise<ThreadRuntimeState> {
  await resumeThreadSubscription(deps, threadId);
  const state = deps.threads.get(threadId);
  if (state) {
    return state;
  }
  throw new Error(`thread not found after resume: ${threadId}`);
}

export async function refreshThreadState(
  { appServer, threads }: ThreadSubscriptionDeps,
  threadId: string
): Promise<void> {
  let thread: AppServerThread;
  try {
    thread = await appServer.threadRead(threadId);
  } catch (error) {
    if (!isThreadNotMaterializedError(error)) {
      throw error;
    }
    thread = await appServer.threadRead(threadId, false);
  }

  const existing = threads.get(threadId);
  upsertThreadState({ threads, thread, preserveLiveMessages: true });
  const state = threads.get(threadId);
  if (state && existing?.isSubscribed) {
    state.isSubscribed = true;
  }
}

export async function resumeThreadSubscription(
  deps: ThreadSubscriptionDeps,
  threadId: string
): Promise<void> {
  const existing = deps.threads.get(threadId);
  if (existing?.isSubscribed) {
    await refreshThreadState(deps, threadId);
    return;
  }

  try {
    const resumed = await deps.appServer.threadResume(threadId);
    upsertThreadState({ threads: deps.threads, thread: resumed.thread, resume: resumed });
    const state = deps.threads.get(threadId);
    if (state) {
      state.isSubscribed = true;
    }
  } catch (error) {
    if (!isNoRolloutFoundError(error)) {
      throw error;
    }

    const current = deps.threads.get(threadId);
    if (current?.thread != null) {
      current.isSubscribed = true;
      return;
    }
    throw error;
  }
}

export async function unsubscribeInactiveThreadSubscriptions(
  { appServer, threads }: ThreadSubscriptionDeps,
  activeThreadId: string
): Promise<void> {
  const staleThreadIds = [...threads.values()]
    .filter((entry) => entry.thread != null && entry.thread.id !== activeThreadId && entry.isSubscribed)
    .map((entry) => entry.thread!.id);

  for (const threadId of staleThreadIds) {
    try {
      await appServer.threadUnsubscribe(threadId);
    } catch {
      // Ignore unsubscribe failures; the next resume rebinds the stream.
    }

    const state = threads.get(threadId);
    if (state) {
      state.isSubscribed = false;
    }
  }
}

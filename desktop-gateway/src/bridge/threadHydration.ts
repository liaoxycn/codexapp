import type { AppServerThread } from "../appServerTypes.js";
import { AppServerClient } from "../appServerClient.js";
import type { GatewayThreadPayload } from "../protocol.js";
import {
  createPlaceholderThreadRuntimeState,
  refreshSummarySnapshotEntry,
  syncSelectedThreadSnapshots,
} from "./runtimeSummaryState.js";
import { upsertThreadState } from "./runtimeState.js";
import {
  buildVisibleThreadSummaries,
  dedupeSummaries,
  isThreadNotMaterializedError,
} from "./summaries.js";
import type { ThreadRuntimeState } from "./types.js";

type HydrationClient = Pick<AppServerClient, "threadList" | "threadRead">;

interface ThreadCatalogHydrationDeps {
  appServer: HydrationClient;
  threads: Map<string, ThreadRuntimeState>;
}

interface HydrateThreadCatalogParams extends ThreadCatalogHydrationDeps {
  currentThreadId: string;
}

export async function hydrateThreadCatalog({
  appServer,
  threads,
  currentThreadId,
}: HydrateThreadCatalogParams): Promise<string> {
  const activeList = await appServer.threadList(false);
  const archivedList = await appServer.threadList(true);
  const activeThreadIds = new Set(activeList.map((thread) => thread.id));
  const archivedThreadIds = new Set(archivedList.map((thread) => thread.id));
  const catalogThreadIds = new Set([...activeThreadIds, ...archivedThreadIds]);

  for (const threadId of [...threads.keys()]) {
    if (catalogThreadIds.has(threadId)) {
      continue;
    }

    const existing = threads.get(threadId);
    const preservePendingCurrent =
      threadId === currentThreadId &&
      !activeThreadIds.has(threadId) &&
      (existing?.thread != null || existing?.isSubscribed === true);
    if (!preservePendingCurrent) {
      threads.delete(threadId);
    }
  }

  const detailedThreads = await readThreadDetailsForSummaries(appServer, activeList);
  const detailedArchivedThreads = await readThreadDetailsForSummaries(appServer, archivedList);
  const detailedArchivedOnlyThreads = detailedArchivedThreads.filter((thread) => !activeThreadIds.has(thread.id));
  const retainedSummaries = [...threads.values()]
    .filter((entry) => !catalogThreadIds.has(entry.summary.id))
    .map((entry) => entry.summary);
  const summaries = dedupeSummaries([
    ...retainedSummaries,
    ...buildVisibleThreadSummaries(detailedThreads),
    ...buildVisibleThreadSummaries(detailedArchivedOnlyThreads).map((summary) => ({
      ...summary,
      archived: true,
      status: "idle" as const,
    })),
  ]);

  if (summaries.length === 0) {
    return "";
  }

  const candidateId =
    currentThreadId && summaries.some((thread) => thread.id === currentThreadId && thread.archived !== true)
      ? currentThreadId
      : summaries.find((thread) => thread.archived !== true)?.id ?? "";

  for (const thread of detailedThreads) {
    upsertThreadState({ threads, thread, summaries, syncSelection: false });
  }
  for (const thread of detailedArchivedOnlyThreads) {
    upsertThreadState({ threads, thread, summaries, archived: true, syncSelection: false });
  }

  materializeMissingSummaryStates(threads, summaries, candidateId);
  syncSelectedThreadSnapshots(threads, candidateId);
  return candidateId;
}

async function readThreadDetailsForSummaries(
  appServer: Pick<AppServerClient, "threadRead">,
  threads: AppServerThread[]
): Promise<AppServerThread[]> {
  return Promise.all(
    threads.map(async (thread) => {
      try {
        return await appServer.threadRead(thread.id);
      } catch (error) {
        if (!isThreadNotMaterializedError(error)) {
          console.warn(
            `[gateway] thread/read failed for ${thread.id}: ${error instanceof Error ? error.message : String(error)}`
          );
        }
        return thread;
      }
    })
  );
}

function materializeMissingSummaryStates(
  threads: Map<string, ThreadRuntimeState>,
  summaries: GatewayThreadPayload[],
  selectedThreadId: string
): void {
  for (const summary of summaries) {
    const existing = threads.get(summary.id);
    if (!existing) {
      threads.set(
        summary.id,
        createPlaceholderThreadRuntimeState(summary, summaries, selectedThreadId)
      );
      continue;
    }

    refreshSummarySnapshotEntry(existing, summary, summaries);
  }
}

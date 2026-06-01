import process from "node:process";
import {
  buildRuntimeSummaries,
} from "./runtimeSummaryState.js";
import { upsertThreadState } from "./runtimeState.js";
import {
  dedupeSummaries,
  mapThreadToSummary,
} from "./summaries.js";
import { findNextActiveThreadId } from "./threadSelection.js";
import type { ThreadCatalogActionDeps } from "./threadCatalogActions.js";
import type { ClientSnapshot } from "../protocol.js";

export async function createCatalogThread(
  deps: ThreadCatalogActionDeps,
  cwd?: string
): Promise<ClientSnapshot> {
  const currentThreadId = deps.getCurrentThreadId();
  const selectedCwd = deps.threads.get(currentThreadId)?.thread?.cwd
    ?? deps.threads.get(currentThreadId)?.snapshot.cwd
    ?? process.cwd();
  const targetCwd = cwd?.trim() || selectedCwd;

  const started = await deps.appServer.threadStart(targetCwd);
  const threadId = started.thread.id;

  deps.setCurrentThreadId(threadId);
  deps.incrementSelectionVersion();

  const mergedSummaries = dedupeSummaries([
    ...buildRuntimeSummaries(deps.threads),
    mapThreadToSummary(started.thread),
  ]);

  upsertThreadState({
    threads: deps.threads,
    thread: started.thread,
    summaries: mergedSummaries,
    resume: started,
  });

  deps.syncSelectedThread(threadId);
  deps.emitChanged();
  return deps.getSnapshot(threadId);
}

export async function forkCatalogThread(
  deps: ThreadCatalogActionDeps,
  threadId: string
): Promise<ClientSnapshot> {
  const resolved = deps.resolveThreadId(threadId);
  const forked = await deps.appServer.threadFork(resolved);
  const forkedThreadId = forked.thread.id;

  deps.setCurrentThreadId(forkedThreadId);
  deps.incrementSelectionVersion();

  const mergedSummaries = dedupeSummaries([
    ...buildRuntimeSummaries(deps.threads),
    mapThreadToSummary(forked.thread),
  ]);

  upsertThreadState({
    threads: deps.threads,
    thread: forked.thread,
    summaries: mergedSummaries,
    resume: forked,
  });

  deps.syncSelectedThread(forkedThreadId);
  deps.emitChanged();
  return deps.getSnapshot(forkedThreadId);
}

export async function renameCatalogThread(
  deps: ThreadCatalogActionDeps,
  threadId: string,
  name: string
): Promise<ClientSnapshot> {
  const resolved = deps.resolveThreadId(threadId);
  const trimmed = name.trim();
  if (!trimmed) {
    return deps.getSnapshot(resolved);
  }

  await deps.appServer.threadSetName(resolved, trimmed);
  await deps.refreshThread(resolved);
  deps.syncSelectedThread(resolved);
  deps.emitChanged();
  return deps.getSnapshot(resolved);
}

export async function archiveCatalogThread(
  deps: ThreadCatalogActionDeps,
  threadId: string
): Promise<ClientSnapshot> {
  const resolved = deps.resolveThreadId(threadId);

  await deps.appServer.threadArchive(resolved);
  await deps.hydrateThreads();

  const nextThreadId = findNextActiveThreadId(deps.threads);
  deps.setCurrentThreadId(nextThreadId);
  deps.incrementSelectionVersion();

  if (nextThreadId) {
    await deps.refreshThread(nextThreadId);
  }

  deps.syncSelectedThread(nextThreadId);
  deps.emitChanged();
  return deps.getSnapshot(nextThreadId);
}

export async function unarchiveCatalogThread(
  deps: ThreadCatalogActionDeps,
  threadId: string
): Promise<ClientSnapshot> {
  await deps.appServer.threadUnarchive(threadId);
  await deps.hydrateThreads();

  const resolved = deps.resolveThreadId(threadId);
  if (deps.hasThread(threadId)) {
    deps.setCurrentThreadId(threadId);
    deps.incrementSelectionVersion();
    await deps.refreshThread(threadId);
    deps.syncSelectedThread(threadId);
    deps.emitChanged();
    return deps.getSnapshot(threadId);
  }

  deps.syncSelectedThread(resolved);
  deps.emitChanged();
  return deps.getSnapshot(resolved);
}

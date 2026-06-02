import { mkdirSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import {
  buildRuntimeSummaries,
} from "./runtimeSummaryState.js";
import { upsertThreadState } from "./runtimeState.js";
import {
  dedupeSummaries,
  mapThreadToSummary,
} from "./summaries.js";
import type { ThreadCatalogActionDeps } from "./threadCatalogActions.js";
import type { ClientSnapshot, ThreadStartOptions } from "../protocol.js";

export async function createCatalogThread(
  deps: ThreadCatalogActionDeps,
  cwd?: string,
  options: ThreadStartOptions = {}
): Promise<ClientSnapshot> {
  const explicitCwd = options.cwd?.trim() || cwd?.trim();
  const targetCwd = explicitCwd || createDesktopChatCwd();

  const started = await deps.appServer.threadStart(targetCwd, {
    ...options,
    cwd: targetCwd,
  });
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
    isLocalCatalogEntry: true,
  });

  deps.syncSelectedThread(threadId);
  deps.emitChanged();
  return deps.getSnapshot(threadId);
}

function createDesktopChatCwd(now = new Date()): string {
  const date = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0"),
  ].join("-");
  const cwd = join(homedir(), "Documents", "Codex", date, "new-chat");
  mkdirSync(cwd, { recursive: true });
  return cwd;
}

export async function forkCatalogThread(
  deps: ThreadCatalogActionDeps,
  threadId: string,
  numTurns?: number
): Promise<ClientSnapshot> {
  const resolved = deps.resolveThreadId(threadId);
  const forked = await deps.appServer.threadFork(resolved);
  let forkedThread = forked.thread;
  const targetTurnCount = normalizeForkTurnCount(numTurns);
  const sourceTurnCount = targetTurnCount == null
    ? null
    : await resolveSourceTurnCount(deps, resolved);
  if (sourceTurnCount != null && targetTurnCount != null && targetTurnCount < sourceTurnCount) {
    const rollbackCount = sourceTurnCount - targetTurnCount;
    const rolledBack = await deps.appServer.threadRollback(forked.thread.id, rollbackCount);
    forkedThread = rolledBack.thread;
  }
  const forkedThreadId = forkedThread.id;

  deps.setCurrentThreadId(forkedThreadId);
  deps.incrementSelectionVersion();

  const mergedSummaries = dedupeSummaries([
    ...buildRuntimeSummaries(deps.threads),
    mapThreadToSummary(forkedThread),
  ]);

  upsertThreadState({
    threads: deps.threads,
    thread: forkedThread,
    summaries: mergedSummaries,
    resume: { ...forked, thread: forkedThread },
    isLocalCatalogEntry: true,
  });

  deps.syncSelectedThread(forkedThreadId);
  deps.emitChanged();
  return deps.getSnapshot(forkedThreadId);
}

function normalizeForkTurnCount(numTurns: number | undefined): number | null {
  if (!Number.isInteger(numTurns) || numTurns == null || numTurns <= 0) {
    return null;
  }
  return numTurns;
}

async function resolveSourceTurnCount(
  deps: ThreadCatalogActionDeps,
  threadId: string
): Promise<number | null> {
  const cachedThread = deps.threads.get(threadId)?.thread;
  if (cachedThread && cachedThread.turns.length > 0) {
    return cachedThread.turns.length;
  }

  const sourceThread = await deps.appServer.threadRead(threadId, true);
  return sourceThread.turns.length;
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
  deps.threads.delete(resolved);
  await deps.hydrateThreads();

  deps.setCurrentThreadId("");
  deps.incrementSelectionVersion();

  deps.syncSelectedThread("");
  deps.emitChanged();
  return deps.getSnapshot("");
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

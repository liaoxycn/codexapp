import type {
  AppServerThread,
  ThreadListResult,
  ThreadReadResult,
  ThreadForkResponse,
  ThreadRollbackResponse,
  ThreadResumeResult,
  ThreadStartResponse,
} from "./appServerTypes.js";

const THREAD_LIST_PAGE_SIZE = 100;
const THREAD_LIST_MAX_PAGES = 10;
const THREAD_LIST_SOURCE_KINDS = ["cli", "vscode", "appServer", "unknown"];

type RequestFn = <TParams extends object, TResult = unknown>(
  method: string,
  params: TParams
) => Promise<TResult>;

export async function listThreads(
  request: RequestFn,
  archived = false
): Promise<AppServerThread[]> {
  const threads: AppServerThread[] = [];
  let cursor: string | null = null;
  for (let page = 0; page < THREAD_LIST_MAX_PAGES; page += 1) {
    const result = (await request("thread/list", {
      cursor,
      limit: THREAD_LIST_PAGE_SIZE,
      sortKey: "updated_at",
      sortDirection: "desc",
      sourceKinds: THREAD_LIST_SOURCE_KINDS,
      archived,
    })) as ThreadListResult;
    threads.push(...result.data);
    cursor = result.nextCursor ?? null;
    if (!cursor) {
      break;
    }
  }
  return threads;
}

export async function readThread(
  request: RequestFn,
  threadId: string,
  includeTurns = true
): Promise<AppServerThread> {
  const result = (await request("thread/read", {
    threadId,
    includeTurns,
  })) as ThreadReadResult;
  return result.thread;
}

export async function resumeThread(
  request: RequestFn,
  threadId: string,
  options: { excludeTurns?: boolean } = {}
): Promise<ThreadResumeResult> {
  return (await request("thread/resume", {
    threadId,
    excludeTurns: options.excludeTurns ?? false,
  })) as ThreadResumeResult;
}

export async function startThread(
  request: RequestFn,
  cwd?: string | null
): Promise<ThreadStartResponse> {
  return (await request("thread/start", {
    cwd: cwd ?? null,
  })) as ThreadStartResponse;
}

export async function forkThread(
  request: RequestFn,
  threadId: string
): Promise<ThreadForkResponse> {
  return (await request("thread/fork", {
    threadId,
    threadSource: "user",
  })) as ThreadForkResponse;
}

export async function rollbackThread(
  request: RequestFn,
  threadId: string,
  numTurns: number
): Promise<ThreadRollbackResponse> {
  return (await request("thread/rollback", {
    threadId,
    numTurns,
  })) as ThreadRollbackResponse;
}

export async function setThreadName(
  request: RequestFn,
  threadId: string,
  name: string
): Promise<void> {
  await request("thread/name/set", {
    threadId,
    name,
  });
}

export async function archiveThread(request: RequestFn, threadId: string): Promise<void> {
  await request("thread/archive", {
    threadId,
  });
}

export async function unarchiveThread(request: RequestFn, threadId: string): Promise<void> {
  await request("thread/unarchive", {
    threadId,
  });
}

export async function unsubscribeThread(request: RequestFn, threadId: string): Promise<void> {
  await request("thread/unsubscribe", {
    threadId,
  });
}

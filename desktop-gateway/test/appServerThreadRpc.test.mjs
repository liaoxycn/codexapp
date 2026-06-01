import assert from "node:assert/strict";
import test from "node:test";
import {
  archiveThread,
  listThreads,
  readThread,
  resumeThread,
  setThreadName,
  startThread,
  unarchiveThread,
  unsubscribeThread,
} from "../dist/appServerThreadRpc.js";

test("listThreads reads updated threads across pages", async () => {
  const calls = [];
  const request = async (method, params) => {
    calls.push({ method, params });
    if (calls.length === 1) {
      return {
        data: [{ id: "thread-1" }],
        nextCursor: "page-2",
      };
    }
    return {
      data: [{ id: "thread-2" }],
      nextCursor: null,
    };
  };

  const threads = await listThreads(request, false);

  assert.deepEqual(threads.map((thread) => thread.id), ["thread-1", "thread-2"]);
  assert.deepEqual(calls, [
    {
      method: "thread/list",
      params: {
        cursor: null,
        limit: 100,
        sortKey: "updated_at",
        sortDirection: "desc",
        sourceKinds: ["cli", "vscode", "appServer", "unknown"],
        archived: false,
      },
    },
    {
      method: "thread/list",
      params: {
        cursor: "page-2",
        limit: 100,
        sortKey: "updated_at",
        sortDirection: "desc",
        sourceKinds: ["cli", "vscode", "appServer", "unknown"],
        archived: false,
      },
    },
  ]);
});

test("thread RPC helpers forward expected payloads", async () => {
  const calls = [];
  const request = async (method, params) => {
    calls.push({ method, params });
    if (method === "thread/read") {
      return { thread: { id: params.threadId } };
    }
    if (method === "thread/resume") {
      return { threadId: params.threadId, model: null };
    }
    if (method === "thread/start") {
      return { threadId: "started-1" };
    }
    return null;
  };

  const read = await readThread(request, "thread-1", false);
  const resumed = await resumeThread(request, "thread-2", { excludeTurns: true });
  const started = await startThread(request, "D:/Projects/Test");
  await setThreadName(request, "thread-3", "Rename me");
  await archiveThread(request, "thread-4");
  await unarchiveThread(request, "thread-5");
  await unsubscribeThread(request, "thread-6");

  assert.equal(read.id, "thread-1");
  assert.equal(resumed.threadId, "thread-2");
  assert.equal(started.threadId, "started-1");
  assert.deepEqual(calls, [
    { method: "thread/read", params: { threadId: "thread-1", includeTurns: false } },
    { method: "thread/resume", params: { threadId: "thread-2", excludeTurns: true } },
    { method: "thread/start", params: { cwd: "D:/Projects/Test" } },
    { method: "thread/name/set", params: { threadId: "thread-3", name: "Rename me" } },
    { method: "thread/archive", params: { threadId: "thread-4" } },
    { method: "thread/unarchive", params: { threadId: "thread-5" } },
    { method: "thread/unsubscribe", params: { threadId: "thread-6" } },
  ]);
});

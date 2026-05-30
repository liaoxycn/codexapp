import assert from "node:assert/strict";
import test from "node:test";
import { AppServerClient } from "../dist/appServerClient.js";

test("threadList reads updated threads across pages", async () => {
  const client = new AppServerClient();
  const calls = [];
  client.request = async (method, params) => {
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

  const threads = await client.threadList(false);

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

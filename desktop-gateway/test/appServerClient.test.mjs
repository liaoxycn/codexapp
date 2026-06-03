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
        archived: false,
      },
    },
  ]);
});

test("configOptions keeps app server request context", async () => {
  const client = new AppServerClient();
  const calls = [];
  client.request = async function request(method, params) {
    assert.equal(this, client);
    calls.push({ method, params });
    if (method === "config/read") {
      return {
        config: {
          model: "gpt-5",
          model_reasoning_effort: "medium",
          sandbox_mode: "workspace-write",
        },
      };
    }
    return {
      data: [
        {
          id: "gpt-5",
          model: "gpt-5",
          displayName: "GPT-5",
          description: "default",
          isDefault: true,
          hidden: false,
          supportedReasoningEfforts: [
            { reasoningEffort: "medium", description: "balanced" },
          ],
          defaultReasoningEffort: "medium",
        },
      ],
      nextCursor: null,
    };
  };

  const options = await client.configOptions("D:/repo");

  assert.equal(options.defaults.model, "gpt-5");
  assert.equal(options.models[0].label, "GPT-5");
  assert.equal(options.defaults.sandboxMode, "workspace-write");
  assert.deepEqual(
    options.sandboxModes.map((option) => option.value),
    ["read-only", "workspace-write", "danger-full-access"]
  );
  assert.deepEqual(calls.map((call) => call.method), ["config/read", "model/list"]);
});

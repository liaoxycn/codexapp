import assert from "node:assert/strict";
import test from "node:test";
import {
  isNotification,
  isResponse,
  isServerRequest,
  resolveAppServerCommand,
} from "../dist/appServerTransport.js";

test("resolveAppServerCommand returns cmd wrapper on windows", () => {
  const command = resolveAppServerCommand("win32");

  assert.equal(command.file, "cmd.exe");
  assert.deepEqual(command.args, ["/d", "/s", "/c", "codex", "app-server"]);
});

test("resolveAppServerCommand returns direct codex command on non-windows", () => {
  const command = resolveAppServerCommand("linux");

  assert.equal(command.file, "codex");
  assert.deepEqual(command.args, ["app-server"]);
});

test("json-rpc inbound helpers classify response, request and notification", () => {
  const response = { jsonrpc: "2.0", id: 1, result: { ok: true } };
  const request = { jsonrpc: "2.0", id: 2, method: "thread/read", params: { threadId: "thread-1" } };
  const notification = { jsonrpc: "2.0", method: "thread/started", params: { threadId: "thread-1" } };

  assert.equal(isResponse(response), true);
  assert.equal(isServerRequest(response), false);
  assert.equal(isNotification(response), false);

  assert.equal(isResponse(request), false);
  assert.equal(isServerRequest(request), true);
  assert.equal(isNotification(request), false);

  assert.equal(isResponse(notification), false);
  assert.equal(isServerRequest(notification), false);
  assert.equal(isNotification(notification), true);
});

import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import test from "node:test";
import { attachGatewayClientSocket, summarizeInboundMessage } from "../dist/server/clientConnection.js";

test("summarizeInboundMessage redacts prompt text and pair token", () => {
  const summary = summarizeInboundMessage(JSON.stringify({
    type: "send_prompt",
    threadId: "thread-1",
    text: "secret prompt content",
    pairToken: "secret-token",
  }));

  assert.equal(summary, "type=send_prompt thread=thread-1 textLen=21");
  assert.equal(summary.includes("secret"), false);
});

test("summarizeInboundMessage includes routing metadata only", () => {
  const summary = summarizeInboundMessage(JSON.stringify({
    type: "hello",
    client: "android",
    selectedThreadId: "thread-2",
    capabilities: ["snapshot_patch"],
  }));

  assert.equal(summary, "type=hello capabilities=1");
});

test("summarizeInboundMessage handles invalid json", () => {
  assert.equal(summarizeInboundMessage("{bad"), "invalid-json");
});

test("attachGatewayClientSocket serializes inbound messages per client", async () => {
  const socket = new EventEmitter();
  const calls = [];
  let releaseFirst;
  const firstDone = new Promise((resolve) => {
    releaseFirst = resolve;
  });

  attachGatewayClientSocket({
    context: { socket },
    handleMessage: async (_context, raw) => {
      calls.push(`start:${raw}`);
      if (raw === "first") {
        await firstDone;
      }
      calls.push(`end:${raw}`);
    },
    onClosed: () => {},
  });

  socket.emit("message", Buffer.from("first"));
  socket.emit("message", Buffer.from("second"));
  await Promise.resolve();
  await Promise.resolve();

  assert.deepEqual(calls, ["start:first"]);

  releaseFirst();
  await waitFor(() => calls.length === 4);

  assert.deepEqual(calls, ["start:first", "end:first", "start:second", "end:second"]);
});

async function waitFor(predicate) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    if (predicate()) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 1));
  }
  assert.fail("condition was not met");
}

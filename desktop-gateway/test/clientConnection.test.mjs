import assert from "node:assert/strict";
import test from "node:test";
import { summarizeInboundMessage } from "../dist/server/clientConnection.js";

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

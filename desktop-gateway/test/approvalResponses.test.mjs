import assert from "node:assert/strict";
import test from "node:test";
import { buildApprovalResponse } from "../dist/bridge/summaries.js";

test("buildApprovalResponse maps modern command and file approval decisions", () => {
  assert.deepEqual(buildApprovalResponse({ kind: "command", text: "" }, true), {
    decision: "accept",
  });
  assert.deepEqual(buildApprovalResponse({ kind: "file", text: "" }, false), {
    decision: "decline",
  });
});

test("buildApprovalResponse maps legacy approval decisions", () => {
  assert.deepEqual(buildApprovalResponse({ kind: "legacyCommand", text: "" }, true), {
    decision: "approved",
  });
  assert.deepEqual(buildApprovalResponse({ kind: "legacyPatch", text: "" }, false), {
    decision: "denied",
  });
});

test("buildApprovalResponse maps MCP elicitation actions", () => {
  assert.deepEqual(buildApprovalResponse({ kind: "mcpElicitation", text: "" }, false), {
    action: "decline",
    content: null,
    _meta: null,
  });
});

test("buildApprovalResponse grants requested permissions only when allowed", () => {
  assert.deepEqual(
    buildApprovalResponse(
      {
        kind: "permissions",
        text: "",
        permissions: { fileSystem: { read: ["D:/repo"], write: null }, network: { enabled: true } },
      },
      true
    ),
    {
      permissions: {
        fileSystem: { read: ["D:/repo"], write: null },
        network: { enabled: true },
      },
      scope: "turn",
    }
  );
  assert.deepEqual(
    buildApprovalResponse(
      {
        kind: "permissions",
        text: "",
        permissions: { fileSystem: { read: ["D:/repo"], write: null }, network: { enabled: true } },
      },
      false
    ),
    { permissions: { fileSystem: null, network: null }, scope: "turn" }
  );
});

test("buildApprovalResponse maps tool user input answers", () => {
  assert.deepEqual(
    buildApprovalResponse(
      {
        kind: "toolUserInput",
        text: "",
        questions: [{ id: "mode", options: [{ label: "快速", description: "继续" }] }],
      },
      true
    ),
    { answers: { mode: { answers: ["快速"] } } }
  );
  assert.deepEqual(buildApprovalResponse({ kind: "toolUserInput", text: "" }, false), {
    answers: {},
  });
});

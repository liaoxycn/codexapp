import assert from "node:assert/strict";
import test from "node:test";
import {
  isExpectedGatewayPath,
  normalizeGatewayPath,
  requestGatewayPath,
} from "../dist/server/gatewayPaths.js";

test("normalizeGatewayPath trims trailing slashes and preserves root", () => {
  assert.equal(normalizeGatewayPath("/mobile///"), "/mobile");
  assert.equal(normalizeGatewayPath("/"), "/");
});

test("requestGatewayPath resolves request pathname without trailing slash noise", () => {
  assert.equal(requestGatewayPath("/mobile///?foo=1"), "/mobile");
  assert.equal(requestGatewayPath(undefined), "/");
});

test("isExpectedGatewayPath compares normalized request path and gateway path", () => {
  assert.equal(isExpectedGatewayPath("/mobile/", "/mobile"), true);
  assert.equal(isExpectedGatewayPath("/other", "/mobile"), false);
});

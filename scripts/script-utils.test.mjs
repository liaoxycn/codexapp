import assert from "node:assert/strict";
import test from "node:test";
import { parseArgs } from "./script-utils.mjs";

test("parseArgs consumes multi-part release notes until the next known option", () => {
  const parsed = withArgv(
    ["-Version", "0.2.11", "-Notes", "## 本次更新", "- 修复发布脚本", "-VersionCode", "31"],
    () =>
      parseArgs(
        {
          Version: "",
          VersionCode: 0,
          Notes: "",
        },
        { consumeRestKeys: ["Notes"] }
      )
  );

  assert.equal(parsed.Version, "0.2.11");
  assert.equal(parsed.VersionCode, 31);
  assert.equal(parsed.Notes, "## 本次更新\n- 修复发布脚本");
});

test("parseArgs keeps normal single-value parsing strict", () => {
  const parsed = withArgv(["-OutputName", "CodexMobile.apk"], () =>
    parseArgs({
      OutputName: "",
    })
  );

  assert.equal(parsed.OutputName, "CodexMobile.apk");
});

function withArgv(args, callback) {
  const originalArgv = process.argv;
  process.argv = ["node", "script", ...args];
  try {
    return callback();
  } finally {
    process.argv = originalArgv;
  }
}

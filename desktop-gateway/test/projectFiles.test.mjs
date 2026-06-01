import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { listProjectFiles } from "../dist/bridge/projectFiles.js";

test("listProjectFiles only returns files under the requested project", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "codexapp-project-files-"));
  fs.mkdirSync(path.join(root, "src"), { recursive: true });
  fs.mkdirSync(path.join(root, "node_modules", "pkg"), { recursive: true });
  fs.writeFileSync(path.join(root, "README.md"), "readme");
  fs.writeFileSync(path.join(root, "src", "App.ts"), "app");
  fs.writeFileSync(path.join(root, "node_modules", "pkg", "index.js"), "ignored");

  const files = listProjectFiles(root);

  assert.deepEqual(
    files.map((file) => file.label).sort(),
    ["README.md", "src/App.ts"]
  );
  assert.equal(files.every((file) => path.relative(root, file.path).startsWith("..") === false), true);
});

test("listProjectFiles returns empty for blank or missing project cwd", () => {
  assert.deepEqual(listProjectFiles(""), []);
  assert.deepEqual(listProjectFiles(path.join(os.tmpdir(), "missing-codexapp-project")), []);
});

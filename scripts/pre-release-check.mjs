#!/usr/bin/env node
import path from "node:path";
import { fileURLToPath } from "node:url";
import { parseArgs, runChecked } from "./script-utils.mjs";

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const args = parseArgs(
  {
    devRun: false,
  },
  { booleanKeys: ["devRun"] }
);

const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
const node = process.execPath;

const steps = [
  {
    name: "Android compile + unit tests",
    cwd: rootDir,
    file: gradle,
    args: [":app:compileDebugKotlin", ":app:testDebugUnitTest"],
  },
  {
    name: "Android instrumentation compile",
    cwd: rootDir,
    file: gradle,
    args: [":app:compileDebugAndroidTestKotlin"],
  },
  {
    name: "Gateway TypeScript build",
    cwd: path.join(rootDir, "desktop-gateway"),
    file: "npm",
    args: ["run", "build"],
  },
  {
    name: "Gateway unit tests",
    cwd: rootDir,
    file: node,
    args: ["--test", "desktop-gateway/test/*.test.mjs"],
  },
  {
    name: "Gateway protocol selftest",
    cwd: path.join(rootDir, "desktop-gateway"),
    file: "npm",
    args: ["run", "protocol:selftest"],
  },
];

if (args.devRun) {
  steps.push({
    name: "Device dev-run",
    cwd: rootDir,
    file: node,
    args: ["scripts/dev-run.mjs"],
  });
}

for (const [index, step] of steps.entries()) {
  console.log(`\n[pre-release] ${index + 1}/${steps.length} ${step.name}`);
  await runChecked(step.file, step.args, {
    cwd: step.cwd,
    displayName: step.name,
  });
}

console.log("\n[pre-release] all checks passed");

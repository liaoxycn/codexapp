#!/usr/bin/env node
import { existsSync } from "node:fs";
import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { isBlank, parseArgs, runCapture, runChecked } from "./script-utils.mjs";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");
const options = parseArgs(
  {
    Version: "",
    VersionCode: 0,
    Notes: "",
    NotesFile: "",
    CommitMessage: "",
    Remote: "origin",
    Branch: "",
    RunChecks: false,
  },
  { booleanKeys: ["RunChecks"] }
);

async function main() {
  validateOptions();
  const version = normalizeVersion(options.Version);
  const tag = `v${version}`;
  const branch = options.Branch || await currentBranch();
  const notes = await resolveNotes();

  await ensureNoExistingTag(tag, options.Remote);
  if (options.RunChecks) {
    await runChecked(process.execPath, [path.join(scriptDir, "pre-release-check.mjs")], {
      cwd: root,
      displayName: "pre-release-check.mjs",
    });
  }

  await updateAndroidVersion(version, options.VersionCode);
  await writeFile(path.join(root, "docs", "RELEASE_NOTES.md"), notes, "utf8");

  await runChecked("git", ["add", "-A"], { cwd: root, displayName: "git add" });
  await runChecked("git", ["commit", "-m", options.CommitMessage || `Release ${tag}`], {
    cwd: root,
    displayName: "git commit",
  });
  await runChecked("git", ["push", options.Remote, branch], { cwd: root, displayName: "git push branch" });
  await runChecked("git", ["tag", "-a", tag, "-m", options.CommitMessage || `Release ${tag}`], {
    cwd: root,
    displayName: "git tag",
  });
  await runChecked("git", ["push", options.Remote, tag], { cwd: root, displayName: "git push tag" });

  console.log(`[github-release] pushed ${branch} and ${tag}`);
  console.log("[github-release] tag push triggers GitHub Actions release build; not waiting for workflow completion");
}

function validateOptions() {
  if (isBlank(options.Version)) {
    throw new Error("Missing required -Version, for example: -Version 0.2.1");
  }
  if (!Number.isInteger(options.VersionCode) || options.VersionCode <= 0) {
    throw new Error("Missing required positive integer -VersionCode, for example: -VersionCode 21");
  }
  if (isBlank(options.Notes) && isBlank(options.NotesFile)) {
    throw new Error("Missing release notes: pass -Notes \"...\" or -NotesFile docs/RELEASE_NOTES.md");
  }
}

function normalizeVersion(version) {
  const normalized = String(version).trim().replace(/^v/i, "");
  if (!/^\d+\.\d+\.\d+$/.test(normalized)) {
    throw new Error(`Version must be semver x.y.z: ${version}`);
  }
  return normalized;
}

async function resolveNotes() {
  const notes = isBlank(options.NotesFile)
    ? options.Notes
    : await readFile(path.resolve(root, options.NotesFile), "utf8");
  const trimmed = notes.trim();
  if (trimmed.length < 8) {
    throw new Error("Release notes are too short");
  }
  return `${trimmed}\n`;
}

async function currentBranch() {
  const result = await runCapture("git", ["branch", "--show-current"], { cwd: root });
  if (result.code !== 0) {
    throw new Error(result.stderr || "Failed to read current git branch");
  }
  const branch = result.stdout.trim();
  if (!branch) {
    throw new Error("Cannot release from detached HEAD; pass -Branch explicitly after checking out a branch");
  }
  return branch;
}

async function ensureNoExistingTag(tag, remote) {
  const local = await runCapture("git", ["rev-parse", "-q", "--verify", `refs/tags/${tag}`], { cwd: root });
  if (local.code === 0) {
    throw new Error(`Local tag already exists: ${tag}`);
  }
  const remoteTag = await runCapture("git", ["ls-remote", "--tags", remote, tag], { cwd: root });
  if (remoteTag.code !== 0) {
    throw new Error(remoteTag.stderr || `Failed to query remote tag ${tag}`);
  }
  if (remoteTag.stdout.trim()) {
    throw new Error(`Remote tag already exists: ${tag}`);
  }
}

async function updateAndroidVersion(version, versionCode) {
  const gradlePath = path.join(root, "app", "build.gradle.kts");
  if (!existsSync(gradlePath)) {
    throw new Error(`Missing Android build file: ${gradlePath}`);
  }
  const source = await readFile(gradlePath, "utf8");
  const next = source
    .replace(/versionCode\s*=\s*\d+/, `versionCode = ${versionCode}`)
    .replace(/versionName\s*=\s*"[^"]+"/, `versionName = "${version}"`);
  if (next === source) {
    throw new Error("Android version fields were not updated");
  }
  await writeFile(gradlePath, next, "utf8");
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});

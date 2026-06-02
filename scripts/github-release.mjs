#!/usr/bin/env node
import { existsSync } from "node:fs";
import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { isBlank, parseArgs, runCapture, runChecked } from "./script-utils.mjs";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");
const remote = "origin";
const pushRetries = 5;

async function main() {
  const options = parseReleaseArgs();
  validateOptions(options);
  const version = normalizeVersion(options.Version);
  const tag = `v${version}`;
  const branch = await currentBranch();
  const notes = await resolveNotes(options);
  const commitMessage = `Release ${tag}`;
  const releaseState = await inspectReleaseState(tag, remote, pushRetries);

  if (!releaseState.hasLocalTag) {
    await updateAndroidVersion(version, options.VersionCode);
    await writeFile(path.join(root, "docs", "RELEASE_NOTES.md"), notes, "utf8");

    await runChecked("git", ["add", "-A"], { cwd: root, displayName: "git add" });
    await runChecked("git", ["commit", "-m", commitMessage], {
      cwd: root,
      displayName: "git commit",
    });
  } else {
    console.log(`[github-release] local ${tag} already points at HEAD; resuming push only`);
  }
  await runCheckedWithRetry("git", ["push", remote, branch], {
    cwd: root,
    displayName: "git push branch",
    attempts: pushRetries,
  });
  if (!releaseState.hasLocalTag) {
    await runChecked("git", ["tag", "-a", tag, "-m", commitMessage], {
      cwd: root,
      displayName: "git tag",
    });
  }
  await runCheckedWithRetry("git", ["push", remote, tag], {
    cwd: root,
    displayName: "git push tag",
    attempts: pushRetries,
  });

  console.log(`[github-release] pushed ${branch} and ${tag}`);
  console.log("[github-release] tag push triggers GitHub Actions release build; not waiting for workflow completion");
}

async function runCheckedWithRetry(file, args, { attempts, displayName, cwd }) {
  const total = Math.max(1, Number.isInteger(attempts) ? attempts : 1);
  let lastError;
  for (let attempt = 1; attempt <= total; attempt += 1) {
    try {
      await runChecked(file, args, { cwd, displayName });
      return;
    } catch (error) {
      lastError = error;
      if (attempt >= total) {
        break;
      }
      console.warn(`[github-release] ${displayName} failed, retry ${attempt + 1}/${total}: ${error.message}`);
    }
  }
  throw lastError;
}

function validateOptions(options) {
  if (isBlank(options.Version)) {
    throw new Error("Missing required -Version, for example: -Version 0.2.1");
  }
  if (!Number.isInteger(options.VersionCode) || options.VersionCode <= 0) {
    throw new Error("Missing required positive integer -VersionCode, for example: -VersionCode 21");
  }
  if (isBlank(options.Notes)) {
    throw new Error("Missing release notes: pass -Notes \"...\"");
  }
}

function parseReleaseArgs() {
  return parseArgs({
    Version: "",
    VersionCode: 0,
    Notes: "",
  });
}

function normalizeVersion(version) {
  const normalized = String(version).trim().replace(/^v/i, "");
  if (!/^\d+\.\d+\.\d+$/.test(normalized)) {
    throw new Error(`Version must be semver x.y.z: ${version}`);
  }
  return normalized;
}

async function resolveNotes(options) {
  const notes = options.Notes;
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
    throw new Error("Cannot release from detached HEAD; check out a branch before release");
  }
  return branch;
}

async function inspectReleaseState(tag, remote, attempts) {
  const remoteTag = await runCaptureWithRetry("git", ["ls-remote", "--tags", remote, tag], {
    cwd: root,
    displayName: "git query remote tag",
    attempts,
  });
  if (remoteTag.stdout.trim()) {
    throw new Error(`Remote tag already exists: ${tag}`);
  }
  const localTag = await runCapture("git", ["rev-parse", "-q", "--verify", `refs/tags/${tag}`], { cwd: root });
  if (localTag.code !== 0) {
    return { hasLocalTag: false };
  }
  const tagCommit = await runCapture("git", ["rev-list", "-n", "1", tag], { cwd: root });
  if (tagCommit.code !== 0) {
    throw new Error(tagCommit.stderr || `Failed to resolve local tag: ${tag}`);
  }
  const head = await runCapture("git", ["rev-parse", "HEAD"], { cwd: root });
  if (head.code !== 0) {
    throw new Error(head.stderr || "Failed to resolve HEAD");
  }
  if (tagCommit.stdout.trim() !== head.stdout.trim()) {
    throw new Error(`Local tag ${tag} exists but does not point at HEAD`);
  }
  return { hasLocalTag: true };
}

async function runCaptureWithRetry(file, args, { attempts, displayName, cwd }) {
  const total = Math.max(1, Number.isInteger(attempts) ? attempts : 1);
  let lastResult;
  for (let attempt = 1; attempt <= total; attempt += 1) {
    const result = await runCapture(file, args, { cwd });
    if (result.code === 0) {
      return result;
    }
    lastResult = result;
    if (attempt < total) {
      const detail = (result.stderr || result.stdout || `exit ${result.code}`).trim();
      console.warn(`[github-release] ${displayName} failed, retry ${attempt + 1}/${total}: ${detail}`);
    }
  }
  throw new Error(lastResult?.stderr || lastResult?.stdout || `${displayName} failed`);
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
  console.error(`Error: ${error.message}`);
  process.exitCode = 1;
});

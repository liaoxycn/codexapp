#!/usr/bin/env node
import { existsSync } from "node:fs";
import { appendFile, mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { isBlank, parseArgs, runCapture } from "./script-utils.mjs";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");
const remote = "origin";
const pushRetries = 3;

async function main() {
  const logger = await createReleaseLogger().catch(() => createConsoleLogger());
  try {
    await runRelease(logger);
  } catch (error) {
    await logger.log(`ERROR ${error.stack || error.message}`);
    await logger.log("externalExitCode=0 reason=internal-error-recorded");
    console.error(`[github-release] internal error recorded; see ${logger.relativePath}`);
  } finally {
    await logger.log("externalExitCode=0");
    await logger.log("END");
    console.log(`[github-release] log: ${logger.relativePath}`);
  }
}

async function runRelease(logger) {
  await logger.log("START");
  const options = parseReleaseArgs();
  validateOptions(options);
  const version = normalizeVersion(options.Version);
  const tag = `v${version}`;
  const branch = await currentBranch(logger);
  const notes = await resolveNotes(options);
  const commitMessage = `Release ${tag}`;
  await logger.log(`release version=${version} versionCode=${options.VersionCode} tag=${tag} branch=${branch} notesLength=${notes.length}`);
  const releaseState = await inspectReleaseState(tag, remote, pushRetries, logger);

  if (releaseState.hasRemoteTag) {
    await logger.log(`remote tag already exists; treat release as already triggered tag=${tag}`);
    console.log(`[github-release] ${tag} already exists on ${remote}; nothing to trigger`);
    return;
  }

  if (!releaseState.hasLocalTag) {
    await logger.log("update Android version and release notes");
    await updateAndroidVersion(version, options.VersionCode);
    await writeFile(path.join(root, "docs", "RELEASE_NOTES.md"), notes, "utf8");

    await runCheckedLogged("git", ["add", "-A"], { cwd: root, displayName: "git add", logger });
    await runCheckedLogged("git", ["commit", "-m", commitMessage], {
      cwd: root,
      displayName: "git commit",
      logger,
    });
  } else {
    const message = `local ${tag} already points at HEAD; resuming push only`;
    await logger.log(message);
    console.log(`[github-release] ${message}`);
  }
  const branchPushed = await runBestEffortWithRetry("git", ["push", remote, branch], {
    cwd: root,
    displayName: "git push branch",
    attempts: pushRetries,
    logger,
  });
  if (!releaseState.hasLocalTag) {
    await runCheckedLogged("git", ["tag", "-a", tag, "-m", commitMessage], {
      cwd: root,
      displayName: "git tag",
      logger,
    });
  }
  const tagPushed = await runBestEffortWithRetry("git", ["push", remote, tag], {
    cwd: root,
    displayName: "git push tag",
    attempts: pushRetries,
    logger,
  });

  await logger.log(`push result branch=${branchPushed ? "ok" : "failed"} tag=${tagPushed ? "ok" : "failed"}`);
  await logger.log(`githubActionsTrigger=${tagPushed ? "attempted" : "not-confirmed"}`);
  console.log(`[github-release] branch push: ${branchPushed ? "ok" : "failed, recorded in log"}`);
  console.log(`[github-release] tag push/action trigger: ${tagPushed ? "ok" : "failed after retries, recorded in log"}`);
  console.log("[github-release] not waiting for workflow completion");
}

async function createReleaseLogger() {
  const logDir = path.join(root, "scripts", "logs");
  await mkdir(logDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const filePath = path.join(logDir, `github-release-${stamp}.log`);
  return {
    filePath,
    relativePath: path.relative(root, filePath),
    async log(message) {
      await appendFile(filePath, `[${new Date().toISOString()}] ${message}\n`, "utf8");
    },
  };
}

function createConsoleLogger() {
  return {
    relativePath: "console",
    async log(message) {
      console.error(`[github-release-log] ${message}`);
    },
  };
}

async function runCheckedLogged(file, args, { displayName, cwd, logger }) {
  const result = await runCaptureLogged(file, args, { displayName, cwd, logger });
  if (result.code !== 0) {
    throw new Error(`${displayName || file} failed with exit code ${result.code}`);
  }
  return result;
}

async function runBestEffortWithRetry(file, args, { attempts, displayName, cwd, logger }) {
  const total = Math.max(1, Number.isInteger(attempts) ? attempts : 1);
  for (let attempt = 1; attempt <= total; attempt += 1) {
    const result = await runCaptureLogged(file, args, { cwd, displayName: `${displayName} attempt ${attempt}/${total}`, logger });
    if (result.code === 0) {
      return true;
    }
    const detail = (result.stderr || result.stdout || `exit ${result.code}`).trim();
    await logger.log(`${displayName} failed attempt=${attempt}/${total} detail=${detail}`);
    if (attempt < total) {
      console.warn(`[github-release] ${displayName} failed, retry ${attempt + 1}/${total}`);
    }
  }
  await logger.log(`${displayName} gave up after ${total} attempts`);
  return false;
}

async function runCaptureLogged(file, args, { displayName, cwd, logger }) {
  const label = displayName || `${file} ${args.join(" ")}`;
  await logger.log(`RUN ${label}: ${file} ${args.map(quoteArg).join(" ")}`);
  const result = await runCapture(file, args, { cwd });
  await logger.log(`EXIT ${label}: code=${result.code} signal=${result.signal ?? ""}`);
  if (result.stdout.trim()) {
    await logger.log(`STDOUT ${label}:\n${result.stdout.trim()}`);
  }
  if (result.stderr.trim()) {
    await logger.log(`STDERR ${label}:\n${result.stderr.trim()}`);
  }
  return result;
}

function quoteArg(value) {
  const text = String(value);
  return /\s/.test(text) ? JSON.stringify(text) : text;
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
  return parseArgs(
    {
      Version: "",
      VersionCode: 0,
      Notes: "",
    },
    { consumeRestKeys: ["Notes"] }
  );
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

async function currentBranch(logger) {
  const result = await runCaptureLogged("git", ["branch", "--show-current"], {
    cwd: root,
    displayName: "git current branch",
    logger,
  });
  if (result.code !== 0) {
    throw new Error(result.stderr || "Failed to read current git branch");
  }
  const branch = result.stdout.trim();
  if (!branch) {
    throw new Error("Cannot release from detached HEAD; check out a branch before release");
  }
  return branch;
}

async function inspectReleaseState(tag, remote, attempts, logger) {
  const remoteTag = await runCaptureWithRetry("git", ["ls-remote", "--tags", remote, tag], {
    cwd: root,
    displayName: "git query remote tag",
    attempts,
    logger,
  });
  if (remoteTag.stdout.trim()) {
    return { hasLocalTag: false, hasRemoteTag: true };
  }
  const localTag = await runCaptureLogged("git", ["rev-parse", "-q", "--verify", `refs/tags/${tag}`], {
    cwd: root,
    displayName: "git query local tag",
    logger,
  });
  if (localTag.code !== 0) {
    return { hasLocalTag: false, hasRemoteTag: false };
  }
  const tagCommit = await runCaptureLogged("git", ["rev-list", "-n", "1", tag], {
    cwd: root,
    displayName: "git resolve tag commit",
    logger,
  });
  if (tagCommit.code !== 0) {
    throw new Error(tagCommit.stderr || `Failed to resolve local tag: ${tag}`);
  }
  const head = await runCaptureLogged("git", ["rev-parse", "HEAD"], {
    cwd: root,
    displayName: "git resolve HEAD",
    logger,
  });
  if (head.code !== 0) {
    throw new Error(head.stderr || "Failed to resolve HEAD");
  }
  if (tagCommit.stdout.trim() !== head.stdout.trim()) {
    throw new Error(`Local tag ${tag} exists but does not point at HEAD`);
  }
  return { hasLocalTag: true, hasRemoteTag: false };
}

async function runCaptureWithRetry(file, args, { attempts, displayName, cwd, logger }) {
  const total = Math.max(1, Number.isInteger(attempts) ? attempts : 1);
  let lastResult;
  for (let attempt = 1; attempt <= total; attempt += 1) {
    const result = await runCaptureLogged(file, args, {
      cwd,
      displayName: `${displayName} attempt ${attempt}/${total}`,
      logger,
    });
    if (result.code === 0) {
      return result;
    }
    lastResult = result;
    if (attempt < total) {
      const detail = (result.stderr || result.stdout || `exit ${result.code}`).trim();
      await logger.log(`${displayName} failed attempt=${attempt}/${total} detail=${detail}`);
      console.warn(`[github-release] ${displayName} failed, retry ${attempt + 1}/${total}`);
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
  console.error(`[github-release] unexpected wrapper error recorded: ${error.message}`);
}).finally(() => {
  process.exitCode = 0;
});

#!/usr/bin/env node
import { existsSync } from "node:fs";
import { appendFile, mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { isBlank, parseArgs, runCapture } from "./script-utils.mjs";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");
// 发布脚本固定操作当前仓库的 origin 远端。
const remote = "origin";
// 分支和 tag 推送最多重试 3 次；失败只记录日志，对外仍返回 0。
const pushRetries = 3;

/**
 * 脚本总入口：创建日志器和摘要对象，运行发布流程。
 * 这里会吞掉内部错误并把外部退出码保持为 0，避免发布脚本失败中断调用方。
 */
async function main() {
  const logger = await createReleaseLogger().catch(() => createConsoleLogger());
  const summary = createReleaseSummary(logger);
  try {
    await runRelease(logger, summary);
  } catch (error) {
    // 所有未处理异常都写入日志和 latest 摘要，但不向外抛出。
    summary.status = "internal-error-recorded";
    summary.error = error.stack || error.message;
    await logger.log(`ERROR ${error.stack || error.message}`);
    await logger.log("externalExitCode=0 reason=internal-error-recorded");
    console.error(`[github-release] internal error recorded; see ${logger.relativePath}`);
  } finally {
    // 不管成功、失败还是提前返回，都落一份最新摘要，方便排查最近一次发布。
    summary.finishedAt = new Date().toISOString();
    summary.externalExitCode = 0;
    await writeLatestSummary(summary, logger);
    await logger.log("externalExitCode=0");
    await logger.log("END");
    console.log(`[github-release] log: ${logger.relativePath}`);
  }
}

/**
 * 发布主流程：
 * 1. 解析参数和当前 Git 状态。
 * 2. 更新 Android 版本号与 release notes。
 * 3. 提交改动、打 tag、推送分支和 tag。
 * 4. tag 推送成功后由 GitHub Actions 自动触发发布流水线。
 */
async function runRelease(logger, summary) {
  await logger.log("START");
  const options = parseReleaseArgs();
  validateOptions(options);

  // 版本输入支持带 v 前缀，但内部统一用纯 x.y.z；Git tag 再补 v 前缀。
  const version = normalizeVersion(options.Version);
  const tag = `v${version}`;
  const branch = await currentBranch(logger);
  const notes = await resolveNotes(options);
  const commitMessage = `Release ${tag}`;

  // 先写入运行中摘要，后续步骤再不断补充推送结果和最终状态。
  Object.assign(summary, {
    status: "running",
    version,
    versionCode: options.VersionCode,
    tag,
    branch,
    notesLength: notes.length,
  });
  await logger.log(`release version=${version} versionCode=${options.VersionCode} tag=${tag} branch=${branch} notesLength=${notes.length}`);

  // 先检查本地/远端 tag，支持失败后重复执行脚本恢复发布。
  const releaseState = await inspectReleaseState(tag, remote, pushRetries, logger);
  summary.releaseState = releaseState;

  if (releaseState.hasRemoteTag) {
    // 远端 tag 已存在，说明 GitHub Actions 至少已经有机会被触发，脚本直接视为完成。
    summary.status = "already-triggered";
    summary.githubActionsTrigger = "already-triggered";
    await logger.log(`remote tag already exists; treat release as already triggered tag=${tag}`);
    console.log(`[github-release] ${tag} already exists on ${remote}; nothing to trigger`);
    return;
  }

  if (!releaseState.hasLocalTag) {
    // 首次发布：更新版本文件和更新说明，再把当前工作区整体提交为 release commit。
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
    // 本地 tag 已存在且指向 HEAD 时，不重复改文件/提交/tag，只继续补推送。
    const message = `local ${tag} already points at HEAD; resuming push only`;
    await logger.log(message);
    console.log(`[github-release] ${message}`);
  }

  // 先推分支，确保 release commit 到远端；失败不抛错，只记录结果。
  const branchPushed = await runBestEffortWithRetry("git", ["push", remote, branch], {
    cwd: root,
    displayName: "git push branch",
    attempts: pushRetries,
    logger,
  });
  if (!releaseState.hasLocalTag) {
    // 分支推送尝试后再创建 tag；tag 推送是触发 GitHub Actions 的关键动作。
    await runCheckedLogged("git", ["tag", "-a", tag, "-m", commitMessage], {
      cwd: root,
      displayName: "git tag",
      logger,
    });
  }

  // tag 推送成功通常会触发发布流水线；脚本不等待 Actions 执行完成。
  const tagPushed = await runBestEffortWithRetry("git", ["push", remote, tag], {
    cwd: root,
    displayName: "git push tag",
    attempts: pushRetries,
    logger,
  });
  summary.branchPushed = branchPushed;
  summary.tagPushed = tagPushed;
  summary.githubActionsTrigger = tagPushed ? "attempted" : "not-confirmed";
  summary.status = tagPushed ? "triggered" : "push-failed-recorded";

  await logger.log(`push result branch=${branchPushed ? "ok" : "failed"} tag=${tagPushed ? "ok" : "failed"}`);
  await logger.log(`githubActionsTrigger=${tagPushed ? "attempted" : "not-confirmed"}`);
  console.log(`[github-release] branch push: ${branchPushed ? "ok" : "failed, recorded in log"}`);
  console.log(`[github-release] tag push/action trigger: ${tagPushed ? "ok" : "failed after retries, recorded in log"}`);
  console.log("[github-release] not waiting for workflow completion");
}

/**
 * 创建文件日志器，日志路径形如 scripts/release/github-release-时间戳.log。
 */
async function createReleaseLogger() {
  const logDir = path.join(root, "scripts", "release");
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

/**
 * 创建本次发布的结构化摘要，最后会写入 github-release-latest.json。
 */
function createReleaseSummary(logger) {
  return {
    startedAt: new Date().toISOString(),
    finishedAt: null,
    status: "starting",
    logPath: logger.relativePath,
    externalExitCode: 0,
  };
}

/**
 * 写入最近一次发布摘要；console fallback 模式没有文件路径，直接跳过。
 */
async function writeLatestSummary(summary, logger) {
  if (logger.relativePath === "console") {
    return;
  }
  const summaryPath = path.join(root, "scripts", "release", "github-release-latest.json");
  await writeFile(summaryPath, `${JSON.stringify(summary, null, 2)}\n`, "utf8");
}

/**
 * 文件日志创建失败时的降级日志器，把日志写到 stderr。
 */
function createConsoleLogger() {
  return {
    relativePath: "console",
    async log(message) {
      console.error(`[github-release-log] ${message}`);
    },
  };
}

/**
 * 执行命令并要求退出码为 0；失败时抛错进入 main 的统一记录逻辑。
 */
async function runCheckedLogged(file, args, { displayName, cwd, logger }) {
  const result = await runCaptureLogged(file, args, { displayName, cwd, logger });
  if (result.code !== 0) {
    throw new Error(`${displayName || file} failed with exit code ${result.code}`);
  }
  return result;
}

/**
 * 执行允许失败的命令并按次数重试。
 * 用于 git push 这类网络相关步骤：最终失败只返回 false，不中断脚本。
 */
async function runBestEffortWithRetry(file, args, { attempts, displayName, cwd, logger }) {
  const total = Math.max(1, Number.isInteger(attempts) ? attempts : 1);
  for (let attempt = 1; attempt <= total; attempt += 1) {
    const result = await runCaptureLogged(file, args, { cwd, displayName: `${displayName} attempt ${attempt}/${total}`, logger });
    if (result.code === 0) {
      return true;
    }
    // 保存失败详情到日志，控制台只提示重试进度。
    const detail = (result.stderr || result.stdout || `exit ${result.code}`).trim();
    await logger.log(`${displayName} failed attempt=${attempt}/${total} detail=${detail}`);
    if (attempt < total) {
      console.warn(`[github-release] ${displayName} failed, retry ${attempt + 1}/${total}`);
    }
  }
  await logger.log(`${displayName} gave up after ${total} attempts`);
  return false;
}

/**
 * 执行命令、捕获 stdout/stderr，并把命令、退出码、输出全部写入日志。
 */
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

/**
 * 日志展示命令参数时，如果参数包含空白，就用 JSON 字符串包起来。
 */
function quoteArg(value) {
  const text = String(value);
  return /\s/.test(text) ? JSON.stringify(text) : text;
}

/**
 * 校验命令行参数是否满足发布最低要求。
 */
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

/**
 * 解析发布参数：
 * -Version 版本号，-VersionCode Android 整数版本号，-Notes 更新说明。
 */
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

/**
 * 统一版本号格式：允许用户输入 v0.2.1，但要求最终必须是 x.y.z。
 */
function normalizeVersion(version) {
  const normalized = String(version).trim().replace(/^v/i, "");
  if (!/^\d+\.\d+\.\d+$/.test(normalized)) {
    throw new Error(`Version must be semver x.y.z: ${version}`);
  }
  return normalized;
}

/**
 * 整理更新说明，并拒绝过短内容，避免发布出空说明。
 */
async function resolveNotes(options) {
  const notes = options.Notes;
  const trimmed = notes.trim();
  if (trimmed.length < 8) {
    throw new Error("Release notes are too short");
  }
  return `${trimmed}\n`;
}

/**
 * 读取当前 Git 分支；发布必须在普通分支上执行，不能在 detached HEAD 上执行。
 */
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

/**
 * 检查发布 tag 的状态，用来决定是首次发布、恢复推送，还是远端已触发过。
 */
async function inspectReleaseState(tag, remote, attempts, logger) {
  // 先查远端 tag：远端存在时说明发布触发动作已经完成，不再动本地文件。
  const remoteTag = await runCaptureWithRetry("git", ["ls-remote", "--tags", remote, tag], {
    cwd: root,
    displayName: "git query remote tag",
    attempts,
    logger,
  });
  if (remoteTag.stdout.trim()) {
    return { hasLocalTag: false, hasRemoteTag: true };
  }

  // 再查本地 tag：本地没有 tag 时就是首次发布。
  const localTag = await runCaptureLogged("git", ["rev-parse", "-q", "--verify", `refs/tags/${tag}`], {
    cwd: root,
    displayName: "git query local tag",
    logger,
  });
  if (localTag.code !== 0) {
    return { hasLocalTag: false, hasRemoteTag: false };
  }

  // 本地 tag 必须指向当前 HEAD，才允许把它当作“上次失败后继续推送”。
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

/**
 * 执行必须成功的命令并按次数重试。
 * 与 runBestEffortWithRetry 不同，重试耗尽后会抛错。
 */
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
    // 只在还有下一次机会时记录重试提示；最后一次失败由下方统一抛错。
    if (attempt < total) {
      const detail = (result.stderr || result.stdout || `exit ${result.code}`).trim();
      await logger.log(`${displayName} failed attempt=${attempt}/${total} detail=${detail}`);
      console.warn(`[github-release] ${displayName} failed, retry ${attempt + 1}/${total}`);
    }
  }
  throw new Error(lastResult?.stderr || lastResult?.stdout || `${displayName} failed`);
}

/**
 * 更新 Android app/build.gradle.kts 内的 versionCode 和 versionName。
 */
async function updateAndroidVersion(version, versionCode) {
  const gradlePath = path.join(root, "app", "build.gradle.kts");
  if (!existsSync(gradlePath)) {
    throw new Error(`Missing Android build file: ${gradlePath}`);
  }
  const source = await readFile(gradlePath, "utf8");
  // 用正则只替换版本字段，保留 Gradle 文件其他内容和格式。
  const next = source
    .replace(/versionCode\s*=\s*\d+/, `versionCode = ${versionCode}`)
    .replace(/versionName\s*=\s*"[^"]+"/, `versionName = "${version}"`);
  if (next === source) {
    throw new Error("Android version fields were not updated");
  }
  await writeFile(gradlePath, next, "utf8");
}

// 最外层兜底：即使 main 自身出现意外，也保持退出码为 0。
main().catch((error) => {
  console.error(`[github-release] unexpected wrapper error recorded: ${error.message}`);
}).finally(() => {
  process.exitCode = 0;
});

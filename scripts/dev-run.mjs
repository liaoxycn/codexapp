import { createWriteStream, existsSync } from "node:fs";
import { appendFile, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  assertExists,
  ensureDir,
  getAdbPath,
  getEmulatorPath,
  parseArgs,
  readTrim,
  runCapture,
  runChecked,
  sleep,
  startProcess,
  waitForPort,
  writeEmpty,
} from "./script-utils.mjs";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");
const logsDir = path.join(scriptDir, "logs");
const paths = {
  gatewayLog: path.join(logsDir, "gateway-dev.log"),
  gatewayErr: path.join(logsDir, "gateway-dev.err.log"),
  scriptLog: path.join(logsDir, "dev-run.log"),
  buildLog: path.join(logsDir, "apk-build.log"),
  buildErr: path.join(logsDir, "apk-build.err.log"),
  buildExitCode: path.join(logsDir, "apk-build.exitcode"),
  debugApk: path.join(root, "app", "build", "outputs", "apk", "debug", "app-debug.apk"),
};

const options = parseArgs(
  {
    GatewayDir: path.join(scriptDir, "..", "desktop-gateway"),
    AppId: "com.codexapp",
    Activity: ".MainActivity",
    GatewayHost: "127.0.0.1",
    GatewayBindHost: "0.0.0.0",
    GatewayPort: 8765,
    GatewayPath: "/mobile",
    AvdName: "codexflow_api35",
    FinishDelaySeconds: 8,
    BuildTimeoutMinutes: 15,
    EmulatorTimeoutMinutes: 4,
    SkipOpenApp: false,
  },
  { booleanKeys: ["SkipOpenApp"] }
);

async function main() {
  await ensureDir(logsDir);
  await writeEmpty(paths.scriptLog);

  try {
    const adb = getAdbPath();
    const emulator = getEmulatorPath();

    log("1/4 start emulator and build APK in parallel");
    await startEmulatorIfNeeded(options.AvdName, adb, emulator);
    const build = await startDebugApkBuild();
    await waitForBuildAndEmulator(build, adb);

    log("2/4 restart gateway dev");
    await stopGatewayProcesses();
    await sleep(800);
    await writeEmpty(paths.gatewayLog);
    await writeEmpty(paths.gatewayErr);

    const gatewayRoot = path.resolve(options.GatewayDir);
    startDetachedGatewayProcess(gatewayRoot);
    log(`waiting for gateway on ${options.GatewayHost}:${options.GatewayPort}`);
    if (!(await waitForPort(options.GatewayHost, options.GatewayPort, 50_000))) {
      throw new Error(`gateway not listening within 50 seconds: ${options.GatewayHost}:${options.GatewayPort}`);
    }
    if (!(await waitForPort(options.GatewayHost, options.GatewayPort, 1_000))) {
      throw new Error(`gateway port check failed: ${options.GatewayHost}:${options.GatewayPort}`);
    }

    log("3/4 install debug apk");
    assertExists(paths.debugApk, "debug APK not found after build");
    const deviceSerial = await getRunningEmulatorSerial(adb);
    if (!deviceSerial) {
      throw new Error("no booted emulator device found for install");
    }
    await runChecked(adb, ["-s", deviceSerial, "install", "-r", paths.debugApk], {
      cwd: root,
      displayName: "adb install",
    });

    log("4/4 open app");
    if (!options.SkipOpenApp) {
      await runChecked(adb, ["-s", deviceSerial, "shell", "am", "start", "-n", `${options.AppId}/${options.Activity}`], {
        cwd: root,
        displayName: "adb start activity",
      });
    }

    await tailLog(paths.gatewayLog, 20);
    log("all steps done, waiting before exit");
    await sleep(Number(options.FinishDelaySeconds) * 1000);
    log("done");
  } catch (error) {
    await writeErrorDetails(error);
    await writeLogSectionFromFile(paths.gatewayLog);
    await writeLogSectionFromFile(paths.gatewayErr);
    await writeLogSectionFromFile(paths.buildLog);
    await writeLogSectionFromFile(paths.buildErr);
    throw error;
  }
}

async function startDebugApkBuild() {
  log("Starting debug APK build");
  await writeEmpty(paths.buildLog);
  await writeEmpty(paths.buildErr);
  if (existsSync(paths.buildExitCode)) {
    await rm(paths.buildExitCode, { force: true });
  }

  const child = startLoggedProcess(path.join(root, "gradlew.bat"), [":app:assembleDebug", "--console=plain"], {
    cwd: root,
    stdoutPath: paths.buildLog,
    stderrPath: paths.buildErr,
  });
  child.on("close", async (code) => {
    await writeFile(paths.buildExitCode, `${code ?? -1}\n`, "utf8");
  });
  return child;
}

function startLoggedProcess(file, args, { cwd, env, detached = false, stdoutPath, stderrPath }) {
  const stdout = createWriteStream(stdoutPath, { flags: "a" });
  const stderr = createWriteStream(stderrPath, { flags: "a" });
  const child = startProcess(file, args, {
    cwd,
    env,
    detached,
    stdio: ["ignore", "pipe", "pipe"],
  });
  child.stdout.pipe(stdout);
  child.stderr.pipe(stderr);
  child.on("error", (error) => {
    stderr.write(`${error.stack || error.message}\n`);
  });
  child.on("close", () => {
    stdout.end();
    stderr.end();
  });
  return child;
}

function startDetachedGatewayProcess(gatewayRoot) {
  const child = startProcess(
    process.execPath,
    [path.join(scriptDir, "run-gateway-dev.mjs"), gatewayRoot, paths.gatewayLog, paths.gatewayErr],
    {
      cwd: root,
      detached: true,
      stdio: "ignore",
      env: gatewayEnv(),
    }
  );
  child.unref();
  return child;
}

function gatewayEnv() {
  return {
    ...process.env,
    CODEX_MOBILE_GATEWAY_HOST: String(options.GatewayBindHost).trim(),
    CODEX_MOBILE_GATEWAY_PORT: String(options.GatewayPort),
    CODEX_MOBILE_GATEWAY_PATH: String(options.GatewayPath).trim(),
  };
}

async function waitForBuildAndEmulator(build, adb) {
  let buildDone = false;
  let emulatorDone = false;
  const buildDeadline = Date.now() + Number(options.BuildTimeoutMinutes) * 60_000;
  const emulatorDeadline = Date.now() + Number(options.EmulatorTimeoutMinutes) * 60_000;

  while (!(buildDone && emulatorDone)) {
    if (!buildDone) {
      if (build.exitCode !== null) {
        const exitCode = await readBuildExitCode();
        if (exitCode !== 0) {
          throw new Error(`debug APK build failed with exit code ${exitCode}`);
        }
        buildDone = true;
        log("Debug APK build done");
      } else if (Date.now() > buildDeadline) {
        await stopProcessTree(build.pid);
        throw new Error(`debug APK build timeout after ${options.BuildTimeoutMinutes} minutes`);
      }
    }

    if (!emulatorDone) {
      if (await testEmulatorBooted(adb)) {
        emulatorDone = true;
        log("Emulator boot done");
      } else if (Date.now() > emulatorDeadline) {
        await stopProcessTree(build.pid);
        throw new Error(`Emulator boot timeout after ${options.EmulatorTimeoutMinutes} minutes`);
      }
    }

    if (!(buildDone && emulatorDone)) {
      await sleep(2_000);
    }
  }
}

async function readBuildExitCode() {
  for (let i = 0; i < 10; i += 1) {
    if (existsSync(paths.buildExitCode)) {
      const text = await readTrim(paths.buildExitCode);
      const exitCode = Number.parseInt(text, 10);
      if (Number.isInteger(exitCode)) {
        return exitCode;
      }
      throw new Error(`debug APK build wrote invalid exit code: ${text}`);
    }
    await sleep(100);
  }
  throw new Error(`debug APK build finished but exit code file was not written: ${paths.buildExitCode}`);
}

async function startEmulatorIfNeeded(avdName, adb, emulator) {
  const serial = await getAnyEmulatorSerial(adb);
  if (serial) {
    log(`Emulator already running: ${serial}`);
    return;
  }

  log(`Starting emulator ${avdName}`);
  const child = startProcess(emulator, ["-avd", avdName, "-netdelay", "none", "-netspeed", "full"], {
    detached: true,
    stdio: "ignore",
  });
  child.unref();
}

async function getRunningEmulatorSerial(adb) {
  const { stdout } = await runCapture(adb, ["devices"]);
  for (const line of stdout.split(/\r?\n/)) {
    const match = line.match(/^(emulator-\d+)\s+device\b/);
    if (match) {
      return match[1];
    }
  }
  return null;
}

async function getAnyEmulatorSerial(adb) {
  const { stdout } = await runCapture(adb, ["devices"]);
  for (const line of stdout.split(/\r?\n/)) {
    const match = line.match(/^(emulator-\d+)\s+/);
    if (match) {
      return match[1];
    }
  }
  return null;
}

async function testEmulatorBooted(adb) {
  const serial = await getRunningEmulatorSerial(adb);
  if (!serial) {
    return false;
  }
  const { stdout } = await runCapture(adb, ["-s", serial, "shell", "getprop", "sys.boot_completed"]);
  return stdout.trim() === "1";
}

async function stopGatewayProcesses() {
  if (process.platform === "win32") {
    const query = await runCapture("powershell.exe", [
      "-NoProfile",
      "-Command",
      "Get-CimInstance Win32_Process -Filter \"Name = 'node.exe'\" | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress",
    ]);
    for (const pid of parseGatewayPidsFromJson(query.stdout)) {
      await runCapture("taskkill.exe", ["/PID", pid, "/F", "/T"]);
    }
    return;
  }
  const query = await runCapture("pgrep", ["-af", "desktop-gateway"]);
  for (const line of query.stdout.split(/\r?\n/)) {
    const match = line.match(/^(\d+)\s+.*desktop-gateway/);
    if (match && Number(match[1]) !== process.pid) {
      await runCapture("kill", ["-TERM", match[1]]);
    }
  }
}

function parseGatewayPidsFromJson(json) {
  const pids = [];
  if (!json.trim()) {
    return pids;
  }

  const parsed = JSON.parse(json);
  const processes = Array.isArray(parsed) ? parsed : [parsed];
  for (const item of processes) {
    const commandLine = String(item.CommandLine || "");
    const pid = Number(item.ProcessId);
    if (commandLine.includes("desktop-gateway") && pid !== process.pid) {
      pids.push(String(pid));
    }
  }
  return pids;
}

async function stopProcessTree(pid) {
  if (!pid) {
    return;
  }
  const result =
    process.platform === "win32"
      ? await runCapture("taskkill.exe", ["/PID", String(pid), "/T", "/F"])
      : await runCapture("kill", ["-TERM", String(pid)]);
  if (result.code !== 0) {
    log(`WARN: failed to stop process tree PID ${pid}`);
    for (const line of `${result.stdout}${result.stderr}`.split(/\r?\n/).filter(Boolean)) {
      log(`WARN: ${line}`);
    }
  }
}

async function tailLog(filePath, lines = 80) {
  if (!existsSync(filePath)) {
    return;
  }
  console.log("");
  console.log(`---- ${filePath} ----`);
  const content = await readFile(filePath, "utf8");
  console.log(content.split(/\r?\n/).slice(-lines).join("\n"));
}

async function writeLogSectionFromFile(filePath, lines = 80) {
  if (!existsSync(filePath)) {
    return;
  }
  const header = `---- ${filePath} ----`;
  console.log("");
  console.log(header);
  await appendFile(paths.scriptLog, `\n${header}\n`, "utf8");
  const content = await readFile(filePath, "utf8");
  const tail = content.split(/\r?\n/).slice(-lines).join("\n");
  console.log(tail);
  await appendFile(paths.scriptLog, `${tail}\n`, "utf8");
}

async function writeErrorDetails(error) {
  log(`FAILED: ${error.message}`);
  log(`ERROR_TYPE: ${error?.constructor?.name || "Error"}`);
  if (error.stack) {
    await appendFile(paths.scriptLog, `STACK:\n${error.stack}\n`, "utf8");
  }
}

function log(message) {
  const line = `[${new Date().toLocaleTimeString("zh-CN", { hour12: false })}] ${message}`;
  console.log(line);
  appendFile(paths.scriptLog, `${line}\n`, "utf8").catch(() => {});
}

main().catch(() => {
  process.exitCode = 1;
});

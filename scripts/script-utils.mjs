import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import net from "node:net";
import os from "node:os";
import path from "node:path";

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function isBlank(value) {
  return value == null || String(value).trim() === "";
}

export function parseArgs(defaults, { booleanKeys = [] } = {}) {
  const result = { ...defaults };
  const keyMap = new Map(Object.keys(defaults).map((key) => [normalizeKey(key), key]));
  const booleanSet = new Set(booleanKeys.map(normalizeKey));
  const args = process.argv.slice(2);

  for (let i = 0; i < args.length; i += 1) {
    const raw = args[i];
    if (!raw.startsWith("-")) {
      continue;
    }

    const withoutDash = raw.replace(/^-+/, "");
    const eqIndex = withoutDash.indexOf("=");
    const rawKey = eqIndex >= 0 ? withoutDash.slice(0, eqIndex) : withoutDash;
    const normalized = normalizeKey(rawKey);
    const key = keyMap.get(normalized);
    if (!key) {
      throw new Error(`Unknown argument: ${raw}`);
    }

    if (booleanSet.has(normalized)) {
      result[key] = eqIndex >= 0 ? parseBoolean(withoutDash.slice(eqIndex + 1)) : true;
      continue;
    }

    const value = eqIndex >= 0 ? withoutDash.slice(eqIndex + 1) : args[++i];
    if (value == null || value.startsWith("-")) {
      throw new Error(`Missing value for argument: ${raw}`);
    }
    result[key] = typeof defaults[key] === "number" ? Number(value) : value;
  }

  return result;
}

export async function ensureDir(dir) {
  await mkdir(dir, { recursive: true });
}

export async function removeIfExists(filePath) {
  if (existsSync(filePath)) {
    await rm(filePath, { force: true });
  }
}

export function getAndroidSdkPath() {
  return process.env.ANDROID_SDK_ROOT || path.join(process.env.LOCALAPPDATA || "", "Android", "Sdk");
}

export function getAdbPath() {
  const adb = path.join(getAndroidSdkPath(), "platform-tools", process.platform === "win32" ? "adb.exe" : "adb");
  assertExists(adb, "ADB not found");
  return adb;
}

export function getEmulatorPath() {
  const emulator = path.join(
    getAndroidSdkPath(),
    "emulator",
    process.platform === "win32" ? "emulator.exe" : "emulator"
  );
  assertExists(emulator, "Emulator not found");
  return emulator;
}

export function assertExists(filePath, message) {
  if (!existsSync(filePath)) {
    throw new Error(`${message}: ${filePath}`);
  }
}

export async function runChecked(file, args = [], options = {}) {
  const { code } = await runProcess(file, args, options);
  if (code !== 0) {
    throw new Error(`${options.displayName || file} failed with exit code ${code}`);
  }
}

export function startProcess(file, args = [], options = {}) {
  const command = commandForPlatform(file, args);
  return spawn(command.file, command.args, {
    cwd: options.cwd,
    env: options.env,
    detached: options.detached,
    stdio: options.stdio ?? "inherit",
    windowsHide: options.windowsHide ?? true,
  });
}

export function runProcess(file, args = [], options = {}) {
  return new Promise((resolve, reject) => {
    const child = startProcess(file, args, options);
    child.on("error", reject);
    child.on("close", (code, signal) => resolve({ code: code ?? -1, signal }));
  });
}

export async function runCapture(file, args = [], options = {}) {
  return new Promise((resolve, reject) => {
    const child = startProcess(file, args, { ...options, stdio: ["ignore", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (chunk) => {
      stdout += chunk;
    });
    child.stderr.on("data", (chunk) => {
      stderr += chunk;
    });
    child.on("error", reject);
    child.on("close", (code, signal) => resolve({ code: code ?? -1, signal, stdout, stderr }));
  });
}

export async function waitForPort(host, port, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await canConnect(host, port, 700)) {
      return true;
    }
    await sleep(600);
  }
  return false;
}

export async function findLatestVersionDir(root) {
  const entries = await readdir(root, { withFileTypes: true });
  return entries
    .filter((entry) => entry.isDirectory() && /^\d+(\.\d+){1,2}$/.test(entry.name))
    .map((entry) => entry.name)
    .sort(compareVersions)
    .reverse()[0];
}

export async function readTrim(filePath) {
  return (await readFile(filePath, "utf8")).trim();
}

export async function writeEmpty(filePath) {
  await writeFile(filePath, "", "utf8");
}

export function homePath(...parts) {
  return path.join(os.homedir(), ...parts);
}

function canConnect(host, port, timeoutMs) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host, port });
    const done = (ok) => {
      socket.removeAllListeners();
      socket.destroy();
      resolve(ok);
    };
    socket.setTimeout(timeoutMs);
    socket.on("connect", () => done(true));
    socket.on("timeout", () => done(false));
    socket.on("error", () => done(false));
  });
}

function commandForPlatform(file, args) {
  if (process.platform === "win32" && !path.extname(file)) {
    const pathext = (process.env.PATHEXT || ".COM;.EXE;.BAT;.CMD")
      .split(";")
      .map((extension) => extension.toLowerCase());
    const pathDirs = (process.env.PATH || "").split(path.delimiter);
    for (const dir of pathDirs) {
      for (const extension of pathext) {
        const candidate = path.join(dir, `${file}${extension}`);
        if (existsSync(candidate)) {
          file = candidate;
          break;
        }
      }
      if (path.extname(file)) {
        break;
      }
    }
  }
  if (process.platform === "win32" && /\.(bat|cmd)$/i.test(file)) {
    return { file: "cmd.exe", args: ["/d", "/c", "call", file, ...args] };
  }
  return { file, args };
}

function normalizeKey(key) {
  return key.toLowerCase().replace(/[^a-z0-9]/g, "");
}

function parseBoolean(value) {
  return !["0", "false", "no", "off"].includes(String(value).trim().toLowerCase());
}

function compareVersions(a, b) {
  const left = a.split(".").map(Number);
  const right = b.split(".").map(Number);
  for (let i = 0; i < Math.max(left.length, right.length); i += 1) {
    const delta = (left[i] || 0) - (right[i] || 0);
    if (delta !== 0) {
      return delta;
    }
  }
  return 0;
}

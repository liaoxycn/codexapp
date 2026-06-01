import { existsSync } from "node:fs";
import { mkdir, rm } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  assertExists,
  findLatestVersionDir,
  getAndroidSdkPath,
  homePath,
  parseArgs,
  runCapture,
  runChecked,
} from "./script-utils.mjs";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");
const options = parseArgs({ OutputName: "CodexMobile.apk" });

async function main() {
  const apkDir = path.join(root, "app", "build", "outputs", "apk", "release");
  const unsignedApk = path.join(apkDir, "app-release-unsigned.apk");
  const alignedApk = path.join(apkDir, options.OutputName);

  console.log("1/3 build release APK");
  await runChecked(path.join(root, "gradlew.bat"), [":app:assembleRelease"], {
    cwd: root,
    displayName: "assembleRelease",
  });

  console.log("2/3 zipalign and sign");
  const buildTools = await getBuildToolsPath();
  const zipalign = path.join(buildTools, process.platform === "win32" ? "zipalign.exe" : "zipalign");
  const apksigner = path.join(buildTools, process.platform === "win32" ? "apksigner.bat" : "apksigner");
  const debugKeystore = await ensureDebugKeystore();

  assertExists(unsignedApk, "Unsigned APK not found");
  if (existsSync(alignedApk)) {
    await rm(alignedApk, { force: true });
  }

  await runChecked(zipalign, ["-f", "-p", "4", unsignedApk, alignedApk], { cwd: root, displayName: "zipalign" });
  await runChecked(
    apksigner,
    [
      "sign",
      "--ks",
      debugKeystore,
      "--ks-key-alias",
      "androiddebugkey",
      "--ks-pass",
      "pass:android",
      "--key-pass",
      "pass:android",
      alignedApk,
    ],
    { cwd: root, displayName: "apksigner sign" }
  );
  await runChecked(apksigner, ["verify", "--verbose", alignedApk], { cwd: root, displayName: "apksigner verify" });

  console.log("3/3 done");
  console.log(alignedApk);
}

async function getBuildToolsPath() {
  const buildToolsRoot = path.join(getAndroidSdkPath(), "build-tools");
  const preferred = path.join(buildToolsRoot, "35.0.0");
  if (existsSync(preferred)) {
    return preferred;
  }
  assertExists(buildToolsRoot, "Android build-tools not found");
  const latest = await findLatestVersionDir(buildToolsRoot);
  if (!latest) {
    throw new Error(`Android build-tools not found under: ${buildToolsRoot}`);
  }
  return path.join(buildToolsRoot, latest);
}

async function ensureDebugKeystore() {
  const keystoreDir = homePath(".android");
  await mkdir(keystoreDir, { recursive: true });
  const debugKeystore = path.join(keystoreDir, "debug.keystore");
  if (existsSync(debugKeystore)) {
    return debugKeystore;
  }

  const keytool = await findCommand("keytool");
  await runChecked(
    keytool,
    [
      "-genkeypair",
      "-keystore",
      debugKeystore,
      "-storetype",
      "JKS",
      "-storepass",
      "android",
      "-keypass",
      "android",
      "-alias",
      "androiddebugkey",
      "-keyalg",
      "RSA",
      "-keysize",
      "2048",
      "-validity",
      "10000",
      "-dname",
      "CN=Android Debug,O=Android,C=US",
    ],
    { cwd: root, displayName: "keytool" }
  );
  return debugKeystore;
}

async function findCommand(command) {
  const finder = process.platform === "win32" ? "where.exe" : "which";
  const result = await runCapture(finder, [command]);
  if (result.code !== 0) {
    throw new Error(`${command} not found`);
  }
  return result.stdout.split(/\r?\n/).find(Boolean).trim();
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});

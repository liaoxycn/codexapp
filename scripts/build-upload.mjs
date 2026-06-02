import path from "node:path";
import { fileURLToPath } from "node:url";
import { isBlank, parseArgs, runChecked } from "./script-utils.mjs";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");
const options = parseArgs({
  WebDavUrl: process.env.CODEX_WEBDAV_URL,
  WebDavUser: process.env.CODEX_WEBDAV_USER,
  WebDavPassword: process.env.CODEX_WEBDAV_PASSWORD,
  RemotePath: "codex",
  OutputName: "codexapp.apk",
});

async function main() {
  if (isBlank(options.WebDavUrl) || isBlank(options.WebDavUser) || isBlank(options.WebDavPassword)) {
    throw new Error(
      "Set WebDAV parameters with -WebDavUrl -WebDavUser -WebDavPassword or CODEX_WEBDAV_URL CODEX_WEBDAV_USER CODEX_WEBDAV_PASSWORD"
    );
  }

  await runChecked(process.execPath, [path.join(scriptDir, "build-apk.mjs"), "-OutputName", options.OutputName], {
    cwd: root,
    displayName: "build-apk.mjs",
  });

  await runChecked(
    process.execPath,
    [
      path.join(scriptDir, "upload-apk.mjs"),
      "-WebDavUrl",
      options.WebDavUrl,
      "-WebDavUser",
      options.WebDavUser,
      "-WebDavPassword",
      options.WebDavPassword,
      "-RemotePath",
      options.RemotePath,
      "-ApkPath",
      path.join(root, "app", "build", "outputs", "apk", "release", options.OutputName),
    ],
    { cwd: root, displayName: "upload-apk.mjs" }
  );
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});

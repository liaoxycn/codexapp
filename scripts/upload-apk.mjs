import { createReadStream, existsSync } from "node:fs";
import { stat } from "node:fs/promises";
import http from "node:http";
import https from "node:https";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { isBlank, parseArgs } from "./script-utils.mjs";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");
const options = parseArgs({
  WebDavUrl: process.env.CODEX_WEBDAV_URL,
  WebDavUser: process.env.CODEX_WEBDAV_USER,
  WebDavPassword: process.env.CODEX_WEBDAV_PASSWORD,
  RemotePath: "codex",
  ApkPath: path.join(root, "app", "build", "outputs", "apk", "release", "CodexMobile.apk"),
});

async function main() {
  if (isBlank(options.WebDavUrl) || isBlank(options.WebDavUser) || isBlank(options.WebDavPassword)) {
    throw new Error(
      "Set WebDAV parameters with -WebDavUrl -WebDavUser -WebDavPassword or CODEX_WEBDAV_URL CODEX_WEBDAV_USER CODEX_WEBDAV_PASSWORD"
    );
  }
  if (!existsSync(options.ApkPath)) {
    throw new Error(`APK not found: ${options.ApkPath}`);
  }

  const remoteUrl = buildRemoteUrl(options.WebDavUrl, options.RemotePath, path.basename(options.ApkPath));
  await putFile(remoteUrl, options.ApkPath, options.WebDavUser, options.WebDavPassword);

  console.log("done");
  console.log(options.ApkPath);
  console.log(remoteUrl);
}

function buildRemoteUrl(baseUrl, remotePath, fileName) {
  const folder = String(remotePath || "").trim().replace(/^\/+|\/+$/g, "");
  const parts = [String(baseUrl).replace(/\/+$/g, "")];
  if (folder) {
    parts.push(encodeURIComponent(folder));
  }
  parts.push(encodeURIComponent(fileName));
  return parts.join("/");
}

async function putFile(urlString, filePath, user, password) {
  const url = new URL(urlString);
  const fileStat = await stat(filePath);
  const client = url.protocol === "https:" ? https : http;
  const auth = Buffer.from(`${user}:${password}`, "utf8").toString("base64");

  await new Promise((resolve, reject) => {
    const request = client.request(
      url,
      {
        method: "PUT",
        headers: {
          Authorization: `Basic ${auth}`,
          "Content-Type": "application/vnd.android.package-archive",
          "Content-Length": fileStat.size,
        },
      },
      (response) => {
        let body = "";
        response.setEncoding("utf8");
        response.on("data", (chunk) => {
          body += chunk;
        });
        response.on("end", () => {
          if (response.statusCode >= 200 && response.statusCode < 300) {
            resolve();
            return;
          }
          reject(new Error(`WebDAV upload failed: ${response.statusCode} ${response.statusMessage}${body ? `\n${body}` : ""}`));
        });
      }
    );
    request.on("error", reject);
    createReadStream(filePath).pipe(request);
  });
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});

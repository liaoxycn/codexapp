import { createWriteStream } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { startProcess } from "./script-utils.mjs";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");
const gatewayRoot = process.argv[2] || path.join(root, "desktop-gateway");
const gatewayLog = process.argv[3] || path.join(scriptDir, "logs", "gateway-dev.log");
const gatewayErr = process.argv[4] || path.join(scriptDir, "logs", "gateway-dev.err.log");

const out = createWriteStream(gatewayLog, { flags: "a" });
const err = createWriteStream(gatewayErr, { flags: "a" });

const child = startProcess("npm", ["run", "dev"], {
  cwd: gatewayRoot,
  env: process.env,
  stdio: ["ignore", "pipe", "pipe"],
});

child.stdout.pipe(out);
child.stderr.pipe(err);

child.on("error", (error) => {
  err.write(`${error.stack || error.message}\n`);
});

child.on("close", (code, signal) => {
  err.write(`[gateway-runner] exited code=${code ?? "null"} signal=${signal ?? "null"}\n`);
  out.end();
  err.end();
  process.exitCode = code ?? 1;
});

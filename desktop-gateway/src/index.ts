import process from "node:process";
import { GatewayServer } from "./server.js";

const host = process.env.CODEX_MOBILE_GATEWAY_HOST ?? "0.0.0.0";
const port = Number.parseInt(process.env.CODEX_MOBILE_GATEWAY_PORT ?? "8765", 10);
const path = normalizePath(process.env.CODEX_MOBILE_GATEWAY_PATH ?? "/mobile");
const pairToken = emptyToUndefined(process.env.CODEX_MOBILE_PAIR_TOKEN);
const workspacePath = process.env.CODEX_MOBILE_WORKSPACE ?? process.cwd();

new GatewayServer({
  host,
  port,
  path,
  pairToken,
  workspacePath
}).start().catch((error) => {
  console.error("[gateway] failed to start:", error);
  process.exit(1);
});

function normalizePath(value: string): string {
  return value.startsWith("/") ? value : `/${value}`;
}

function emptyToUndefined(value?: string): string | undefined {
  if (!value) {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

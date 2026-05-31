import { URL } from "node:url";

export function normalizeGatewayPath(path: string): string {
  const normalized = path.replace(/\/+$/, "");
  return normalized.length > 0 ? normalized : "/";
}

export function requestGatewayPath(url?: string): string {
  const pathname = new URL(url ?? "/", "ws://localhost").pathname;
  return normalizeGatewayPath(pathname);
}

export function isExpectedGatewayPath(url: string | undefined, expectedPath: string): boolean {
  return requestGatewayPath(url) === normalizeGatewayPath(expectedPath);
}

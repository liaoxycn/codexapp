import type { GatewayMessagePayload } from "../protocol.js";

export function cloneMessage(message: GatewayMessagePayload): GatewayMessagePayload {
  return {
    ...message,
    blocks: message.blocks.map((block) => ({ ...block }))
  };
}

export function shrinkWorkspacePath(value: string): string {
  const normalized = value.replaceAll("\\", "/");
  const segments = normalized.split("/").filter(Boolean);
  return segments.slice(-1)[0] ?? value;
}

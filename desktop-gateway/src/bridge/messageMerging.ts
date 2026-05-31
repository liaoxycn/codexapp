import type { GatewayMessagePayload } from "../protocol.js";

export function isOptimisticUserMessage(message: GatewayMessagePayload): boolean {
  return message.role === "user" && message.id.startsWith("user-live-");
}

export function isSameUserMessage(left: GatewayMessagePayload, right: GatewayMessagePayload): boolean {
  return (
    left.role === "user" &&
    right.role === "user" &&
    normalizeMessageText(left) === normalizeMessageText(right) &&
    normalizeMessageText(left).length > 0
  );
}

export function normalizeMessageText(message: GatewayMessagePayload): string {
  return message.blocks
    .filter((block) => block.kind === "text")
    .map((block) => block.value.trim())
    .join("\n")
    .trim();
}

export function mergeMessageBlocks(
  primary: GatewayMessagePayload,
  secondary: GatewayMessagePayload
): GatewayMessagePayload {
  const mergedBlocks = [...primary.blocks];
  for (const block of secondary.blocks) {
    const index = mergedBlocks.findIndex((entry) => entry.kind === block.kind);
    if (index < 0) {
      mergedBlocks.push(block);
      continue;
    }

    const current = mergedBlocks[index]!;
    if (block.value.length > current.value.length) {
      mergedBlocks[index] = block;
    }
  }

  return {
    ...primary,
    blocks: mergedBlocks,
  };
}

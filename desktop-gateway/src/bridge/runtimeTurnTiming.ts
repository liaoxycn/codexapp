import type { GatewayMessagePayload } from "../protocol.js";
import type { ThreadRuntimeState } from "./types.js";

export function markCurrentTurnStarted(
  state: ThreadRuntimeState,
  timestamp?: number | null,
  nowMs = Date.now(),
  reset = false
): void {
  state.currentTurnStartedAtMs = normalizeTimestampMs(timestamp) ?? (reset ? nowMs : state.currentTurnStartedAtMs ?? nowMs);
}

export function clearCurrentTurnStarted(state: ThreadRuntimeState): void {
  state.currentTurnStartedAtMs = null;
}

export function getLiveTurnDurationMs(state: ThreadRuntimeState, nowMs = Date.now()): number | undefined {
  const startedAtMs = state.currentTurnStartedAtMs;
  if (!Number.isFinite(startedAtMs) || startedAtMs == null || startedAtMs <= 0) {
    return undefined;
  }
  return Math.max(1000, Math.round(nowMs - startedAtMs));
}

export function withLiveAssistantDuration(
  state: ThreadRuntimeState,
  message: GatewayMessagePayload
): GatewayMessagePayload {
  if (message.role !== "assistant") {
    return message;
  }
  const durationMs = getLiveTurnDurationMs(state);
  return durationMs == null ? message : { ...message, durationMs };
}

function normalizeTimestampMs(timestamp: number | null | undefined): number | null {
  if (typeof timestamp !== "number" || !Number.isFinite(timestamp) || timestamp <= 0) {
    return null;
  }
  return timestamp < 10_000_000_000 ? Math.round(timestamp * 1000) : Math.round(timestamp);
}

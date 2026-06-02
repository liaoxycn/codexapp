import {
  RUNNING_SIGNAL_LEASE_MS,
  TURN_COMPLETION_GRACE_MS,
  type ThreadRuntimeState,
} from "./types.js";

export function markRunningSignal(
  state: ThreadRuntimeState,
  nowMs = Date.now(),
  leaseMs = RUNNING_SIGNAL_LEASE_MS
): void {
  state.runningSignalUntilMs = Math.max(state.runningSignalUntilMs ?? 0, nowMs + leaseMs);
}

export function markTurnCompletionGrace(
  state: ThreadRuntimeState,
  nowMs = Date.now(),
  graceMs = TURN_COMPLETION_GRACE_MS
): void {
  state.turnCompletionGraceUntilMs = Math.max(state.turnCompletionGraceUntilMs ?? 0, nowMs + graceMs);
}

export function hasRunningLease(state: ThreadRuntimeState, nowMs = Date.now()): boolean {
  return (state.runningSignalUntilMs ?? 0) > nowMs || (state.turnCompletionGraceUntilMs ?? 0) > nowMs;
}

export function hasRunningActivityLease(state: ThreadRuntimeState, nowMs = Date.now()): boolean {
  return (state.runningSignalUntilMs ?? 0) > nowMs;
}

export function clearRunningLease(state: ThreadRuntimeState): void {
  state.runningSignalUntilMs = 0;
  state.turnCompletionGraceUntilMs = 0;
}

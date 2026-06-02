import type { ThreadLifecycleStatus, ThreadRuntimeState } from "./types.js";

let nextRuntimeSeq = 1;

export function initializeRuntimeStatus(
  state: ThreadRuntimeState,
  status: ThreadLifecycleStatus
): void {
  state.runtimeStatus = status;
  state.activeTurnIds = state.activeTurnIds ?? [];
  state.activeHookIds = state.activeHookIds ?? [];
  state.runtimeStatusSeq = state.runtimeStatusSeq ?? 0;
  state.runtimeTerminalSeq = state.runtimeTerminalSeq ?? 0;
  applyRuntimeStatus(state, status, false);
}

export function markRuntimeTurnStarted(state: ThreadRuntimeState, turnId: string | null | undefined): void {
  ensureRuntimeStatusShape(state);
  addUnique(state.activeTurnIds, turnId);
  applyRuntimeStatus(state, "running");
}

export function markRuntimeTurnFinished(
  state: ThreadRuntimeState,
  turnId: string | null | undefined,
  status?: string
): void {
  ensureRuntimeStatusShape(state);
  removeValue(state.activeTurnIds, turnId);
  state.runtimeTerminalSeq = nextRuntimeSeq++;
  applyRuntimeStatus(state, normalizeTerminalStatus(status, state), false);
}

export function markRuntimeHookStarted(state: ThreadRuntimeState, hookId: string | null | undefined): void {
  ensureRuntimeStatusShape(state);
  addUnique(state.activeHookIds, hookId);
  applyRuntimeStatus(state, "running");
}

export function markRuntimeHookFinished(
  state: ThreadRuntimeState,
  hookId: string | null | undefined,
  status?: string
): void {
  ensureRuntimeStatusShape(state);
  removeValue(state.activeHookIds, hookId);
  state.runtimeTerminalSeq = nextRuntimeSeq++;
  applyRuntimeStatus(state, normalizeTerminalStatus(status, state), false);
}

export function markRuntimeApprovalPending(state: ThreadRuntimeState): void {
  ensureRuntimeStatusShape(state);
  applyRuntimeStatus(state, "needs_approval");
}

export function markRuntimeApprovalResolved(state: ThreadRuntimeState): void {
  ensureRuntimeStatusShape(state);
  applyRuntimeStatus(state, state.activeTurnIds.length > 0 || state.activeHookIds.length > 0 ? "running" : "idle");
}

export function markRuntimeFailed(state: ThreadRuntimeState): void {
  ensureRuntimeStatusShape(state);
  state.activeTurnIds = [];
  state.activeHookIds = [];
  state.runtimeTerminalSeq = nextRuntimeSeq++;
  applyRuntimeStatus(state, "failed", false);
}

export function markRuntimeIdle(state: ThreadRuntimeState): void {
  ensureRuntimeStatusShape(state);
  state.activeTurnIds = [];
  state.activeHookIds = [];
  state.runtimeTerminalSeq = nextRuntimeSeq++;
  applyRuntimeStatus(state, "idle", false);
}

export function resolveRuntimeStatus(state: ThreadRuntimeState): ThreadLifecycleStatus {
  ensureRuntimeStatusShape(state);
  if (state.pendingApproval) {
    return "needs_approval";
  }
  if (state.activeTurnIds.length > 0 || state.activeHookIds.length > 0) {
    return "running";
  }
  return state.runtimeStatus;
}

export function applyRuntimeStatusToSnapshot(state: ThreadRuntimeState): void {
  ensureRuntimeStatusShape(state);
  const status = resolveRuntimeStatus(state);
  state.runtimeStatus = status;
  state.snapshot.isGenerating = status === "running";
  state.snapshot.pendingApproval = state.pendingApproval?.text ?? null;
  state.summary = { ...state.summary, status };
  state.snapshot.threads = state.snapshot.threads.map((thread) =>
    thread.id === state.summary.id ? { ...thread, status } : thread
  );
}

function applyRuntimeStatus(
  state: ThreadRuntimeState,
  status: ThreadLifecycleStatus,
  bumpSeq = true
): void {
  state.runtimeStatus = status;
  if (bumpSeq) {
    state.runtimeStatusSeq = nextRuntimeSeq++;
  }
  state.snapshot.isGenerating = status === "running";
  state.snapshot.pendingApproval = state.pendingApproval?.text ?? null;
  state.summary = { ...state.summary, status };
}

function ensureRuntimeStatusShape(state: ThreadRuntimeState): void {
  state.activeTurnIds = Array.isArray(state.activeTurnIds) ? state.activeTurnIds : [];
  state.activeHookIds = Array.isArray(state.activeHookIds) ? state.activeHookIds : [];
  state.runtimeStatus = state.runtimeStatus ?? (state.snapshot?.isGenerating ? "running" : "idle");
  state.runtimeStatusSeq = Number.isFinite(state.runtimeStatusSeq) ? state.runtimeStatusSeq : 0;
  state.runtimeTerminalSeq = Number.isFinite(state.runtimeTerminalSeq) ? state.runtimeTerminalSeq : 0;
}

function normalizeTerminalStatus(status: string | undefined, state: ThreadRuntimeState): ThreadLifecycleStatus {
  if (status?.toLowerCase() === "failed") {
    return "failed";
  }
  if (state.pendingApproval) {
    return "needs_approval";
  }
  if (state.activeTurnIds.length > 0 || state.activeHookIds.length > 0) {
    return "running";
  }
  return "idle";
}

function addUnique(values: string[], value: string | null | undefined): void {
  if (!value || values.includes(value)) {
    return;
  }
  values.push(value);
}

function removeValue(values: string[], value: string | null | undefined): void {
  if (!value) {
    return;
  }
  const index = values.indexOf(value);
  if (index >= 0) {
    values.splice(index, 1);
  }
}

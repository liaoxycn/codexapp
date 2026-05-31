import type { AppServerThreadStatus } from "./appServerTypes.js";

export function getThreadStatusType(status: AppServerThreadStatus): string {
  if (typeof status === "string") {
    return status.toLowerCase();
  }
  return status.type.toLowerCase();
}

export function getThreadActiveFlags(status: AppServerThreadStatus): string[] {
  if (typeof status === "string" || !("activeFlags" in status)) {
    return [];
  }
  return Array.isArray(status.activeFlags) ? status.activeFlags : [];
}

export function hasThreadActiveFlag(status: AppServerThreadStatus, flag: string): boolean {
  return getThreadActiveFlags(status).some((value) => value.toLowerCase() === flag.toLowerCase());
}

export function isTerminalTurnStatus(status?: string): boolean {
  if (!status) {
    return false;
  }
  return [
    "failed",
    "completed",
    "complete",
    "cancelled",
    "canceled",
    "done",
    "idle",
    "stopped",
    "interrupted",
  ].includes(status.toLowerCase());
}

export function isRunningStatusType(type: string): boolean {
  return type === "active" || type === "in_progress" || type === "inprogress" || type === "running";
}

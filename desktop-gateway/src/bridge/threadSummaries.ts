import type { AppServerThread } from "../appServerTypes.js";
import type { GatewayThreadPayload } from "../protocol.js";
import { resolveThreadSummaryStatus } from "../threadState.js";
import { asString, asTextInputEntries } from "./appServerValues.js";
import type { ThreadRuntimeState } from "./types.js";

export function mapThreadToSummary(
  thread: AppServerThread,
  archived = false,
  updatedAtOverride?: number
): GatewayThreadPayload {
  const grouping = deriveThreadGrouping(thread);
  const status = resolveThreadSummaryStatus(thread);
  const subtitle = buildThreadSubtitle(thread);
  return {
    id: thread.id,
    title: thread.name ?? buildThreadTitle(thread.preview),
    preview: thread.preview,
    subtitle,
    cwd: thread.cwd,
    status,
    updatedAt: updatedAtOverride ?? toMillisTimestamp(thread.updatedAt),
    groupKind: grouping.kind,
    groupLabel: grouping.label,
    archived,
  };
}

export function dedupeSummaries(items: GatewayThreadPayload[]): GatewayThreadPayload[] {
  const map = new Map<string, GatewayThreadPayload>();
  for (const item of items) {
    map.set(item.id, item);
  }
  return [...map.values()];
}

export function touchThreadActivity(state: ThreadRuntimeState, timestampMs?: number | null): void {
  if (typeof timestampMs !== "number" || !Number.isFinite(timestampMs) || timestampMs <= 0) {
    return;
  }
  state.lastActivityAtMs = Math.max(state.lastActivityAtMs, timestampMs);
  state.summary = {
    ...state.summary,
    updatedAt: state.lastActivityAtMs,
  };
  state.snapshot.threads = state.snapshot.threads.map((thread) =>
    thread.id === state.summary.id ? { ...thread, updatedAt: state.lastActivityAtMs } : thread
  );
}

export function getThreadLastActivityAtMs(thread: AppServerThread): number {
  let latest = 0;
  for (const turn of thread.turns) {
    if (typeof turn.startedAt === "number" && turn.startedAt > 0) {
      latest = Math.max(latest, toMillisTimestamp(turn.startedAt));
    }
    if (typeof turn.completedAt === "number" && turn.completedAt > 0) {
      latest = Math.max(latest, toMillisTimestamp(turn.completedAt));
    }
  }
  return latest > 0 ? latest : toMillisTimestamp(thread.updatedAt);
}

export function buildVisibleThreadSummaries(threads: AppServerThread[]): GatewayThreadPayload[] {
  return threads.map((thread) => mapThreadToSummary(thread, false, getThreadLastActivityAtMs(thread)));
}

function buildThreadTitle(preview: string): string {
  const firstLine = preview.trim().split(/\r?\n/, 1)[0] ?? "Codex 会话";
  return firstLine.slice(0, 32) || "Codex 会话";
}

function buildThreadSubtitle(thread: AppServerThread): string {
  const latestText = extractLatestThreadText(thread);
  if (latestText.length > 0) {
    return latestText;
  }
  return thread.preview.trim();
}

function extractLatestThreadText(thread: AppServerThread): string {
  for (let turnIndex = thread.turns.length - 1; turnIndex >= 0; turnIndex -= 1) {
    const turn = thread.turns[turnIndex];
    for (let itemIndex = turn.items.length - 1; itemIndex >= 0; itemIndex -= 1) {
      const item = turn.items[itemIndex];
      const text = extractThreadItemText(item);
      if (text.length > 0) {
        return text;
      }
    }
  }
  return "";
}

function extractThreadItemText(item: AppServerThread["turns"][number]["items"][number]): string {
  switch (item.type) {
    case "userMessage":
      return asTextInputEntries(item.content)
        .map((entry) => entry.text.trim())
        .find((value) => value.length > 0) ?? "";
    case "agentMessage":
      return asString(item.text).trim();
    default:
      return "";
  }
}

function deriveThreadGrouping(thread: AppServerThread): { kind: "project" | "chat"; label: string } {
  const cwd = thread.cwd.replaceAll("\\", "/");
  const segments = cwd.split("/").filter(Boolean);
  const leaf = segments.at(-1) ?? "";
  if (leaf.length === 0) {
    return { kind: "chat", label: "普通会话" };
  }
  return { kind: "project", label: leaf };
}

function toMillisTimestamp(seconds: number): number {
  return seconds > 0 ? seconds * 1000 : 0;
}

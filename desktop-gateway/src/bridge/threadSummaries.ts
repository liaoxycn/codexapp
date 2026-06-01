import { existsSync, readFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import type { AppServerThread } from "../appServerTypes.js";
import type { GatewayThreadPayload } from "../protocol.js";
import { resolveThreadSummaryStatus } from "../threadState.js";
import { asString, asTextInputEntries } from "./appServerValues.js";
import type { ThreadRuntimeState } from "./types.js";

interface DesktopThreadVisibilityState {
  visibleThreadIds: ReadonlySet<string>;
  importedThreadIds: ReadonlySet<string>;
}

interface BuildVisibleThreadSummariesOptions {
  desktopVisibility?: DesktopThreadVisibilityState | null;
}

let cachedDesktopVisibility: DesktopThreadVisibilityState | null | undefined;

export function mapThreadToSummary(
  thread: AppServerThread,
  archived = false,
  updatedAtOverride?: number
): GatewayThreadPayload {
  const grouping = deriveThreadGrouping(thread);
  const status = resolveThreadSummaryStatus(thread);
  const subtitle = buildThreadSubtitle(thread);
  const gitInfo = buildThreadGitInfo(thread);
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
    ...gitInfo,
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
  const normalizedTimestampMs = normalizeActivityTimestampMs(timestampMs);
  if (normalizedTimestampMs <= 0) {
    return;
  }
  state.lastActivityAtMs = Math.max(state.lastActivityAtMs, normalizedTimestampMs);
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

export function buildVisibleThreadSummaries(
  threads: AppServerThread[],
  options: BuildVisibleThreadSummariesOptions = {}
): GatewayThreadPayload[] {
  const desktopVisibility =
    "desktopVisibility" in options ? options.desktopVisibility : readDesktopThreadVisibility();
  return threads
    .filter((thread) => isDesktopMainListThread(thread, desktopVisibility))
    .map((thread) => mapThreadToSummary(thread, false, getThreadLastActivityAtMs(thread)));
}

function buildThreadTitle(preview: string): string {
  const firstLine = preview.trim().split(/\r?\n/, 1)[0] ?? "Codex 会话";
  return firstLine.slice(0, 32) || "Codex 会话";
}

export function isDesktopMainListThread(
  thread: AppServerThread,
  desktopVisibility: DesktopThreadVisibilityState | null = readDesktopThreadVisibility()
): boolean {
  const source = thread.source;
  if (source === "cli" || source === "exec" || source === "unknown" || typeof source === "object") {
    return false;
  }
  if (thread.name?.trim() === "<environment_context>") {
    return false;
  }
  if (desktopVisibility?.importedThreadIds.has(thread.id)) {
    return false;
  }
  if (source === "appServer" || source === undefined) {
    return true;
  }
  if (source === "vscode" && desktopVisibility && desktopVisibility.visibleThreadIds.size > 0) {
    return desktopVisibility.visibleThreadIds.has(thread.id);
  }
  return true;
}

function readDesktopThreadVisibility(): DesktopThreadVisibilityState | null {
  if (cachedDesktopVisibility !== undefined) {
    return cachedDesktopVisibility;
  }
  const codexHome = join(homedir(), ".codex");
  cachedDesktopVisibility = {
    visibleThreadIds: readDesktopHeartbeatThreadIds(join(codexHome, ".codex-global-state.json")),
    importedThreadIds: readImportedThreadIds(join(codexHome, "external_agent_session_imports.json")),
  };
  return cachedDesktopVisibility;
}

function readDesktopHeartbeatThreadIds(path: string): ReadonlySet<string> {
  const state = readJsonObject(path);
  const persisted = getObject(state["electron-persisted-atom-state"]);
  const permissions = getObject(persisted["heartbeat-thread-permissions-by-id"]);
  return new Set(Object.keys(permissions));
}

function readImportedThreadIds(path: string): ReadonlySet<string> {
  const state = readJsonObject(path);
  const records = Array.isArray(state.records) ? state.records : [];
  return new Set(
    records
      .map((record) => getObject(record).imported_thread_id)
      .filter((value): value is string => typeof value === "string" && value.length > 0)
  );
}

function normalizeActivityTimestampMs(timestamp: number | null | undefined): number {
  if (typeof timestamp !== "number" || !Number.isFinite(timestamp) || timestamp <= 0) {
    return 0;
  }
  // App-server item/turn timestamps usually arrive in seconds; local overlay clocks are ms.
  return timestamp < 10_000_000_000 ? timestamp * 1000 : timestamp;
}

function readJsonObject(path: string): Record<string, unknown> {
  if (!existsSync(path)) {
    return {};
  }
  try {
    return getObject(JSON.parse(readFileSync(path, "utf8")));
  } catch {
    return {};
  }
}

function getObject(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
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
  if (leaf.length === 0 || isDesktopChatWorkingDirectory(segments)) {
    return { kind: "chat", label: "普通会话" };
  }
  return { kind: "project", label: leaf };
}

function isDesktopChatWorkingDirectory(segments: string[]): boolean {
  const leaf = segments.at(-1)?.toLowerCase() ?? "";
  if (leaf === "new-chat") {
    return true;
  }

  const codexIndex = segments.findIndex((segment) => segment.toLowerCase() === "codex");
  if (codexIndex < 0) {
    return false;
  }
  const dateSegment = segments[codexIndex + 1] ?? "";
  return /^\d{4}-\d{2}-\d{2}$/.test(dateSegment);
}

function buildThreadGitInfo(thread: AppServerThread): { gitBranch?: string; gitSha?: string } {
  const branch = thread.gitInfo?.branch?.trim();
  const sha = thread.gitInfo?.sha?.trim();
  return {
    ...(branch ? { gitBranch: branch } : {}),
    ...(sha ? { gitSha: sha.slice(0, 7) } : {}),
  };
}

function toMillisTimestamp(seconds: number): number {
  return seconds > 0 ? seconds * 1000 : 0;
}

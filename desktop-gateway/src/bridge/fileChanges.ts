import type { GatewayBlockPayload } from "../protocol.js";

export function buildFileChangeBlocks(
  changes: Array<{ path?: string; kind?: string | { type?: string }; diff?: string | null }>,
  status: string,
  cwd = ""
): GatewayBlockPayload[] {
  const summary = summarizeFileChanges(changes, status);
  const details = formatFileChangeDetails(changes, cwd);
  const blocks: GatewayBlockPayload[] = [{ kind: "fileChangeSummary", value: summary }];
  for (const detail of details) {
    blocks.push({ kind: "fileChangeMeta", value: detail.label, path: detail.path });
    if (detail.diff.length > 0) {
      blocks.push({ kind: "fileChangeDiff", language: "diff", value: detail.diff });
    }
  }
  return blocks;
}


function summarizeFileChanges(
  changes: Array<{ path?: string; kind?: string | { type?: string }; diff?: string | null }>,
  status: string
): string {
  if (!Array.isArray(changes) || changes.length === 0) {
    return status === "inProgress" ? "文件改动中" : `文件改动 ${status}`;
  }
  const counts = { add: 0, delete: 0, update: 0 };
  for (const change of changes) {
    counts[normalizeChangeKind(change.kind)] += 1;
  }
  const parts: string[] = [];
  if (counts.add > 0) {
    parts.push(`已创建 ${counts.add} 个文件`);
  }
  if (counts.delete > 0) {
    parts.push(`已删除 ${counts.delete} 个文件`);
  }
  if (counts.update > 0) {
    parts.push(`已编辑 ${counts.update} 个文件`);
  }
  return parts.length > 0 ? parts.join(" · ") : `已编辑 ${changes.length} 个文件`;
}

function formatFileChangeDetails(
  changes: Array<{ path?: string; kind?: string | { type?: string }; diff?: string | null }>,
  cwd: string
): Array<{ label: string; path: string; diff: string }> {
  return changes.map((change) => {
    const action = describeChangeKind(change.kind);
    const rawPath = change.path?.trim() || "unknown";
    const path = formatProjectRelativePath(rawPath, cwd);
    const diff = change.diff?.trim() ?? "";
    return {
      label: `${action} ${formatFileName(path)}`,
      path,
      diff,
    };
  });
}

function formatFileName(path: string): string {
  const normalized = path.replaceAll("\\", "/");
  return normalized.split("/").filter(Boolean).at(-1) ?? path;
}

function formatProjectRelativePath(path: string, cwd: string): string {
  const normalizedPath = path.replaceAll("\\", "/");
  const normalizedCwd = cwd.trim().replaceAll("\\", "/").replace(/\/+$/, "");
  if (normalizedCwd.length > 0 && normalizedPath.toLowerCase().startsWith(`${normalizedCwd.toLowerCase()}/`)) {
    return normalizedPath.slice(normalizedCwd.length + 1);
  }
  if (/^[A-Za-z]:\//.test(normalizedPath) || normalizedPath.startsWith("/")) {
    return formatFileName(normalizedPath);
  }
  return normalizedPath;
}

function normalizeChangeKind(kind: unknown): "add" | "delete" | "update" {
  if (typeof kind === "string") {
    const normalized = kind.toLowerCase();
    if (normalized === "add" || normalized === "delete" || normalized === "update") {
      return normalized;
    }
  }
  if (typeof kind === "object" && kind != null) {
    const candidate = kind as Record<string, unknown>;
    const normalized = typeof candidate.type === "string" ? candidate.type.toLowerCase() : "";
    if (normalized === "add" || normalized === "delete" || normalized === "update") {
      return normalized;
    }
  }
  return "update";
}

function describeChangeKind(kind: unknown): string {
  switch (normalizeChangeKind(kind)) {
    case "add":
      return "已创建";
    case "delete":
      return "已删除";
    default:
      return "已编辑";
  }
}


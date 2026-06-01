import fs from "node:fs";
import path from "node:path";
import type { GatewayFilePayload } from "../protocol.js";

const MAX_FILES = 300;
const MAX_DEPTH = 6;
const IGNORED_DIRS = new Set([
  ".git",
  ".gradle",
  ".idea",
  ".kotlin",
  ".next",
  ".turbo",
  ".venv",
  "artifacts",
  "build",
  "coverage",
  "dist",
  "node_modules",
  "out",
  "target",
]);

export function listProjectFiles(cwd: string): GatewayFilePayload[] {
  const root = safeRealDirectory(cwd);
  if (!root) {
    return [];
  }

  const files: GatewayFilePayload[] = [];
  walkProjectDirectory(root, root, 0, files);
  return files;
}

function walkProjectDirectory(
  root: string,
  directory: string,
  depth: number,
  files: GatewayFilePayload[]
): void {
  if (files.length >= MAX_FILES || depth > MAX_DEPTH) {
    return;
  }

  let entries: fs.Dirent[];
  try {
    entries = fs.readdirSync(directory, { withFileTypes: true });
  } catch {
    return;
  }

  entries.sort((left, right) => Number(right.isDirectory()) - Number(left.isDirectory()) || left.name.localeCompare(right.name));
  for (const entry of entries) {
    if (files.length >= MAX_FILES) {
      return;
    }
    if (entry.name.startsWith(".") && entry.name !== ".codex") {
      continue;
    }

    const fullPath = path.join(directory, entry.name);
    if (entry.isSymbolicLink()) {
      continue;
    }
    if (entry.isDirectory()) {
      if (!IGNORED_DIRS.has(entry.name)) {
        walkProjectDirectory(root, fullPath, depth + 1, files);
      }
      continue;
    }
    if (!entry.isFile()) {
      continue;
    }

    const realPath = safeRealFile(fullPath);
    if (!realPath || !isInsideDirectory(root, realPath)) {
      continue;
    }
    if (isExcludedProjectFile(realPath)) {
      continue;
    }
    const label = path.relative(root, realPath).replaceAll(path.sep, "/");
    files.push({
      label,
      path: realPath,
    });
  }
}

function safeRealDirectory(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  try {
    const real = fs.realpathSync(trimmed);
    return fs.statSync(real).isDirectory() ? real : null;
  } catch {
    return null;
  }
}

function safeRealFile(value: string): string | null {
  try {
    const real = fs.realpathSync(value);
    return fs.statSync(real).isFile() ? real : null;
  } catch {
    return null;
  }
}

function isInsideDirectory(root: string, candidate: string): boolean {
  const relative = path.relative(root, candidate);
  return relative !== "" && !relative.startsWith("..") && !path.isAbsolute(relative);
}

function isExcludedProjectFile(filePath: string): boolean {
  const segments = filePath.replaceAll("\\", "/").split("/").filter(Boolean);
  if (segments.some((segment) => segment.startsWith(".") && segment !== ".codex")) {
    return true;
  }
  return segments.some((segment) => [".git", ".gradle", "build", "dist", "node_modules", "out", "target"].includes(segment));
}

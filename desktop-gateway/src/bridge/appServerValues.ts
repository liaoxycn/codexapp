export interface TextInputEntry {
  type: string;
  text: string;
}

function isTextInputEntry(value: unknown): value is TextInputEntry {
  return typeof value === "object" && value != null && "type" in value && "text" in value;
}

export function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

export function asStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((entry): entry is string => typeof entry === "string") : [];
}

export function asTextInputEntries(value: unknown): TextInputEntry[] {
  return Array.isArray(value)
    ? value.filter((entry): entry is TextInputEntry => isTextInputEntry(entry))
    : [];
}

export function asHookPromptFragments(value: unknown): Array<{ type?: string; text?: string }> {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((entry) => {
    if (typeof entry !== "object" || entry == null) {
      return {};
    }
    const candidate = entry as Record<string, unknown>;
    return {
      type: typeof candidate.type === "string" ? candidate.type : undefined,
      text: typeof candidate.text === "string" ? candidate.text : undefined,
    };
  });
}

export function asFileChanges(
  value: unknown
): Array<{ path?: string; kind?: string; diff?: string | null }> {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((change) => {
    if (typeof change !== "object" || change == null) {
      return {};
    }
    const candidate = change as Record<string, unknown>;
    return {
      path: typeof candidate.path === "string" ? candidate.path : undefined,
      kind: typeof candidate.kind === "string" ? candidate.kind : undefined,
      diff: typeof candidate.diff === "string" ? candidate.diff : null,
    };
  });
}

export function formatFileChanges(changes: Array<{ path?: string; kind?: string; diff?: string | null }>): string {
  return changes
    .map((change) => change.diff?.trim() || `${change.kind ?? "update"} ${change.path ?? "unknown"}`)
    .join("\n");
}


export function flattenHookPrompt(fragments: Array<{ type?: string; text?: string }> | undefined): string {
  if (!Array.isArray(fragments)) {
    return "";
  }
  return fragments
    .map((fragment) => asString(fragment.text))
    .filter((value) => value.length > 0)
    .join("\n");
}


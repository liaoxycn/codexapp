import { randomUUID } from "node:crypto";
import { EventEmitter } from "node:events";
import process from "node:process";
import type { ClientSnapshot } from "../protocol.js";
import { cloneMessage, shrinkWorkspacePath } from "./helpers.js";
import { buildAssistantResponse } from "./mockResponses.js";
import { createSeedThreads } from "./seedThreads.js";
import type { ThreadRecord } from "./types.js";

export class InMemoryDesktopBackend {
  private readonly events = new EventEmitter();
  private readonly threads = new Map<string, ThreadRecord>();

  constructor(private readonly workspacePath: string = process.cwd()) {
    for (const thread of createSeedThreads(workspacePath)) {
      this.threads.set(thread.summary.id, thread);
    }
  }

  subscribe(listener: () => void): () => void {
    this.events.on("changed", listener);
    return () => this.events.off("changed", listener);
  }

  getDefaultThreadId(): string {
    return this.listThreads().find((thread) => !thread.archived)?.id
      ?? this.listThreads()[0]?.id
      ?? "thread-android-shell";
  }

  hasThread(threadId: string): boolean {
    return this.threads.has(threadId);
  }

  listThreads() {
    return [...this.threads.values()].map((thread) => ({ ...thread.summary }));
  }

  getSnapshot(selectedThreadId?: string): ClientSnapshot {
    const threadId = this.hasThread(selectedThreadId ?? "") ? selectedThreadId! : this.getDefaultThreadId();
    const thread = this.threads.get(threadId)!;
    return {
      threads: this.listThreads(),
      selectedThreadId: threadId,
      messages: thread.messages.map(cloneMessage),
      pendingApproval: thread.pendingApproval ?? null,
      chips: thread.chips.map((chip) => ({ ...chip })),
      slashCommands: [...thread.slashCommands],
      cwd: thread.cwd,
      permissionSummary: thread.permissionSummary,
      isGenerating: thread.isGenerating
    };
  }

  createThread(cwd?: string): ClientSnapshot {
    const id = randomUUID();
    const workspacePath = cwd?.trim() || this.workspacePath;
    this.threads.set(id, {
      summary: {
        id,
        title: "新会话",
        preview: "等待输入",
        status: "idle",
        groupKind: "project",
        groupLabel: shrinkWorkspacePath(workspacePath),
        cwd: workspacePath,
        archived: false
      },
      messages: [],
      chips: [
        { label: "openai", icon: "context" },
        { label: shrinkWorkspacePath(workspacePath), icon: "file" }
      ],
      pendingApproval: null,
      slashCommands: ["/compact  压缩上下文", "/goal  设置目标", "! ls  运行 shell 命令"],
      cwd: workspacePath,
      permissionSummary: "workspace-write",
      isGenerating: false
    });
    this.events.emit("changed");
    return this.getSnapshot(id);
  }

  forkThread(threadId: string): ClientSnapshot {
    const source = this.ensureThread(threadId);
    const id = randomUUID();
    this.threads.set(id, {
      ...source,
      summary: {
        ...source.summary,
        id,
        title: `${source.summary.title} 副本`,
        status: "idle",
        archived: false
      },
      messages: source.messages.map(cloneMessage),
      chips: source.chips.map((chip) => ({ ...chip })),
      pendingApproval: null,
      slashCommands: [...source.slashCommands],
      isGenerating: false,
      pendingTimer: undefined
    });
    this.events.emit("changed");
    return this.getSnapshot(id);
  }

  selectThread(threadId: string): ClientSnapshot {
    return this.getSnapshot(threadId);
  }

  renameThread(threadId: string, name: string): ClientSnapshot {
    const thread = this.ensureThread(threadId);
    const trimmed = name.trim();
    if (trimmed.length > 0) {
      thread.summary = { ...thread.summary, title: trimmed };
    }
    this.events.emit("changed");
    return this.getSnapshot(thread.summary.id);
  }

  archiveThread(threadId: string): ClientSnapshot {
    const thread = this.ensureThread(threadId);
    thread.summary = { ...thread.summary, archived: true };
    this.events.emit("changed");
    return this.getSnapshot(this.getDefaultThreadId());
  }

  unarchiveThread(threadId: string): ClientSnapshot {
    const thread = this.ensureThread(threadId);
    thread.summary = { ...thread.summary, archived: false };
    this.events.emit("changed");
    return this.getSnapshot(thread.summary.id);
  }

  refreshThreads(selectedThreadId?: string): ClientSnapshot {
    return this.getSnapshot(selectedThreadId);
  }

  loadOlderMessages(threadId: string): ClientSnapshot {
    return this.getSnapshot(threadId);
  }

  sendPrompt(threadId: string, text: string): ClientSnapshot {
    const thread = this.ensureThread(threadId);
    if (thread.pendingTimer) {
      clearTimeout(thread.pendingTimer);
      thread.pendingTimer = undefined;
    }

    thread.messages = [
      ...thread.messages,
      {
        id: randomUUID(),
        role: "user",
        blocks: [{ kind: "text", value: text }]
      },
      {
        id: "assistant-pending",
        role: "assistant",
        blocks: [{ kind: "status", value: "正在生成…" }]
      }
    ];
    thread.isGenerating = true;
    thread.summary.status = "running";
    thread.summary.preview = text;
    thread.pendingApproval = text.includes("审批")
      ? "允许桌面端执行一次受限命令，以验证移动端审批流。"
      : null;

    thread.pendingTimer = setTimeout(() => {
      const resolved = this.ensureThread(threadId);
      resolved.messages = resolved.messages
        .filter((message) => message.id != "assistant-pending")
        .concat(buildAssistantResponse(text));
      resolved.isGenerating = false;
      resolved.summary.status = resolved.pendingApproval ? "needs_approval" : "idle";
      resolved.pendingTimer = undefined;
      this.events.emit("changed");
    }, 1400);

    this.events.emit("changed");
    return this.getSnapshot(threadId);
  }

  stopTurn(threadId: string): ClientSnapshot {
    const thread = this.ensureThread(threadId);
    if (thread.pendingTimer) {
      clearTimeout(thread.pendingTimer);
      thread.pendingTimer = undefined;
    }

    thread.messages = thread.messages
      .filter((message) => message.id != "assistant-pending")
      .concat({
        id: randomUUID(),
        role: "system",
        blocks: [{ kind: "status", value: "已停止，桌面端可继续补充输入。" }]
      });
    thread.isGenerating = false;
    thread.summary.status = thread.pendingApproval ? "needs_approval" : "idle";
    this.events.emit("changed");
    return this.getSnapshot(threadId);
  }

  approveCurrent(threadId: string, allow: boolean): ClientSnapshot {
    const thread = this.ensureThread(threadId);
    if (!thread.pendingApproval) {
      return this.getSnapshot(threadId);
    }
    thread.pendingApproval = null;
    thread.summary.status = thread.isGenerating ? "running" : "idle";
    thread.messages = thread.messages.concat({
      id: randomUUID(),
      role: "system",
      blocks: [{ kind: "status", value: allow ? "审批已允许" : "审批已拒绝" }]
    });
    this.events.emit("changed");
    return this.getSnapshot(threadId);
  }

  private ensureThread(threadId: string): ThreadRecord {
    return this.threads.get(threadId) ?? this.threads.get(this.getDefaultThreadId())!;
  }
}

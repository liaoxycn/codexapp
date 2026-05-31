import { randomUUID } from "node:crypto";
import { EventEmitter } from "node:events";
import process from "node:process";
import type {
  ClientSnapshot,
  GatewayBlockPayload,
  GatewayChipPayload,
  GatewayMessagePayload,
  GatewayThreadPayload
} from "./protocol.js";

interface ThreadRecord {
  summary: GatewayThreadPayload;
  messages: GatewayMessagePayload[];
  chips: GatewayChipPayload[];
  pendingApproval?: string | null;
  slashCommands: string[];
  cwd: string;
  permissionSummary: string;
  isGenerating: boolean;
  pendingTimer?: NodeJS.Timeout;
}

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

  listThreads(): GatewayThreadPayload[] {
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

function buildAssistantResponse(text: string): GatewayMessagePayload {
  const blocks: GatewayBlockPayload[] = [
    {
      kind: "text",
      value: "这条回复来自 desktop gateway 的内存后端。下一步把这里替换成真实 codex app-server 回放即可。"
    },
    {
      kind: "code",
      language: "json",
      value: JSON.stringify(
        {
          upstream: "codex app-server",
          action: "turn/start",
          prompt: text
        },
        null,
        2
      )
    }
  ];

  if (text.startsWith("!")) {
    blocks.push({
      kind: "text",
      value: "命令入口保留给 gateway 聚合。后续会把 shell 命令、输出块和审批请求映射到移动端卡片。"
    });
  }

  return {
    id: randomUUID(),
    role: "assistant",
    blocks
  };
}

function cloneMessage(message: GatewayMessagePayload): GatewayMessagePayload {
  return {
    ...message,
    blocks: message.blocks.map((block) => ({ ...block }))
  };
}

function shrinkWorkspacePath(value: string): string {
  const normalized = value.replaceAll("\\", "/");
  const segments = normalized.split("/").filter(Boolean);
  return segments.slice(-1)[0] ?? value;
}

function createSeedThreads(workspacePath: string): ThreadRecord[] {
  return [
    {
      summary: {
        id: "thread-android-shell",
        title: "Android 壳开发",
        preview: "desktop gateway + app-server bridge",
        status: "running",
        cwd: workspacePath,
        groupKind: "project",
        groupLabel: shrinkWorkspacePath(workspacePath)
      },
      messages: [
        {
          id: randomUUID(),
          role: "user",
          blocks: [{ kind: "text", value: "先把 Android 壳和 desktop gateway 的链路打通。" }]
        },
        {
          id: randomUUID(),
          role: "assistant",
          blocks: [
            {
              kind: "text",
              value: "先用窄协议跑通 `hello / select_thread / send_prompt / stop_turn`，再把真实 app-server 事件压平给移动端。"
            },
            {
              kind: "code",
              language: "rpc",
              value: "thread/list\nthread/read\nturn/start\nturn/interrupt"
            }
          ]
        }
      ],
      chips: [
        { label: "gateway.ts", icon: "file" },
        { label: "workspace map", icon: "context" }
      ],
      pendingApproval: "允许桌面端扫描工作区目录，为移动端输入区生成最近文件索引。",
      slashCommands: ["/compact  压缩上下文", "/goal  设置目标", "! ls  运行 shell 命令"],
      cwd: workspacePath,
      permissionSummary: "workspace-write",
      isGenerating: false
    },
    {
      summary: {
        id: "thread-composer",
        title: "输入区状态机",
        preview: "steer / approval / retry",
        status: "needs_approval",
        cwd: workspacePath,
        groupKind: "project",
        groupLabel: shrinkWorkspacePath(workspacePath)
      },
      messages: [
        {
          id: randomUUID(),
          role: "user",
          blocks: [{ kind: "text", value: "把移动端输入区状态机列全。" }]
        },
        {
          id: randomUUID(),
          role: "assistant",
          blocks: [
            {
              kind: "text",
              value: "至少要覆盖空输入、输入中、发送中、生成中、生成中补充输入、待审批、待补充、失败重试。"
            }
          ]
        }
      ],
      chips: [
        { label: "composer state", icon: "context" },
        { label: "approval flow", icon: "file" }
      ],
      pendingApproval: "允许一次命令审批模拟，验证移动端底部审批卡片。",
      slashCommands: ["/compact  压缩上下文", "/rollback  回滚上轮", "! git status  查看状态"],
      cwd: workspacePath,
      permissionSummary: "workspace-write",
      isGenerating: false
    },
    {
      summary: {
        id: "thread-history",
        title: "线程历史分页",
        preview: "thread/turns/list experimental",
        status: "idle",
        cwd: workspacePath,
        groupKind: "project",
        groupLabel: shrinkWorkspacePath(workspacePath),
        archived: true
      },
      messages: [
        {
          id: randomUUID(),
          role: "user",
          blocks: [{ kind: "text", value: "历史过长时移动端怎么分页？" }]
        },
        {
          id: randomUUID(),
          role: "assistant",
          blocks: [
            {
              kind: "text",
              value: "默认只拉最近窗口；长历史再灰度启用 `thread/turns/list`，避免 UI 和协议同时失控。"
            }
          ]
        }
      ],
      chips: [{ label: "turn history", icon: "context" }],
      pendingApproval: null,
      slashCommands: ["/compact  压缩上下文"],
      cwd: workspacePath,
      permissionSummary: "workspace-write",
      isGenerating: false
    }
  ];
}

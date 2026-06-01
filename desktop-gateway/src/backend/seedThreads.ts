import { randomUUID } from "node:crypto";
import type { ThreadRecord } from "./types.js";
import { shrinkWorkspacePath } from "./helpers.js";

export function createSeedThreads(workspacePath: string): ThreadRecord[] {
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
      slashCommands: ["/compact  压缩上下文", "/rollback  回滚上轮", "! ls  运行 shell 命令"],
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
        title: "线程历史窗口",
        preview: "thread/read bounded window",
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
              value: "当前 app-server 未暴露 `thread/turns/list` 请求；移动端先基于 `thread/read` 返回历史做本地窗口展开。"
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

# Mobile Gateway 对接协议

本文档描述 Android App 与 `desktop-gateway` 的 WebSocket 协议。底层 Codex App Server JSON-RPC schema 见 `docs/research/codex-app-server-protocol/`；移动端只对接 gateway 的压平协议。

## 连接

- URL: `ws://<desktop-ip>:8765/mobile`
- 模拟器默认: `ws://10.0.2.2:8765/mobile`
- 首包必须发送 `hello`。如 gateway 配置了 pair token，`pairToken` 必须匹配。
- Android 默认声明能力: `snapshot_patch`。不声明时 gateway 每次发送完整 `snapshot`。

```json
{
  "type": "hello",
  "client": "android-shell",
  "version": "0.2.0",
  "pairToken": null,
  "capabilities": ["snapshot_patch"]
}
```

## 客户端消息

| type | 参数 | 作用 |
| --- | --- | --- |
| `hello` | `client`, `version`, `pairToken`, `capabilities` | 鉴权、能力协商、拉首个 snapshot |
| `create_thread` | `cwd?` | 新开会话，默认继承当前 cwd |
| `select_thread` | `threadId` | 切换会话 |
| `fork_thread` | `threadId` | 从指定会话 fork |
| `rename_thread` | `threadId`, `name` | 重命名会话 |
| `archive_thread` | `threadId` | 归档会话并选择下一个可用会话 |
| `unarchive_thread` | `threadId` | 反归档并选中 |
| `refresh_threads` | `forceSnapshot?` | 手动刷新目录与当前会话；`forceSnapshot=true` 时下一包强制完整 `snapshot` |
| `load_older_messages` | 无 | 当前会话加载更早历史 |
| `send_prompt` | `text`, `threadId?` | 发送输入；如指定 threadId 会先切换 |
| `stop_turn` | 无 | 中断当前 turn |
| `approve_pending` | 无 | 允许当前审批 |
| `reject_pending` | 无 | 拒绝当前审批 |

当前协议未单独定义“编辑已发送消息”。移动端编辑/重发走组合流程：本地把旧用户消息内容填回输入框，用户修改后发送新的 `send_prompt`；如需要回滚上下文，先发送 slash 命令 `/rollback`。

## 服务端消息

### status

```json
{ "type": "status", "status": "connected", "detail": "已配对 android-shell 0.2.0" }
```

`status` 可为 `connected`、`connecting`、`disconnected`、`error`。`detail` 直接展示给用户。

### snapshot

`snapshot` 是完整状态，客户端应替换本地远端状态。

```json
{
  "type": "snapshot",
  "revision": 12,
  "threads": [],
  "selectedThreadId": "thread-1",
  "messages": [],
  "hasMoreHistory": false,
  "pendingApproval": null,
  "chips": [],
  "slashCommands": ["/compact", "/rollback", "! ls"],
  "cwd": "D:/repo",
  "permissionSummary": "workspace-write",
  "isGenerating": false
}
```

### snapshot_patch

`snapshot_patch` 只带变更字段。客户端必须校验 `baseRevision`；不匹配时发送 `refresh_threads` 且 `forceSnapshot=true`，gateway 下一包应发送完整 snapshot。

```json
{
  "type": "snapshot_patch",
  "baseRevision": 12,
  "revision": 13,
  "changed": ["messages", "isGenerating"],
  "messages": [],
  "isGenerating": true
}
```

## 主要 payload

### thread

```ts
{
  id: string
  title: string
  preview: string
  subtitle?: string
  cwd?: string
  status: "running" | "idle" | "needs_approval" | "failed"
  updatedAt?: number
  groupKind?: "project" | "chat"
  groupLabel?: string
  archived?: boolean
  gitBranch?: string
  gitSha?: string
}
```

`groupLabel/cwd/gitBranch/gitSha` 用于抽屉搜索与项目分组。归档线程可展示在历史/归档区，不应默认抢占当前选中线程。

### message

```ts
{
  id: string
  role: "user" | "assistant" | "system"
  blocks: Array<{
    kind:
      | "text" | "code" | "status" | "reasoning"
      | "commandSummary" | "commandMeta"
      | "fileChangeSummary" | "fileChangeMeta" | "fileChangeDiff"
    value: string
    language?: string
    path?: string
  }>
}
```

客户端必须按 `blocks` 顺序渲染。未知 `kind` 应降级为文本，避免新协议导致空消息。

## App Server 映射规则

gateway 负责把 Codex App Server JSON-RPC 压平成移动端 snapshot：

- `thread/list/read/resume/start/fork/archive/unarchive` -> `threads`、`selectedThreadId`、`messages`
- `turn/start/steer/interrupt` -> `send_prompt`、`stop_turn`
- `serverRequest/*` -> `pendingApproval`
- `item/*`、`rawResponseItem/completed`、`thread/realtime/*` -> `messages`
- `thread/tokenUsage/updated`、`hook/*`、`model/*`、`warning/error` -> system/status message
- `account/*`、`skills/changed`、`mcpServer/*`、`app/list/updated`、`fs/changed`、`fuzzyFileSearch/*` -> selected thread 的 operational status
- `command/exec/outputDelta`、`process/outputDelta`、`process/exited` 是 connection-scoped 流；当前只 acknowledge，不写入会话

当前 `ServerNotification` 分发要求：schema 里的通知 method 必须在 `desktop-gateway/src/bridge/notifications.ts` 显式处理或显式 no-op。

## 高频人类流程测试清单

每次协议或 app/gateway 交互改动后，至少覆盖：

1. 首连：启动 gateway，App 连接，收到首个 snapshot。
2. 新会话：创建会话，输入消息，看到用户 optimistic 消息和助手流式响应。
3. 切换会话：连续切换两个会话，标题、消息、输入草稿不串。
4. 浏览历史：进入长会话，触发 `load_older_messages`，滚动锚点不跳。
5. 中断会话：发送长任务后 `stop_turn`，状态从 running 回到 idle，并出现停止提示。
6. 编辑/重发：长按已发送用户消息，回填输入框，修改后重新发送；必要时先 `/rollback`。
7. 审批：触发命令/文件审批，分别测试允许、拒绝。
8. 归档/fork/重命名：目录和当前选中线程更新正确。
9. 断线重连：断开 gateway 后重连，不丢当前线程和草稿。
10. patch 降级：制造 patch revision 不匹配，应自动请求 `forceSnapshot`，下一次 full snapshot 恢复。

## 自测命令

后端或 app 代码改动后先跑：

```powershell
node scripts/dev-run.mjs
```

完整回归：

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
cd desktop-gateway
npm run build
node --test test\*.test.mjs
npm run protocol:selftest
```

注意：`npm run build` 必须先于 `node --test`，测试读取 `dist/`，并行会读到旧构建。

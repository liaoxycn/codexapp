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
| `create_thread` | `cwd?`, `model?`, `reasoningEffort?`, `sandboxMode?` | 直接新开会话，默认继承当前 cwd |
| `select_thread` | `threadId` | 切换会话 |
| `fork_thread` | `threadId`, `numTurns?` | 从指定会话 fork；传 `numTurns` 时从第 N 个 turn 后分叉 |
| `rename_thread` | `threadId`, `name` | 重命名会话 |
| `archive_thread` | `threadId` | 归档会话并回到新会话草稿态 |
| `unarchive_thread` | `threadId` | 反归档并选中 |
| `refresh_threads` | `forceSnapshot?` | 手动刷新目录与当前会话；`forceSnapshot=true` 时下一包强制完整 `snapshot` |
| `load_older_messages` | 无 | 当前会话加载更早历史 |
| `send_prompt` | `text`, `threadId?`, `newThread?`, `cwd?`, `model?`, `reasoningEffort?`, `sandboxMode?` | 发送输入；`newThread=true` 或无选中会话时先按草稿配置创建真实会话 |
| `stop_turn` | 无 | 中断当前 turn |
| `approve_pending` | 无 | 允许当前审批 |
| `reject_pending` | 无 | 拒绝当前审批 |

当前协议未单独定义“编辑已发送消息”。移动端编辑/重发走组合流程：本地把旧用户消息内容填回输入框，用户修改后发送新的 `send_prompt`；如需要回滚上下文，先发送 slash 命令 `/rollback`。

分叉入口属于消息/turn，不属于会话列表。App Server 的 `thread/fork` 参数只有 `threadId`，没有 turn id；gateway 通过 `fork_thread.numTurns` 实现移动端 turn 级分叉：先调用 `thread/fork` 复制会话，再按源会话总 turn 数对新会话调用 `thread/rollback` 裁掉后续 turns。

移动端默认启动到“新对话草稿态”。草稿态只保存在本地 UI；用户发送第一条消息时，App 发送 `send_prompt.newThread=true` 与草稿配置，gateway 先调用 app-server `thread/start`，再对新线程调用 `turn/start`。归档当前会话后，gateway 返回 `selectedThreadId=""`、空消息和空 cwd，App 保持草稿态，不自动跳到其他会话。

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
  "files": [{ "label": "src/App.ts", "path": "D:/repo/src/App.ts" }],
  "slashCommands": ["/compact", "/rollback", "! ls"],
  "cwd": "D:/repo",
  "permissionSummary": "workspace-write",
  "configOptions": {
    "models": [{ "label": "GPT-5", "value": "gpt-5" }],
    "reasoningEfforts": [{ "label": "medium", "value": "medium" }],
    "sandboxModes": [{ "label": "workspace-write", "value": "workspace-write" }],
    "defaults": {
      "model": "gpt-5",
      "reasoningEffort": "medium",
      "sandboxMode": "workspace-write"
    }
  },
  "isGenerating": false
}
```

`files` 只能由 gateway 根据当前 `cwd` 项目目录生成；App 不做全盘/跨项目文件枚举。无 `cwd` 时必须为空。

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

`groupLabel/cwd/gitBranch/gitSha` 用于抽屉搜索与项目分组。移动端目录只同步非归档线程；归档线程不查询、不展示，不应默认抢占当前选中线程。

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
  forkNumTurns?: number
  rollbackNumTurns?: number
  durationMs?: number
  isFinal?: boolean
}
```

客户端必须按 `blocks` 顺序渲染。未知 `kind` 应降级为文本，避免新协议导致空消息。`forkNumTurns` 表示从当前助手回复所在 turn 分叉需要保留的 turn 数；移动端只在助手回复消息菜单展示“从此处分叉”。`rollbackNumTurns` 表示从当前用户消息编辑/重发需要回滚的 turn 数。`durationMs` 是该 turn 的真实耗时。`isFinal=true` 只可由 gateway 在 app-server turn 已完成时下发；移动端只能用它判断最终回复并收起过程信息，不能用 assistant 文本是否存在来推断。

## App Server 映射规则

gateway 负责把 Codex App Server JSON-RPC 压平成移动端 snapshot：

- `thread/list/read/resume/start/fork/archive/unarchive` -> `threads`、`selectedThreadId`、`messages`
- `config/read`、`model/list` -> `configOptions`；新会话页只能展示 app-server 返回的真实模型、推理和 sandbox 选项，不展示审批策略选择
- `file/search`/项目文件枚举 -> `projectFiles`；移动端文件面板只展示当前项目内文件，并过滤 app-server 已排除路径
- `turn/start/steer/interrupt` -> `send_prompt`、`stop_turn`
- `serverRequest/*` -> `pendingApproval`
- `item/*`、`rawResponseItem/completed`、`thread/realtime/*` -> `messages`
- `thread/tokenUsage/updated`、`hook/*`、`model/*`、`warning/error` -> system/status message
- `account/*`、`skills/changed`、`mcpServer/*`、`app/list/updated`、`fs/changed`、`fuzzyFileSearch/*` -> selected thread 的 operational status
- `command/exec/outputDelta`、`process/outputDelta`、`process/exited` 是 connection-scoped 流；当前只 acknowledge，不写入会话

当前 `ServerNotification` 分发要求：schema 里的通知 method 必须在 `desktop-gateway/src/bridge/notifications.ts` 显式处理或显式 no-op。

会话目录对齐 Codex Desktop：`thread/list` 不显式传 `sourceKinds`，使用 app-server 默认交互会话来源，且只请求 `archived=false`。gateway 再按 Desktop 主列表可见性过滤：排除 `cli/exec/unknown/custom`、外部导入线程、`<environment_context>` 占位线程；`vscode` 会话保留规则对齐 Desktop 本地状态：会话 `cwd` 位于 `project-order` / `electron-saved-workspace-roots` / `active-workspace-roots` 的项目根下，或属于 `projectless-thread-ids`，或属于 Desktop 合成普通会话目录时保留；保留 `appServer` 手机新建线程。`cwd` 属于 Desktop 合成普通会话目录时，例如 `Codex/YYYY-MM-DD/new-chat` 或 `Codex/YYYY-MM-DD/<session>`，归为 `groupKind="chat"`、`groupLabel="普通会话"`，不能归到项目 `new-chat`。

## 高频人类流程测试清单

每次协议或 app/gateway 交互改动后，至少覆盖：

1. 首连：启动 gateway，App 连接，收到首个 snapshot。
2. 新会话：创建会话，输入消息，看到用户 optimistic 消息和助手流式响应。
3. 切换会话：连续切换两个会话，标题、消息、输入草稿不串。
4. 浏览历史：进入长会话，触发 `load_older_messages`，滚动锚点不跳。
5. 中断会话：发送长任务后 `stop_turn`，状态从 running 回到 idle，并出现停止提示。
6. 编辑/重发：长按已发送用户消息，回填输入框，修改后重新发送；必要时先 `/rollback`。
7. 审批：触发命令/文件审批，分别测试允许、拒绝。
8. 归档/fork/重命名：归档和重命名在目录操作；fork 在消息/turn 操作，分叉后选中新会话。
9. 断线重连：断开 gateway 后重连，不丢当前线程和草稿。
10. patch 降级：制造 patch revision 不匹配，应自动请求 `forceSnapshot`，下一次 full snapshot 恢复。
11. App 更新：进程冷启动时检查一次 GitHub latest release；本地版本低时显示更新提示；点击后优先交给 Android 系统下载器下载 APK，App 内不显示下载进度；系统下载器不可用时打开浏览器跳转 GitHub latest release 页面。

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

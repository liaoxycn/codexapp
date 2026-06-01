# Mobile Gateway 高频流程测试进度

本文档记录按真实用户视角测试移动端 gateway 协议的进度。协议细节见 `docs/MOBILE_GATEWAY_PROTOCOL.md`。

## 当前已完成修复

| 项 | 概述 | 用户视角流程步骤自述 | 发现的问题 | 修复情况 |
| --- | --- | --- | --- | --- |
| 协议文档 | 明确 Android App 与 `desktop-gateway` 的 WebSocket 对接协议。 | 我打开 App，配置 gateway 地址并连接；我期望首包握手、后续快照、线程操作和审批操作都有稳定约定。 | 原先缺少移动 gateway 的成文协议，客户端/服务端能力边界不清晰。 | 已新增 `docs/MOBILE_GATEWAY_PROTOCOL.md`，覆盖连接、客户端消息、服务端消息、payload、app-server 映射和回归命令。 |
| 通知覆盖 | 覆盖 Codex App Server 高频通知并压平到移动端消息/状态。 | 我在桌面 Codex 里看到模型切换、计划、diff、token、hook、raw/realtime item 等变化时，手机端也应有可理解的状态。 | 多类 app-server notification 未显式处理，移动端可能无感或丢状态。 | 已补 `desktop-gateway/src/bridge/notifications.ts` 分发，schema 中通知已显式处理或显式 no-op；已有 Node 测试覆盖。 |
| 编辑/重发反馈 | 长按用户消息后回填输入框，并让用户立刻能继续编辑。 | 我长按已发送消息，点编辑/重发；我期望旧内容进入输入框、输入框获得焦点、键盘弹出，然后我修改并发送。 | 旧实现只回填输入框，不聚焦，用户容易以为点击无效。 | 已提交 `a63124d Focus composer when editing user messages`：增加 `composerFocusRequest`，回填后请求焦点并显示键盘；已加单测和 Compose 仪器测试。 |
| 线程管理反馈 | 抽屉内 fork/归档/取消归档/重命名后回到主线程视图。 | 我在手机抽屉里操作会话；操作完成后我期望抽屉收起，马上看到当前会话和状态变化。 | 旧实现触发操作后抽屉仍停留在列表上，移动端用户很难判断操作是否生效。 | 已让 fork、重命名、归档、取消归档触发后自动关闭抽屉。 |
| 断线状态收口 | gateway 断开时清掉运行中/审批瞬态状态。 | 我在生成或审批中遇到 gateway 断线；我期望看到明确断线状态，不再误以为任务还在运行或仍可审批。 | 旧状态可能保留 `isGenerating` 或 `pendingApproval`，导致停止/审批 UI 与真实连接状态不一致。 | 已让断开、连接失败、消息解析失败清除运行中和审批状态，同时保留真实会话列表与消息。 |
| 历史锚点验证 | 加载更早消息后保持原阅读位置。 | 我滚到长会话顶部加载历史；旧消息插入到前面后，我期望刚才正在看的消息仍停在原位置附近。 | 现有代码有锚点恢复逻辑，但测试只覆盖触发加载，缺少恢复索引验证。 | 已抽出锚点恢复索引 helper，并补单测覆盖旧消息 prepend 后恢复到原 anchor。 |

## 高频流程测试矩阵

| 流程 | 概述 | 用户视角流程步骤自述 | 发现的问题 | 修复情况 |
| --- | --- | --- | --- | --- |
| 首连 | App 连接 gateway 并收到首个 snapshot。 | 我启动 gateway，打开 App，使用默认模拟器地址连接；我期望看到已连接状态、线程列表和当前线程。 | 已发现协议缺少成文首连要求。 | 文档已补 `hello`、pair token、`snapshot_patch` 能力协商；`node scripts/dev-run.mjs` 已验证 App 能连接并触发 hello/refresh。 |
| 新会话与发送 | 创建新会话，发送 prompt，显示 optimistic 用户消息和助手响应。 | 我从抽屉新建会话，输入问题并发送；我期望立即看到自己的消息，随后看到助手流式输出。 | 早期缺少对 raw/realtime item 的完整移动端呈现保障。 | 已补 raw/realtime/item 通知映射和测试；仍需继续补更完整的端到端用户流程测试。 |
| 切换会话 | 在两个会话间切换，消息和草稿不串。 | 我在会话 A 输入草稿，切到会话 B，再切回 A；我期望标题、消息、草稿都回到对应会话。 | 暂未在本轮发现新问题。 | 现有单测覆盖草稿/切换基础逻辑；后续继续做真实流程复测。 |
| 浏览历史 | 长会话加载更早消息并保持阅读位置。 | 我进入长会话，滚到顶部触发加载历史；我期望旧消息出现，当前阅读锚点不跳。 | 锚点恢复缺少直接测试。 | 已补锚点恢复索引单测；后续仍可补端到端滚动视觉验证。 |
| 中断会话 | 运行中 turn 可停止并回到 idle。 | 我发送长任务，点停止；我期望状态从运行中恢复为空闲，并看到停止提示。 | 暂未在本轮发现新问题。 | gateway 已有 `stop_turn`/interrupt 测试；后续继续跑真实用户流程。 |
| 编辑/重发 | 已发送消息可回填、修改、再次发送。 | 我长按旧消息，选择编辑，修改文本后再次发送；必要时先输入 `/rollback` 回滚上下文。 | 回填后不聚焦，反馈弱。 | 已修复并提交；`connectedDebugAndroidTest` 已覆盖输入框聚焦。 |
| 审批 | 命令、文件、权限审批可允许或拒绝。 | 我触发需要确认的命令或文件修改，分别点允许/拒绝；我期望 pending 状态准确消失，并看到结果。 | 多种 serverRequest 类型需要统一映射。 | 已补 command/file/MCP/权限/tool input 审批响应映射测试；仍需继续做移动端完整交互复测。 |
| 归档/fork/重命名 | 线程目录操作后当前选中和列表更新正确。 | 我重命名当前会话、fork 出新会话、归档旧会话；我期望抽屉收起，并在主视图看到当前标题/状态更新。 | 触发操作后抽屉不关闭，反馈弱。 | 已修复抽屉自动收起；gateway/app 命令发送与目录变更已有测试。 |
| 断线重连 | gateway 断开后 App 能恢复连接且不丢草稿。 | 我断开 gateway，再重新启动；我期望 App 显示断线、自动/手动重连，当前线程和输入草稿还在，且不会残留运行中/审批按钮。 | 断线后可能残留运行中或审批瞬态状态。 | 已修复断线/失败状态收口；已有重连策略代码，后续需补真实断线重连自动化覆盖。 |
| patch 降级 | patch revision 不匹配时自动请求 full snapshot 恢复。 | 我在异常网络/乱序情况下收到过期 patch；我期望 App 不应用错误状态，而是自动刷新完整状态，不需要手动猜测怎么恢复。 | 旧实现只提示“请刷新”，gateway 继续保留 patch 基线，恢复链路不够自动。 | 已补 `refresh_threads.forceSnapshot`：Android stale patch 时自动请求强制完整 snapshot；gateway 收到后重置该客户端 patch 基线。 |

## 最近自测记录

- `node scripts/dev-run.mjs`：通过，已构建安装 APK、启动 gateway、打开 App，并看到 Android hello/refresh。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`：通过。
- `.\gradlew.bat :app:connectedDebugAndroidTest`：通过，16 个仪器测试通过。
- `desktop-gateway npm run build`：通过。
- `desktop-gateway node --test test\*.test.mjs`：通过，127 个测试通过。
- `desktop-gateway npm run protocol:selftest`：通过。

## 下一步优先级

1. 把高频流程矩阵补成更完整的自动化端到端测试。
2. 继续按用户视角找弱反馈问题，例如编辑/重发是否还需要 toast 或视觉确认。
3. 盘点无法实现或未使用的兼容导出层、mock 代码，确认无引用后清理。

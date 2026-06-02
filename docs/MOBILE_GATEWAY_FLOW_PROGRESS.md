# Mobile Gateway 高频流程测试进度

本文档只保留当前版本仍有价值的结论。协议细节见 `docs/MOBILE_GATEWAY_PROTOCOL.md`。

## 当前版本概览

| 项 | 当前状态 |
| --- | --- |
| 协议与通知 | 已补齐 Android App 与 `desktop-gateway` 协议文档，常见 app-server 通知已映射或显式 no-op。 |
| 新会话流程 | 默认进入草稿页，首条消息发送时才创建真实会话；模型、推理、sandbox 选项来自真实配置。 |
| 会话列表 | 只展示 Desktop 当前可见的未归档会话；普通会话不再误归到 `new-chat` 或错误项目。 |
| 消息流 | 运行中展示 reasoning/命令/工具/网页搜索等过程；完成后过程折叠到“已处理 X 项”，最终回复保留完整正文。 |
| turn 操作 | 编辑后重发改为“发送时才回滚”；复制、分叉、耗时移到 assistant 最终回复底部；未完成 turn 禁止复制/分叉。 |
| 长会话交互 | 顶部下拉加载历史、底部上滑刷新、历史锚点恢复、较大的初始消息窗口都已收口。 |
| 运行态同步 | 多 turn、goal、stale refresh、断线等场景已补运行态租约和 App 侧 live refresh 兜底，减少“闪空闲”。 |
| 连接与诊断 | 抽屉有轻量诊断折叠行；主区断线横幅展示具体原因；入站日志已脱敏。 |
| 线程操作反馈 | 重命名、归档、取消归档、分叉后反馈路径已收口；归档当前会话会回到新对话草稿页。 |
| 更新与发版 | 冷启动仅检查一次 GitHub Release；系统下载器优先；发布检查脚本与发布脚本已补日志和 latest 摘要。 |

## 最近验证

- `node scripts/dev-run.mjs`：通过；按项目要求完成最新安装与启动验证。
- `desktop-gateway npm run build`、`desktop-gateway node --test test\\*.mjs`、`desktop-gateway npm run protocol:selftest`：最近一轮均通过。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`、`.\gradlew.bat :app:compileDebugAndroidTestKotlin`：最近一轮均通过。
- ADB/真机烟测已覆盖：新对话首条消息、普通会话默认 `cwd`、抽屉会话可见性、更新提示、长会话运行态与刷新提示。
- 最近已发布版本：`v0.2.24`。

## 稳定性 + 可观测 + 体验收口

| 项 | 概述 | 用户视角流程步骤自述 | 发现的问题/修复情况 |
| --- | --- | --- | --- |
| 状态诊断折叠行 | 抽屉内提供轻量状态排障信息。 | 我打开抽屉；普通空闲状态不出现调试噪声；出现运行中、切换中、失败或关键远程动作完成后 5 秒内，才看到一行小号诊断摘要，点开查看 selected/pending/generating/running/action trace。 | 之前状态同步问题只能靠日志猜测，App 端看不到当前 snapshot revision、运行会话数和最后动作。已新增 gateway `diagnostics` payload 与 App 条件折叠行，成功动作过期后自动隐藏，不影响正常会话列表阅读。 |
| Gateway 动作 trace | 移动端远程动作统一带 trace id、类型、状态和耗时日志。 | 我点击刷新、切换、发送、分叉、归档等按钮；如果状态异常，可以用抽屉诊断里的 trace id 对照 gateway 日志。 | 旧日志分散，排查“来回跳/状态闪空闲”缺少统一动作边界。已在 backend action 和手动 refresh 管线加入 trace，并随 snapshot 下发最后动作状态。 |
| Gateway 入站日志摘要 | gateway 日志可用于排障，但不泄露 prompt 原文。 | 我查看 gateway 日志，只看到 `type/thread/textLen/numTurns` 等路由摘要，不会把输入内容或 pair token 原样打印出来。 | 旧日志直接输出完整 websocket payload，排障信息太吵，也可能暴露用户输入。已改成结构化摘要，并补测试验证 prompt 与 token 不进入日志摘要。 |
| 发布前检查脚本 | 发布前可一键串联核心检查。 | 我准备发包前运行 `node scripts/pre-release-check.mjs`；需要真机安装再加 `--dev-run`。 | 之前发布检查命令散落在文档和历史记录里，容易漏跑。已新增脚本并同步项目 wiki；本轮仍按项目要求单独执行 `node scripts/dev-run.mjs`。 |
| 诊断行过期收口 | 最近动作诊断提示应按时间自动消失。 | 我点刷新/切换后打开抽屉；我期望 5 秒内能看到 trace，超过 5 秒即使没有新 snapshot 推送，也自动恢复干净列表。 | 发现旧实现直接在 Compose 中读 `System.currentTimeMillis()`，没有状态时钟，若远端不再推送状态，成功动作诊断行可能停留到下一次重组。已新增诊断时钟，在动作过期点主动触发重组隐藏，失败/运行中/切换中仍持续显示。 |
| 底部刷新提示文案 | 上滑刷新提示要准确表达当前状态。 | 我在会话底部上滑刷新；如果当前会话正在生成，App 不应显示“刷新会话中”误导我以为刷新已触发，而应提示会话仍在运行。 | 旧 `PullRefreshHint` 把 `isGenerating` 和 `isManualRefreshing` 合并成“刷新会话中”，但生成中刷新实际被禁止。已抽出 `pullRefreshHintLabel`，手动刷新显示“刷新会话中”，生成中显示“会话运行中”，并补单测覆盖四种提示状态。 |
| 发布脚本自收口 | 发包脚本应把部署细节收进日志，外部 AI 只负责传参等待退出。 | 我执行 `node scripts/github-release.mjs -Version ... -VersionCode ... -Notes ...` 发包；我期望脚本自己提交、写 release notes、推分支、推 tag 并触发 Actions。若 GitHub 网络短暂失败，脚本内部重试并写日志，外部调用不再围绕部署失败反复消耗对话；多行更新说明也应完整写入 release notes。 | 旧脚本 push/tag 失败会抛到外部，AI 需要人工判断重跑；后续断点恢复仍会把网络错误暴露给对话。已改为关键步骤写入 `scripts/logs/github-release-*.log`，branch push/tag push 各自最多重试 3 次，失败只记录日志且脚本对外正常结束；远端 tag 已存在时视为已触发发布。本轮继续修复 `-Notes` 参数截断问题：发布脚本会消费 `-Notes` 后续文本直到下一个已知参数，Markdown bullet 不再被误判成参数，并新增脚本单测覆盖。 |
| App 侧 live refresh 兜底 | 选中会话顶层仍在生成时，不能因为列表状态短暂 idle 就停止刷新。 | 我观察长耗时会话，若抽屉列表某次旧快照把会话状态刷成空闲，但当前消息流仍有生成态或审批态；我期望 App 继续保持当前会话刷新，不让旧列表状态把后续同步停掉。 | 旧 `selectedThreadNeedsLiveRefresh` 只看会话列表里的 selected thread 状态，忽略顶层 `isGenerating/pendingApproval`。当 gateway 顶层证据仍显示当前会话在跑、列表摘要却短暂 idle 时，App 可能停止 live refresh。已把顶层生成/审批态作为刷新兜底，但仍要求有 selectedThreadId，避免新会话草稿或无选中会话误刷。 |
| App live refresh 执行器 | 策略命中运行中/审批会话后，应真的启动受控刷新，而不是只保留策略函数。 | 我停留在运行中的长会话或 goal 会话；即使 gateway 某些推送不完整，App 也应每隔数秒刷新当前选中会话，直到会话空闲、切换会话或断开连接。 | 深排发现 `LiveRefreshCoordinator.sync()` 仍是空实现，策略函数有测试但没有执行轮询，长耗时会话缺少 App 侧主动兜底。本轮实现按当前 selected thread 启动 2.5 秒间隔轮询，pending selection/目标变化/空闲会立即停止；新增单测覆盖运行中轮询、pending 切换停止、空闲不轮询、同一会话不重复启动。 |
| Goal 多 turn 运行态续租 | 有目标的持续会话不应在多个 turn 间隙掉成空闲。 | 我在 Desktop 开启 goal 目标后让会话持续执行多个 turn；我期望移动端抽屉和消息流只要 goal 仍是 active，就稳定显示运行中，刷新也不突然闪空闲。 | 深排发现 `thread/goal/updated` 旧逻辑只写“目标”状态消息，没有把 `active` goal 当成运行信号；多 turn 场景下上一轮 `turn/completed` 或随后的 `thread/read` 可能把 `isGenerating/currentTurnId` 清掉。已让 `active` goal 更新续租 running、记录 `turnId`、刷新本地活动时间；`complete/paused/budgetLimited` 不强制拉成 running，避免误报。 |
| 同秒 idle refresh 防降级 | 刚续租的运行态不能被同一秒返回的旧快照覆盖。 | 我在 goal 会话运行中点刷新或等自动同步；如果 Desktop app-server 返回的 `thread/read` 仍显示上一 turn completed 且时间戳与 goal 更新时间同秒，我期望移动端仍保持运行中。 | App-server时间戳粒度是秒，本地 overlay 是毫秒；旧 overlay 保留逻辑要求 incoming 活动时间严格小于本地时间，同秒快照会被误认为不旧，导致 running lease 被 idle refresh 打掉。已改为有效 running lease 期间，同时间戳或更旧的 idle 快照不能降级，只有明确更新的快照才覆盖。 |
| 断线瞬态清理 | 断线/失败/解析错误后不应残留切换、刷新、加载遮罩。 | 我在切换会话、加载历史或手动刷新时遇到 gateway 断开；我期望立即看到断线/错误状态，不再卡在“正在切换会话”“刷新中”或加载历史状态。 | 旧断线收口只清理 `isGenerating/pendingApproval`，没有清 `pendingSelectionThreadId/pendingThreadTitle/isThreadSwitching/isLoadingOlder/isManualRefreshing`。已让断线、连接失败、手动断开、入站解析失败统一清理这些瞬态；补充 mutation 单测覆盖。 |
| 发布 latest 摘要 | 发布脚本结束后应有固定路径摘要，避免每次翻长日志判断状态。 | 我运行发布脚本后，只需查看 `scripts/logs/github-release-latest.json`，即可知道版本、tag、日志路径、branch/tag push 结果和 Actions 触发状态。 | 旧日志只有时间戳文件，外部 AI 或用户要判断上次发包是否成功仍需找最新日志再阅读。已新增 latest JSON 摘要，脚本任何内部错误仍写入状态且对外退出正常；实际重试发现 Markdown 说明里出现 `-Notes` 字样会被误判为参数，已改为只识别完整 CLI option token 并补回归测试。 |
| App 入站日志脱敏 | 移动端调试日志应帮助排查协议状态，但不能输出完整消息正文。 | 我用 logcat 排查 gateway 连接时，只需要看到入站消息类型、revision、线程/消息数量、changed 字段、运行态等摘要；不应把用户 prompt、助手回复、状态 detail 全量打印出来。 | `GatewayRepositoryConnection` 旧日志直接 `Log.d("inbound: $raw")`，长消息会刷屏且可能泄露正文。本轮改成 `summarizeInboundForLog` 结构化摘要，异常 JSON 也只记录字节数和错误类型；新增单测确保 title/preview/正文/detail 不出现在日志摘要里。 |
| 主区连接横幅细节 | 消息区顶部连接横幅在断线/异常时应直接给出原因。 | 我停在主消息区遇到 gateway 断开、解析失败或连接异常；我期望不用先打开抽屉，也能在顶部横幅直接看到具体原因，再决定是否重连。 | 旧主区横幅只有“连接异常/未连接 Desktop Gateway”，具体 `connectionDetail` 只在抽屉里可见，主区排障反馈太弱。 | 已让主区连接横幅在 `ERROR`/`DISCONNECTED` 时显示最多两行具体原因，并过滤“未连接 gateway/未连接 desktop gateway”这类泛文案；补单测和 Compose 可见性测试覆盖解析失败详情显示。 |
| 编辑后重发提示 | 用户进入“编辑后重发”后，输入区附近应有明确提示。 | 我长按旧用户消息点“编辑后重发”；我期望除了输入框被回填，还能马上看到这次处于“下次发送会回滚并重发”的状态，避免误以为只是普通编辑。 | 旧实现虽然已改成“发送时才回滚并重发”，但待发送状态只存在 `ComposerActionHandler` 私有字段里，UI 无感知，提示太弱。 | 已把待编辑重发状态透出到 `HomeUiState`，并在 composer 上方显示轻提示“下一次发送会回滚最近 N 轮后重发”；发送成功、清空输入或改成普通替换后会自动清除；补状态单测和 Compose 可见性测试。 |
| 全局通知 toast 视觉 | 右上角 operational notice 应像常规 toast，清楚可读且不挡标题栏。 | 我触发 MCP/账号等全局通知时，希望看到不透明气泡、清晰字体和稳定位置，而不是过透明的弹幕浮层；通知应整体下移，不压住顶部标题栏。 | 旧样式使用半透明白底和偏轻文字，贴近顶部且行间距过小，可读性差。本轮改为深色不透明 toast 气泡、13sp 中等字重、轻阴影、14dp 圆角，整体下移到标题栏下方，最多同时显示 3 条，并补 Compose 渲染测试。 |
| 未完成 assistant 禁用操作 | 对话未完成的 turn assistant 不能出现复制和分叉功能。 | 我观察正在生成的 assistant 回复；即使底部显示实时耗时，也不应看到“复制文本”或“从此处分叉”按钮，避免复制半截内容或从未稳定上下文分叉。 | 旧 UI 只看消息是否在 turn 末尾和 `isFinal` 元数据，若运行中消息带了 final/fork 元数据仍可能暴露操作。本轮给运行中的当前 turn 标记 `assistantActionsEnabled=false`，只禁复制/分叉，保留耗时显示；补单测和 Compose 回归测试。 |

## 后续只保留两类工作

1. 继续补关键路径自动化，优先长会话、goal、多 turn、断线恢复。
2. 只在发现真实用户可感知问题时再补交互或文档，不再保留阶段性流水账。

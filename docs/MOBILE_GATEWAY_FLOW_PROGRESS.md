# Mobile Gateway 高频流程测试进度

本文档记录按真实用户视角测试移动端 gateway 协议的进度。协议细节见 `docs/MOBILE_GATEWAY_PROTOCOL.md`。

## 当前已完成修复

| 项 | 概述 | 用户视角流程步骤自述 | 发现的问题 | 修复情况 |
| --- | --- | --- | --- | --- |
| 协议文档 | 明确 Android App 与 `desktop-gateway` 的 WebSocket 对接协议。 | 我打开 App，配置 gateway 地址并连接；我期望首包握手、后续快照、线程操作和审批操作都有稳定约定。 | 原先缺少移动 gateway 的成文协议，客户端/服务端能力边界不清晰。 | 已新增 `docs/MOBILE_GATEWAY_PROTOCOL.md`，覆盖连接、客户端消息、服务端消息、payload、app-server 映射和回归命令。 |
| 通知覆盖 | 覆盖 Codex App Server 高频通知并压平到移动端消息/状态。 | 我在桌面 Codex 里看到模型切换、计划、diff、token、hook、raw/realtime item 等变化时，手机端也应有可理解的状态。 | 多类 app-server notification 未显式处理，移动端可能无感或丢状态。 | 已补 `desktop-gateway/src/bridge/notifications.ts` 分发，schema 中通知已显式处理或显式 no-op；已有 Node 测试覆盖。 |
| 编辑/重发反馈 | 长按用户消息后回填输入框，并让用户立刻能继续编辑。 | 我长按已发送消息，点编辑/重发；我期望旧内容进入输入框、输入框获得焦点、键盘弹出，然后我修改并发送。 | 旧实现只回填输入框，不聚焦，用户容易以为点击无效。 | 已提交 `a63124d Focus composer when editing user messages`：增加 `composerFocusRequest`，回填后请求焦点并显示键盘；已加单测和 Compose 仪器测试。 |
| 编辑后重发延迟回滚 | “编辑后重发”必须先回填草稿，用户确认发送后才回滚并发送。 | 我点用户消息菜单里的“编辑后重发”；我期望只把旧消息回填到输入框并聚焦，我修改确认后点发送，App 才回滚到该 turn 并发送新文本。 | 旧实现点击菜单时立刻调用 `rollbackThread`，还没确认新文本就已经改动会话上下文；同时 UI 会显示远程 loading，误导用户以为已提交。 | `ComposerActionHandler` 新增待编辑重发状态；点击“编辑后重发”只回填并记录 `rollbackNumTurns`；发送时改走 gateway `resend_prompt` 一次性回滚并发送；失败保留草稿；编辑入口不再显示远程 loading。 |
| 线程管理反馈 | 抽屉内归档/取消归档/重命名后回到主线程视图。 | 我在手机抽屉里操作会话；操作完成后我期望抽屉收起，马上看到当前会话和状态变化。 | 旧实现触发操作后抽屉仍停留在列表上，移动端用户很难判断操作是否生效。 | 已让重命名、归档、取消归档触发后自动关闭抽屉；分叉入口已迁移到消息/turn。 |
| 断线状态收口 | gateway 断开时清掉运行中/审批瞬态状态。 | 我在生成或审批中遇到 gateway 断线；我期望看到明确断线状态，不再误以为任务还在运行或仍可审批。 | 旧状态可能保留 `isGenerating` 或 `pendingApproval`，导致停止/审批 UI 与真实连接状态不一致。 | 已让断开、连接失败、消息解析失败清除运行中和审批状态，同时保留真实会话列表与消息。 |
| 历史锚点验证 | 加载更早消息后保持原阅读位置。 | 我滚到长会话顶部加载历史；旧消息插入到前面后，我期望刚才正在看的消息仍停在原位置附近。 | 现有代码有锚点恢复逻辑，但测试只覆盖触发加载，缺少恢复索引验证。 | 已抽出锚点恢复索引 helper，并补单测覆盖旧消息 prepend 后恢复到原 anchor。 |
| 冗余导出清理 | 移除 gateway bridge 内部纯聚合导出层。 | 我维护协议映射时，期望内部模块职责清晰，动作逻辑直接引用真实模块，避免绕一层找实现。 | `turnActions.ts` 只是 prompt/approval 的 re-export；`runtimeMessages.ts` 也混合真实逻辑和 re-export，增加定位成本。 | 已把内部引用改到 `promptActions`、`approvalActions`、`runtimeMessageStore`、`runtimeAssistantMessages`，删除纯 re-export 的 `turnActions.ts`。 |
| 协议自测扩展 | 高频线程管理/中断路径纳入 `protocol:selftest`。 | 我像真实用户一样重命名、发送、停止、归档、恢复会话；我期望 bridge 后端路径能被一条协议自测持续覆盖。 | 原 selftest 已覆盖创建、审批、compact、fork、rollback、archive，但缺少重命名、中断和反归档断言。 | 已扩展 fake app-server 与 selftest，覆盖 rename、stopTurn、unarchive。 |
| 新会话草稿页 | App 启动默认进入未落库的新对话页，首条消息才创建真实会话。 | 我打开 App，先看到“新对话”；普通新建不选项目；从项目分组新建时项目已由入口确认；我只在草稿页用下拉选择模型和推理强度，输入第一条消息发送后才创建真实会话；表单应在标题栏和输入区之间垂直居中。 | 旧行为点击新建就直接创建空会话，容易产生 Desktop 不存在或用户没真正开始的会话噪声；本轮发现模型/推理/权限曾是硬编码选项，且误加 Desktop 没有的审批选择；草稿页还重复展示项目选择，和项目入口语义冲突；主动复测又发现模型下拉弹层未跟输入框同宽，长模型名拥挤；截图反馈表单贴近顶部，页面下半部过空。 | gateway 启动/刷新时调用 app-server `config/read` 和 `model/list` 生成 `configOptions`；App 新会话页只展示真实返回的模型、推理、sandbox，去掉审批选择；项目选择已从草稿页移除，`cwd` 只来自新建入口；模型/推理改为同宽下拉；草稿态消息列表改为垂直居中；`send_prompt.newThread=true` 时 gateway 先 `thread/start` 再发送 prompt；Android/Gateway 测试已覆盖。 |
| 新会话模型读取中 | 新会话草稿页模型下拉必须显示真实可用模型，不能长期停在“读取中”。 | 我打开 App 默认新对话页；我期望模型下拉在 gateway 连接后显示实际模型列表和默认模型，而不是一直灰色“读取中”。 | gateway 日志持续打印 `config options unavailable: Cannot read properties of undefined (reading 'request')`。根因是 `AppServerBridgeBackend.refreshConfigOptions()` 把 `this.appServer.configOptions` 方法取出来单独调用，丢失 `this`，导致 `AppServerClient.configOptions()` 内部 `this.request` 为 `undefined`。 | 改为 `this.appServer.configOptions(process.cwd())` 保留实例上下文；新增 `appServerClient.test.mjs` 回归测试验证 `configOptions` 调用时 request 的 `this` 仍是 client；`dev-run` 后 gateway 错误日志不再出现该错误。 |
| 归档后选中策略 | 归档当前会话后回到新会话草稿页，不自动切到别的会话。 | 我归档当前会话；我期望主区显示新对话草稿，而不是突然跳到另一个历史会话。 | 旧 gateway 会按默认线程选择下一个未归档会话，移动端用户容易误操作到无关上下文。 | `archive_thread` 响应已清空 `selectedThreadId/messages/cwd`；App 归档前进入草稿态；Node 单测已覆盖。 |
| 归档会话查询收口 | App 端不查询、不展示已归档会话。 | 我打开 App 抽屉或手动刷新；我期望只看到 Desktop 主列表里的非归档会话，归档后的会话立即从移动端目录消失，抽屉里也不再有“归档”分组。 | 主动排查发现 gateway hydration 会额外调用 `thread/list archived=true`，并把归档会话合进移动端 snapshot；归档动作本地也可能残留刚归档的会话。本轮继续排查发现 Android 抽屉仍保留旧的“归档”分组渲染，即使 gateway 不查归档也会留下错误入口。 | gateway 已停止查询归档列表，只调用 `threadList(false)`；归档后删除本地缓存并回草稿态；Android snapshot mapper 兜底过滤 `archived=true`；本轮删除抽屉“归档”分组和 `archivedThreads` 分区，测试改为断言归档会话完全不进入抽屉分组。 |
| 信息流文本选择 | 去掉消息复制按钮，改为像网页文本一样长按自由选择。 | 我阅读用户/助手/状态消息时，长按文本选择需要复制的片段；界面不再出现整条复制按钮。 | 原信息流有复制按钮，只能整条复制，占空间，也不符合用户想自由选择片段的诉求。 | 已移除用户菜单、助手消息、系统消息复制按钮；文本、markdown、reasoning、inline status 包装 `SelectionContainer`；Compose 测试已改为断言复制入口不存在。 |
| 消息流 turn 级操作按钮 | 信息流操作按钮只挂在每个 turn 的可操作末尾消息上。 | 我阅读一个 turn 内的多段回复或多条用户输入时，只应在最终用户消息看到编辑/重发，只应在最终助手回复看到分叉；按钮应轻量贴边，不再挤占整列正文空间。 | 旧实现只要消息带 `rollbackNumTurns`/`forkNumTurns` 就显示按钮，导致同一 turn 内多条消息右侧都出现三点；按钮 32dp 独立占位，压缩右侧阅读空间。 | 新增 turn 边界判断，只在连续同角色消息的最后一条显示对应操作；按钮缩为 24dp，气泡/正文只预留 22dp 轻量空间；新增 Android 回归测试覆盖同 turn 多条用户/助手消息只末尾显示按钮。 |
| turn 过程信息折叠 | 移动端消息流对齐 Desktop：执行过程增量输出，最终回复产出后默认收起过程，最终回复全文显示。 | 我发送一个长任务；运行中我能持续看到 reasoning、命令、状态和流式 assistant 文本。等 turn 真正结束并出现最终回复后，我期望最终回复成为主内容且完整显示，不再需要底部“展开/收起”；过程信息移动到最终回复顶部的“已处理 X 项”入口，点开才展开详情。 | 旧移动端把过程消息和最终回复按消息数组平铺，完成后过程仍占据大量屏幕空间；如果把过程直接隐藏，又会导致运行中无法看增量进度。后续发现最终回复底部仍有“展开/收起全文”，和 turn 顶部过程展开入口重复；再复测发现运行中已有 assistant 文本时会被误判成最终回复，导致过程提前收起；根因是移动端用“有 assistant 文本”推断最终回复，协议没有显式最终标记，`isGenerating` 又可能短暂不同步。 | gateway 新增 `isFinal`，只在 app-server turn `completedAt` 存在或 `status` 为终态时标记 assistant 文本为最终回复；Android 协议/模型同步该字段，`toTurnMessageItems` 只在 `isFinal=true` 时收起过程。未最终完成时，即使已有流式 assistant 文本且全局生成态短暂为空闲，也保持过程信息平铺增量显示；新增单测覆盖运行中流式文本不折叠、完成后折叠、跨 turn 不串、协议最终标记透传。 |
| App 更新检查 | App 冷启动后异步检查 GitHub Releases，有新版本时在抽屉顶部提示，下载交给系统默认下载器。 | 我打开 App，进程冷启动时自动查询 `liaoxycn/codexapp` 最新 release；后台切走再切回不应重复检查。若 release 版本大于本地版本，我打开抽屉应在 gateway 状态下方看到小号警告色更新提示；点击后优先交给系统下载器；若系统下载器不可用或启动失败，应自动打开浏览器跳转 GitHub latest release 页面。 | 旧实现放在 ViewModel init，后台恢复导致 Activity/ViewModel 重建时可能重复检查；下载用 App 内 OkHttp 写文件并显示进度，完成后再拉安装器，容易触发“安装未知应用”授权页。后续发现仅返回下载错误仍不够，部分设备/权限环境下系统下载器可能不可用。 | 新增进程级 `AppUpdateStartupGate`，只允许冷启动检查一次；下载改为 `DownloadManager.enqueue`，保存到公共 Downloads，通知由系统下载器显示；移除 App 内下载进度、FileProvider、`REQUEST_INSTALL_PACKAGES` 和安装入口；系统下载器不可用、下载地址为空或 enqueue 异常时，自动用 `ACTION_VIEW` 打开 GitHub latest release 页面；单测覆盖版本比较和启动闸门。 |
| 信息流上滑刷新 | 消息流滚到底部后继续上滑应触发当前会话刷新。 | 我在信息流底部继续向上滑动；我期望看到刷新提示并刷新当前会话，不需要去抽屉点刷新。 | 旧实现只在底部 overscroll 后的 `onPostFling` 触发刷新；前面收窄消息列表底部空白后，普通拖拽松手经常没有 fling 回调，表现为提示不稳定或完全不刷新。 | 上滑距离达到阈值后立即触发一次刷新，不再依赖 fling；增加短时去重避免连续触发；新增单测覆盖到达阈值触发、生成中/刷新中/已触发时不重复触发。 |
| 顶部下拉加载历史 | 消息流滚到顶部后继续下拉，应加载更早历史消息。 | 我进入长会话，滚到最顶部，再继续向下拉；我期望出现“加载更早消息”，旧消息插入后当前阅读锚点保持稳定。 | 前面改底部上滑刷新后，消息列表 nested scroll 只接了底部刷新；历史加载仍是旧的“到顶并停止后自动触发”，没有真正监听顶部下拉 overscroll，用户感知像功能消失。 | 新增顶部历史加载 nested scroll 控制器：只有在顶部、存在历史、非加载中且下拉距离超过阈值才触发 `load_older_messages`；保留加载后的锚点恢复；与底部上滑刷新合并到同一个 nested scroll 管线，互不抢方向；单测和 androidTest 编译已覆盖。 |
| 消息流窗口与边缘手势 | 默认多展示 turn，顶部加载历史和底部刷新要顺滑稳定。 | 我打开长会话，默认应看到足够多的上下文；滚到顶部继续下拉应稳定加载历史，滚到底部继续上拉应稳定刷新，不该卡在必须精准 overscroll 的手感。 | 旧 gateway 初始窗口只有 24 条消息，长会话默认 turn 太少；Android 顶部/底部触发都依赖 `available.y` 和严格到顶/到底，许多正常拖拽被 LazyColumn 消耗后不会触发。 | gateway 初始窗口和分页步长提升到 80 条；Android 边缘判断允许 24px 容差，顶部/底部都用 consumed/available 方向累计拖拽，阈值降为 88/96px；保留历史锚点恢复和底部刷新短时去重。 |
| 会话运行态同步 | 持续运行中的 Desktop 会话，移动端不应突然变空闲再恢复。 | 我在 Desktop 运行一个多 loop/长耗时会话，同时打开手机端观察抽屉和消息流；我期望只要 Desktop 仍在运行，App 状态就持续显示运行中，刷新也不应闪空闲。 | 深排发现 gateway 把 `thread/status/changed` 和 `turn/completed` 当成强降级信号：多 loop 时上一轮 completed 后会立刻 refresh 并清 `currentTurnId/isGenerating`，下一轮 `turn/started` 若稍晚到达就闪空闲；更严重的是 completed 的 refresh 未返回时下一轮已 started，旧 finalize 返回后会误清新 turn。Codex hooks 文档只适合工具/Stop/UserPromptSubmit 等生命周期扩展，不是可靠会话运行状态源。 | gateway 新增运行态租约：`turn/started`、assistant/reasoning/command/file/tool/realtime 增量、retry error 等强运行信号续租；`turn/completed` 只进入短 grace，不立即降 idle；`thread/status/changed` 和 stale `thread/read` 在租约内不能降级；finalize 携带 completed turn id，若发现新 turn 已开始则不清新 turn。已加回归测试覆盖 stale status、completion grace、多 loop finalize 竞态。 |
| 过程信息流增强 | 移动端运行过程要更接近 Desktop，明确展示思考、命令、工具、网页搜索等状态。 | 我发送需要执行命令/搜索/调用工具的任务；运行中应看到“正在思考”“正在运行 npm run build”“正在搜索网页”等增量状态，完成后能看到“已运行命令”“已搜索网页”，最终回复出现后过程仍可从“已处理 X 项”展开查看。 | gateway 原来把 tool/web/search 压成很短的普通 text，命令只显示 `命令 inProgress/completed`，App 的 reasoning/status 也只是静态文字，视觉上不像 Desktop 的实时过程流。 | gateway 映射增强：reasoning 空内容变“正在思考”；commandExecution 显示“正在运行 <命令> / 已运行命令 / 命令执行失败”；dynamic/mcp tool 和 webSearch/raw response 映射为 status block；App 的 reasoning/status 对“正在”状态显示小 loading，完成/搜索状态使用对应图标；新增 gateway messageMapping 测试。 |
| turn 底部操作栏 | assistant 回复底部对齐 Desktop，展示复制、分叉、耗时。 | 我看到 assistant 回复正文后，底部像 Desktop 一样出现轻量信息：运行中实时显示耗时；最终回复完成后显示复制、从此处分叉和最终耗时；顶部“已处理 X 项”只负责展开过程，不再有三点菜单。 | 旧顶部行同时承载过程展开和三点菜单，入口位置不像 Desktop；无过程消息时右侧三点按钮还会挤占正文宽度；后续发现耗时只在最终回复后显示，运行中刷新时不会实时更新。 | 已移除 assistant 顶部/右侧三点菜单；复制、分叉迁移到最终回复底部图标行；gateway 对运行中 turn 按 `startedAt` 动态计算 `durationMs`，完成后使用真实 `durationMs` 或完成时间差；Android 对 turn 最后一条 assistant 消息持续显示耗时，复制/分叉仍只在 `isFinal=true` 时显示。 |
| Desktop 普通会话分组 | 对齐 Codex Desktop 会话列表，把合成普通会话目录归为“普通会话”。 | 我在 Desktop 看到“查看深圳天气”是普通会话；我打开 App，也应在普通会话分组看到它，而不是项目 `new-chat`。 | `cwd` 叶子目录 `new-chat` 被当成项目名，`Codex/YYYY-MM-DD/...` 这类 Desktop 合成目录也可能被误判为项目。 | `deriveThreadGrouping` 已识别 `new-chat` 叶子和 `Codex/YYYY-MM-DD/...` 模式为普通会话；已加 `查看深圳天气` 回归测试。 |
| 会话数对齐 Desktop | 移动端列表数量应和 Codex Desktop 主列表一致，不把历史扫描结果全量展示，也不能漏掉 Desktop 正在显示的会话。 | 我对比 Desktop 右侧会话列表和 App 抽屉；我期望看到 `md2html/项目会话测试`、`md2html/写一个1`、`codexapp/持续优化`、`codexapp/1`、`codexapp/hello`、普通会话 `回复一下/重启会话是否中断/查看深圳天气` 等 Desktop 当前可见项。 | 先前误把 `.codex-global-state.json` 的 `heartbeat-thread-permissions-by-id` 当成 Desktop 主列表索引，导致 `写一个1`、`hello`、`回复一下` 这类 `vscode` 会话被过滤掉；但完全放开 `vscode` 又会把历史导入和非工作区项目带进来。 | gateway 改为按 Desktop 本地状态过滤：保留项目根来自 `project-order` / `electron-saved-workspace-roots` / `active-workspace-roots` 的 `vscode` 会话，保留 `projectless-thread-ids` 和 Desktop 合成普通会话目录，仍排除 `cli/exec/unknown/custom`、外部导入线程、`<environment_context>`；回归测试覆盖少会话问题。 |
| 分叉入口迁移到 turn 回复 | 会话分叉不放在会话列表，也不放在用户发送消息上，改为对齐 Desktop：从助手回复消息操作。 | 我打开某个会话，在最终助手回复底部点“从此处分叉”图标；我期望先看到确认弹窗，确认后全局 loading 遮罩提示“正在生成分叉会话”，分叉成功且新会话已经出现在 App 会话列表后，再自动切换到新分叉会话。 | 调研 app-server 协议发现 `thread/fork` 只能按 `threadId` 分叉，没有 `turnId`；会话列表入口语义不对；继续对齐 Desktop 后确认入口应在 turn 的助手回复消息。用户实测发现 Desktop 已出现分叉会话但 App 看不到，根因是 gateway 的 Desktop 主列表过滤只认 heartbeat 索引；继续实测又发现分叉会话默认“进行中”。本轮发现 App 点击分叉会直接执行，缺少风险确认；局部按钮 spinner 也不能表达“正在创建并等待新会话进入列表”。 | 抽屉会话菜单和用户消息菜单均已移除“分叉”；gateway 仅给助手消息下发 `forkNumTurns`；App 从最终助手回复底部图标先打开 `ForkThreadConfirmDialog`，确认后发送 `fork_thread.numTurns`；新增本地 `isForkingThread` 全局遮罩，只有远端 `selectedThreadId` 变为非源会话且该 id 已出现在 `threads` 后才清除并显示新会话；12 秒兜底避免断连时永久遮罩；测试覆盖 pending 清除条件。 |
| 输入快捷面板收口 | 输入区快捷面板只保留清空、`/命令`、文件，所有命令类能力统一进 `/` 面板。 | 我点开输入区快捷面板；我只看到紧凑的清空、`/命令`、文件，不再看到 Shell、默认项目、文件 chip、紧凑/常规、压缩、回滚；我打开 `/` 命令列表时，每项有图标，shell 入口也在列表里。 | 快捷面板曾重复暴露 Shell 与 `/命令`，还把新会话草稿的“默认项目”和 `.codex/AGENTS.md` 等上下文文件 chip 显示出来；用户又要求文件入口必须可搜索且只能选项目内文件。 | 已移除独立 Shell 快捷按钮和全部文件 chip；新增文件入口，gateway 仅扫描当前 `cwd` 内文件并跳过 `node_modules/.git/build` 等目录，App 本地搜索后插入 `@{path}`；无项目时不跨项目展示文件。 |
| 项目文件选择 | 快捷面板文件入口只能选当前项目文件。 | 我在项目会话点“文件”，搜索文件名，选择结果后输入框插入 `@{项目内路径}`；我在普通无项目会话点“文件”，只看到无项目提示，不会出现其他项目文件。 | 旧自动 chip 会把 `.codex/AGENTS.md` 等上下文源混入快捷区；若 App 自己扫盘或复用历史文件，可能跨项目选错文件。 | `desktop-gateway` 快照新增 `files`；`listProjectFiles(cwd)` 使用真实路径校验并跳过 symlink，结果限定在项目根下；Android 新增 `FilePickerPanel` 搜索面板和插入测试。 |
| 输入区字号统一 | 快捷按钮、命令面板和输入框主体字号保持一致。 | 我打开输入快捷面板和 `/命令` 列表；我期望“清空”“/命令”“搜索命令”“/compact”“压缩上下文”“回复 Codex”视觉字号一致，不再一大一小。 | 主动复测截图发现快捷按钮为 11sp，命令标题/搜索框/输入框为 13sp，命令说明又是 11sp，整体像拼接出来。 | 已新增 `ComposerTypography` 统一主体字号为 `13sp/17sp`，快捷按钮、命令搜索、命令标题、命令说明、输入占位/正文全部引用同一规格；cwd/权限摘要仍作为元信息使用小字号。 |
| 返回行为 | 返回键先关闭浮层，再二次返回退出。 | 我打开命令面板/快捷面板/抽屉/连接弹窗后按返回；我期望先关闭当前浮层。没有浮层时第一次返回弹 toast，第二次才退出 App。 | 返回行为曾直接退出或跳过面板关闭；本轮主动复测发现关闭 `/命令` 后第二次返回会残留快捷工具条，不能继续退出。 | 已在 Composer 内增加本地 BackHandler：先关命令子面板，再收起输入工具并清焦点/隐藏键盘；外层仍处理 dialog/drawer/二次退出 toast。ADB 复测确认第一下返回关 `/命令`，第二下收起快捷工具条。 |
| 用户消息操作按钮 | 用户消息操作按钮在气泡右侧垂直居中。 | 我看自己发出的消息；三点操作按钮应贴在消息气泡右侧，和气泡中线对齐，不挤在左边。 | 旧布局把三点按钮放在气泡左侧且顶部对齐。 | 已调整用户消息 Row：气泡在左、三点在右，垂直居中。 |
| 消息流抖动/状态同步 | 发送 hello 后消息流应稳定显示，不被旧刷新覆盖，抽屉状态要跟 Desktop 正在运行一致。 | 我在 md2html 项目“项目会话测试”里发送 `hello`；我期望用户消息和助手 turn 稳定出现，不时隐时现，也不因为后台刷新跳回旧会话。我把 App 切后台/重连后打开抽屉，Desktop 仍在跑的当前会话应显示运行中，不应显示空闲。 | App 侧每 1.2 秒主动 `refresh_threads` 与 gateway/app-server 推送并行，可能用旧快照覆盖实时消息；gateway 刷新期间若用户手动切会话，旧刷新会晚到并覆盖新选择。继续复测发现 App 重连 hello 只返回静态 snapshot，没有重新 `selectThread/threadResume` 恢复 app-server 订阅；另一个问题是 runtime 运行态实际存在 `snapshot.isGenerating`，但旧 overlay 保留逻辑只看顶层 `isGenerating`，陈旧 idle 列表刷新可能把运行中压回空闲。 | App 侧 live 自动刷新已停用，只保留手动刷新动画；gateway 在任何 await 前捕获选择版本，刷新返回时如用户已切换则返回当前快照，不再激活旧线程；本轮修复 hello 带当前线程时会重新 `selectThread` 恢复订阅，并让 runtime overlay 保留逻辑识别 `snapshot.isGenerating`；已补 Gateway 回归测试覆盖重连恢复 running 和旧 idle 刷新不覆盖运行态。 |
| 长耗时状态回写 | 长任务刷新后不能把运行中会话误降成空闲。 | 我在 Desktop 看到某个会话还在进行中，手机抽屉刷新后也必须继续显示“运行中”，不能因为线程详情尚未完全物化或刷新更慢就回退成空闲。 | 旧实现里，`thread/read` 的陈旧快照会覆盖通知流里刚更新的运行态 overlay；同时 turn/通知时间戳混用秒和毫秒，导致 stale 判定失效。 | gateway 现在保留更晚的 live overlay，旧 `thread/read` 不再把运行中会话降回空闲；`turn/started` / `item/*` 时间戳已统一归一化；新增回归测试覆盖“旧刷新覆盖 live 状态”和“秒/毫秒混用”两条链路。 |
| 后台恢复保持原会话 | App 切后台再打开，保持切后台前的页面；冷启动仍默认新对话。 | 我在 `md2html / 项目会话测试` 里阅读消息，按 Home 后从桌面图标重新打开；我期望仍停在 `项目会话测试`。我彻底关闭后再启动，则期望回到“新对话”。 | 复现发现两层问题：从 launcher 拉起可能新建 Activity/ViewModel，导致回草稿页；WebSocket 重连 hello 也没有携带当前 threadId，Gateway 新连接会按默认会话发首包 snapshot。 | `MainActivity` 改为 `singleTop` 复用后台 Activity；Android hello 增加内存态 `selectedThreadId`；Gateway hello 校验并用该 thread 返回首包 snapshot。ADB 复测确认 Home 后从 launcher 打开仍显示 `项目会话测试`，`force-stop` 冷启动仍显示“新对话”。 |
| 桌面端重启提示 | 移动端改动会话后只提示用户手动重启 Codex Desktop，不再自动 poke/刷新；有运行中会话时不显示提示；确认后才重启。 | 我在 App 里重命名、归档、恢复、分叉或发送消息；如果当前没有运行中的会话，我打开抽屉应在 gateway 连接状态下方看到小号警告色“桌面端待同步/立即重启”；点击后先出现确认弹窗，说明会关闭并重新打开 Codex Desktop，可能打断桌面端输入/弹窗/本地操作；点“确认重启”后才真正重启。 | 旧 `/poke` 会自动聚焦 Desktop 并发送 Esc/Ctrl+R/Ctrl+Shift+R/F5，发送消息后也会自动触发，既不可靠也打断用户；后来又需要避免运行中会话时诱导重启。本轮发现提示行点击会直接重启，没有二次确认，风险太高。 | 已保留 `/poke` 接口但改为重启 Codex Desktop 进程；gateway 对移动端会话变更只记录 `desktopRestartRequired` 并下发 snapshot/patch；App 抽屉提示需同时满足“有变更且无 running 会话”；点击提示只打开 `RestartDesktopConfirmDialog`，确认按钮才发送 `restart_desktop`；成功后清标记。Gateway/Android 单测已覆盖协议、patch、命令发送和提示状态映射。 |
| 抽屉搜索键盘收起 | 从抽屉搜索结果切换会话后，键盘不应残留到会话页。 | 我打开抽屉，搜索 `md2html`，点 `项目会话测试`；我期望抽屉关闭、键盘收起、输入区回到底部。 | 搜索框持有焦点，选择会话后 IME 仍显示，导致主会话输入区被顶到半屏，像是页面布局错乱。 | 抽屉内离开搜索上下文的动作统一清焦点并隐藏键盘；ADB 复测确认选择会话后 `mInputShown=false`，输入区回到底部。 |
| 配置读取回归可信度 | 自测必须覆盖 gateway 真实配置读取链路，不能靠空配置或 warning 过关。 | 我打开新对话页，看到的模型、推理、权限应来自 gateway/app-server；回归测试也应验证 snapshot 里确实带了配置。 | 主动跑 `protocol:selftest` 发现 fake app-server 缺 `configOptions()`，测试虽通过但打印 `config options unavailable`，容易掩盖配置读取断链。 | 已给 bridge fake 和 protocol selftest fake 补 `configOptions()`，并断言 `configOptions.defaults.model` 进入 snapshot；重跑后 warning 消失。 |
| 滚到底部按钮避让 | 浮动“滚到底部”不能遮挡消息右侧操作按钮。 | 我在长会话里向上翻阅，看到“滚到底部”出现；我仍应能点用户消息右侧三点按钮，且按钮保持完整、垂直居中。 | 主动复测发现“滚到底部”固定在右下角，和用户消息操作按钮同一右侧轨道，靠底部时会遮挡/截断三点按钮。 | 已把“滚到底部”改到底部居中，并新增仪器测试断言它不占用用户消息操作轨道；ADB 复测确认浮动按钮 x=477-603，消息操作按钮 x=943-1069。 |
| 消息流底部裁剪 | 消息列表控件不能越界覆盖 composer。 | 我在长会话里停在靠近输入区的位置；用户消息右侧三点按钮应只出现在消息列表内，不能伸进输入框或发送按钮区域。 | 主动复测发现列表没有裁剪边界，用户消息按钮触控区域可越过 ThreadScreen 底部，进入 composer 上方/发送区。 | 已给 `ThreadMessageList` 增加 `clipToBounds()`；ADB 复测确认底部用户消息操作按钮最多到 y=2203，正好停在 composer 上边界。 |
| 抽屉搜索排序 | 搜索结果应优先展示标题/项目/路径命中，不让预览文本命中抢在前面。 | 我在抽屉搜索 `md2html`；我期望 `md2html / 项目会话测试` 排在只因预览里提到 md2html 的 `codexapp` 会话前面。 | 原实现过滤后仍按更新时间排序，导致当前 `codexapp` 会话只因预览命中就排到目标项目上方，容易误点。 | 已增加搜索命中权重：标题、项目、cwd、git 元数据优先，预览命中最后；同级再按更新时间。单测覆盖项目/标题命中优先；ADB 复测 `md2html` 排序正确。 |
| 远程按钮 loading 反馈 | 所有会调用 gateway/远程的按钮，点击后必须有明确执行中反馈。 | 我点击抽屉刷新、重启 Desktop、连接/断开 gateway、发送/停止、审批允许/拒绝、消息重发/编辑重发、助手回复分叉、会话归档/重命名；我期望按钮立刻转圈或进入执行中状态，不会像没点到。 | 抽屉刷新原本走普通刷新，不会触发 `isManualRefreshing`；多处远程按钮点击后立即关闭菜单或无任何视觉变化，网络慢时用户容易重复点。 | 抽屉刷新改走 animated refresh 并在按钮内显示 spinner；重启提示条、连接弹窗、发送/停止、审批、消息菜单、助手分叉、会话行操作和重命名保存均已加 loading/禁用重复点击；无完成回调的菜单动作使用短时 pending 防止永久锁死。 |
| 顶部栏与输入 Dock 视觉重设计 | 顶部标题栏、底部输入区、快捷命令区对齐新会话草稿页的高端克制风格。 | 我打开新对话页；顶部标题、菜单/新建按钮、底部输入框、快捷按钮和命令面板应像同一套产品组件，不再像临时拼接的表单和列表。 | 原顶部栏是普通横排工具条，状态点和标题挤在一起；底部快捷区靠分割线堆叠，命令列表直贴输入框，视觉割裂。 | 顶部改为居中标题+状态、圆形图标按钮；底部改为单一胶囊 dock，输入框内嵌，快捷按钮变成工具带；命令/文件面板改为带标题的内嵌浮层列表。 |
| 文件选择列表阅读体验 | 快捷功能“文件”应按项目相对路径清晰展示，避免绝对路径平铺。 | 我在项目会话点“文件”；我期望先按目录看到分组，列表主标题是文件名，副标题是相对目录，不出现 `D:/Projects/...` 这种长绝对路径刷屏。 | 文件面板直接把 `file.path` 作为副标题展示，而 `path` 是用于插入引用的绝对路径；同时列表完全平铺，文件多时难以扫读。 | App 展示层改为按首级目录分组；行内主标题显示文件名，副标题显示项目相对目录；传入当前 `cwd` 做通用相对化兜底，即使后端误传绝对 label 也不直接展示绝对路径。 |
| 顶部新建入口收口 | 标题栏不再重复放新建会话按钮。 | 我看顶部标题栏；只需要菜单入口，新增会话应从会话列表/抽屉完成，避免两个入口重复。 | 顶部右侧已有“+”，抽屉会话列表也有新建入口，功能重复。 | 已移除 TopBar 右侧新建按钮和参数调用；保留等宽不可见占位保持标题居中。 |
| 会话生效配置展示 | 输入区快捷面板红框区域展示本会话真实生效配置。 | 我打开某个真实会话并展开输入区快捷面板；我期望看到权限模式、提供商、模型、推理强度，且这些值来自当前会话实际 resume/start 配置，不再展示项目路径或旧权限摘要，也不出现“默认/未知”等瞎编占位。 | 原 UI 只展示 `cwd` 和 `permissionSummary`；gateway 虽能拿到 app-server `model/modelProvider/sandbox/reasoningEffort`，但没有透传给 App；旧 `toResumeMetadata` 还会用 `openai/never/dangerFullAccess` 补默认值，容易把未知配置伪装成真实配置；继续复测发现中文标签“权限/提供商/模型/推理”占宽，导致小屏一行放不下。 | gateway snapshot/patch 新增 `sessionConfig`，从 app-server `threadStart/threadResume/threadFork` 返回值和线程 `modelProvider` 构造；缺失字段保持空；Android 协议、状态映射和 Compose UI 已串通，只展示非空配置项；UI 已去掉中文标签，改为纯值 `workspace-write · my_codex · gpt-5.4-mini · medium`；Node/Android 单测覆盖 full snapshot 与 patch 映射。 |
| 消息流底部空白 | 滚动到底部时，最后一条消息应贴近输入区上沿，不出现大块空白。 | 我打开长会话并滚到底部；收起或展开输入工具后，我期望最后一条消息与输入区之间只有正常呼吸间距，而不是半屏空白。 | `ThreadScreen` 已经通过 `Scaffold bottomBar` 自动给内容区避让真实 composer 高度，但 `LazyColumn` 又额外叠加 `composerPadding + 28dp`；展开输入工具时 `composerPadding` 最高接近 298dp，导致滚到底部仍保留很高空白。 | `composerPadding` 已改为消息列表内部轻量底部间距：紧凑 8dp、常规 10dp；保留 `Scaffold` 负责真实输入区避让；更新单测覆盖该语义。ADB 复测 `md2html/项目会话测试` 收起态和展开态均无异常大空白。 |
| 全局运行通知弹幕 | MCP ready、账号、远程控制、文件搜索等无会话归属的运行通知要显示，但不能污染任何会话消息流。 | 我切换到一个空闲会话；我期望只看到该会话自己的消息。若出现 “MCP 服务 cloudflare-api: 已就绪” 等全局状态，它应在标题栏下方右侧逐条蹦出，浅色半透明底、黑字显示，再缓缓淡出消失。 | app-server 的 `mcpServer/startupStatus/updated`、账号/远程控制/文件搜索等通知没有 `threadId`，gateway 旧逻辑会把它们写进当前选中会话的 `snapshot.messages`；切换会话时通知到达就插入，随后 `thread/read` 刷新又覆盖为真实会话内容，表现为时有时无。上次修复后不进消息流，但也完全不显示；初版浮层过于靠上挡标题，深色背景透明后又看不清。 | gateway 新增瞬态 `operationalNotices` 队列，只随下一次 snapshot/patch 下发并立即消费；App 新增 `OperationalNoticeOverlay`，位置下移避开标题栏，使用半透明白底黑字、右侧滑入、约 2.6 秒后淡出；消息流仍不接收这类全局通知。 |

## 高频流程测试矩阵

| 流程 | 概述 | 用户视角流程步骤自述 | 发现的问题 | 修复情况 |
| --- | --- | --- | --- | --- |
| 首连 | App 连接 gateway 并收到首个 snapshot。 | 我启动 gateway，打开 App，使用默认模拟器地址连接；我期望看到已连接状态、线程列表和当前线程。 | 已发现协议缺少成文首连要求。 | 文档已补 `hello`、pair token、`snapshot_patch` 能力协商；`node scripts/dev-run.mjs` 已验证 App 能连接并触发 hello/refresh。 |
| 新会话与发送 | 从草稿页发送第一条 prompt，显示 optimistic 用户消息和助手响应。 | 我在新对话草稿页用真实配置下拉选择模型/推理、输入问题并发送；普通新建应创建无项目会话，从项目入口新建应沿用入口项目。 | 早期缺少对 raw/realtime item 的完整移动端呈现保障；本轮发现新建空会话会制造列表噪声，配置选项不能硬编码，项目选择也不该在草稿页重复出现。 | 已补 raw/realtime/item 通知映射和测试；本轮改为首条消息才创建真实会话，并从 Codex app-server 读取真实配置；草稿页项目选择已移除，模型/推理改为下拉。 |
| 新会话首条消息稳定性 | 新建会话发送首条消息时不能闪回上次会话。 | 我停在新对话草稿页，输入首条消息并发送；我期望页面保持新会话提交态，随后进入真实新会话，期间不显示上次会话内容，用户消息只出现一次。 | 发现 App 在发送成功后过早退出草稿页，gateway 旧 selectedThreadId 和过期刷新会短暂覆盖新会话；同时本地 optimistic 用户消息会和服务端真实用户消息重复。 | 已改为等远端返回真实 `selectedThreadId` 后才退出草稿；gateway 在 `newThread` 发送开始即清空旧 selection 并递增版本，拦截 stale refresh；移动端 snapshot 合并会移除被真实用户消息替代的 optimistic 消息。 |
| 会话切换稳定架构 | 会话切换必须以用户意图为准，旧会话快照不能抢回页面。 | 我在抽屉点击会话 B；我期望当前页面盖上“正在切换会话”全局遮罩，直到 gateway 返回 B 的 snapshot 且 B 已在列表中，才真正切过去。期间任何旧会话 A 的刷新、patch 或 live refresh 都不能让页面来回跳。 | 根因是 Android 旧实现点击列表后立即把 `selectedThreadId` 改成目标，同时所有 snapshot/patch 都无条件清掉 `isThreadSwitching` 并替换消息；迟到的旧会话快照会抢回消息页。live refresh 在切换中还可能继续刷新旧 selected。 | 新增 `pendingSelectionThreadId` 作为本地切换意图；`startSelectingThread` 不再清空当前消息或立即改 selected，只显示全局遮罩；Android reducer 只接受“selectedThreadId 等于 pending 且该 id 已在 threads 中”的目标快照，其余快照只更新列表/连接状态；pending 切换时停止旧会话 live refresh；新增 reducer 和 refresh policy 回归测试。 |
| 默认新建普通会话 | 未点项目名右侧“新建”时，默认新会话不能绑定当前项目。 | 我打开抽屉，点击顶部“新建会话”，不点击任何项目右侧新建按钮；发送第一条消息后，我期望它进入普通会话分组，不归到 `codexapp` 或上一个项目。 | gateway 调用 app-server `thread/start` 时如果不传 `cwd`，协议会默认使用 server cwd，也就是 gateway 当前启动目录 `codexapp`，导致默认新建被误判为项目会话。 | 已让普通新建显式使用 Desktop 普通会话目录 `~/Documents/Codex/YYYY-MM-DD/new-chat`；只有项目右侧新建才传项目 cwd；回归测试覆盖不会继承当前项目 cwd。 |
| 切换会话 | 在两个会话间切换，消息和草稿不串。 | 我在会话 A 输入草稿，切到会话 B，再切回 A；我期望标题、消息、草稿都回到对应会话。 | 暂未在本轮发现新问题。 | 现有单测覆盖草稿/切换基础逻辑；后续继续做真实流程复测。 |
| 浏览历史 | 长会话加载更早消息并保持阅读位置。 | 我进入长会话，滚到顶部触发加载历史；我期望旧消息出现，当前阅读锚点不跳。 | 锚点恢复缺少直接测试。 | 已补锚点恢复索引单测；后续仍可补端到端滚动视觉验证。 |
| 中断会话 | 运行中 turn 可停止并回到 idle。 | 我发送长任务，点停止；我期望状态从运行中恢复为空闲，并看到停止提示。 | 协议 selftest 未直接覆盖停止路径。 | 已把 `stopTurn` 纳入 `protocol:selftest`；gateway 也已有 interrupt 单测。 |
| 编辑/重发 | 已发送消息可回填、修改、再次发送。 | 我长按旧消息，选择编辑，旧文本先进入输入框；我改完并点发送后，才从该消息对应 turn 回滚并发送。点“重发”则无需编辑，立即回滚并发送原文本。 | 回填后不聚焦，反馈弱；后续又发现“编辑后重发”点击菜单时提前回滚，违背用户确认后再提交的预期。 | 已修复聚焦；本轮改为“回填 + 用户确认发送 + resend_prompt 回滚发送”的两段式流程；单测覆盖点击不远程调用、发送才调用、失败保留草稿。 |
| 审批 | 命令、文件、权限审批可允许或拒绝。 | 我触发需要确认的命令或文件修改，分别点允许/拒绝；我期望 pending 状态准确消失，并看到结果。 | 多种 serverRequest 类型需要统一映射。 | 已补 command/file/MCP/权限/tool input 审批响应映射测试；仍需继续做移动端完整交互复测。 |
| 远程动作即时反馈 | gateway 操作按钮点击后给出 loading 动画。 | 我逐个点击刷新、发送、停止、审批、重启、连接、重命名/归档、重发/分叉；我期望按钮本体或操作行马上显示处理中，且处理中不能重复提交。 | 远程动作分散在抽屉、composer、消息流、连接弹窗，没有统一 pending 表现；部分动作只有菜单消失，没有执行反馈。 | 已给主要远程入口增加 spinner 和短时 pending；抽屉刷新接入现有 `isManualRefreshing`；连接/断开成功后自动关闭弹窗。 |
| 顶部与输入区视觉一致性 | 新会话页上下壳层与草稿卡保持统一风格。 | 我停在新对话页，展开快捷区和 `/命令`；我期望顶部、草稿卡、底部 dock、命令面板的圆角、边框、字号和黑白灰层级一致。 | 原顶部/底部与草稿卡风格不一致，底部命令区显得像调试列表。 | 已统一为白底、细边框、圆形/胶囊控件、克制灰阶；截图复测覆盖收起态、快捷展开态和命令面板展开态。 |
| 文件选择分组 | 文件面板不能平铺绝对路径。 | 我打开文件面板并搜索文件；我期望分组标题帮助定位目录，行标题短，路径信息只保留相对目录。 | 旧展示把绝对路径放在每行副标题，阅读成本高。 | 已新增文件展示模型和分组函数，单测覆盖相对目录分组、项目根目录展示、绝对路径 label 兜底相对化。 |
| 归档/分叉/重命名 | 目录只保留非归档会话的归档、重命名；分叉从助手回复消息触发。 | 我在抽屉里重命名或归档会话；我在消息流最终助手回复底部点“从此处分叉”；我期望先确认，再看到全局生成遮罩，分叉成功且新会话可查到后自动进入新会话。 | 触发操作后抽屉不关闭；归档后会跳到别的会话；旧分叉入口放在会话列表，无法表达目标 turn；曾误放到用户消息菜单；本轮发现抽屉还残留归档分组，分叉也缺少二次确认和全局执行反馈。 | 已修复抽屉自动收起；归档后回草稿态且不再查询/展示归档会话；本轮删除抽屉归档分组；助手分叉增加确认弹窗、全局 loading 遮罩和“新会话已入列表才清 pending/切换”的本地状态保护；相关 Android 单测通过。 |
| 断线重连 | gateway 断开后 App 能恢复连接且不丢草稿。 | 我断开 gateway，再重新启动；我期望 App 显示断线、自动/手动重连，当前线程和输入草稿还在，且不会残留运行中/审批按钮。 | 断线后可能残留运行中或审批瞬态状态；另外手动点连接时，如果旧自动重连计时还没到，存在重复发起 connect 的竞态窗口。 | 已修复断线/失败状态收口，并补 HomeRepositoryCoordinator 自动化覆盖：验证断线后会自动重连、手动断开不会误重连、手动连接会取消待执行自动重连避免重复 connect。 |
| 后台恢复 | App 被系统切后台或从 launcher 重新拉起时不改变当前页面。 | 我停在某个真实会话或新会话草稿页，按 Home，再重新打开 App；我期望当前页面、标题和消息流保持不变。 | 本轮发现 launcher 拉起可能重建 Activity；重连首包也可能回默认会话。 | 已用 `singleTop` 复用后台 Activity，并让 hello 携带当前内存态选中线程；设备实测覆盖真实会话恢复，另测 `force-stop` 冷启动仍进新对话。 |
| patch 降级 | patch revision 不匹配时自动请求 full snapshot 恢复。 | 我在异常网络/乱序情况下收到过期 patch；我期望 App 不应用错误状态，而是自动刷新完整状态，不需要手动猜测怎么恢复。 | 旧实现只提示“请刷新”，gateway 继续保留 patch 基线，恢复链路不够自动。 | 已补 `refresh_threads.forceSnapshot`：Android stale patch 时自动请求强制完整 snapshot；gateway 收到后重置该客户端 patch 基线。 |

## 最近自测记录

- `.\gradlew.bat :app:compileDebugKotlin`：通过。
- `.\gradlew.bat :app:testDebugUnitTest`：通过。
- `.\gradlew.bat :app:connectedDebugAndroidTest`：通过，22 个仪器测试通过。
- `desktop-gateway npm run build`：通过。
- `desktop-gateway node --test test\*.test.mjs`：通过，147 个测试通过。
- `desktop-gateway npm run protocol:selftest`：通过；配置读取 warning 已消除。
- `desktop-gateway npm run protocol:selftest`：通过。
- `node scripts/dev-run.mjs`：通过；已重启真实 app-server gateway、编译安装 debug APK、打开 App；gateway 日志显示 Android hello 握手成功。
- `node scripts/dev-run.mjs`：通过；本轮 loading 反馈改动后已重新编译安装并打开 App。
- `.\gradlew.bat :app:testDebugUnitTest`：通过。
- `node scripts/dev-run.mjs`：通过；顶部栏与输入 dock 重设计后已重新编译安装并打开 App。
- `.\gradlew.bat :app:testDebugUnitTest`：通过；覆盖 pending selection、防旧快照抢页、上下拉阈值和分叉 pending。
- `node --test desktop-gateway/test/bridgeCatalogController.test.mjs desktop-gateway/test/bridgeBackend.test.mjs desktop-gateway/test/runtimeSummaryState.test.mjs`：通过；验证 history window 扩展不破坏 gateway 目录/消息窗口。
- `node scripts/dev-run.mjs`：通过；会话切换遮罩和消息流窗口/边缘手势重构后已重新编译安装并打开 App，未卸载。
- `.\gradlew.bat :app:testDebugUnitTest`：通过。
- ADB 截图复测：新会话页顶部居中标题、底部收起态 dock、快捷展开态、`/命令` 面板均无遮挡和明显文字溢出。
- `node scripts/dev-run.mjs`：通过；文件面板分组/相对路径展示改动后已重新编译安装并打开 App。
- `.\gradlew.bat :app:testDebugUnitTest`：通过。
- ADB 截图复测：无项目新会话下文件面板显示“当前会话无项目”，不跨项目展示文件。
- `node scripts/dev-run.mjs`：通过；顶部栏移除新增按钮后已重新编译安装并打开 App。
- `.\gradlew.bat :app:testDebugUnitTest`：通过。
- `node scripts/dev-run.mjs`：通过；会话配置展示协议改动后已重新编译安装并打开 App，未卸载。
- `desktop-gateway npm run build`：通过。
- `desktop-gateway node --test test\backendActions.test.mjs test\bridgeBackend.test.mjs`：通过，44 个测试通过。
- `desktop-gateway npm run protocol:selftest`：通过。
- `.\gradlew.bat :app:compileDebugKotlin`：通过。
- `.\gradlew.bat :app:testDebugUnitTest`：通过。
- `node scripts/dev-run.mjs`：通过；消息流底部空白修复后已重新编译安装并打开 App，未卸载。
- `.\gradlew.bat :app:testDebugUnitTest --tests com.codexapp.ui.ThreadScreenStateTest`：通过。
- `.\gradlew.bat :app:testDebugUnitTest`：通过。
- ADB 复测：`md2html / 项目会话测试` 长会话底部在输入工具收起态、展开态都不再出现异常高空白。
- `node scripts/dev-run.mjs`：通过；配置行去中文标签后已重新编译安装并打开 App，未卸载。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.ui.ThreadScreenStateTest`：通过。
- ADB 复测：真实会话输入工具展开后配置行显示为 `workspace-write · my_codex · gpt-5.4-mini · medium`。
- `node scripts/dev-run.mjs`：通过；全局 operational notice 修复后已重启 gateway、编译安装并打开 App，未卸载。
- `desktop-gateway npm run build`：通过。
- `desktop-gateway node --test test\notificationCoverage.test.mjs`：通过，12 个测试通过。
- ADB 截图复测：标题栏右侧新建按钮已消失，标题仍居中。
- ADB 手工复测：新会话页不再显示项目选择，模型/推理为同宽下拉；快捷面板只剩紧凑的“清空//命令/文件”，不再显示 Shell、“默认项目”和文件 chip；打开 `/命令` 不误插命令；连续返回先关 `/命令`，再收起输入工具条；`md2html / 项目会话测试` 后台再打开仍保持原会话；`force-stop` 冷启动仍进入“新对话”；抽屉搜索切换会话后键盘收起、输入区回到底部；“滚到底部”按钮不再遮挡右侧用户消息操作按钮；底部消息操作按钮不再伸进 composer；搜索 `md2html` 时目标项目排在预览命中的 `codexapp` 前面。
- `node scripts/dev-run.mjs`：通过；编辑后重发延迟回滚改动后已重新编译安装并打开 App，未卸载。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.ui.ComposerActionHandlerTest`：通过；覆盖点击只回填、发送才调用 `resend_prompt`、失败保留草稿。
- `node scripts/dev-run.mjs`：通过；全局 operational notice 弹幕改动后已重新编译安装并打开 App，未卸载。
- `node scripts/dev-run.mjs`：通过；弹幕透明度、黑字样式和避让标题栏调整后已重新编译安装并打开 App，未卸载。
- `node scripts/dev-run.mjs`：通过；turn 过程信息折叠改动后已重新编译安装并打开 App，未卸载。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.ui.MessageTurnActionsTest --tests com.codexapp.ui.ThreadScreenStateTest`：通过。
- `node scripts/dev-run.mjs`：通过；新会话模型读取中修复后已重新编译安装并打开 App，未卸载，gateway 错误日志尾部为空。
- `desktop-gateway npm run build`：通过。
- `desktop-gateway node --test test\appServerClient.test.mjs test\bridgeBackend.test.mjs`：通过，36 个测试通过。
- `.\gradlew.bat :app:testDebugUnitTest --tests com.codexapp.ui.NewThreadDraftCardOptionsTest --tests com.codexapp.ui.GatewaySnapshotMapperTest`：通过。
- `node scripts/dev-run.mjs`：通过；最终 assistant 回复全文显示改动后已重新编译安装并打开 App，未卸载。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.ui.MessageTurnActionsTest --tests com.codexapp.ui.CodeBlockLogicTest`：通过。
- `desktop-gateway npm run build`：通过。
- `desktop-gateway node --test test\notificationCoverage.test.mjs test\backendActions.test.mjs`：通过，22 个测试通过。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.ui.GatewaySnapshotMapperTest --tests com.codexapp.data.GatewayInboundStateReducerTest`：通过。
- `.\gradlew.bat :app:testDebugUnitTest`：通过。
- `node scripts/dev-run.mjs`：通过；新对话表单垂直居中改动后已重新编译安装并打开 App，未卸载。
- `.\gradlew.bat :app:compileDebugKotlin`：通过。
- `.\gradlew.bat :app:testDebugUnitTest --tests com.codexapp.ui.NewThreadDraftCardOptionsTest`：通过。
- `node scripts/dev-run.mjs`：通过；App 更新检查/下载入口改动后已重新编译安装并打开 App，未卸载。
- GitHub release 实查：`latest=v0.1.7`，APK asset 为 `codexapp.apk`，本地调试包 `0.1.8`，因此当前不会弹更新提示。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.update.AppUpdateManagerTest --tests com.codexapp.ui.MessageTurnActionsTest`：通过。
- `node scripts/dev-run.mjs`：通过；信息流上滑刷新触发修复后已重新编译安装并打开 App，未卸载。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.ui.ThreadPullRefreshControllerTest --tests com.codexapp.ui.ThreadScreenStateTest`：通过。
- `node scripts/dev-run.mjs`：通过；turn 顶部耗时和三点菜单改动后已重新编译安装并打开 App，未卸载。
- `desktop-gateway npm run build`：通过。
- `desktop-gateway node --test test\bridgeBackend.test.mjs test\turnFinalization.test.mjs test\threadState.test.mjs`：通过，58 个测试通过。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.ui.AssistantMessageHeaderTest --tests com.codexapp.ui.GatewaySnapshotMapperTest --tests com.codexapp.ui.MessageTurnActionsTest`：通过。
- `node scripts/dev-run.mjs`：通过；最终回复显式 `isFinal` 协议修复后已重新编译安装并打开 App，未卸载。
- `desktop-gateway npm run build`：通过。
- `desktop-gateway node --test test\runtimeSnapshotMessages.test.mjs test\bridgeBackend.test.mjs test\turnFinalization.test.mjs test\threadState.test.mjs`：通过，62 个测试通过。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.ui.MessageTurnActionsTest --tests com.codexapp.ui.GatewaySnapshotMapperTest --tests com.codexapp.ui.AssistantMessageHeaderTest`：通过。
- `node scripts/dev-run.mjs`：通过；assistant turn 操作迁移到底部图标栏后已重新编译安装并打开 App，未卸载。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.ui.AssistantMessageHeaderTest --tests com.codexapp.ui.MessageTurnActionsTest`：通过。
- `.\gradlew.bat :app:compileDebugAndroidTestKotlin`：通过；同步验证底部图标标签和旧 DrawerContent 测试调用可编译。
- `node scripts/dev-run.mjs`：通过；运行中 turn 耗时实时更新改动后已重新编译安装并打开 App，未卸载。
- `desktop-gateway npm run build`：通过。
- `desktop-gateway node --test test\runtimeSnapshotMessages.test.mjs test\bridgeBackend.test.mjs test\threadState.test.mjs`：通过，61 个测试通过。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.ui.AssistantMessageHeaderTest --tests com.codexapp.ui.MessageTurnActionsTest --tests com.codexapp.ui.GatewaySnapshotMapperTest`：通过。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.ui.ThreadScreenStateTest --tests com.codexapp.ui.ThreadPullRefreshControllerTest`：通过。
- `.\gradlew.bat :app:compileDebugAndroidTestKotlin`：通过；验证顶部下拉加载历史相关仪器测试可编译。
- `node scripts/dev-run.mjs`：通过；顶部下拉加载历史修复后已重新编译安装并打开 App，未卸载。
- `desktop-gateway npm run build`：通过。
- `desktop-gateway node --test test\messageMapping.test.mjs test\runtimeSnapshotMessages.test.mjs test\bridgeBackend.test.mjs`：通过，42 个测试通过。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.ui.AssistantMessageCardsTest --tests com.codexapp.ui.MessageTurnActionsTest --tests com.codexapp.ui.ThreadScreenStateTest`：通过。
- `node scripts/dev-run.mjs`：通过；过程信息流增强后已重新编译安装并打开 App，未卸载。
- 发布前完整自测：`node scripts/dev-run.mjs`、`desktop-gateway npm run build`、`desktop-gateway node --test test\*.test.mjs`、`desktop-gateway npm run protocol:selftest`、`.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`、`.\gradlew.bat :app:compileDebugAndroidTestKotlin` 均通过；APK 保持安装状态。
- `.\gradlew.bat :app:testDebugUnitTest --tests com.codexapp.ui.HomeUiStateStoreTest --tests com.codexapp.data.GatewayRepositoryCommandActionsTest --tests com.codexapp.ui.GatewaySnapshotMapperTest --tests com.codexapp.ui.SessionRemoteMutationsTest`：通过；覆盖新会话提交态和 optimistic 消息去重。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`：通过。
- `desktop-gateway npm run build`：通过。
- `desktop-gateway node --test test\*.mjs`：通过，165 个测试通过。
- `node scripts/dev-run.mjs`：通过；新会话首条消息竞态修复后已重新编译安装并打开 App，未卸载。
- ADB 烟测：新对话草稿页发送 `newchat_race_fix_check` 后未闪回上次会话，消息流只显示一条用户消息；gateway 日志显示过期 live refresh 被忽略。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`：通过。
- `desktop-gateway npm run build`：通过。
- `desktop-gateway node --test test\*.mjs`：通过，166 个测试通过。
- `node scripts/dev-run.mjs`：通过；默认普通新建 cwd 修复后已重新编译安装并打开 App，未卸载。
- ADB 烟测：点击抽屉顶部“新建会话”后发送 `ordinary_default_chat_final`，未点击项目右侧新建；已确认普通会话目录 `~/Documents/Codex/2026-06-02/new-chat` 存在，消息流只显示一条用户消息。
- `node scripts/dev-run.mjs`：通过；会话可见性过滤修复后已重新编译安装并打开 App，未卸载。
- 真实 gateway 诊断：`thread/list(false)` 经 `buildVisibleThreadSummaries` 后返回 14 条，已包含 `codexapp/1`、`codexapp/hello`、`md2html/写一个1`、普通会话 `回复一下`。
- `desktop-gateway npm run build` + `node --test desktop-gateway/test/threadSummaries.test.mjs desktop-gateway/test/bridgeBackend.test.mjs`：通过，42 个测试通过。
- ADB 抽屉复测：第一屏已显示 `codexapp/1`、`codexapp/hello`、`md2html/写一个1`；继续滚动后命中普通会话 `回复一下`、`查看深圳天气`、`重启会话是否中断`。
- `node scripts/dev-run.mjs`：通过；App 更新检查改为冷启动一次、系统下载器下载后已重新编译安装并打开 App，未卸载。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.update.AppUpdateManagerTest`：通过；覆盖版本比较和进程级启动检查闸门。
- ADB 抽屉烟测：源码和 UI 检索均确认更新提示不再包含 `下载更新`、`正在下载`、`立即安装`；当前未触发新版提示时不展示下载行。
- `desktop-gateway npm run build`：通过。
- `node --test desktop-gateway/test/turnFinalization.test.mjs desktop-gateway/test/bridgeBackend.test.mjs desktop-gateway/test/runtimeSummaryState.test.mjs desktop-gateway/test/threadState.test.mjs desktop-gateway/test/notificationCoverage.test.mjs`：通过，79 个测试通过；覆盖会话运行态租约、多 loop finalize 竞态和 stale refresh。
- `node scripts/dev-run.mjs`：通过；已重启真实 gateway、编译安装 debug APK、打开 App，日志显示 Android hello 握手成功，未卸载 App。
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.codexapp.update.AppUpdateManagerTest`：通过；覆盖更新检查基础逻辑并验证 App 更新改动可编译。
- `node scripts/dev-run.mjs`：通过；系统下载器优先、浏览器打开 GitHub latest release 兜底改动后已重新编译安装并打开 App，未卸载。
- `.\gradlew.bat :app:compileDebugKotlin`：通过；Desktop 同步确认重启弹窗补充原因文案后编译通过。
- `node scripts/dev-run.mjs`：通过；Desktop 同步确认重启弹窗文案改动后已重新编译安装并打开 App，未卸载。

## 发布前汇总排查

| 项目 | 概述 | 将要测试用户视角的流程步骤自述 | 发现的问题/修复情况 |
| --- | --- | --- | --- |
| 新会话草稿与真实配置 | 默认进入新对话草稿页，配置不瞎编。 | 我冷启动 App，连接 gateway，确认模型/推理/权限来自真实配置；输入首条消息后才创建会话。 | 已实现草稿页、真实 `configOptions`、垂直居中布局；修复模型长期“读取中”的 `this` 绑定问题。 |
| 会话目录对齐 Desktop | App 会话数和分组应接近 Desktop 主列表。 | 我打开抽屉，对比 Desktop 会话列表，确认普通会话不归 `new-chat`，归档和非交互线程不出现。 | 已按 Desktop 可见性过滤，默认只查 `archived=false`，并修复合成普通会话目录分组。 |
| 消息过程流 | 运行中要看得见正在发生什么，完成后不挤占正文。 | 我发送会执行命令/搜索的任务，运行中看增量状态；最终回复出现后过程收进“已处理 X 项”，正文完整显示。 | 已增强 reasoning/command/tool/webSearch 映射；`isFinal` 显式协议避免提前收起；底部展示复制、分叉、耗时。 |
| 历史和刷新 | 长会话顶部加载历史、底部上滑刷新都可用。 | 我进入长会话，顶部下拉加载旧消息并保持锚点；到底部后上滑刷新当前会话。 | 已恢复顶部历史分页和底部刷新手势，补充状态控制和回归测试。 |
| 输入区和文件选择 | 快捷区只保留清空、命令、文件，文件选择可读。 | 我展开输入区，打开 `/命令` 和文件面板，确认按钮紧凑、文件为项目内树形相对路径。 | 已移除紧凑/常规、压缩、回滚、Shell、默认项目等入口；文件面板改为项目内树形搜索。 |
| Desktop 同步提示 | 移动端改动后提示是否重启 Desktop，但有运行中会话时不提示，且确认后才执行。 | 我从手机重命名/归档/分叉/发送消息，抽屉顶部出现小号警告色重启提示；点提示先弹警告确认；有运行中会话时不出现。 | `/poke` 旧自动刷新逻辑已删除，保留接口；gateway 记录移动端变更并提供 Desktop 重启提示；App 已增加二次确认弹窗；本轮补充重启原因：Desktop 不会实时自动刷新移动端触发的外部会话变更，重启用于重新读取会话索引。 |
| App 更新 | 冷启动后异步检查 GitHub release。 | 我安装低版本包，打开 App，看到更新提示；后台切回不重复检查；点击后由系统下载器下载 APK。 | 已接入 GitHub latest release API、进程级启动检查闸门和 Android `DownloadManager`；App 内不显示下载进度，不再拉应用内安装 intent。 |
| 发布工程化 | 本地和 GitHub Actions 都能打包 APK。 | 我运行本地 dev-run 自测，再推送 tag，等待 release workflow 产出 `codexapp.apk`。 | 已新增本地脚本，更新 release workflow 说明和项目 wiki；发布前继续跑完整自测。 |

## 下一步优先级

1. 把高频流程矩阵补成更完整的自动化端到端测试。
2. 继续按用户视角找弱反馈问题，例如编辑/重发是否还需要 toast 或视觉确认。
3. 继续盘点无法实现或未使用的兼容导出层、mock 代码，确认无引用后清理。

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
| 同秒 idle refresh 防降级 | 刚续租的运行态不能被同一秒返回的旧快照覆盖。 | 我在 goal 会话运行中点刷新或等自动同步；如果 Desktop app-server 返回的 `thread/read` 仍显示上一 turn completed 且时间戳与 goal 更新时间同秒，我期望移动端仍保持运行中。 | App-server 时间戳粒度是秒，本地 overlay 是毫秒；旧 overlay 保留逻辑要求 incoming 活动时间严格小于本地时间，同秒快照会被误认为不旧，导致 running lease 被 idle refresh 打掉。已改为有效 running lease 期间，同时间戳或更旧的 idle 快照不能降级，只有明确更新的快照才覆盖。 |
| 断线瞬态清理 | 断线/失败/解析错误后不应残留切换、刷新、加载遮罩。 | 我在切换会话、加载历史或手动刷新时遇到 gateway 断开；我期望立即看到断线/错误状态，不再卡在“正在切换会话”“刷新中”或加载历史状态。 | 旧断线收口只清理 `isGenerating/pendingApproval`，没有清 `pendingSelectionThreadId/pendingThreadTitle/isThreadSwitching/isLoadingOlder/isManualRefreshing`。已让断线、连接失败、手动断开、入站解析失败统一清理这些瞬态；补充 mutation 单测覆盖。 |
| 发布 latest 摘要 | 发布脚本结束后应有固定路径摘要，避免每次翻长日志判断状态。 | 我运行发布脚本后，只需查看 `scripts/logs/github-release-latest.json`，即可知道版本、tag、日志路径、branch/tag push 结果和 Actions 触发状态。 | 旧日志只有时间戳文件，外部 AI 或用户要判断上次发包是否成功仍需找最新日志再阅读。已新增 latest JSON 摘要，脚本任何内部错误仍写入状态且对外退出正常；实际重试发现 Markdown 说明里出现 `-Notes` 字样会被误判为参数，已改为只识别完整 CLI option token 并补回归测试。 |
| App 入站日志脱敏 | 移动端调试日志应帮助排查协议状态，但不能输出完整消息正文。 | 我用 logcat 排查 gateway 连接时，只需要看到入站消息类型、revision、线程/消息数量、changed 字段、运行态等摘要；不应把用户 prompt、助手回复、状态 detail 全量打印出来。 | `GatewayRepositoryConnection` 旧日志直接 `Log.d("inbound: $raw")`，长消息会刷屏且可能泄露正文。本轮改成 `summarizeInboundForLog` 结构化摘要，异常 JSON 也只记录字节数和错误类型；新增单测确保 title/preview/正文/detail 不出现在日志摘要里。 |
| 主区连接横幅细节 | 消息区顶部连接横幅在断线/异常时应直接给出原因。 | 我停在主消息区遇到 gateway 断开、解析失败或连接异常；我期望不用先打开抽屉，也能在顶部横幅直接看到具体原因，再决定是否重连。 | 旧主区横幅只有“连接异常/未连接 Desktop Gateway”，具体 `connectionDetail` 只在抽屉里可见，主区排障反馈太弱。 | 已让主区连接横幅在 `ERROR`/`DISCONNECTED` 时显示最多两行具体原因，并过滤“未连接 gateway/未连接 desktop gateway”这类泛文案；补单测和 Compose 可见性测试覆盖解析失败详情显示。 |
| 编辑后重发提示 | 用户进入“编辑后重发”后，输入区附近应有明确提示。 | 我长按旧用户消息点“编辑后重发”；我期望除了输入框被回填，还能马上看到这次处于“下次发送会回滚并重发”的状态，避免误以为只是普通编辑。 | 旧实现虽然已改成“发送时才回滚并重发”，但待发送状态只存在 `ComposerActionHandler` 私有字段里，UI 无感知，提示太弱。 | 已把待编辑重发状态透出到 `HomeUiState`，并在 composer 上方显示轻提示“下一次发送会回滚最近 N 轮后重发”；发送成功、清空输入或改成普通替换后会自动清除；补状态单测和 Compose 可见性测试。 |
| 全局通知 toast 视觉 | 右上角 operational notice 应像常规 toast，清楚可读且不挡标题栏。 | 我触发 MCP/账号等全局通知时，希望看到不透明气泡、清晰字体和稳定位置，而不是过透明的弹幕浮层；通知应整体下移，不压住顶部标题栏。 | 旧样式使用半透明白底和偏轻文字，贴近顶部且行间距过小，可读性差。本轮改为深色不透明 toast 气泡、13sp 中等字重、轻阴影、14dp 圆角，整体下移到标题栏下方，最多同时显示 3 条，并补 Compose 渲染测试。 |
| 未完成 assistant 禁用操作 | 对话未完成的 turn assistant 不能出现复制和分叉功能。 | 我观察正在生成的 assistant 回复；即使底部显示实时耗时，也不应看到“复制文本”或“从此处分叉”按钮，避免复制半截内容或从未稳定上下文分叉。 | 旧 UI 只看消息是否在 turn 末尾和 `isFinal` 元数据，若运行中消息带了 final/fork 元数据仍可能暴露操作。本轮给运行中的当前 turn 标记 `assistantActionsEnabled=false`，只禁复制/分叉，保留耗时显示；补单测和 Compose 回归测试。 |

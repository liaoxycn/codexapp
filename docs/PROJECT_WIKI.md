# 项目 Wiki

## 目录职责

```text
codexapp/
├─ app/                                  Android 客户端工程，负责手机端界面、连接和交互
│  ├─ src/main/java/com/codex/mobile/
│  │  ├─ model/                          Android 端 UI/会话领域模型
│  │  │  ├─ ThreadModels.kt              线程摘要、分组、状态
│  │  │  ├─ MessageModels.kt             消息、消息块、角色
│  │  │  ├─ ComposerModels.kt            输入区 chip 模型
│  │  │  ├─ AppUpdateState.kt            App 更新检查/下载/安装状态
│  │  │  ├─ ConnectionModels.kt          连接状态、gateway 配置
│  │  │  ├─ SessionRemoteState.kt        仓储远端状态
│  │  │  └─ HomeUiState.kt               Home 页面渲染状态
│  │  ├─ data/                           会话仓储接口与状态编排
│  │  │  ├─ SessionRepository.kt         仓储接口
│  │  │  ├─ DefaultSessionRepository.kt  gateway 配置保存、状态流编排与依赖装配
│  │  │  ├─ GatewayRepositoryCommandActions.kt 已连接命令守卫、optimistic prompt、本地状态回写
│  │  │  ├─ GatewayRepositoryConnection.kt WebSocket 生命周期接线与连接状态回写
│  │  │  ├─ GatewayInboundStateReducer.kt gateway 原始消息 -> SessionRemoteState 归并
│  │  │  ├─ SessionConnectionMutations.kt 连接状态/错误/手动刷新变更纯函数
│  │  │  ├─ SessionThreadMutations.kt    新建会话、切换会话、历史加载变更纯函数
│  │  │  ├─ SessionMessageMutations.kt   optimistic prompt 与发送失败消息变更纯函数
│  │  │  └─ gateway/                     gateway DTO、命令发送器、WebSocket、配置、快照映射、连接地址工具
│  │  └─ ui/
│  │     ├─ app/                         Compose 应用装配、顶层 scaffold、连接弹窗
│  │     │  ├─ CodexApp.kt               应用装配与顶层组件串联
│  │     │  ├─ CodexAppController.kt     顶层本地 UI 状态、drawer/gateway/back 行为控制器
│  │     │  ├─ CodexAppLogic.kt          顶栏线程标题与状态解析纯函数
│  │     │  ├─ GatewayDialog.kt          Desktop Gateway 连接弹窗装配与按钮动作
│  │     │  ├─ GatewayDialogState.kt     连接弹窗本地草稿状态
│  │     │  ├─ GatewayDialogStatus.kt    连接状态提示条与说明文案
│  │     │  └─ GatewayDialogFields.kt    地址/配对码输入字段
│  │     ├─ drawer/                      会话抽屉、项目分组、排序、时间展示
│  │     │  ├─ DrawerContent.kt          抽屉顶层装配
│  │     │  ├─ DrawerHeader.kt           抽屉标题、连接状态、header 操作按钮装配
│  │     │  ├─ DrawerSectionsState.kt    项目分组展开状态与 section 组合
│  │     │  ├─ DrawerThreadList.kt       项目区/普通会话区列表渲染
│  │     │  ├─ DrawerThreadSections.kt   抽屉项目分组/展开策略纯函数
│  │     │  ├─ DrawerFormatters.kt       会话排序与相对时间格式化纯函数
│  │     │  ├─ DrawerSectionChrome.kt    抽屉 section 标题与 header 操作按钮
│  │     │  ├─ DrawerConnectionStatus.kt 抽屉 gateway 连接状态行
│  │     │  ├─ DrawerGroupHeader.kt      项目组标题装配与新建会话入口
│  │     │  ├─ DrawerGroupHeaderContent.kt 项目标题、次级路径、展开箭头内容体
│  │     │  ├─ DrawerGroupHeaderLogic.kt 项目组语义文案与标题尺寸纯函数
│  │     │  └─ DrawerThreadRow.kt        会话行渲染
│  │     ├─ thread/                      线程消息列表、刷新、历史加载、连接横幅、空状态
│  │     │  ├─ ThreadScreen.kt           线程页装配、连接横幅/下拉提示/滚到底部按钮
│  │     │  ├─ ThreadListController.kt   线程列表行为装配器，组合滚动/刷新/历史分页模块
│  │     │  ├─ ThreadListMetrics.kt      线程页布局 metrics 纯函数与结构
│  │     │  ├─ ThreadPullRefreshController.kt 线程列表底部下拉刷新手势与提示控制
│  │     │  ├─ ThreadAutoScroll.kt       新消息自动滚底策略
│  │     │  ├─ ThreadHistoryPaging.kt    顶部加载历史与锚点恢复
│  │     │  ├─ ThreadMessageList.kt      线程消息 LazyColumn 内容体
│  │     │  ├─ ThreadConnectionBanner.kt 连接状态横幅与重连入口
│  │     │  └─ ThreadStateCards.kt       空会话/切换中状态卡片
│  │     ├─ message/                     消息卡片、Markdown、代码块、命令执行、文件变更
│  │     │  ├─ MessageCards.kt           消息角色分发入口
│  │     │  ├─ UserMessages.kt           用户消息气泡
│  │     │  ├─ AssistantMessages.kt      助手消息渲染与卡片装配
│  │     │  ├─ AssistantMessageCards.kt  助手消息文件/命令卡片提取纯函数
│  │     │  ├─ CommandExecutionCard.kt   命令执行摘要卡片与详情展开
│  │     │  ├─ CodeBlock.kt              代码/命令输出块装配
│  │     │  ├─ CodeBlockChrome.kt        代码块 header 与展开按钮外壳
│  │     │  ├─ CodeBlockLogic.kt         代码块折叠策略、标签与提示纯函数
│  │     │  ├─ MarkdownMessages.kt       Markdown 块级渲染与折叠展开
│  │     │  ├─ MarkdownLineItem.kt       Markdown 单行块渲染
│  │     │  ├─ MarkdownParsing.kt        Markdown 行解析纯函数与行模型
│  │     │  ├─ MarkdownInline.kt         inline markdown 富文本解析与文本组件
│  │     │  ├─ ExpandableTextButton.kt   通用展开/收起按钮
│  │     │  ├─ FileChangeMessages.kt     文件变更卡片装配与共享尺寸 token
│  │     │  ├─ FileChangeRow.kt          文件变更单行展开态、路径与 diff 入口
│  │     │  ├─ FileDiffBlock.kt          diff 文本块渲染
│  │     │  ├─ FileChangeParsing.kt      文件变更 block -> entry 提取纯函数
│  │     │  └─ FileChangeDiff.kt         diff 行着色与富文本构建纯函数
│  │     ├─ composer/                    输入框、slash 命令面板、工具按钮、面板状态
│  │     │  ├─ Composer.kt               输入区装配、局部状态与事件桥接
│  │     │  ├─ ComposerController.kt     输入框控制器装配与回调收口
│  │     │  ├─ ComposerControllerState.kt 本地 remembered 状态、焦点对象、输入字段草稿
│  │     │  ├─ ComposerControllerEffects.kt IME 收起、字段同步、副作用处理
│  │     │  ├─ ComposerControllerRules.kt 发送可用性与失败清焦点判定纯函数
│  │     │  ├─ ComposerPanel.kt          输入区详情面板强类型枚举
│  │     │  ├─ ComposerLogic.kt          slash token/过滤/placeholder 纯函数
│  │     │  ├─ ComposerButtons.kt        输入区小按钮与图标按钮
│  │     │  ├─ ComposerDetails.kt        详情工具区、cwd/权限摘要、slash 面板装配
│  │     │  ├─ ComposerInputBar.kt       主输入条装配
│  │     │  ├─ ComposerInputField.kt     文本输入框、placeholder、IME 发送行为
│  │     │  ├─ ComposerInputActions.kt   工具展开按钮、发送/停止动作按钮
│  │     │  ├─ FilePickerPanel.kt        项目内文件树选择与搜索面板
│  │     │  └─ SlashCommandPanel.kt      slash 命令搜索面板与命令行项
│  │     ├─ state/                       Home 状态映射、输入文本纯函数、重连/live refresh 策略
│  │     │  ├─ HomeViewModel.kt          Home 页面公开事件入口，桥接 repository 与 UI state
│  │     │  ├─ HomeViewModelDelegate.kt  Home 页面事件分发与状态层装配
│  │     │  ├─ HomeUiStateStore.kt       repository 状态 + composer 本地状态 -> HomeUiState 组合
│  │     │  ├─ HomeRepositoryActions.kt  用户动作 -> repository/协调器 调用收口
│  │     │  ├─ HomeRepositoryCoordinator.kt repository 自动连接、state 监听、重连/live refresh 副作用协调
│  │     │  ├─ ComposerActionHandler.kt  composer 输入、模板、compact、发送动作收口
│  │     │  ├─ ComposerSession.kt        输入框草稿、线程切换草稿恢复、slash/模板写入
│  │     │  ├─ ReconnectCoordinator.kt   gateway 自动重连节流与手动断开策略
│  │     │  ├─ LiveRefreshCoordinator.kt 线程 live refresh 与手动刷新动画编排
│  │     │  ├─ ComposerDrafts.kt         草稿读写纯函数
│  │     │  ├─ ComposerText.kt           slash token/命令模板拼接纯函数
│  │     │  ├─ HomeStateMapper.kt        SessionRemoteState -> HomeUiState 映射
│  │     │  └─ ReconnectPolicy.kt        重连/轮询判定纯函数
│  │     ├─ common/                      共享顶部栏、状态点、线程状态文本
│  │     │  ├─ TopBar.kt                 顶部栏与 header icon button
│  │     │  └─ ThreadStatusIndicators.kt 线程状态点、状态文本、状态标签/颜色
│  │     └─ theme/                       Compose 主题
│  │  ├─ update/                         GitHub release 更新检查、系统下载器下载 APK
│  ├─ src/main/res/drawable/             Android 启动器图标前景等矢量资源
│  ├─ src/main/res/mipmap-anydpi-v26/    Android adaptive icon 配置
│  └─ build/                             Android 构建输出，默认不提交
├─ desktop-gateway/                      桌面转发层，连接 Android App 与本机 codex app-server
│  ├─ src/
│  │  ├─ appServerTransport.ts           codex app-server 子进程传输层、JSON-RPC 请求/事件分发
│  │  ├─ appServerLifecycle.ts           app-server 初始化握手与 client 生命周期启动辅助
│  │  ├─ appServerThreadRpc.ts           thread/list/read/resume/start/archive 等线程 RPC
│  │  ├─ appServerTurnRpc.ts             turn/start/steer/interrupt 与 compact/shell RPC
│  │  ├─ threadStatus.ts                 app-server 线程状态字段解析、activeFlags 与终态判断
│  │  ├─ threadSummaryState.ts           线程运行中判断、summary/lifecycle 状态映射与 overlay 保留策略
│  │  ├─ backend/                        内存 mock backend、seed 数据、消息生成辅助
│  │  │  ├─ InMemoryDesktopBackend.ts    mock backend 主实现
│  │  │  ├─ seedThreads.ts               默认 seed 线程数据
│  │  │  ├─ mockResponses.ts             mock assistant/system 响应块生成
│  │  │  ├─ helpers.ts                   snapshot clone 与工作区路径辅助
│  │  │  └─ types.ts                     mock backend 线程记录类型
│  │  ├─ bridge/                         app-server bridge 主实现与纯映射辅助
│  │  │  ├─ AppServerBridgeBackend.ts    bridge 主协调器，串联线程目录、通知、turn 动作
│  │  │  ├─ BridgeCatalogController.ts   线程目录、切换选中、history window、hydration 动作收口
│  │  │  ├─ BridgeThreadController.ts    turn/approval 动作与 lifecycle deps 装配
│  │  │  ├─ bridgeBackendLifecycle.ts    app-server 生命周期回调、审批请求、turn/compact 收尾
│  │  │  ├─ bridgeRuntimeStore.ts        bridge 可变运行态、选中线程、snapshot 与变更事件
│  │  │  ├─ notifications.ts             app-server notification 路由分发
│  │  │  ├─ threadNotifications.ts       线程状态、turn 完成、compact、审批完成通知处理
│  │  │  ├─ itemNotifications.ts         agent delta、reasoning、命令输出、文件变更通知处理
│  │  │  ├─ summaries.ts                 thread summary/snapshot、权限摘要、历史窗口、错误辅助
│  │  │  ├─ threadSummaries.ts           线程 title/subtitle/grouping/lastActivity/summary 映射纯函数
│  │  │  ├─ messageMapping.ts            thread item -> message block 映射、命令执行块生成
│  │  │  ├─ messageMerging.ts            optimistic user/live assistant 消息等价与 block 合并纯函数
│  │  │  ├─ runtimeAssistantMessages.ts  live assistant 占位消息、delta 合并、最终消息收口
│  │  │  ├─ runtimeMessageStore.ts       snapshot 消息数组 upsert/merge/rename 底层写入器
│  │  │  ├─ snapshotMapping.ts           兼容导出层，聚合 summary/message mapping 导出
│  │  │  ├─ runtimeMessages.ts           thread item -> message 增量落盘入口
│  │  │  ├─ runtimeSnapshotMessages.ts   snapshot 消息窗口、compact 状态归并、历史消息回灌
│  │  │  ├─ runtimeState.ts              单线程 runtime state 组装与 snapshot 回写
│  │  │  ├─ runtimeSummaryState.ts       summary 同步、占位态创建、failed 收口
│  │  │  ├─ promptActions.ts             prompt 提交、compact、shell 前置审批、turn interrupt 编排
│  │  │  ├─ approvalActions.ts           shell/权限审批通过拒绝、pendingApproval 状态回写
│  │  │  ├─ turnFinalization.ts          turn/compact 完成态刷新、消息收口、状态回写
│  │  │  ├─ fileChanges.ts               文件变更块与 diff 摘要生成
│  │  │  ├─ serverRequests.ts            app-server 审批请求 -> gateway PendingApproval 映射
│  │  │  ├─ threadHydration.ts           thread list/read 目录 hydration 与 summary 占位态补全
│  │  │  ├─ threadSubscriptions.ts       thread resume/read/unsubscribe 订阅恢复与解绑辅助
│  │  │  ├─ threadCatalogMutations.ts    新建/重命名/归档/反归档等线程目录变更动作
│  │  │  ├─ threadCatalogActions.ts      线程目录 refresh/history 动作与 mutation 兼容导出层
│  │  │  ├─ threadSelection.ts           默认线程解析、切换选中、refresh stale 判定辅助
│  │  │  └─ types.ts                     bridge 运行态类型、窗口大小、审批类型
│  │  ├─ server/                         gateway WebSocket/HTTP server 主实现
│  │     ├─ GatewayServer.ts             server 装配、动作委托、backend 选择
│  │     ├─ GatewayBackendController.ts  server 后端选择、refresh/action handler 装配、desktop poke 委托
│  │     ├─ backendActions.ts            snapshot/status/patch 出站、backend action 错误收口、refresh 调度桥接
│  │     ├─ clientConnection.ts          Android 客户端上下文、patch 能力状态、socket 事件绑定、断开清理
│  │     ├─ clientMessages.ts            Android 客户端入站消息解析、鉴权/能力协商与路由分发
│  │     ├─ clientThreadMessages.ts      线程目录/刷新/历史加载等客户端消息处理
│  │     ├─ clientTurnMessages.ts        prompt/停止/审批等 turn 客户端消息处理
│  │     ├─ gatewayPaths.ts              gateway upgrade 路径规范化与判定纯函数
│  │     ├─ liveRefresh.ts               snapshot/list/live refresh 调度与 stale refresh 防抖
│  │     ├─ httpPoke.ts                  `/poke` HTTP API 与桌面窗口唤起
│  │     └─ types.ts                     server 侧客户端上下文、backend 合约、常量
│  │  ├─ backend.ts                      backend 兼容导出层
│  │  └─ threadState.ts                  线程状态兼容导出层，聚合 threadStatus/threadSummaryState
│  ├─ dist/                              TypeScript 构建输出，默认不提交
│  ├─ scripts/                           gateway 辅助脚本，包含 exe 构建与协议自测
│  └─ node_modules/                      npm 依赖，默认不提交
├─ scripts/                              本地构建、部署、自测 Node 脚本
│  ├─ dev-run.mjs                        重启 gateway、编译、安装并打开 App 的主自测入口
│  ├─ build-apk.mjs                      本地 release APK 构建、zipalign、debug keystore 签名
│  ├─ build-upload.mjs                   构建 APK 后上传 WebDAV
│  ├─ upload-apk.mjs                     WebDAV 上传实现
│  ├─ run-gateway-dev.mjs                gateway 开发模式启动辅助
│  ├─ poke-desktop.mjs                   本地触发 `/poke`/Desktop 唤起逻辑的验证脚本
│  ├─ pre-release-check.mjs              发布前一键检查，串联 Android/Gateway/协议自测，可选 dev-run
│  ├─ github-release.mjs                 提交当前阶段改动、写版本/更新说明、推送 tag 触发 Actions，并把发布日志写入 scripts/logs
│  ├─ script-utils.mjs                   脚本共享工具
│  └─ logs/                              脚本本地运行日志，默认不提交
├─ docs/                                 项目文档、规范、wiki、调研资料入口
│  ├─ MOBILE_GATEWAY_PROTOCOL.md         Android App 与 desktop-gateway 对接协议、payload 与高频流程测试清单
│  ├─ MOBILE_GATEWAY_FLOW_PROGRESS.md    高频用户流程测试、发现问题与修复进度记录
│  └─ research/                          调研资料、外部协议资料、生成型参考数据
│     └─ codex-app-server-protocol/
│        ├─ types/                       Codex App Server 协议生成的 TypeScript 类型
│        └─ schema/                      Codex App Server 协议生成的 JSON Schema
├─ artifacts/                            构建或发布过程产生的临时成果物，默认不提交
├─ build/                                Gradle 根构建输出，默认不提交
├─ gradle/                               Gradle Wrapper 配置
└─ stitch_codex_mobile_shell/            外部/辅助资源目录，改动前先确认用途
```

## 资料归档规则

- 调研资料统一放入 `docs/research/`。
- 新增调研资料目录时，同步更新 `docs/research/README.md`。
- 生成型协议、schema、类型定义等不要放项目根目录。
- 生成文件不要手改；需要更新时重新生成或整体替换。
- 新增重要目录时，同步更新本 wiki。

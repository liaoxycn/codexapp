# Android Agent Debug Bridge

Debug APK 启动后，App 会在设备本机 `127.0.0.1:19090` 开一个轻量 HTTP 调试桥。release 或非 debuggable 构建不启动。

## 常用命令

```powershell
node scripts/agent-debug.mjs -Endpoint /health
node scripts/agent-debug.mjs -Endpoint /health -EnsureApp
node scripts/agent-debug.mjs -Endpoint "/state?messageLimit=20"
node scripts/agent-debug.mjs -Endpoint /routes
node scripts/agent-debug.mjs -Action refresh_current
node scripts/agent-debug.mjs -Action set_composer_text -Text hello
node scripts/agent-debug.mjs -Action send_text -Text "hello" -WaitFor "isGenerating=true"
node scripts/agent-debug.mjs -Action send_text -Text "long task" -WaitFor "isGenerating=true" -ThenAction stop -ThenWaitFor "isGenerating=false"
node scripts/agent-debug.mjs -Action edit_and_resend_user_message -Text "hello again" -RollbackNumTurns 1
node scripts/agent-debug.mjs -WaitFor "isGenerating=false,testSummary.lastAssistantIsFinal=true" -TimeoutMs 120000
node scripts/agent-debug.mjs -Endpoint /state -RequireText "回复 Codex" -ForbidText "停止生成"
node scripts/agent-debug.mjs -Endpoint /state -Bundle tmp/agent-debug/latest
node scripts/agent-debug.mjs -TapText "已处理" -RequireText "正在思考"
node scripts/agent-debug.mjs -TapDesc "展开输入工具" -AfterTapWaitFor "showComposerDetails=true"
node scripts/agent-debug.mjs -TapDescIfPresent "展开处理过程" -RequireText "已运行"
node scripts/agent-debug.mjs -TapDesc "复制文本"
node scripts/agent-debug.mjs -RestartApp -AfterRestartWaitFor "connectionStatus=connected"
node scripts/agent-debug.mjs -Scenario smoke -Bundle tmp/agent-debug/smoke
node scripts/agent-debug.mjs -Scenario smoke,draft-config -Bundle tmp/agent-debug/batch-smoke-draft
node scripts/agent-debug.mjs -Scenario all -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/all
node scripts/agent-debug.mjs -Scenario composer-tools -Bundle tmp/agent-debug/composer-tools
node scripts/agent-debug.mjs -Scenario send-stop -Cwd "D:/Projects/home" -Text "Start a long answer and wait for stop." -Bundle tmp/agent-debug/send-stop
node scripts/agent-debug.mjs -Scenario running-actions-hidden -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/running-actions-hidden
node scripts/agent-debug.mjs -Scenario running-reconnect-finalization -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/running-reconnect-finalization
node scripts/agent-debug.mjs -Scenario final-basic -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/final-basic
node scripts/agent-debug.mjs -Scenario markdown-final -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/markdown-final
node scripts/agent-debug.mjs -Scenario final-actions -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/final-actions
node scripts/agent-debug.mjs -Scenario archive-reconnect-clean -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/archive-reconnect-clean
node scripts/agent-debug.mjs -Scenario command-process -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/command-process
node scripts/agent-debug.mjs -Scenario process-details -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/process-details
node scripts/agent-debug.mjs -Scenario drawer-history -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/drawer-history
node scripts/agent-debug.mjs -Scenario reconnect-current -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/reconnect-current
node scripts/agent-debug.mjs -Scenario app-restart-current -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/app-restart-current
node scripts/agent-debug.mjs -Scenario edit-resend -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/edit-resend
node scripts/agent-debug.mjs -Scenario resend-user-message -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/resend-user-message
node scripts/agent-debug.mjs -Scenario user-message-menu -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/user-message-menu
node scripts/agent-debug.mjs -Scenario long-thread-scroll -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/long-thread-scroll
node scripts/agent-debug.mjs -Scenario draft-config -Cwd "D:/Projects/home" -Bundle tmp/agent-debug/draft-config
node scripts/agent-debug.mjs -Swipe up -AfterSwipeWaitMs 800 -Bundle tmp/agent-debug/swipe
node scripts/agent-debug.mjs -ArchiveThreadIds "thread-a,thread-b"
node scripts/agent-debug.mjs -ArchiveTitlePrefix "drawer-" -ArchiveLimit 20
```

脚本默认执行：

```powershell
adb forward tcp:19090 tcp:19090
```

## 端点

- `GET /health`：bridge 可用性、端口、schema。
- `GET /routes`：可用端点与动作列表。
- `GET /state?messageLimit=40&threadLimit=50`：当前 UI/会话状态、消息块、诊断字段、测试 tag hints、`testSummary` 消息流计数；`testSummary` 区分 user / assistant / system 数量。
- `POST /action`：安全动作分发。

## 动作

- 会话：`refresh_threads`、`refresh_current`、`new_thread`、`select_thread`、`load_older_messages`。
- 生成：`send`、`send_text`、`stop`、`approve`、`reject`。
- 输入框：`set_composer_text`、`insert_composer_text`、`clear_composer`、`insert_shell_template`、`apply_slash_command`。
- turn：`edit_and_resend_user_message`、`resend_user_message`、`compact_context`、`rollback_last_turn`。
- 线程管理：`fork_thread`、`rename_thread`、`archive_thread`、`unarchive_thread`。
- 配置：`update_new_thread_draft`、`update_current_thread_config`。
- gateway：`connect_gateway`、`disconnect_gateway`、`reconnect_gateway`。

## CLI 参数

- `-Help`：打印 CLI 用法。
- `-Scenario smoke|composer-tools|...`：运行固定远程测试场景；支持逗号列表和 `all`。配合 `-Bundle` 会为每一步保存证据包和 `scenario-summary.json`；批量模式会按场景创建子目录，并在根目录写 `scenario-batch-summary.json`。
- `-WaitFor "<条件>"`：轮询 `/state`，条件用逗号连接，支持 `= != ~= > >= < <=`，如 `isGenerating=false,messageCount>=2`；用 `A||B` 表示任一分支满足即可。
- `-BridgeDisconnectLimit 8`：等待 `/state` 时连续请求失败达到阈值就提前判定 bridge 断开，用于快速暴露 App crash。
- `-ThenAction <action>` / `-ThenWaitFor "<条件>"`：先完成前一个 action/wait，再执行第二个 action，用于“发送后立即停止”等链式流程。
- `-RestartApp` / `-AfterRestartWaitFor "<条件>"`：通过 adb force-stop + am start 重启 App，bridge 恢复后继续轮询 `/state`。
- `-TimeoutMs 30000` / `-IntervalMs 800`：等待超时与轮询间隔。
- `-Output <path>`：保存最终 JSON。
- `-Screenshot <path>`：保存当前屏幕截图。
- `-UiDump <path>`：保存当前 UI XML。
- `-HideKeyboard`：截图或 UI dump 前收起软键盘；会轮询 UI dump，若仍检测到输入法包则补发 Back 再截图。
- `-Bundle <dir>`：保存 `state.json`、`screenshot.png`、`ui.xml`、`summary.json`。
- 场景模式下的 step `summary` 优先取该步骤 `Bundle/summary.json` 的最终状态，避免 wait 刚满足时的瞬时状态和截图证据不一致。
- `-ArchiveThreadIds "id1,id2"`：按精确线程 ID 归档，并等待这些线程从可见列表消失，适合场景清理分叉/原始会话。
- `-RequireText "A,B"` / `-ForbidText "A,B"`：基于 UI XML 断言屏幕文字或 `content-desc`。
- `-RequireEnabledDesc "A,B"` / `-RequireDisabledDesc "A,B"`：断言匹配 `content-desc` 的节点存在且处于启用/禁用状态。
- `-UiAssertTimeoutMs 5000` / `-UiAssertIntervalMs 350`：UI 文本断言会重试，等待 Compose 完成重绘。
- `-TapText "文字"` / `-TapDesc "content-desc"`：从 UI XML 找到节点 bounds，再通过 adb tap 点中心；找不到会失败。
- `-TapTextIfPresent "文字"` / `-TapDescIfPresent "content-desc"`：存在则点击，不存在不失败，适合“可能已展开”的状态收敛。
- `-TapIndex 0` / `-TapTimeoutMs 5000` / `-AfterTapWaitMs 500`：选择第几个匹配项、等待点击目标、点击后等待重绘。
- `-AfterTapWaitFor "<条件>"`：点击完成后继续轮询 `/state`，用于验证 UI 状态变化。
- 便捷字段：`-Text`、`-ThreadId`、`-NumTurns`、`-RollbackNumTurns`、`-Name`、`-Command`、`-Cwd`、`-Model`、`-ReasoningEffort`、`-PermissionMode`、`-Url`、`-PairToken`。

## 场景

- `smoke`：检查 bridge、读取状态、关闭输入工具、设置/清空输入框。
- `composer-tools`：按用户点击方式展开/收起输入工具，并断言 `showComposerDetails`。
- `send-stop`：先进入新会话草稿，再发送长 prompt；若捕获到 running 则停止，若模型已快速 final 也不误卡死，最后等待 idle；可用 `-Cwd` 指定测试目录。
- `running-actions-hidden`：验证 running 中不显示复制/分叉按钮、底部显示“正在思考中”，并断言同一 running turn 不重复渲染 user 消息。
- `running-reconnect-finalization`：运行短 shell 命令时断开/重连 gateway，断言断开期间仍保持 running，重连后能收口到 idle 并显示命令结果。
- `final-basic`：先进入新会话草稿，发送短 Markdown 回复请求，等待 final，再断言复制/分叉按钮出现且 UI 不暴露 `**OK**` 原始 Markdown。
- `final-actions`：在 `final-basic` 基础上真实点击复制、打开分叉确认弹窗并点击“确认分叉”，等待选中线程切到分叉会话，最后按 ID 清理分叉和原会话。
- `archive-reconnect-clean`：归档当前会话后立即进入新会话草稿，断言保持 connected、`diagnostics.actionStatus!=failed`，且 UI 不显示旧归档线程导致的“连接异常”。
- `command-process`：发送 `! echo codexapp-agent-command`，等待 shell 审批，调用 approve，验证已运行命令和输出。
- `app-restart-current`：生成 final 会话后强制重启 Android App，验证仍选中原会话、final Markdown 渲染和复制/分叉按钮都恢复。
- `edit-resend`：进入编辑后重发状态，再提交编辑后的 prompt，验证旧 turn 已被替换且 UI 不残留旧内容。
- `resend-user-message`：直接触发用户消息重发，验证 rollback 后只有新 user/final turn。
- `user-message-menu`：真实点击用户消息菜单，覆盖“编辑后重发”和“重发”两个菜单入口。
- `long-thread-scroll`：连续创建多轮较高内容，手势上下滑验证早期和最新消息都能稳定显示。
- `draft-config`：验证新会话草稿默认完全访问，并覆盖权限模式切换。

## 约束

- 只绑定设备 `127.0.0.1`，外部必须经 `adb forward` 访问。
- 面向 Agent 调试，默认不自动发送 prompt；`send` 或 `send_text` 需显式调用。
- 输出 JSON schema 当前为 `codexapp.agentDebug.v1`。
## Screenshot regression

```powershell
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.codexapp.ui.MessageFlowScreenshotTest"
```

Screenshot output:

```text
app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/<device>/message-flow-screenshots/
```
## Ensure app preflight

```powershell
node scripts/agent-debug.mjs -Endpoint /health -EnsureApp
```

- `-EnsureApp`: if `/health` is unavailable, launch the app with adb and wait for the debug bridge.
- If the package/activity is missing, the CLI returns structured JSON with `error=ensure_app_failed` and points back to `node scripts/dev-run.mjs`.
- `-AppId`: package id for `-EnsureApp`, default `com.codexapp`.
- `-LaunchActivity`: activity for `-EnsureApp`, default `.MainActivity`.

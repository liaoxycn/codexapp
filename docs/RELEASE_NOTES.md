## 本次更新
- 改造 GitHub 发版脚本，关键步骤写入 scripts/logs/github-release-*.log。
- branch push 和 tag push 各自最多重试 3 次；失败只记录日志，脚本对外正常结束。
- 更新 AGENTS 规范，外部 AI 只需传 Version、VersionCode、Notes 并等待脚本退出。

## Release 产物
- CodexMobile.apk

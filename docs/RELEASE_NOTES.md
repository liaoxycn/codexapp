## 本次更新
- 发布脚本新增 scripts/logs/github-release-latest.json 固定摘要，便于快速确认最近一次发包状态。
- 摘要记录版本、tag、日志路径、push 结果和 Actions 触发状态，内部错误也会落盘。
- 修复发布说明正文包含 -Notes 字样时被误判为参数的问题，并补回归测试。

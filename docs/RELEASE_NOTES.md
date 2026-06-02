## 本次更新
- App 侧 gateway 入站日志改为结构化摘要，只输出类型、revision、线程/消息数、运行态等元数据。
- 不再在 logcat 输出完整 snapshot/patch/status 原文，避免 prompt、助手回复和错误详情泄露或刷屏。
- 新增入站日志摘要脱敏回归测试。

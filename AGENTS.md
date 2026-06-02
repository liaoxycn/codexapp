## 规范
- 所有向用户发起的确认、澄清、追问，都必须使用中文。
- 输出的回答或文档要精简字数，少废话，只保留要点，禁止啰嗦重复。
- 每次改后端或app代码后准备自测前必须先运行 `node scripts/dev-run.mjs`，脚本包含重启后端、编译 APK、部署 APK、打开 App 等完整流程，这样保证测试的是最新代码
- 项目 wiki 见 `docs/PROJECT_WIKI.md`；新增资源目录或调整目录职责时必须同步更新。
- 当涉及特定环境、版本、私有资产或其他外部知识时，严禁自行推测或假设。应优先通过联网搜索、查阅官方文档或可靠来源进行核实验证，确保信息的准确性和时效性。
- 自测时禁止对当前项目"codexapp"会话调式，防止打断当前会话
- 每完成一个阶段性任务（耗时短、简单任务可跳过），应提交 GitHub 并触发新包发布；流水线操作尽量交给脚本执行，减少在部署细节上占用对话。
- 阶段性发包统一执行 `node scripts/github-release.mjs -Version <x.y.z> -VersionCode <正整数> -Notes "<更新说明>"`，或用 `-NotesFile <路径>` 传更新说明；可选 `-CommitMessage`、`-Branch`、`-Remote`、`-RunChecks`。脚本会提交当前改动、推送分支和 `v<Version>` tag，tag push 触发 GitHub Actions 打包；脚本执行成功即视为已触发发布，不等待 Actions 结果。

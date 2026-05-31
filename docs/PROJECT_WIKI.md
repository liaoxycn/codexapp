# 项目 Wiki

## 目录职责

- `app/`：Android 客户端工程，负责手机端界面、连接和交互。
- `desktop-gateway/`：桌面转发层，连接 Android App 与本机 `codex app-server`。
- `scripts/`：本地构建、部署、自测脚本。
- `docs/`：项目文档、规范、wiki、调研资料入口。
- `docs/research/`：调研来的技术文档、外部协议资料、生成型参考数据；索引见 `docs/research/README.md`。
- `docs/research/codex-app-server-protocol/types/`：Codex App Server 协议生成的 TypeScript 类型。
- `docs/research/codex-app-server-protocol/schema/`：Codex App Server 协议生成的 JSON Schema。
- `artifacts/`：构建或发布过程产生的临时成果物，默认不提交。
- `build/`、`app/build/`：Gradle/Android 构建输出，默认不提交。
- `gradle/`：Gradle Wrapper 配置。
- `stitch_codex_mobile_shell/`：外部/辅助资源目录，改动前先确认用途。

## 资料归档规则

- 调研资料统一放入 `docs/research/`。
- 新增调研资料目录时，同步更新 `docs/research/README.md`。
- 生成型协议、schema、类型定义等不要放项目根目录。
- 生成文件不要手改；需要更新时重新生成或整体替换。
- 新增重要目录时，同步更新本 wiki。

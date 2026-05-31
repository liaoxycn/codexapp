# 调研资料索引

## 归档原则

- 每类资料单独建目录，目录名使用小写短横线。
- 每个资料目录应说明来源、用途、是否生成、更新时间。
- 生成型资料不要手改；需要更新时整体替换。
- 新增资料目录后，同步更新本索引。

## 目录索引

| 目录 | 内容 | 来源/性质 | 用途 |
| --- | --- | --- | --- |
| `codex-app-server-protocol/` | Codex App Server 协议资料 | 调研/生成型参考数据 | 对齐 Android、gateway 与 `codex app-server` 通信协议 |

## 资料详情

### `codex-app-server-protocol/`

- `types/`：协议生成的 TypeScript 类型。
- `schema/`：协议生成的 JSON Schema。
- 注意：只作为协议参考资料；当前业务代码未直接引用。

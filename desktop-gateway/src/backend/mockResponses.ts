import { randomUUID } from "node:crypto";
import type { GatewayBlockPayload, GatewayMessagePayload } from "../protocol.js";

export function buildAssistantResponse(text: string): GatewayMessagePayload {
  const blocks: GatewayBlockPayload[] = [
    {
      kind: "text",
      value: "这条回复来自 desktop gateway 的内存后端。下一步把这里替换成真实 codex app-server 回放即可。"
    },
    {
      kind: "code",
      language: "json",
      value: JSON.stringify(
        {
          upstream: "codex app-server",
          action: "turn/start",
          prompt: text
        },
        null,
        2
      )
    }
  ];

  if (text.startsWith("!")) {
    blocks.push({
      kind: "text",
      value: "命令入口保留给 gateway 聚合。后续会把 shell 命令、输出块和审批请求映射到移动端卡片。"
    });
  }

  return {
    id: randomUUID(),
    role: "assistant",
    blocks
  };
}

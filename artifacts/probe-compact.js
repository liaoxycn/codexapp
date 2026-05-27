const WebSocket = require("D:/Projects/home/codexapp/desktop-gateway/node_modules/ws");
const threadId = "019e3e0c-74e4-71e1-9803-d6337ec0e570";
const ws = new WebSocket("ws://127.0.0.1:8765/mobile");
let phase = 0;
ws.on("open", () => {
  ws.send(JSON.stringify({ type: "hello", client: "probe", version: "0.1.0" }));
});
ws.on("message", (buf) => {
  const msg = JSON.parse(String(buf));
  if (msg.type === "snapshot") {
    console.log("SNAPSHOT", phase, JSON.stringify({
      selectedThreadId: msg.selectedThreadId,
      isGenerating: msg.isGenerating,
      pendingApproval: msg.pendingApproval,
      tail: (msg.messages || []).slice(-5)
    }, null, 2));
    if (phase === 0) {
      phase = 1;
      ws.send(JSON.stringify({ type: "select_thread", threadId }));
      return;
    }
    if (phase === 1) {
      phase = 2;
      ws.send(JSON.stringify({ type: "send_prompt", text: "/compact" }));
      return;
    }
    if (phase === 2 && (msg.messages || []).some((m) => JSON.stringify(m).includes("已请求压缩上下文"))) {
      phase = 3;
      return;
    }
    if (phase === 3 && (msg.messages || []).some((m) => JSON.stringify(m).includes("上下文已压缩"))) {
      ws.close();
    }
  } else {
    console.log("MSG", JSON.stringify(msg));
  }
});
ws.on("close", () => process.exit(0));
ws.on("error", (err) => {
  console.error(err);
  process.exit(1);
});
setTimeout(() => {
  console.error("timeout");
  process.exit(2);
}, 15000);

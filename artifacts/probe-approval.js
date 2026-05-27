const WebSocket = require("D:/Projects/home/codexapp/desktop-gateway/node_modules/ws");
const threadId = "019e3e0c-74e4-71e1-9803-d6337ec0e570";
const prompt = "请运行 pwd 查看当前工作目录，并只返回结果摘要。";
const ws = new WebSocket("ws://127.0.0.1:8765/mobile");
let phase = 0;
let deadline = Date.now() + 60000;
ws.on("open", () => ws.send(JSON.stringify({ type: "hello", client: "approval-probe", version: "0.1.0" })));
ws.on("message", (buf) => {
  const msg = JSON.parse(String(buf));
  if (msg.type !== "snapshot") {
    console.log("MSG", JSON.stringify(msg));
    return;
  }
  const tail = (msg.messages || []).slice(-6);
  console.log("SNAPSHOT", phase, JSON.stringify({
    pendingApproval: msg.pendingApproval,
    isGenerating: msg.isGenerating,
    tail,
  }, null, 2));
  if (phase === 0) {
    phase = 1;
    ws.send(JSON.stringify({ type: "select_thread", threadId }));
    return;
  }
  if (phase === 1) {
    phase = 2;
    ws.send(JSON.stringify({ type: "send_prompt", text: prompt }));
    return;
  }
  if (msg.pendingApproval) {
    console.log("APPROVAL_FOUND");
    ws.close();
  }
});
ws.on("close", () => process.exit(0));
ws.on("error", (err) => { console.error(err); process.exit(1); });
const timer = setInterval(() => {
  if (Date.now() > deadline) {
    clearInterval(timer);
    console.error("timeout");
    process.exit(2);
  }
}, 1000);

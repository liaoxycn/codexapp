const WebSocket = require("D:/Projects/home/codexapp/desktop-gateway/node_modules/ws");
const threadId = "019e3e0c-74e4-71e1-9803-d6337ec0e570";
const prompt = "请执行 shell 命令 ls -la，并在执行前先申请审批。";
const ws = new WebSocket("ws://127.0.0.1:8765/mobile");
let phase = 0;
let sentPrompt = false;
ws.on("open", () => ws.send(JSON.stringify({ type: "hello", client: "approval-probe2", version: "0.1.0" })));
ws.on("message", (buf) => {
  const msg = JSON.parse(String(buf));
  if (msg.type !== "snapshot") {
    console.log("MSG", JSON.stringify(msg));
    return;
  }
  const tail = (msg.messages || []).slice(-6);
  console.log("SNAPSHOT", phase, JSON.stringify({ pendingApproval: msg.pendingApproval, isGenerating: msg.isGenerating, tail }, null, 2));
  if (phase === 0) {
    phase = 1;
    ws.send(JSON.stringify({ type: "select_thread", threadId }));
    return;
  }
  if (phase === 1 && !sentPrompt) {
    sentPrompt = true;
    phase = 2;
    setTimeout(() => ws.send(JSON.stringify({ type: "send_prompt", text: prompt })), 300);
    return;
  }
  if (phase === 2 && msg.pendingApproval) {
    console.log("APPROVAL_FOUND");
    ws.close();
  }
});
ws.on("close", () => process.exit(0));
ws.on("error", (err) => { console.error(err); process.exit(1); });
setTimeout(() => { console.error("timeout"); process.exit(2); }, 45000);

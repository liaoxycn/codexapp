const WebSocket = require("D:/Projects/home/codexapp/desktop-gateway/node_modules/ws");
const threadId = "019e3de4-7af7-7350-bcc9-8c3701307391";
const prompt = "请在当前工作区创建文件 mobile_approval_probe.txt，内容为 ok。";
const ws = new WebSocket("ws://127.0.0.1:8765/mobile");
let phase = 0;
let sent = false;
ws.on("open", () => ws.send(JSON.stringify({ type: "hello", client: "approval-file-probe", version: "0.1.0" })));
ws.on("message", (buf) => {
  const msg = JSON.parse(String(buf));
  if (msg.type !== "snapshot") {
    console.log("MSG", JSON.stringify(msg));
    return;
  }
  const tail = (msg.messages || []).slice(-5);
  console.log("SNAPSHOT", phase, JSON.stringify({
    selectedThreadId: msg.selectedThreadId,
    pendingApproval: msg.pendingApproval,
    isGenerating: msg.isGenerating,
    tail,
  }, null, 2));
  if (phase === 0) {
    phase = 1;
    ws.send(JSON.stringify({ type: "select_thread", threadId }));
    return;
  }
  if (phase === 1 && !sent) {
    sent = true;
    phase = 2;
    setTimeout(() => ws.send(JSON.stringify({ type: "send_prompt", text: prompt })), 300);
    return;
  }
  if (phase >= 2 && msg.pendingApproval) {
    console.log("APPROVAL_FOUND");
    ws.close();
  }
});
ws.on("close", () => process.exit(0));
ws.on("error", (err) => { console.error(err); process.exit(1); });
setTimeout(() => { console.error("timeout"); process.exit(2); }, 60000);

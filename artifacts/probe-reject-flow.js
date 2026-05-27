const WebSocket = require("D:/Projects/home/codexapp/desktop-gateway/node_modules/ws");
const threadId = "019e3de4-7af7-7350-bcc9-8c3701307391";
const ws = new WebSocket("ws://127.0.0.1:8765/mobile");
let phase = 0;
let sent = false;
ws.on("open", () => ws.send(JSON.stringify({ type: "hello", client: "reject-probe", version: "0.1.0" })));
ws.on("message", (buf) => {
  const msg = JSON.parse(String(buf));
  if (msg.type !== "snapshot") {
    console.log("MSG", JSON.stringify(msg));
    return;
  }
  const tail = (msg.messages || []).slice(-8);
  console.log("SNAPSHOT", phase, JSON.stringify({ pendingApproval: msg.pendingApproval, isGenerating: msg.isGenerating, tail }, null, 2));
  if (phase === 0) {
    phase = 1;
    ws.send(JSON.stringify({ type: "select_thread", threadId }));
    return;
  }
  if (phase === 1 && !sent) {
    sent = true;
    phase = 2;
    setTimeout(() => ws.send(JSON.stringify({ type: "send_prompt", text: "! dir" })), 300);
    return;
  }
  if (phase === 2 && msg.pendingApproval) {
    phase = 3;
    ws.send(JSON.stringify({ type: "reject_pending" }));
    return;
  }
  const hasRejected = tail.some((m) => JSON.stringify(m).includes("审批已拒绝"));
  const hasCommand = tail.some((m) => JSON.stringify(m).includes("命令:"));
  if (phase === 3 && hasRejected && !hasCommand && !msg.pendingApproval) {
    console.log("REJECT_FLOW_OK");
    ws.close();
  }
});
ws.on("close", () => process.exit(0));
ws.on("error", (err) => { console.error(err); process.exit(1); });
setTimeout(() => { console.error("timeout"); process.exit(2); }, 60000);

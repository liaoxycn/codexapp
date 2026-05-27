const WebSocket = require("D:/Projects/home/codexapp/desktop-gateway/node_modules/ws");
const threadId = "019e3e0c-74e4-71e1-9803-d6337ec0e570";
const ws = new WebSocket("ws://127.0.0.1:8765/mobile");
let phase = 0;
let last = null;
ws.on("open", () => ws.send(JSON.stringify({ type: "hello", client: "probe2", version: "0.1.0" })));
ws.on("message", (buf) => {
  const msg = JSON.parse(String(buf));
  if (msg.type !== 'snapshot') {
    console.log('MSG', JSON.stringify(msg));
    return;
  }
  const tail = (msg.messages || []).slice(-6);
  last = { isGenerating: msg.isGenerating, tail };
  console.log('SNAPSHOT', phase, JSON.stringify({ isGenerating: msg.isGenerating, tail }, null, 2));
  if (phase === 0) {
    phase = 1;
    ws.send(JSON.stringify({ type: 'select_thread', threadId }));
    return;
  }
  if (phase === 1) {
    phase = 2;
    ws.send(JSON.stringify({ type: 'send_prompt', text: '/compact' }));
    return;
  }
  const hasCompacted = tail.some((m) => JSON.stringify(m).includes('上下文已压缩'));
  const hasThinking = tail.some((m) => JSON.stringify(m).includes('思考中'));
  if (phase === 2 && hasCompacted) {
    phase = 3;
    return;
  }
  if (phase === 3 && !msg.isGenerating && !hasThinking) {
    ws.close();
  }
});
ws.on('close', () => process.exit(0));
ws.on('error', (err) => { console.error(err); process.exit(1); });
setTimeout(() => {
  console.error('timeout', JSON.stringify(last, null, 2));
  process.exit(2);
}, 30000);

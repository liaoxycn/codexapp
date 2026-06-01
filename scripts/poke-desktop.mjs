const gatewayUrl = process.argv[2] ?? "http://127.0.0.1:8765/poke";

const payload = {
  reason: "manual_poke_script",
  source: "scripts/poke-desktop.mjs",
  timestamp: Date.now(),
};

console.log(`[poke] POST ${gatewayUrl}`);
console.log(`[poke] payload ${JSON.stringify(payload)}`);

try {
  const response = await fetch(gatewayUrl, {
    method: "POST",
    headers: {
      "content-type": "application/json",
    },
    body: JSON.stringify(payload),
  });
  const text = await response.text();
  console.log(`[poke] status ${response.status}`);
  console.log(`[poke] body ${text}`);
  if (!response.ok) {
    process.exitCode = 1;
  }
} catch (error) {
  console.error(`[poke] failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exitCode = 1;
}

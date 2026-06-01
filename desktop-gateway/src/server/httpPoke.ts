import process from "node:process";
import { spawnSync } from "node:child_process";
import { URL } from "node:url";
import type { IncomingMessage, ServerResponse } from "node:http";

export type DesktopPokePayload = {
  reason?: string;
  threadId?: string;
  source?: string;
  timestamp?: number;
};

export async function handlePokeHttpRequest(
  request: IncomingMessage,
  response: ServerResponse,
  restartDesktopFn: (payload: DesktopPokePayload) => Promise<{ ok: boolean }>
): Promise<void> {
  const url = new URL(request.url ?? "/", "http://localhost");
  if (request.method !== "POST" || url.pathname.replace(/\/+$/, "") !== "/poke") {
    response.statusCode = 404;
    response.end("not found");
    return;
  }

  try {
    const body = await readRequestBody(request);
    const payload = body.trim().length > 0
      ? JSON.parse(body) as DesktopPokePayload
      : {};
    console.log(`[gateway] poke received ${body}`);
    const result = await restartDesktopFn(payload);
    response.statusCode = 200;
    response.setHeader("content-type", "application/json; charset=utf-8");
    response.end(JSON.stringify({ ok: result.ok }));
    console.log(`[gateway] poke result ok=${result.ok}`);
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.warn(`[gateway] poke failed: ${detail}`);
    response.statusCode = 500;
    response.setHeader("content-type", "application/json; charset=utf-8");
    response.end(JSON.stringify({ ok: false, error: detail }));
  }
}

export async function restartCodexDesktop(payload: DesktopPokePayload): Promise<{ ok: boolean }> {
  if (process.platform !== "win32") {
    return { ok: false };
  }

  const script = `
$ErrorActionPreference = 'Stop'
$payload = $env:CODEX_MOBILE_POKE_PAYLOAD_JSON | ConvertFrom-Json
$candidates = Get-CimInstance Win32_Process -Filter "Name = 'Codex.exe'" | Where-Object {
  $_.ExecutablePath -like '*\\WindowsApps\\OpenAI.Codex_*\\app\\Codex.exe' -and
  ($_.CommandLine -notmatch '--type=')
}
$target = $candidates | Sort-Object CreationDate -Descending | Select-Object -First 1
if (-not $target) {
  Write-Output (ConvertTo-Json @{ ok = $false; error = 'Codex desktop process not found' })
  exit 0
}
$proc = Get-Process -Id $target.ProcessId -ErrorAction Stop
$path = $target.ExecutablePath
if (-not $path) {
  Write-Output (ConvertTo-Json @{ ok = $false; error = 'Codex executable path not found'; pid = $target.ProcessId })
  exit 0
}
Stop-Process -Id $target.ProcessId -Force
Start-Sleep -Milliseconds 500
$started = Start-Process -FilePath $path -PassThru
Write-Output (ConvertTo-Json @{ ok = $true; restarted = $true; oldPid = $target.ProcessId; pid = $started.Id; title = $proc.MainWindowTitle; path = $path; reason = $payload.reason; threadId = $payload.threadId })
`;

  const result = spawnSync("powershell.exe", [
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-Command",
    script,
  ], {
    encoding: "utf8",
    env: {
      ...process.env,
      CODEX_MOBILE_POKE_PAYLOAD_JSON: JSON.stringify(payload),
    },
    timeout: 4000,
    windowsHide: true,
  });

  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error((result.stderr || result.stdout || `powershell exit ${result.status}`).trim());
  }

  const text = (result.stdout || "").trim();
  if (!text) {
    return { ok: true };
  }
  try {
    return JSON.parse(text) as { ok: boolean };
  } catch {
    return { ok: true };
  }
}

async function readRequestBody(request: IncomingMessage): Promise<string> {
  return await new Promise<string>((resolve, reject) => {
    const chunks: Buffer[] = [];
    request.on("data", (chunk) => chunks.push(Buffer.from(chunk)));
    request.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    request.on("error", reject);
  });
}

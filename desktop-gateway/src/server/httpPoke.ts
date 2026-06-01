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
  focusDesktopWindowFn: (payload: DesktopPokePayload) => Promise<{ ok: boolean }>
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
    const result = await focusDesktopWindowFn(payload);
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

export async function focusDesktopWindow(payload: DesktopPokePayload): Promise<{ ok: boolean }> {
  if (process.platform !== "win32") {
    return { ok: false };
  }

  const script = `
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
$payload = $env:CODEX_MOBILE_POKE_PAYLOAD_JSON | ConvertFrom-Json
$proc = Get-Process | Where-Object { $_.MainWindowTitle -eq 'Codex' -or $_.MainWindowTitle -like 'Codex*' } | Select-Object -First 1
if (-not $proc) {
  Write-Output (ConvertTo-Json @{ ok = $false; error = 'Codex window not found' })
  exit 0
}
$shell = New-Object -ComObject WScript.Shell
try { $null = $shell.AppActivate($proc.Id) } catch {}
try {
  $sig = @"
using System;
using System.Runtime.InteropServices;
public static class WinApi {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
}
"@
  Add-Type -TypeDefinition $sig -ErrorAction SilentlyContinue | Out-Null
  [WinApi]::ShowWindowAsync($proc.MainWindowHandle, 5) | Out-Null
  [WinApi]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
} catch {}
Start-Sleep -Milliseconds 180
$steps = @(
  @{ Name = 'escape'; Keys = '{ESC}' },
  @{ Name = 'reload'; Keys = '^r' },
  @{ Name = 'hard_reload'; Keys = '^+r' },
  @{ Name = 'f5'; Keys = '{F5}' }
)
foreach ($step in $steps) {
  [System.Windows.Forms.SendKeys]::SendWait($step.Keys)
  Start-Sleep -Milliseconds 180
}
Write-Output (ConvertTo-Json @{ ok = $true; pid = $proc.Id; title = $proc.MainWindowTitle; reason = $payload.reason; threadId = $payload.threadId })
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

import path from "node:path";
import { mkdir, open, readFile, rm, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import {
  buildAgentDebugScenario,
  buildActionPayload,
  evaluateWaitConditions,
  evaluateUiNodeAssertions,
  evaluateUiTextAssertions,
  expandAgentDebugScenarioNames,
  findUiTapTarget,
  listAgentDebugScenarios,
  normalizeEndpoint,
  parseWaitConditions,
  summarizeState,
} from "./agent-debug-lib.mjs";
import { getAdbPath, parseArgs, runCapture, runChecked, sleep, waitForPort } from "./script-utils.mjs";

const options = parseArgs(
  {
    Help: false,
    Port: 19090,
    Device: "",
    Scenario: "",
    Endpoint: "/health",
    Action: "",
    WaitFor: "",
    ThenAction: "",
    ThenWaitFor: "",
    TimeoutMs: 30000,
    IntervalMs: 800,
    BridgeDisconnectLimit: 8,
    UiAssertTimeoutMs: 5000,
    UiAssertIntervalMs: 350,
    UiDumpRetries: 3,
    TapText: "",
    TapDesc: "",
    TapTextIfPresent: "",
    TapDescIfPresent: "",
    TapIndex: 0,
    TapTimeoutMs: 5000,
    TapIntervalMs: 350,
    AfterTapWaitMs: 500,
    AfterTapWaitFor: "",
    RestartApp: false,
    AfterRestartWaitFor: "",
    Swipe: "",
    SwipeSteps: 24,
    AfterSwipeWaitMs: 500,
    AfterSwipeWaitFor: "",
    Output: "",
    Bundle: "",
    Screenshot: "",
    UiDump: "",
    RequireText: "",
    ForbidText: "",
    RequireEnabledDesc: "",
    RequireDisabledDesc: "",
    Text: "",
    ThreadId: "",
    NumTurns: "",
    RollbackNumTurns: 0,
    Name: "",
    Command: "",
    Cwd: "",
    Model: "",
    ReasoningEffort: "",
    PermissionMode: "",
    Url: "",
    PairToken: "",
    PayloadJson: "",
    PayloadFile: "",
    ArchiveThreadIds: "",
    ArchiveTitlePrefix: "",
    ArchiveTitleContains: "",
    ArchiveLimit: 50,
    HideKeyboard: false,
    EnsureApp: false,
    AppId: "com.codexapp",
    LaunchActivity: ".MainActivity",
    NoForward: false,
  },
  {
    booleanKeys: ["Help", "HideKeyboard", "EnsureApp", "NoForward", "RestartApp"],
    consumeRestKeys: [
      "Text",
      "Name",
      "Command",
      "Cwd",
      "WaitFor",
      "ThenWaitFor",
      "TapText",
      "TapDesc",
      "TapTextIfPresent",
      "TapDescIfPresent",
      "AfterTapWaitFor",
      "AfterRestartWaitFor",
      "Swipe",
      "AfterSwipeWaitFor",
      "RequireText",
      "ForbidText",
      "RequireEnabledDesc",
      "RequireDisabledDesc",
      "ArchiveThreadIds",
      "ArchiveTitlePrefix",
      "ArchiveTitleContains",
    ],
  }
);

if (options.Help) {
  console.log(helpText());
  process.exit(0);
}

const adb = getAdbPath();
const serialArgs = options.Device ? ["-s", options.Device] : [];
const localPort = Number(options.Port) || 19090;
let bundleUiDumpSnapshot = "";

if (!options.NoForward) {
  await runChecked(adb, [...serialArgs, "forward", `tcp:${localPort}`, `tcp:${localPort}`], {
    displayName: "adb forward agent debug bridge",
    stdio: "ignore",
  });
  await waitForPort("127.0.0.1", localPort, 2_000);
}

let ensureAppResult = null;
if (options.EnsureApp) {
  try {
    ensureAppResult = await ensureAppBridgeReady();
  } catch (error) {
    console.log(
      JSON.stringify({
        ok: false,
        error: "ensure_app_failed",
        message: error?.message || String(error),
      })
    );
    process.exit(1);
  }
}

if (String(options.Scenario || "").trim()) {
  const scenarioNames = expandAgentDebugScenarioNames(options.Scenario);
  const scenarioResult = scenarioNames.length > 1
    ? await runScenarioBatch(scenarioNames)
    : scenarioNames.length === 1
      ? await runScenario(scenarioNames[0])
      : { ok: false, error: "empty_scenario", message: "No scenario names were provided." };
  console.log(JSON.stringify(scenarioResult));
  if (!scenarioResult.ok) {
    process.exitCode = 1;
  }
  process.exit();
}

if (String(options.ArchiveThreadIds || options.ArchiveTitlePrefix || options.ArchiveTitleContains).trim()) {
  const cleanupResult = String(options.ArchiveThreadIds || "").trim()
    ? await archiveThreadsByIds()
    : await archiveThreadsByTitle();
  let result = cleanupResult;
  if (String(options.Bundle || "").trim()) {
    const bundle = await saveBundle(options.Bundle, cleanupResult);
    result = {
      ok: cleanupResult.ok && bundle.ok,
      result: cleanupResult,
      bundle,
    };
  }
  console.log(JSON.stringify(result));
  if (!result.ok) {
    process.exitCode = 1;
  }
  process.exit();
}

const first = await requestJson(options.Action ? "/action" : normalizeEndpoint(options.Endpoint), {
  method: options.Action ? "POST" : "GET",
  body: options.Action ? await buildActionPayload(options.Action, options) : "",
});
let result = first.payload;
if (!first.ok) {
  process.exitCode = 1;
}

if (String(options.WaitFor || "").trim()) {
  result = await waitForState(parseWaitConditions(options.WaitFor));
}

if (String(options.ThenAction || "").trim()) {
  const second = await requestJson("/action", {
    method: "POST",
    body: await buildActionPayload(options.ThenAction, { ...options, Action: options.ThenAction }),
  });
  result = {
    ok: result?.ok !== false && second.ok,
    previous: result,
    action: second.payload,
  };
  if (!second.ok) {
    process.exitCode = 1;
  }
  if (String(options.ThenWaitFor || "").trim()) {
    const waited = await waitForState(parseWaitConditions(options.ThenWaitFor));
    result = {
      ok: result?.ok !== false && waited.ok,
      previous: result,
      thenWait: waited,
    };
  }
}

if (options.RestartApp) {
  const restart = await restartAppAndBridgeReady();
  result = {
    ok: result?.ok !== false && restart.ok,
    result,
    restart,
  };
  if (!restart.ok) {
    process.exitCode = 1;
  }
  if (restart.ok && String(options.AfterRestartWaitFor || "").trim()) {
    const afterRestartWait = await waitForState(parseWaitConditions(options.AfterRestartWaitFor));
    result = {
      ok: result?.ok !== false && afterRestartWait.ok,
      result,
      afterRestartWait,
    };
  }
}

if (String(options.TapText || options.TapDesc || options.TapTextIfPresent || options.TapDescIfPresent).trim()) {
  const tap = await waitAndTapUiTarget();
  result = {
    ok: result?.ok !== false && tap.ok,
    result,
    tap,
  };
  if (!tap.ok) {
    process.exitCode = 1;
  }
  if (tap.ok && String(options.AfterTapWaitFor || "").trim()) {
    const afterTapWait = await waitForState(parseWaitConditions(options.AfterTapWaitFor));
    result = {
      ok: result?.ok !== false && afterTapWait.ok,
      result,
      afterTapWait,
    };
  }
}

if (String(options.Swipe || "").trim()) {
  const swipe = await performSwipe();
  result = {
    ok: result?.ok !== false && swipe.ok,
    result,
    swipe,
  };
  if (!swipe.ok) {
    process.exitCode = 1;
  }
  if (swipe.ok && String(options.AfterSwipeWaitFor || "").trim()) {
    const afterSwipeWait = await waitForState(parseWaitConditions(options.AfterSwipeWaitFor));
    result = {
      ok: result?.ok !== false && afterSwipeWait.ok,
      result,
      afterSwipeWait,
    };
  }
}

if (String(options.Screenshot || "").trim()) {
  await hideKeyboardIfRequested();
  await saveScreenshot(options.Screenshot);
}

if (String(options.UiDump || options.RequireText || options.ForbidText || options.RequireEnabledDesc || options.RequireDisabledDesc).trim()) {
  try {
    await hideKeyboardIfRequested();
    const needsUiTextAssertions = String(options.RequireText || options.ForbidText).trim();
    const needsUiNodeAssertions = String(options.RequireEnabledDesc || options.RequireDisabledDesc).trim();
    const needsUiAssertions = needsUiTextAssertions || needsUiNodeAssertions;
    const uiResult = needsUiAssertions ? await waitForUiAssertions() : { uiDump: await captureUiDump() };
    const uiDump = uiResult.uiDump;
    bundleUiDumpSnapshot = uiDump;
    if (String(options.UiDump || "").trim()) {
      await writeTextFile(options.UiDump, uiDump);
    }
    if (needsUiAssertions) {
      const uiAssertions = uiResult.uiAssertions;
      const uiNodeAssertions = uiResult.uiNodeAssertions || { ok: true, enabledDesc: [], disabledDesc: [] };
      result = {
        ok: result?.ok !== false && uiAssertions.ok && uiNodeAssertions.ok,
        result,
        uiAssertions,
        uiNodeAssertions,
      };
      if (!uiAssertions.ok || !uiNodeAssertions.ok) {
        process.exitCode = 1;
      }
    }
  } catch (error) {
    result = {
      ok: false,
      error: "ui_dump_failed",
      message: error?.message || String(error),
      result,
    };
    process.exitCode = 1;
  }
}

if (String(options.Bundle || "").trim()) {
  try {
    const bundle = await saveBundle(options.Bundle, result);
    result = {
      ok: result?.ok !== false && bundle.ok,
      result,
      bundle,
    };
  } catch (error) {
    result = {
      ok: false,
      error: "bundle_failed",
      message: error?.message || String(error),
      result,
    };
    process.exitCode = 1;
  }
}

const text = JSON.stringify(result);
console.log(text);

if (String(options.Output || "").trim()) {
  await writeTextFile(options.Output, `${text}\n`);
}

async function requestJson(endpoint, request) {
  const url = `http://127.0.0.1:${localPort}${endpoint}`;
  let response;
  try {
    response = await fetch(url, {
      method: request.method,
      headers: request.method === "POST" ? { "Content-Type": "application/json" } : undefined,
      body: request.method === "POST" ? request.body : undefined,
    });
  } catch (error) {
    return {
      ok: false,
      payload: {
        ok: false,
        error: "request_failed",
        message: error?.message || String(error),
      },
    };
  }
  const raw = await response.text();
  let payload;
  try {
    payload = JSON.parse(raw);
  } catch {
    payload = { ok: false, error: "bad_response", raw };
  }
  return { ok: response.ok, payload };
}

async function ensureAppBridgeReady() {
  const first = await requestJson("/health", { method: "GET", body: "" });
  if (first.ok && first.payload?.ok !== false) {
    return {
      ok: true,
      alreadyReady: true,
    };
  }

  const appId = appPackageId();
  const component = appLaunchComponent();
  const launch = await runCapture(adb, [...serialArgs, "shell", "am", "start", "-n", component], {
    displayName: "adb launch app for agent debug",
  });
  if (launch.code !== 0) {
    const packagePath = await runCapture(adb, [...serialArgs, "shell", "pm", "path", appId], {
      displayName: "adb check app package",
    });
    const packageMissing = packagePath.code !== 0 || !String(packagePath.stdout || "").trim();
    const detail = String(launch.stderr || launch.stdout || "").trim() || `am start exited ${launch.code}`;
    const message = packageMissing
      ? `app package ${appId} is not installed; run node scripts/dev-run.mjs before remote testing`
      : `launch failed for ${component}: ${detail}`;
    throw new Error(`EnsureApp failed: ${message}`);
  }
  await sleep(1200);

  const deadline = Date.now() + Math.max(5_000, Number(options.TimeoutMs) || 30_000);
  let last = first;
  while (Date.now() <= deadline) {
    last = await requestJson("/health", { method: "GET", body: "" });
    if (last.ok && last.payload?.ok !== false) {
      return {
        ok: true,
        alreadyReady: false,
      };
    }
    await sleep(Math.max(250, Number(options.IntervalMs) || 800));
  }

  const message = last.payload?.message || last.payload?.error || "agent debug bridge did not become ready";
  throw new Error(`EnsureApp failed: ${message}`);
}

async function restartAppAndBridgeReady() {
  const appId = appPackageId();
  const component = appLaunchComponent();
  const stop = await runCapture(adb, [...serialArgs, "shell", "am", "force-stop", appId], {
    displayName: "adb force-stop app for agent debug",
  });
  if (stop.code !== 0) {
    return {
      ok: false,
      error: "force_stop_failed",
      appId,
      exitCode: stop.code,
      stderr: stop.stderr || "",
    };
  }
  await sleep(500);
  const launch = await runCapture(adb, [...serialArgs, "shell", "am", "start", "-n", component], {
    displayName: "adb restart app for agent debug",
  });
  if (launch.code !== 0) {
    return {
      ok: false,
      error: "launch_failed",
      appId,
      component,
      exitCode: launch.code,
      stderr: launch.stderr || launch.stdout || "",
    };
  }
  await sleep(1200);
  const deadline = Date.now() + Math.max(5_000, Number(options.TimeoutMs) || 30_000);
  let last = null;
  while (Date.now() <= deadline) {
    last = await requestJson("/health", { method: "GET", body: "" });
    if (last.ok && last.payload?.ok !== false) {
      return {
        ok: true,
        appId,
        component,
      };
    }
    await sleep(Math.max(250, Number(options.IntervalMs) || 800));
  }
  return {
    ok: false,
    error: "health_timeout",
    appId,
    component,
    last: last?.payload || null,
  };
}

function appPackageId() {
  return String(options.AppId || "com.codexapp").trim();
}

function appLaunchComponent() {
  const appId = appPackageId();
  const launchActivity = String(options.LaunchActivity || ".MainActivity").trim();
  return launchActivity.includes("/")
    ? launchActivity
    : `${appId}/${launchActivity.startsWith(".") ? launchActivity : `.${launchActivity}`}`;
}

async function runScenario(name) {
  let scenario;
  try {
    scenario = buildAgentDebugScenario(name, options);
  } catch (error) {
    return {
      ok: false,
      error: "unknown_scenario",
      message: error?.message || String(error),
      available: listAgentDebugScenarios(),
    };
  }

  const startedAt = Date.now();
  const steps = [];
  const variables = {};
  for (const step of scenario.steps) {
    const stepArgs = scenarioStepArgs(step, variables);
    const startedStepAt = Date.now();
    const capture = await runCapture(process.execPath, [fileURLToPath(import.meta.url), ...stepArgs], {
      displayName: `agent debug scenario ${scenario.name}:${step.name}`,
    });
    const payload = parseJsonOutput(capture.stdout);
    const ok = capture.code === 0 && payload?.ok !== false;
    const bundleEvidence = await readStepBundleEvidence(stepArgs);
    const summary = bundleEvidence.summary || extractPayloadSummary(payload);
    const state = bundleEvidence.state || extractPayloadState(payload);
    captureScenarioVariables(step, { payload, summary, state }, variables);
    steps.push({
      name: step.name,
      ok,
      exitCode: capture.code,
      elapsedMs: Date.now() - startedStepAt,
      args: stepArgs,
      summary: summary || null,
      error: ok ? "" : payload?.error || payload?.message || capture.stderr || "step_failed",
      output: ok ? undefined : String(capture.stdout || "").slice(-2000),
    });
    if (!ok) {
      break;
    }
  }

  const result = {
    ok: steps.every((step) => step.ok),
    scenario: scenario.name,
    description: scenario.description,
    elapsedMs: Date.now() - startedAt,
    steps,
  };

  if (String(options.Bundle || "").trim()) {
    const bundleDir = path.resolve(String(options.Bundle));
    await mkdir(bundleDir, { recursive: true });
    const summaryPath = path.join(bundleDir, "scenario-summary.json");
    await writeTextFile(summaryPath, `${JSON.stringify(result, null, 2)}\n`);
    result.bundle = {
      dir: bundleDir,
      summary: summaryPath,
    };
  }

  return result;
}

async function runScenarioBatch(names) {
  const startedAt = Date.now();
  const rootBundle = String(options.Bundle || "").trim();
  const originalBundle = options.Bundle;
  const scenarios = [];
  for (const name of names) {
    if (rootBundle) {
      options.Bundle = path.join(rootBundle, name);
    }
    const result = await runScenario(name);
    scenarios.push(result);
    options.Bundle = originalBundle;
    if (!result.ok) {
      break;
    }
  }
  options.Bundle = originalBundle;

  const result = {
    ok: scenarios.every((scenario) => scenario.ok) && scenarios.length === names.length,
    scenarioBatch: names,
    elapsedMs: Date.now() - startedAt,
    scenarios,
  };

  if (rootBundle) {
    const bundleDir = path.resolve(rootBundle);
    await mkdir(bundleDir, { recursive: true });
    const summaryPath = path.join(bundleDir, "scenario-batch-summary.json");
    await writeTextFile(summaryPath, `${JSON.stringify(result, null, 2)}\n`);
    result.bundle = {
      dir: bundleDir,
      summary: summaryPath,
    };
  }

  return result;
}

async function readStepBundleEvidence(stepArgs) {
  const bundleIndex = stepArgs.indexOf("-Bundle");
  const bundleDir = bundleIndex >= 0 ? stepArgs[bundleIndex + 1] : "";
  if (!bundleDir) {
    return {};
  }

  const statePath = path.join(bundleDir, "state.json");
  const summaryPath = path.join(bundleDir, "summary.json");
  const [state, summaryPayload] = await Promise.all([
    readJsonFileIfExists(statePath),
    readJsonFileIfExists(summaryPath),
  ]);
  return {
    state,
    summary: summaryPayload?.state || (state ? summarizeState(state) : null),
  };
}

async function readJsonFileIfExists(filePath) {
  try {
    return JSON.parse(await readFile(filePath, "utf8"));
  } catch {
    return null;
  }
}

function scenarioStepArgs(step, variables = {}) {
  const args = ["-NoForward", "-Port", String(localPort)];
  if (options.Device) {
    args.push("-Device", String(options.Device));
  }
  args.push("-TimeoutMs", String(options.TimeoutMs));
  args.push("-IntervalMs", String(options.IntervalMs));
  args.push("-BridgeDisconnectLimit", String(options.BridgeDisconnectLimit));
  args.push("-UiAssertTimeoutMs", String(options.UiAssertTimeoutMs));
  args.push("-UiAssertIntervalMs", String(options.UiAssertIntervalMs));
  args.push("-TapTimeoutMs", String(options.TapTimeoutMs));
  args.push("-TapIntervalMs", String(options.TapIntervalMs));
  args.push("-AfterTapWaitMs", String(options.AfterTapWaitMs));
  args.push("-SwipeSteps", String(options.SwipeSteps));
  args.push("-AfterSwipeWaitMs", String(options.AfterSwipeWaitMs));
  args.push(...step.args.map((arg) => resolveScenarioArg(arg, variables)));
  if (String(options.Bundle || "").trim()) {
    args.push("-Bundle", path.join(String(options.Bundle), step.name));
  }
  return args;
}

function resolveScenarioArg(value, variables) {
  return String(value).replace(/\$\{([A-Za-z0-9_]+)}/g, (_match, name) => String(variables[name] ?? ""));
}

function captureScenarioVariables(step, sources, variables) {
  if (!step.capture || typeof step.capture !== "object") {
    return;
  }
  for (const [name, pathExpression] of Object.entries(step.capture)) {
    const value = readObjectPath(sources, String(pathExpression));
    if (value != null && String(value).trim()) {
      variables[name] = String(value);
    }
  }
}

function extractPayloadSummary(payload) {
  return (
    payload?.summary ||
    payload?.bundle?.result?.summary ||
    payload?.result?.summary ||
    payload?.thenWait?.summary ||
    payload?.result?.thenWait?.summary ||
    null
  );
}

function extractPayloadState(payload) {
  return (
    payload?.state ||
    payload?.bundle?.result?.state ||
    payload?.result?.state ||
    payload?.thenWait?.state ||
    payload?.result?.thenWait?.state ||
    null
  );
}

function readObjectPath(root, pathExpression) {
  let current = root;
  for (const segment of String(pathExpression || "").split(".").filter(Boolean)) {
    current = current?.[segment];
  }
  return current;
}

function parseJsonOutput(stdout) {
  const lines = String(stdout || "")
    .trim()
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  for (let i = lines.length - 1; i >= 0; i -= 1) {
    try {
      return JSON.parse(lines[i]);
    } catch {
      // keep scanning; child tools may print process output before JSON
    }
  }
  return null;
}

async function waitForState(conditions) {
  const startedAt = Date.now();
  const timeoutMs = Number(options.TimeoutMs) || 30_000;
  const intervalMs = Number(options.IntervalMs) || 800;
  const bridgeDisconnectLimit = Math.max(1, Number(options.BridgeDisconnectLimit) || 8);
  let lastState = null;
  let lastEvaluation = null;
  let consecutiveBridgeFailures = 0;

  while (Date.now() - startedAt <= timeoutMs) {
    const response = await requestJson("/state?messageLimit=80&threadLimit=80", { method: "GET", body: "" });
    lastState = response.payload;
    if (!response.ok && response.payload?.error === "request_failed") {
      consecutiveBridgeFailures += 1;
      if (consecutiveBridgeFailures >= bridgeDisconnectLimit) {
        process.exitCode = 1;
        return {
          ok: false,
          error: "bridge_disconnected",
          waitedMs: Date.now() - startedAt,
          consecutiveFailures: consecutiveBridgeFailures,
          waitFor: lastEvaluation?.results || [],
          summary: summarizeState(lastState),
          state: lastState,
        };
      }
      await sleep(intervalMs);
      continue;
    }
    consecutiveBridgeFailures = 0;
    lastEvaluation = evaluateWaitConditions(lastState, conditions);
    if (lastEvaluation.ok) {
      return {
        ok: true,
        waitedMs: Date.now() - startedAt,
        waitFor: lastEvaluation.results,
        summary: summarizeState(lastState),
        state: lastState,
      };
    }
    await sleep(intervalMs);
  }

  process.exitCode = 1;
  return {
    ok: false,
    error: "wait_timeout",
    waitedMs: Date.now() - startedAt,
    waitFor: lastEvaluation?.results || [],
    summary: summarizeState(lastState),
    state: lastState,
  };
}

async function saveScreenshot(filePath) {
  const capture = await runCapture(adb, [...serialArgs, "exec-out", "screencap", "-p"], {
    displayName: "adb screencap",
    encoding: "buffer",
  });
  if (capture.code !== 0) {
    throw new Error(`adb screencap failed with exit code ${capture.code}`);
  }
  await writeBinaryFile(filePath, capture.stdout);
}

async function captureUiDump() {
  return withUiDumpLock(async () => {
    const retries = Math.max(1, Number(options.UiDumpRetries) || 3);
    let lastError = "";
    for (let attempt = 1; attempt <= retries; attempt += 1) {
      const dumpCommand = await runCapture(
        adb,
        [...serialArgs, "shell", "uiautomator", "dump", "/sdcard/codexapp-agent-window.xml"],
        { displayName: "adb uiautomator dump" }
      );
      if (dumpCommand.code === 0) {
        const dump = await runCapture(adb, [...serialArgs, "exec-out", "cat", "/sdcard/codexapp-agent-window.xml"], {
          displayName: "adb pull ui dump",
        });
        if (dump.code === 0 && String(dump.stdout || "").trim()) {
          return dump.stdout;
        }
        lastError = `pull exit=${dump.code} stderr=${dump.stderr || ""}`.trim();
      } else {
        lastError = `dump exit=${dumpCommand.code} stderr=${dumpCommand.stderr || ""}`.trim();
      }
      await sleep(350 * attempt);
    }
    throw new Error(lastError || "uiautomator dump did not return XML");
  });
}

async function waitForUiAssertions() {
  const startedAt = Date.now();
  const timeoutMs = Number(options.UiAssertTimeoutMs) || 5_000;
  const intervalMs = Number(options.UiAssertIntervalMs) || 350;
  let lastDump = "";
  let lastAssertions = null;
  let lastNodeAssertions = null;

  while (Date.now() - startedAt <= timeoutMs) {
    lastDump = await captureUiDump();
    lastAssertions = evaluateUiTextAssertions(lastDump, options.RequireText, options.ForbidText);
    lastNodeAssertions = evaluateUiNodeAssertions(lastDump, {
      requireEnabledDesc: options.RequireEnabledDesc,
      requireDisabledDesc: options.RequireDisabledDesc,
    });
    if (lastAssertions.ok && lastNodeAssertions.ok) {
      return {
        uiDump: lastDump,
        uiAssertions: {
          ...lastAssertions,
          waitedMs: Date.now() - startedAt,
        },
        uiNodeAssertions: lastNodeAssertions,
      };
    }
    await sleep(intervalMs);
  }

  return {
    uiDump: lastDump,
    uiAssertions: {
      ...(lastAssertions || evaluateUiTextAssertions("", options.RequireText, options.ForbidText)),
      waitedMs: Date.now() - startedAt,
      error: "ui_assert_timeout",
    },
    uiNodeAssertions: lastNodeAssertions || evaluateUiNodeAssertions("", {
      requireEnabledDesc: options.RequireEnabledDesc,
      requireDisabledDesc: options.RequireDisabledDesc,
    }),
  };
}

async function waitAndTapUiTarget() {
  const startedAt = Date.now();
  const timeoutMs = Number(options.TapTimeoutMs) || 5_000;
  const intervalMs = Number(options.TapIntervalMs) || 350;
  const optional = String(options.TapTextIfPresent || options.TapDescIfPresent).trim() &&
    !String(options.TapText || options.TapDesc).trim();
  const tapText = optional ? options.TapTextIfPresent : options.TapText;
  const tapDesc = optional ? options.TapDescIfPresent : options.TapDesc;
  let lastTarget = null;

  while (Date.now() - startedAt <= timeoutMs) {
    const uiDump = await captureUiDump();
    lastTarget = findUiTapTarget(uiDump, {
      text: tapText,
      contentDesc: tapDesc,
      index: options.TapIndex,
    });
    if (lastTarget.ok) {
      const tap = await runCapture(adb, [...serialArgs, "shell", "input", "tap", String(lastTarget.x), String(lastTarget.y)], {
        displayName: "adb tap ui target",
      });
      const ok = tap.code === 0;
      const afterTapWaitMs = Number(options.AfterTapWaitMs) || 0;
      if (afterTapWaitMs > 0) {
        await sleep(afterTapWaitMs);
      }
      return {
        ...lastTarget,
        ok,
        waitedMs: Date.now() - startedAt,
        adbExitCode: tap.code,
        stderr: tap.stderr || "",
      };
    }
    await sleep(intervalMs);
  }

  if (optional) {
    return {
      ...(lastTarget || {
        ok: false,
        text: String(tapText || "").trim(),
        contentDesc: String(tapDesc || "").trim(),
        index: Number(options.TapIndex) || 0,
        matchCount: 0,
      }),
      ok: true,
      skipped: true,
      waitedMs: Date.now() - startedAt,
      reason: "tap_target_absent",
    };
  }

  return {
    ...(lastTarget || {
      ok: false,
      text: String(tapText || "").trim(),
      contentDesc: String(tapDesc || "").trim(),
      index: Number(options.TapIndex) || 0,
      matchCount: 0,
    }),
    ok: false,
    waitedMs: Date.now() - startedAt,
    error: "tap_target_timeout",
  };
}

async function archiveThreadsByTitle() {
  const prefix = String(options.ArchiveTitlePrefix || "").trim();
  const contains = String(options.ArchiveTitleContains || "").trim();
  const limit = Math.max(1, Number(options.ArchiveLimit) || 50);
  const stateResponse = await requestJson(`/state?messageLimit=1&threadLimit=${Math.max(limit, 200)}`, {
    method: "GET",
    body: "",
  });
  if (!stateResponse.ok) {
    return {
      ok: false,
      error: "state_failed",
      state: stateResponse.payload,
    };
  }

  const matches = (Array.isArray(stateResponse.payload?.threads) ? stateResponse.payload.threads : [])
    .filter((thread) => thread && !thread.archived)
    .filter((thread) => {
      const title = String(thread.title || "");
      return (prefix && title.startsWith(prefix)) || (contains && title.includes(contains));
    })
    .slice(0, limit);

  const archived = [];
  for (const thread of matches) {
    const response = await requestJson("/action", {
      method: "POST",
      body: JSON.stringify({ action: "archive_thread", threadId: thread.id }),
    });
    archived.push({
      id: thread.id,
      title: thread.title,
      ok: response.ok && response.payload?.ok !== false,
      error: response.ok ? "" : response.payload?.message || response.payload?.error || "archive_failed",
    });
    await sleep(250);
  }

  return {
    ok: archived.every((item) => item.ok),
    prefix,
    contains,
    matched: matches.length,
    archived,
  };
}

async function archiveThreadsByIds() {
  const ids = String(options.ArchiveThreadIds || "")
    .split(",")
    .map((id) => id.trim())
    .filter(Boolean);
  const uniqueIds = [...new Set(ids)];
  if (!uniqueIds.length) {
    return {
      ok: false,
      error: "missing_thread_ids",
    };
  }

  const archived = [];
  for (const id of uniqueIds) {
    const response = await requestJson("/action", {
      method: "POST",
      body: JSON.stringify({ action: "archive_thread", threadId: id }),
    });
    archived.push({
      id,
      ok: response.ok && response.payload?.ok !== false,
      error: response.ok ? "" : response.payload?.message || response.payload?.error || "archive_failed",
    });
    await sleep(250);
  }

  const hidden = await waitForArchivedThreadsHidden(uniqueIds);
  return {
    ok: archived.every((item) => item.ok) && hidden.ok,
    threadIds: uniqueIds,
    archived,
    hidden,
  };
}

async function waitForArchivedThreadsHidden(threadIds) {
  const startedAt = Date.now();
  const timeoutMs = Number(options.TimeoutMs) || 30_000;
  const intervalMs = Number(options.IntervalMs) || 800;
  let visible = [];
  while (Date.now() - startedAt <= timeoutMs) {
    const stateResponse = await requestJson("/state?messageLimit=1&threadLimit=200", {
      method: "GET",
      body: "",
    });
    if (stateResponse.ok) {
      const visibleIds = new Set(
        (Array.isArray(stateResponse.payload?.threads) ? stateResponse.payload.threads : []).map((thread) => thread.id)
      );
      visible = threadIds.filter((id) => visibleIds.has(id));
      if (!visible.length) {
        return {
          ok: true,
          waitedMs: Date.now() - startedAt,
          visible,
        };
      }
    }
    await sleep(intervalMs);
  }
  return {
    ok: false,
    error: "archive_visibility_timeout",
    waitedMs: Date.now() - startedAt,
    visible,
  };
}

async function performSwipe() {
  const direction = String(options.Swipe || "").trim().toLowerCase();
  const size = await deviceScreenSize();
  if (!size.ok) {
    return size;
  }
  const centerX = Math.round(size.width / 2);
  const centerY = Math.round(size.height / 2);
  const horizontalStart = Math.round(size.width * 0.82);
  const horizontalEnd = Math.round(size.width * 0.18);
  const verticalStart = Math.round(size.height * 0.78);
  const verticalEnd = Math.round(size.height * 0.24);
  let fromX = centerX;
  let fromY = centerY;
  let toX = centerX;
  let toY = centerY;
  if (direction === "up") {
    fromY = verticalStart;
    toY = verticalEnd;
  } else if (direction === "down") {
    fromY = verticalEnd;
    toY = verticalStart;
  } else if (direction === "left") {
    fromX = horizontalStart;
    toX = horizontalEnd;
  } else if (direction === "right") {
    fromX = horizontalEnd;
    toX = horizontalStart;
  } else {
    return {
      ok: false,
      error: "invalid_swipe_direction",
      direction,
    };
  }

  const steps = Math.max(1, Number(options.SwipeSteps) || 24);
  const capture = await runCapture(
    adb,
    [...serialArgs, "shell", "input", "swipe", String(fromX), String(fromY), String(toX), String(toY), String(steps * 10)],
    { displayName: "adb swipe" }
  );
  const afterSwipeWaitMs = Number(options.AfterSwipeWaitMs) || 0;
  if (afterSwipeWaitMs > 0) {
    await sleep(afterSwipeWaitMs);
  }
  return {
    ok: capture.code === 0,
    direction,
    fromX,
    fromY,
    toX,
    toY,
    adbExitCode: capture.code,
    stderr: capture.stderr || "",
  };
}

async function deviceScreenSize() {
  const capture = await runCapture(adb, [...serialArgs, "shell", "wm", "size"], {
    displayName: "adb wm size",
  });
  const match = /Physical size:\s*(\d+)x(\d+)/.exec(String(capture.stdout || ""));
  if (capture.code !== 0 || !match) {
    return {
      ok: false,
      error: "screen_size_unavailable",
      adbExitCode: capture.code,
      stdout: String(capture.stdout || "").trim(),
      stderr: capture.stderr || "",
    };
  }
  return {
    ok: true,
    width: Number(match[1]),
    height: Number(match[2]),
  };
}

async function writeTextFile(filePath, content) {
  await mkdir(path.dirname(filePath), { recursive: true });
  await writeFile(filePath, content, "utf8");
}

async function writeBinaryFile(filePath, content) {
  await mkdir(path.dirname(filePath), { recursive: true });
  await writeFile(filePath, content);
}

async function saveBundle(dirPath, result) {
  const bundleDir = path.resolve(String(dirPath));
  await mkdir(bundleDir, { recursive: true });
  await hideKeyboardIfRequested();
  const stateResponse = await requestJson("/state?messageLimit=160&threadLimit=120", { method: "GET", body: "" });
  const state = stateResponse.payload;
  const files = {
    state: path.join(bundleDir, "state.json"),
    screenshot: path.join(bundleDir, "screenshot.png"),
    uiDump: path.join(bundleDir, "ui.xml"),
    summary: path.join(bundleDir, "summary.json"),
  };
  await writeTextFile(files.state, `${JSON.stringify(state, null, 2)}\n`);
  await saveScreenshot(files.screenshot);
  const uiDump = bundleUiDumpSnapshot || await captureUiDump();
  await writeTextFile(files.uiDump, uiDump);
  await writeTextFile(
    files.summary,
    `${JSON.stringify(
      {
        ok: stateResponse.ok,
        createdAt: new Date().toISOString(),
        state: summarizeState(state),
        result,
      },
      null,
      2
    )}\n`
  );
  return {
    ok: stateResponse.ok,
    dir: bundleDir,
    files,
  };
}

async function withUiDumpLock(callback) {
  const lockPath = path.join("tmp", "agent-debug-uiautomator.lock");
  await mkdir(path.dirname(lockPath), { recursive: true });
  const deadline = Date.now() + 10_000;
  let handle = null;
  while (!handle) {
    try {
      handle = await open(lockPath, "wx");
    } catch (error) {
      if (error?.code !== "EEXIST" || Date.now() > deadline) {
        throw new Error(`ui dump lock unavailable: ${error?.message || error}`);
      }
      await sleep(150);
    }
  }

  try {
    return await callback();
  } finally {
    await handle.close();
    await rm(lockPath, { force: true });
  }
}

async function hideKeyboardIfRequested() {
  if (!options.HideKeyboard) {
    return;
  }
  const deadline = Date.now() + 2_000;
  let pressedBack = false;
  await runCapture(adb, [...serialArgs, "shell", "input", "keyevent", "111"], {
    displayName: "adb hide keyboard",
  });
  await sleep(250);
  while (Date.now() <= deadline) {
    try {
      const uiDump = await captureUiDump();
      if (!isSoftKeyboardVisible(uiDump)) {
        return;
      }
    } catch {
      await sleep(150);
      return;
    }
    if (!pressedBack) {
      pressedBack = true;
      await runCapture(adb, [...serialArgs, "shell", "input", "keyevent", "4"], {
        displayName: "adb back hides keyboard",
      });
    }
    await sleep(250);
  }
}

function isSoftKeyboardVisible(uiDump) {
  return /package="[^"]*(?:inputmethod|keyboard)[^"]*"/i.test(String(uiDump || ""));
}

function helpText() {
  return `Android Agent Debug Bridge CLI

Usage:
  node scripts/agent-debug.mjs -Endpoint /health
  node scripts/agent-debug.mjs -Action send_text -Text "hello" -WaitFor "isGenerating=true"
  node scripts/agent-debug.mjs -Scenario smoke -Bundle tmp/agent-debug/smoke

Options:
  -Help                         Show this help.
  -Scenario <name|a,b|all>      Run built-in flow(s): ${listAgentDebugScenarios().join(", ")}.
  -Endpoint <path>              GET bridge endpoint, default /health.
  -Action <name>                POST bridge action.
  -WaitFor "<expr,...>"         Poll /state until expressions pass.
  -BridgeDisconnectLimit <n>    Abort wait after n consecutive bridge request failures.
  -ThenAction <name>            Run a second action after the first wait.
  -RestartApp                   Force-stop and relaunch the Android app, then wait for bridge health.
  -AfterRestartWaitFor <expr>   Poll /state after -RestartApp until expressions pass.
  -TapText/-TapDesc <value>     Tap a UI node by text or content-desc via uiautomator bounds.
  -TapTextIfPresent/-TapDescIfPresent <value>
                                Tap only when a matching UI node exists; absence is not a failure.
  -Swipe up|down|left|right     Swipe the device viewport through adb input.
  -RequireText/-ForbidText      Assert visible UI text/content-desc with retries.
  -RequireEnabledDesc <text>    Assert a content-desc node exists and is enabled.
  -RequireDisabledDesc <text>   Assert a content-desc node exists and is disabled.
  -HideKeyboard                 Dismiss the soft keyboard before screenshot/UI capture.
  -EnsureApp                    Launch ${options.AppId || "com.codexapp"} if /health is unavailable, then wait for bridge.
  -AppId <id>                   Package id for -EnsureApp, default com.codexapp.
  -LaunchActivity <activity>    Activity for -EnsureApp, default .MainActivity.
  -Bundle <dir>                 Save state.json, screenshot.png, ui.xml and summary.json.
  -ArchiveThreadIds <ids>       Archive exact thread ids, comma-separated.
  -ArchiveTitlePrefix <prefix>  Archive visible non-archived threads whose title starts with prefix.
  -ArchiveTitleContains <text>  Archive visible non-archived threads whose title contains text.
  -NoForward                    Skip adb forward when caller already configured it.
`;
}

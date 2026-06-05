import assert from "node:assert/strict";
import test from "node:test";
import { parseArgs } from "./script-utils.mjs";
import {
  buildAgentDebugScenario,
  buildActionFields,
  evaluateUiNodeAssertions,
  evaluateUiTextAssertions,
  evaluateWaitConditions,
  expandAgentDebugScenarioNames,
  findUiTapTarget,
  listAgentDebugScenarios,
  parseWaitConditions,
  summarizeState,
} from "./agent-debug-lib.mjs";

test("parseArgs consumes multi-part release notes until the next known option", () => {
  const parsed = withArgv(
    ["-Version", "0.2.11", "-Notes", "## 本次更新", "- 修复发布脚本", "-VersionCode", "31"],
    () =>
      parseArgs(
        {
          Version: "",
          VersionCode: 0,
          Notes: "",
        },
        { consumeRestKeys: ["Notes"] }
      )
  );

  assert.equal(parsed.Version, "0.2.11");
  assert.equal(parsed.VersionCode, 31);
  assert.equal(parsed.Notes, "## 本次更新\n- 修复发布脚本");
});

test("parseArgs keeps normal single-value parsing strict", () => {
  const parsed = withArgv(["-OutputName", "codexapp.apk"], () =>
    parseArgs({
      OutputName: "",
    })
  );

  assert.equal(parsed.OutputName, "codexapp.apk");
});

test("parseArgs supports consume-rest values passed with equals syntax", () => {
  const parsed = withArgv(["-Notes=## 本次更新", "- 修复 A", "-Version", "0.2.13"], () =>
    parseArgs(
      {
        Version: "",
        Notes: "",
      },
      { consumeRestKeys: ["Notes"] }
    )
  );

  assert.equal(parsed.Notes, "## 本次更新\n- 修复 A");
  assert.equal(parsed.Version, "0.2.13");
});

test("parseArgs does not treat markdown bullets containing option names as options", () => {
  const parsed = withArgv(["-Version", "0.2.13", "-Notes", "- 修复 -Notes 截断", "-VersionCode", "33"], () =>
    parseArgs(
      {
        Version: "",
        VersionCode: 0,
        Notes: "",
      },
      { consumeRestKeys: ["Notes"] }
    )
  );

  assert.equal(parsed.Notes, "- 修复 -Notes 截断");
  assert.equal(parsed.VersionCode, 33);
});

test("parseArgs keeps payload file and payload json separate", () => {
  const parsed = withArgv(["-PayloadJson", "{\"text\":\"hello\"}", "-PayloadFile", "payload.json"], () =>
    parseArgs({
      PayloadJson: "",
      PayloadFile: "",
    })
  );

  assert.equal(parsed.PayloadJson, "{\"text\":\"hello\"}");
  assert.equal(parsed.PayloadFile, "payload.json");
});

test("parseArgs supports chained action wait arguments", () => {
  const parsed = withArgv(
    [
      "-WaitFor",
      "isGenerating=true",
      "-ThenAction",
      "stop",
      "-ThenWaitFor",
      "isGenerating=false",
      "-AfterTapWaitFor",
      "showComposerDetails=true",
    ],
    () =>
    parseArgs(
      {
        WaitFor: "",
        ThenAction: "",
        ThenWaitFor: "",
        AfterTapWaitFor: "",
      },
      { consumeRestKeys: ["WaitFor", "ThenWaitFor", "AfterTapWaitFor"] }
    )
  );

  assert.equal(parsed.WaitFor, "isGenerating=true");
  assert.equal(parsed.ThenAction, "stop");
  assert.equal(parsed.ThenWaitFor, "isGenerating=false");
  assert.equal(parsed.AfterTapWaitFor, "showComposerDetails=true");
});

test("parseArgs supports agent debug help and scenario flags", () => {
  const parsed = withArgv(
    [
      "-Help",
      "-Scenario",
      "smoke",
      "-HideKeyboard",
      "-EnsureApp",
      "-NoForward",
      "-RestartApp",
      "-AfterRestartWaitFor",
      "connectionStatus=connected",
      "-Swipe",
      "up",
      "-ArchiveThreadIds",
      "thread-a,thread-b",
      "-ArchiveTitlePrefix",
      "drawer-a",
      "-AppId",
      "com.codexapp",
    ],
    () =>
    parseArgs(
      {
        Help: false,
        Scenario: "",
        HideKeyboard: false,
        EnsureApp: false,
        NoForward: false,
        RestartApp: false,
        AfterRestartWaitFor: "",
        Swipe: "",
        ArchiveThreadIds: "",
        ArchiveTitlePrefix: "",
        AppId: "com.codexapp",
        BridgeDisconnectLimit: 8,
      },
      { booleanKeys: ["Help", "HideKeyboard", "EnsureApp", "NoForward", "RestartApp"], consumeRestKeys: ["AfterRestartWaitFor"] }
    )
  );

  assert.equal(parsed.Help, true);
  assert.equal(parsed.Scenario, "smoke");
  assert.equal(parsed.HideKeyboard, true);
  assert.equal(parsed.EnsureApp, true);
  assert.equal(parsed.NoForward, true);
  assert.equal(parsed.RestartApp, true);
  assert.equal(parsed.AfterRestartWaitFor, "connectionStatus=connected");
  assert.equal(parsed.Swipe, "up");
  assert.equal(parsed.ArchiveThreadIds, "thread-a,thread-b");
  assert.equal(parsed.ArchiveTitlePrefix, "drawer-a");
  assert.equal(parsed.AppId, "com.codexapp");
  assert.equal(parsed.BridgeDisconnectLimit, 8);
});

test("agent debug payload fields support send text convenience", () => {
  assert.deepEqual(buildActionFields("send_text", { Text: "hello", ThreadId: "", Cwd: "" }), { text: "hello" });
});

test("agent debug payload fields support edit resend convenience", () => {
  assert.deepEqual(
    buildActionFields("edit_and_resend_user_message", {
      Text: "hello",
      ThreadId: "thread-1",
      RollbackNumTurns: 2,
    }),
    { text: "hello", threadId: "thread-1", rollbackNumTurns: 2 }
  );
});

test("agent debug payload fields support thread management actions", () => {
  assert.deepEqual(
    buildActionFields("fork_thread", {
      ThreadId: "thread-1",
      NumTurns: "4",
    }),
    { threadId: "thread-1", numTurns: 4 }
  );
  assert.deepEqual(
    buildActionFields("rename_thread", {
      ThreadId: "thread-1",
      Name: "Agent regression",
    }),
    { threadId: "thread-1", name: "Agent regression" }
  );
  assert.deepEqual(
    buildActionFields("rename_current_thread", {
      Name: "Agent current",
    }),
    { name: "Agent current" }
  );
});

test("agent debug payload fields support slash command and draft options", () => {
  assert.deepEqual(buildActionFields("apply_slash_command", { Command: "compact", Text: "" }), { command: "compact" });
  assert.deepEqual(
    buildActionFields("update_new_thread_draft", {
      Cwd: "D:/Projects/home/codexapp",
      Model: "gpt-5",
      ReasoningEffort: "high",
      PermissionMode: "full-access",
    }),
    {
      cwd: "D:/Projects/home/codexapp",
      model: "gpt-5",
      reasoningEffort: "high",
      permissionMode: "full-access",
    }
  );
});

test("agent debug wait conditions evaluate state paths", () => {
  const conditions = parseWaitConditions("isGenerating=false,messageCount>=2,testSummary.lastAssistantPreview~=done");
  const result = evaluateWaitConditions(
    {
      isGenerating: false,
      messageCount: 3,
      testSummary: { lastAssistantPreview: "all done" },
    },
    conditions
  );

  assert.equal(result.ok, true);
});

test("agent debug wait conditions support alternative branches", () => {
  const conditions = parseWaitConditions(
    "isGenerating=true,testSummary.runningAssistantMessageCount>=1||isGenerating=false,testSummary.finalAssistantMessageCount>=1"
  );

  const finalResult = evaluateWaitConditions(
    {
      isGenerating: false,
      testSummary: { runningAssistantMessageCount: 0, finalAssistantMessageCount: 1 },
    },
    conditions
  );
  const waitingResult = evaluateWaitConditions(
    {
      isGenerating: false,
      testSummary: { runningAssistantMessageCount: 0, finalAssistantMessageCount: 0 },
    },
    conditions
  );

  assert.equal(finalResult.ok, true);
  assert.equal(finalResult.results[0].alternatives.length, 2);
  assert.equal(waitingResult.ok, false);
});

test("agent debug state summary includes draft config fields", () => {
  const summary = summarizeState({
    currentDraft: {
      cwd: "D:/Projects/home",
      model: "gpt-5.5",
      reasoningEffort: "high",
      permissionMode: "full-access",
      sandboxMode: "danger-full-access",
      approvalPolicy: "never",
    },
    testSummary: {
      userMessageCount: 2,
      assistantMessageCount: 3,
      systemMessageCount: 1,
    },
  });

  assert.equal(summary.currentDraftCwd, "D:/Projects/home");
  assert.equal(summary.currentDraftModel, "gpt-5.5");
  assert.equal(summary.currentDraftReasoningEffort, "high");
  assert.equal(summary.currentDraftPermissionMode, "full-access");
  assert.equal(summary.currentDraftSandboxMode, "danger-full-access");
  assert.equal(summary.currentDraftApprovalPolicy, "never");
  assert.equal(summary.userMessageCount, 2);
  assert.equal(summary.assistantMessageCount, 3);
  assert.equal(summary.systemMessageCount, 1);
});

test("agent debug scenario names expand all and comma lists", () => {
  assert.deepEqual(expandAgentDebugScenarioNames("smoke, draft-config"), ["smoke", "draft-config"]);
  assert.deepEqual(expandAgentDebugScenarioNames("smoke,all,smoke"), listAgentDebugScenarios());
  assert.deepEqual(expandAgentDebugScenarioNames(" , "), []);
});

test("agent debug scenarios expose stable step plans", () => {
  assert.deepEqual(listAgentDebugScenarios(), [
    "smoke",
    "composer-tools",
    "send-stop",
    "running-actions-hidden",
    "running-reconnect-finalization",
    "final-basic",
    "markdown-final",
    "final-actions",
    "archive-reconnect-clean",
    "command-process",
    "process-details",
    "drawer-history",
    "reconnect-current",
    "app-restart-current",
    "edit-resend",
    "resend-user-message",
    "user-message-menu",
    "long-thread-scroll",
    "draft-config",
    "thread-management",
  ]);

  const smoke = buildAgentDebugScenario("smoke", { Text: "probe", Now: 1 });
  assert.equal(smoke.name, "smoke");
  assert.equal(smoke.steps.length, 5);
  assert.deepEqual(smoke.steps[3].args, [
    "-Action",
    "set_composer_text",
    "-Text",
    "probe",
    "-WaitFor",
    "composerText~=probe",
  ]);

  const sendStop = buildAgentDebugScenario("send-stop", { Text: "stop probe", TimeoutMs: 120000 });
  assert.equal(sendStop.steps[0].name, "new-thread-draft");
  assert.equal(sendStop.steps[1].args.some((arg) => arg.includes("testSummary.runningAssistantMessageCount>=1")), true);
  assert.equal(sendStop.steps[1].args.some((arg) => arg.includes("testSummary.finalAssistantMessageCount>=1")), true);
  assert.equal(sendStop.steps[1].args.some((arg) => arg.includes("||")), true);
  assert.equal(sendStop.steps[1].args.some((arg) => arg.includes("selectedThreadId!=")), true);
  assert.equal(sendStop.steps[1].args.includes("120000"), true);
  assert.equal(sendStop.steps[2].args.some((arg) => arg.includes("testSummary.userMessageCount=1")), true);
  assert.equal(sendStop.steps[3].name, "stop-ui");
  assert.equal(sendStop.steps[3].args.includes("-HideKeyboard"), true);
  assert.equal(sendStop.steps[4].name, "archive-current");
  assert.equal(sendStop.steps[4].args.some((arg) => arg.includes("isNewThreadDraft=true")), true);

  const defaultSendStop = buildAgentDebugScenario("send-stop", { Now: 1 });
  assert.equal(defaultSendStop.steps[1].args.some((arg) => arg.includes("请不要调用任何工具或命令")), true);

  const cwdScenario = buildAgentDebugScenario("send-stop", { Text: "probe", Cwd: "D:/tmp" });
  assert.deepEqual(cwdScenario.steps[0].args.slice(0, 4), ["-Action", "new_thread", "-Cwd", "D:/tmp"]);

  const runningActionsHidden = buildAgentDebugScenario("running-actions-hidden", { Now: 1, Cwd: "D:/tmp" });
  assert.equal(
    runningActionsHidden.steps.map((step) => step.name).join(","),
    "new-thread-draft,send-running,running-ui,running-state-no-duplicate-user,stop,stopped-ui,archive-current"
  );
  assert.equal(runningActionsHidden.steps[1].args.some((arg) => arg.includes("isGenerating=true")), true);
  assert.equal(runningActionsHidden.steps[2].args.includes("正在思考中"), true);
  assert.equal(runningActionsHidden.steps[2].args.some((arg) => arg.includes("复制文本,从此处分叉")), true);
  assert.equal(runningActionsHidden.steps[2].args.some((arg) => arg.includes("滚到底部")), true);
  assert.equal(runningActionsHidden.steps[3].args.some((arg) => arg.includes("testSummary.userMessageCount=1")), true);
  assert.equal(runningActionsHidden.steps[6].args.some((arg) => arg.includes("isNewThreadDraft=true")), true);

  const runningReconnectFinalization = buildAgentDebugScenario("running-reconnect-finalization", { Now: 3, Cwd: "D:/tmp" });
  assert.equal(
    runningReconnectFinalization.steps.map((step) => step.name).join(","),
    "new-thread-draft,send-shell,approve-running-command,running-ui-before-disconnect,disconnect-while-running,reconnect-running-thread,command-finalized,finalized-ui,archive-current"
  );
  assert.equal(
    runningReconnectFinalization.steps[1].args.includes(
      "! ping -n 25 127.0.0.1 >NUL && echo codexapp-agent-running-reconnect-3"
    ),
    true
  );
  assert.deepEqual(runningReconnectFinalization.steps[1].capture, { runningThreadId: "summary.selectedThreadId" });
  assert.equal(runningReconnectFinalization.steps[2].args.some((arg) => arg.includes("isGenerating=true")), true);
  assert.equal(runningReconnectFinalization.steps[4].args.some((arg) => arg.includes("connectionStatus=disconnected")), true);
  assert.equal(runningReconnectFinalization.steps[4].args.some((arg) => arg.includes("isGenerating=true")), true);
  assert.equal(runningReconnectFinalization.steps[5].args.some((arg) => arg.includes("connectionStatus=connected")), true);
  assert.equal(runningReconnectFinalization.steps[6].args.some((arg) => arg.includes("codexapp-agent-running-reconnect-3")), true);
  assert.equal(runningReconnectFinalization.steps[7].args.includes("-TapDescIfPresent"), true);
  assert.equal(runningReconnectFinalization.steps[7].args.includes("正在思考中"), true);
  assert.equal(runningReconnectFinalization.steps[8].args.some((arg) => arg.includes("isNewThreadDraft=true")), true);

  const finalBasic = buildAgentDebugScenario("final-basic", { Now: 1, Cwd: "D:/tmp" });
  assert.equal(finalBasic.steps[1].args.includes("Reply exactly with this markdown and nothing else: **OK**"), true);
  assert.equal(
    finalBasic.steps.map((step) => step.name).join(","),
    "new-thread-draft,send,final-ui,archive-current"
  );
  assert.deepEqual(finalBasic.steps[2].args, [
    "-Endpoint",
    "/state",
    "-RequireText",
    "复制文本,从此处分叉",
    "-ForbidText",
    "**OK**",
    "-RequireDisabledDesc",
    "输入内容后发送",
  ]);
  assert.equal(finalBasic.steps[3].args.some((arg) => arg.includes("isNewThreadDraft=true")), true);

  const markdownFinal = buildAgentDebugScenario("markdown-final", { Now: 1, Cwd: "D:/tmp" });
  assert.equal(
    markdownFinal.steps.map((step) => step.name).join(","),
    "new-thread-draft,send,markdown-ui,archive-current"
  );
  assert.equal(markdownFinal.steps[1].args.some((arg) => arg.includes("Agent Markdown")), true);
  assert.equal(markdownFinal.steps[1].args.some((arg) => arg.includes("lastAssistantPreview~=val answer")), true);
  assert.equal(markdownFinal.steps[2].args.includes("Agent Markdown,Bold item,inline code,Codex Docs"), true);
  assert.equal(markdownFinal.steps[2].args.includes("# Agent Markdown,**Bold item**,```"), true);
  assert.equal(markdownFinal.steps[2].args.includes("-RequireDisabledDesc"), true);

  const finalActions = buildAgentDebugScenario("final-actions", { Now: 1, Cwd: "D:/tmp" });
  assert.equal(
    finalActions.steps.map((step) => step.name).join(","),
    "new-thread-draft,send,final-ui,tap-copy,tap-fork,confirm-fork,fork-selected,archive-fork-and-original"
  );
  assert.equal(finalActions.steps[1].args.includes("Reply exactly with this markdown and nothing else: **OK ACTIONS agent-debug-1**"), true);
  assert.deepEqual(finalActions.steps[1].capture, { originalThreadId: "summary.selectedThreadId" });
  assert.deepEqual(finalActions.steps[4].args, [
    "-TapDesc",
    "从此处分叉",
    "-AfterTapWaitMs",
    "800",
    "-RequireText",
    "确认分叉",
  ]);
  assert.deepEqual(finalActions.steps[5].args, ["-TapText", "确认分叉", "-AfterTapWaitMs", "2500", "-ForbidText", "确认分叉"]);
  assert.equal(finalActions.steps[6].args.some((arg) => arg.includes("selectedThreadId!=${originalThreadId}")), true);
  assert.deepEqual(finalActions.steps[6].capture, { forkThreadId: "summary.selectedThreadId" });
  assert.deepEqual(finalActions.steps[7].args, ["-ArchiveThreadIds", "${forkThreadId},${originalThreadId}", "-TimeoutMs", "30000"]);

  const archiveReconnectClean = buildAgentDebugScenario("archive-reconnect-clean", { Now: 7, Cwd: "D:/tmp" });
  assert.equal(
    archiveReconnectClean.steps.map((step) => step.name).join(","),
    "new-thread-draft,send,archive-current,new-thread-after-archive,draft-ui-clean"
  );
  assert.equal(archiveReconnectClean.steps[0].args.some((arg) => arg.includes("connectionStatus=connected")), true);
  assert.equal(archiveReconnectClean.steps[1].args.includes("Reply exactly with this text and nothing else: ARCHIVE CLEAN 7"), true);
  assert.equal(archiveReconnectClean.steps[2].args.some((arg) => arg.includes("connectionStatus=connected")), true);
  assert.equal(archiveReconnectClean.steps[2].args.some((arg) => arg.includes("diagnostics.actionStatus!=failed")), true);
  assert.equal(archiveReconnectClean.steps[3].args.some((arg) => arg.includes("diagnostics.actionStatus!=failed")), true);
  assert.equal(archiveReconnectClean.steps[4].args.includes("连接异常,session "), true);

  const commandProcess = buildAgentDebugScenario("command-process", { Now: 1, Cwd: "D:/tmp" });
  assert.equal(
    commandProcess.steps.map((step) => step.name).join(","),
    "new-thread-draft,send-shell,approve,command-finished,command-ui,command-detail-ui,archive-current"
  );
  assert.equal(commandProcess.steps[1].args.includes("! echo codexapp-agent-command"), true);
  assert.equal(commandProcess.steps[3].args.some((arg) => arg.includes("codexapp-agent-command")), true);
  assert.deepEqual(commandProcess.steps[4].args.slice(0, 2), ["-TapDescIfPresent", "展开处理过程"]);
  assert.equal(commandProcess.steps[4].args.includes("-HideKeyboard"), true);
  assert.equal(commandProcess.steps[5].args.includes("-HideKeyboard"), true);
  assert.equal(commandProcess.steps[5].args.includes("-TapDesc"), true);
  assert.equal(commandProcess.steps[6].args.some((arg) => arg.includes("isNewThreadDraft=true")), true);

  const processDetails = buildAgentDebugScenario("process-details", { Now: 1, Cwd: "D:/tmp" });
  assert.equal(
    processDetails.steps.map((step) => step.name).join(","),
    "new-thread-draft,send,approve,finished,final-ui-collapsed-process,expand-processed,expand-command,expand-command-output,archive-current"
  );
  assert.equal(processDetails.steps[1].args.some((arg) => arg.includes("Write-Output")), true);
  assert.equal(processDetails.steps[3].args.some((arg) => arg.includes("commandOutputBlockCount>=1")), true);
  assert.equal(processDetails.steps[4].args.includes("**PROCESS DONE**,\u9000\u51fa\u7801,codexapp-agent-process"), true);
  assert.deepEqual(processDetails.steps[5].args.slice(0, 2), ["-TapDesc", "展开处理过程"]);
  assert.equal(processDetails.steps[6].args.includes("-TapDescIfPresent"), true);
  assert.equal(processDetails.steps[6].args.includes("展开命令详情"), true);
  assert.equal(processDetails.steps[7].args.includes("展开代码块"), true);
  assert.equal(processDetails.steps[7].args.includes("-TapDescIfPresent"), true);
  assert.equal(processDetails.steps[7].args.includes("codexapp-agent-process"), true);
  assert.equal(processDetails.steps[8].args.some((arg) => arg.includes("isNewThreadDraft=true")), true);

  const drawerHistory = buildAgentDebugScenario("drawer-history", { Now: 9, Cwd: "D:/tmp" });
  assert.equal(
    drawerHistory.steps.map((step) => step.name).join(","),
    "first-new-thread,first-send,first-rename,second-new-thread,second-send,second-rename,open-drawer,select-first-from-drawer,archive-first,open-drawer-again,select-second-from-drawer,archive-second"
  );
  assert.equal(drawerHistory.steps[2].args.includes("drawer-a-9"), true);
  assert.equal(drawerHistory.steps[5].args.includes("drawer-b-9"), true);
  assert.equal(drawerHistory.steps[6].args.includes("打开抽屉"), true);
  assert.equal(drawerHistory.steps[7].args.some((arg) => arg.includes("selectedThreadTitle~=drawer-a-9")), true);
  assert.equal(drawerHistory.steps[11].args.some((arg) => arg.includes("isNewThreadDraft=true")), true);

  const reconnectCurrent = buildAgentDebugScenario("reconnect-current", { Now: 11, Cwd: "D:/tmp" });
  assert.equal(
    reconnectCurrent.steps.map((step) => step.name).join(","),
    "new-thread-draft,send,rename-current,disconnect,reconnect,reconnected-ui,archive-current"
  );
  assert.equal(reconnectCurrent.steps[2].args.includes("reconnect-11"), true);
  assert.equal(reconnectCurrent.steps[3].args.includes("disconnect_gateway"), true);
  assert.equal(reconnectCurrent.steps[4].args.includes("reconnect_gateway"), true);
  assert.equal(reconnectCurrent.steps[4].args.some((arg) => arg.includes("selectedThreadTitle~=reconnect-11")), true);
  assert.equal(reconnectCurrent.steps[5].args.includes("**RECONNECT OK**"), true);

  const appRestartCurrent = buildAgentDebugScenario("app-restart-current", { Now: 12, Cwd: "D:/tmp" });
  assert.equal(
    appRestartCurrent.steps.map((step) => step.name).join(","),
    "new-thread-draft,send,rename-current,restart-app,restarted-ui,archive-current"
  );
  assert.equal(appRestartCurrent.steps[1].args.includes("Reply exactly with this markdown and nothing else: **APP RESTART OK 12**"), true);
  assert.deepEqual(appRestartCurrent.steps[1].capture, { restartThreadId: "summary.selectedThreadId" });
  assert.equal(appRestartCurrent.steps[2].args.includes("app-restart-12"), true);
  assert.equal(appRestartCurrent.steps[3].args.includes("-RestartApp"), true);
  assert.equal(appRestartCurrent.steps[3].args.includes("-AfterRestartWaitFor"), true);
  assert.equal(appRestartCurrent.steps[3].args.some((arg) => arg.includes("selectedThreadId=${restartThreadId}")), true);
  assert.equal(appRestartCurrent.steps[4].args.includes("app-restart-12,APP RESTART OK 12,复制文本,从此处分叉"), true);
  assert.equal(appRestartCurrent.steps[5].args.some((arg) => arg.includes("isNewThreadDraft=true")), true);

  const editResend = buildAgentDebugScenario("edit-resend", { Now: 13, Cwd: "D:/tmp" });
  assert.equal(
    editResend.steps.map((step) => step.name).join(","),
    "new-thread-draft,send-before,rename-current,enter-edit-resend,send-edited,edited-ui,archive-current"
  );
  assert.equal(editResend.steps[2].args.includes("edit-resend-13"), true);
  assert.equal(editResend.steps[3].args.includes("edit_and_resend_user_message"), true);
  assert.equal(editResend.steps[3].args.some((arg) => arg.includes("pendingEditResend.rollbackNumTurns=1")), true);
  assert.equal(editResend.steps[4].args.some((arg) => arg.includes("testSummary.lastUserPreview~=EDIT AFTER 13")), true);
  assert.equal(editResend.steps[5].args.includes("EDIT AFTER 13"), true);
  assert.equal(editResend.steps[5].args.includes("EDIT BEFORE 13"), true);

  const resendUserMessage = buildAgentDebugScenario("resend-user-message", { Now: 15, Cwd: "D:/tmp" });
  assert.equal(
    resendUserMessage.steps.map((step) => step.name).join(","),
    "new-thread-draft,send-before,rename-current,resend,resent-ui,archive-current"
  );
  assert.equal(resendUserMessage.steps[2].args.includes("resend-user-15"), true);
  assert.equal(resendUserMessage.steps[3].args.includes("resend_user_message"), true);
  assert.equal(resendUserMessage.steps[3].args.some((arg) => arg.includes("testSummary.userMessageCount=1")), true);
  assert.equal(resendUserMessage.steps[3].args.some((arg) => arg.includes("testSummary.lastAssistantPreview~=RESEND AFTER 15")), true);
  assert.equal(resendUserMessage.steps[4].args.includes("RESEND BEFORE 15"), true);

  const userMessageMenu = buildAgentDebugScenario("user-message-menu", { Now: 16, Cwd: "D:/tmp" });
  assert.equal(
    userMessageMenu.steps.map((step) => step.name).join(","),
    "new-thread-draft,send-original,rename-current,open-user-menu-for-edit,tap-edit-resend,append-edit,send-edited,open-user-menu-for-resend,tap-resend,archive-current"
  );
  assert.equal(userMessageMenu.steps[2].args.includes("user-menu-16"), true);
  assert.equal(userMessageMenu.steps[3].args.includes("\u7528\u6237\u6d88\u606f\u64cd\u4f5c"), true);
  assert.equal(userMessageMenu.steps[4].args.includes("\u7f16\u8f91\u540e\u91cd\u53d1"), true);
  assert.equal(userMessageMenu.steps[5].args.some((arg) => arg.includes("MENU EDITED 16")), true);
  assert.equal(userMessageMenu.steps[8].args.includes("\u91cd\u53d1"), true);
  assert.equal(userMessageMenu.steps[8].args.includes("-TapIndex"), true);
  assert.equal(userMessageMenu.steps[8].args.includes("1"), true);

  const longThreadScroll = buildAgentDebugScenario("long-thread-scroll", { Now: 18, Cwd: "D:/tmp" });
  assert.equal(
    longThreadScroll.steps.map((step) => step.name).join(","),
    "new-thread-draft,send-a,rename-current,send-b,send-c,swipe-to-older,swipe-to-latest,archive-current"
  );
  assert.equal(longThreadScroll.steps[2].args.includes("scroll-18"), true);
  assert.equal(longThreadScroll.steps[3].args.some((arg) => arg.includes("testSummary.userMessageCount=2")), true);
  assert.equal(longThreadScroll.steps[4].args.some((arg) => arg.includes("testSummary.finalAssistantMessageCount=3")), true);
  assert.deepEqual(longThreadScroll.steps[5].args, [
    "-HideKeyboard",
    "-Swipe",
    "down",
    "-AfterSwipeWaitMs",
    "1000",
    "-RequireText",
    "SCROLL A 18",
  ]);
  assert.deepEqual(longThreadScroll.steps[6].args, [
    "-HideKeyboard",
    "-Swipe",
    "up",
    "-AfterSwipeWaitMs",
    "1000",
    "-RequireText",
    "SCROLL C 18",
  ]);

  const draftConfig = buildAgentDebugScenario("draft-config", { Now: 17, Cwd: "D:/tmp" });
  assert.equal(
    draftConfig.steps.map((step) => step.name).join(","),
    "new-thread-draft,switch-default-permission,switch-full-access"
  );
  assert.equal(draftConfig.steps[0].args.some((arg) => arg.includes("currentDraft.permissionMode=full-access")), true);
  assert.equal(draftConfig.steps[0].args.some((arg) => arg.includes("currentDraft.sandboxMode=danger-full-access")), true);
  assert.equal(draftConfig.steps[1].args.includes("default"), true);
  assert.equal(draftConfig.steps[1].args.some((arg) => arg.includes("currentDraft.sandboxMode=workspace-write")), true);
  assert.equal(draftConfig.steps[2].args.includes("full-access"), true);
  assert.equal(draftConfig.steps[2].args.some((arg) => arg.includes("currentDraft.approvalPolicy=never")), true);

  const threadManagement = buildAgentDebugScenario("thread-management", { Now: 7, Cwd: "D:/tmp" });
  assert.equal(
    threadManagement.steps.map((step) => step.name).join(","),
    "new-thread-draft,send,rename-current,renamed-ui,archive-current"
  );
  assert.equal(threadManagement.steps[2].args.includes("rename_current_thread"), true);
  assert.equal(threadManagement.steps[2].args.some((arg) => arg.includes("selectedThreadTitle~=")), true);
  assert.equal(threadManagement.steps[3].args.includes("agent-debug-thread-7,THREAD OK"), true);
  assert.equal(threadManagement.steps[3].args.includes("**THREAD OK**"), true);
  assert.equal(threadManagement.steps[4].args.some((arg) => arg.includes("isNewThreadDraft=true")), true);
});

test("agent debug ui text assertions read text and content descriptions", () => {
  const result = evaluateUiTextAssertions(
    '<node text="新对话" content-desc="停止生成" /><node text="回复 Codex" />',
    "新对话,停止生成",
    "错误"
  );

  assert.equal(result.ok, true);
});

test("agent debug ui node assertions verify content description enabled state", () => {
  const result = evaluateUiNodeAssertions(
    '<node text="" content-desc="输入内容后发送" enabled="false" bounds="[0,0][10,10]" />' +
      '<node text="" content-desc="发送消息" enabled="true" bounds="[10,0][20,10]" />',
    {
      requireDisabledDesc: "输入内容后发送",
      requireEnabledDesc: "发送消息",
    }
  );

  assert.equal(result.ok, true);
  assert.equal(result.disabledDesc[0].matchCount, 1);
  assert.equal(result.enabledDesc[0].matchCount, 1);
});

test("agent debug finds ui tap target coordinates", () => {
  const result = findUiTapTarget(
    '<node text="已处理 3s" content-desc="" bounds="[20,100][180,140]" />' +
      '<node text="" content-desc="复制文本" bounds="[300,700][360,760]" />',
    { text: "已处理" }
  );

  assert.equal(result.ok, true);
  assert.equal(result.x, 100);
  assert.equal(result.y, 120);
  assert.equal(result.matchCount, 1);
});

test("agent debug finds ui tap target by content description", () => {
  const result = findUiTapTarget(
    '<node text="已处理 3s" content-desc="" bounds="[20,100][180,140]" />' +
      '<node text="" content-desc="复制文本" bounds="[300,700][360,760]" />',
    { contentDesc: "复制" }
  );

  assert.equal(result.ok, true);
  assert.equal(result.x, 330);
  assert.equal(result.y, 730);
});

function withArgv(args, callback) {
  const originalArgv = process.argv;
  process.argv = ["node", "script", ...args];
  try {
    return callback();
  } finally {
    process.argv = originalArgv;
  }
}

export function normalizeEndpoint(value) {
  const endpoint = String(value || "/health").trim();
  return endpoint.startsWith("/") ? endpoint : `/${endpoint}`;
}

export async function buildActionPayload(action, options) {
  const trimmedPayload = String(options.PayloadJson || "").trim();
  if (!trimmedPayload) {
    const trimmedFile = String(options.PayloadFile || "").trim();
    if (!trimmedFile) {
      return JSON.stringify({ action, ...buildActionFields(action, options) });
    }
    const fs = await import("node:fs/promises");
    const fileText = await fs.readFile(trimmedFile, "utf8");
    const parsedFromFile = JSON.parse(fileText);
    return JSON.stringify({ action, ...parsedFromFile });
  }
  const parsed = JSON.parse(trimmedPayload);
  return JSON.stringify({ action, ...parsed });
}

export function buildActionFields(action, options) {
  const normalized = String(action || "").trim().toLowerCase();
  const fields = {};
  if (
    (normalized === "set_composer_text" ||
      normalized === "insert_composer_text" ||
      normalized === "send_text" ||
      normalized === "edit_and_resend_user_message" ||
      normalized === "resend_user_message") &&
    String(options.Text || "").trim()
  ) {
    fields.text = String(options.Text);
  }
  if (normalized === "apply_slash_command") {
    if (String(options.Command || "").trim()) {
      fields.command = String(options.Command);
    } else if (String(options.Text || "").trim()) {
      fields.command = String(options.Text);
    }
  }
  if (normalized === "select_thread" && String(options.ThreadId || "").trim()) {
    fields.threadId = String(options.ThreadId);
  }
  if (
    (normalized === "edit_and_resend_user_message" ||
      normalized === "resend_user_message" ||
      normalized === "fork_thread" ||
      normalized === "rename_thread" ||
      normalized === "archive_thread" ||
      normalized === "unarchive_thread") &&
    String(options.ThreadId || "").trim()
  ) {
    fields.threadId = String(options.ThreadId);
  }
  if (normalized === "fork_thread" && String(options.NumTurns || "").trim()) {
    fields.numTurns = Number(options.NumTurns);
  }
  if ((normalized === "rename_thread" || normalized === "rename_current_thread") && String(options.Name || "").trim()) {
    fields.name = String(options.Name);
  }
  if ((normalized === "edit_and_resend_user_message" || normalized === "resend_user_message") && String(options.RollbackNumTurns || "").trim()) {
    fields.rollbackNumTurns = Number(options.RollbackNumTurns);
  }
  if (
    (normalized === "new_thread" ||
      normalized === "update_new_thread_draft" ||
      normalized === "update_current_thread_config") &&
    String(options.Cwd || "").trim()
  ) {
    fields.cwd = String(options.Cwd);
  }
  if ((normalized === "update_new_thread_draft" || normalized === "update_current_thread_config") && String(options.Model || "").trim()) {
    fields.model = String(options.Model);
  }
  if ((normalized === "update_new_thread_draft" || normalized === "update_current_thread_config") && String(options.ReasoningEffort || "").trim()) {
    fields.reasoningEffort = String(options.ReasoningEffort);
  }
  if ((normalized === "update_new_thread_draft" || normalized === "update_current_thread_config") && String(options.PermissionMode || "").trim()) {
    fields.permissionMode = String(options.PermissionMode);
  }
  if (normalized === "connect_gateway") {
    if (String(options.Url || "").trim()) {
      fields.url = String(options.Url);
    }
    if (String(options.PairToken || "").trim()) {
      fields.pairToken = String(options.PairToken);
    }
  }
  return fields;
}

export function parseWaitConditions(input) {
  const expression = String(input || "").trim();
  const alternatives = expression
    .split("||")
    .map((part) => part.trim())
    .filter(Boolean);
  if (alternatives.length > 1) {
    return [
      {
        expression,
        anyOf: alternatives.map((part) => parseWaitConditions(part)),
      },
    ];
  }
  return expression
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean)
    .map(parseCondition);
}

export function evaluateWaitConditions(state, conditions) {
  const results = conditions.map((condition) => {
    if (condition.anyOf) {
      const alternatives = condition.anyOf.map((alternative) => evaluateWaitConditions(state, alternative));
      return {
        expression: condition.expression,
        ok: alternatives.some((alternative) => alternative.ok),
        alternatives,
      };
    }
    const actual = readJsonPath(state, condition.path);
    return {
      expression: condition.expression,
      actual,
      ok: compareValue(actual, condition.operator, condition.expected),
    };
  });
  return {
    ok: results.every((result) => result.ok),
    results,
  };
}

export function summarizeState(state) {
  return {
    selectedThreadId: state?.selectedThreadId || "",
    selectedThreadTitle: state?.selectedThreadTitle || "",
    selectedThreadStatus: state?.selectedThreadStatus || "",
    isGenerating: state?.isGenerating === true,
    isNewThreadDraft: state?.isNewThreadDraft === true,
    showComposerDetails: state?.showComposerDetails === true,
    connectionStatus: state?.connectionStatus || "",
    gatewayUrl: state?.gatewayUrl || "",
    composerText: state?.composerText || "",
    currentDraftCwd: state?.currentDraft?.cwd || "",
    currentDraftModel: state?.currentDraft?.model || "",
    currentDraftReasoningEffort: state?.currentDraft?.reasoningEffort || "",
    currentDraftPermissionMode: state?.currentDraft?.permissionMode || "",
    currentDraftSandboxMode: state?.currentDraft?.sandboxMode || "",
    currentDraftApprovalPolicy: state?.currentDraft?.approvalPolicy || "",
    messageCount: Number(state?.messageCount || 0),
    userMessageCount: Number(state?.testSummary?.userMessageCount || 0),
    assistantMessageCount: Number(state?.testSummary?.assistantMessageCount || 0),
    systemMessageCount: Number(state?.testSummary?.systemMessageCount || 0),
    pendingApproval: state?.pendingApproval || "",
    lastAssistantIsFinal: state?.testSummary?.lastAssistantIsFinal === true,
    runningAssistantMessageCount: Number(state?.testSummary?.runningAssistantMessageCount || 0),
    finalAssistantMessageCount: Number(state?.testSummary?.finalAssistantMessageCount || 0),
    lastAssistantBlockKinds: state?.testSummary?.lastAssistantBlockKinds || [],
    lastAssistantProcessBlockKinds: state?.testSummary?.lastAssistantProcessBlockKinds || [],
    lastAssistantProcessPreview: state?.testSummary?.lastAssistantProcessPreview || "",
    lastAssistantPreview: state?.testSummary?.lastAssistantPreview || "",
    lastUserPreview: state?.testSummary?.lastUserPreview || "",
  };
}

export function listAgentDebugScenarios() {
  return [
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
  ];
}

export function expandAgentDebugScenarioNames(input) {
  const available = listAgentDebugScenarios();
  const expanded = [];
  const seen = new Set();
  for (const part of String(input || "").split(",")) {
    const name = part.trim().toLowerCase();
    if (!name) {
      continue;
    }
    const names = name === "all" ? available : [name];
    for (const candidate of names) {
      if (!seen.has(candidate)) {
        seen.add(candidate);
        expanded.push(candidate);
      }
    }
  }
  return expanded;
}

export function buildAgentDebugScenario(name, options = {}) {
  const normalized = String(name || "").trim().toLowerCase();
  const hasCustomText = String(options.Text || "").trim().length > 0;
  const promptText = hasCustomText ? String(options.Text) : `agent-debug-${Number(options.Now || Date.now())}`;

  if (normalized === "smoke") {
    return {
      name: "smoke",
      description: "Bridge health, state read, composer set/clear.",
      steps: [
        { name: "health", args: ["-Endpoint", "/health"] },
        { name: "state", args: ["-Endpoint", "/state?messageLimit=20&threadLimit=20"] },
        {
          name: "composer-close",
          args: ["-Action", "close_composer_details", "-WaitFor", "showComposerDetails=false"],
        },
        {
          name: "composer-set",
          args: ["-Action", "set_composer_text", "-Text", promptText, "-WaitFor", `composerText~=${promptText}`],
        },
        {
          name: "composer-clear",
          args: ["-Action", "clear_composer", "-WaitFor", "composerText="],
        },
      ],
    };
  }

  if (normalized === "composer-tools") {
    return {
      name: "composer-tools",
      description: "Tap composer tools exactly as a user would and verify state transitions.",
      steps: [
        {
          name: "composer-close",
          args: ["-Action", "close_composer_details", "-WaitFor", "showComposerDetails=false"],
        },
        {
          name: "tap-expand-tools",
          args: ["-TapDesc", "展开输入工具", "-AfterTapWaitFor", "showComposerDetails=true"],
        },
        {
          name: "tap-collapse-tools",
          args: ["-TapDesc", "收起输入工具", "-AfterTapWaitFor", "showComposerDetails=false"],
        },
      ],
    };
  }

  if (normalized === "send-stop") {
    const stopPrompt = hasCustomText
      ? promptText
      : [
          "请不要调用任何工具或命令。",
          "直接输出一篇关于移动端消息流稳定性的长回答，至少 2000 字，分 12 段。",
          "这是自动化停止生成测试，收到后立刻开始输出正文。",
        ].join("\n");
    const runningOrFinishedWait = [
      "selectedThreadId!=,isNewThreadDraft=false,isGenerating=true,testSummary.runningAssistantMessageCount>=1",
      "selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.finalAssistantMessageCount>=1",
    ].join("||");
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    return {
      name: "send-stop",
      description: "Send a long prompt, stop if the turn is still running, then verify idle state.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send",
          args: [
            "-Action",
            "send_text",
            "-Text",
            stopPrompt,
            "-WaitFor",
            runningOrFinishedWait,
            "-TimeoutMs",
            String(options.TimeoutMs || 60_000),
          ],
        },
        {
          name: "stop",
          args: ["-Action", "stop", "-WaitFor", "isGenerating=false,testSummary.userMessageCount=1", "-TimeoutMs", "30000"],
        },
        {
          name: "stop-ui",
          args: ["-HideKeyboard", "-RequireText", "已处理", "-ForbidText", "正在思考中"],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "running-actions-hidden") {
    const runningPrompt = hasCustomText
      ? promptText
      : [
          "Do not use tools or commands.",
          "Write a long plain text answer about mobile assistant streaming stability.",
          "Use at least 1200 words so this automated test can inspect the running UI before stopping.",
        ].join("\n");
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    return {
      name: "running-actions-hidden",
      description: "Verify final-only assistant actions stay hidden while a turn is still running.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send-running",
          args: [
            "-Action",
            "send_text",
            "-Text",
            runningPrompt,
            "-WaitFor",
            "selectedThreadId!=,isNewThreadDraft=false,isGenerating=true,testSummary.runningAssistantMessageCount>=1",
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "running-ui",
          args: [
            "-HideKeyboard",
            "-RequireText",
            "\u6b63\u5728\u601d\u8003\u4e2d",
            "-ForbidText",
            "\u590d\u5236\u6587\u672c,\u4ece\u6b64\u5904\u5206\u53c9,\u6eda\u5230\u5e95\u90e8",
          ],
        },
        {
          name: "running-state-no-duplicate-user",
          args: ["-WaitFor", "testSummary.userMessageCount=1", "-TimeoutMs", "5000"],
        },
        {
          name: "stop",
          args: ["-Action", "stop", "-WaitFor", "isGenerating=false", "-TimeoutMs", "30000"],
        },
        {
          name: "stopped-ui",
          args: ["-HideKeyboard", "-ForbidText", "\u6b63\u5728\u601d\u8003\u4e2d"],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "running-reconnect-finalization") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=,connectionStatus=connected"];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const suffix = Number(options.Now || Date.now());
    const outputNeedle = `codexapp-agent-running-reconnect-${suffix}`;
    const commandText = hasCustomText
      ? promptText
      : `! ping -n 25 127.0.0.1 >NUL && echo ${outputNeedle}`;
    return {
      name: "running-reconnect-finalization",
      description: "Disconnect and reconnect the gateway while a turn is running, then verify it finalizes cleanly.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send-shell",
          args: ["-Action", "send_text", "-Text", commandText, "-WaitFor", "pendingApproval~=shell", "-TimeoutMs", "30000"],
          capture: {
            runningThreadId: "summary.selectedThreadId",
          },
        },
        {
          name: "approve-running-command",
          args: [
            "-Action",
            "approve",
            "-WaitFor",
            "selectedThreadId=${runningThreadId},isGenerating=true,testSummary.commandBlockCount>=1",
            "-TimeoutMs",
            "30000",
          ],
        },
        {
          name: "running-ui-before-disconnect",
          args: [
            "-HideKeyboard",
            "-RequireText",
            "\u6b63\u5728\u601d\u8003\u4e2d",
            "-ForbidText",
            "\u590d\u5236\u6587\u672c,\u4ece\u6b64\u5904\u5206\u53c9",
          ],
        },
        {
          name: "disconnect-while-running",
          args: [
            "-Action",
            "disconnect_gateway",
            "-WaitFor",
            "connectionStatus=disconnected,selectedThreadId=${runningThreadId},isGenerating=true",
            "-TimeoutMs",
            "30000",
          ],
        },
        {
          name: "reconnect-running-thread",
          args: [
            "-Action",
            "reconnect_gateway",
            "-WaitFor",
            "connectionStatus=connected,selectedThreadId=${runningThreadId},isNewThreadDraft=false",
            "-TimeoutMs",
            "30000",
          ],
        },
        {
          name: "command-finalized",
          args: [
            "-WaitFor",
            `selectedThreadId=\${runningThreadId},isGenerating=false,testSummary.commandBlockCount>=1,testSummary.lastAssistantPreview~=${outputNeedle}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "finalized-ui",
          args: [
            "-TapDescIfPresent",
            "\u5c55\u5f00\u5904\u7406\u8fc7\u7a0b",
            "-TapTimeoutMs",
            "1200",
            "-HideKeyboard",
            "-RequireText",
            `\u5df2\u5904\u7406,${outputNeedle}`,
            "-ForbidText",
            "\u6b63\u5728\u601d\u8003\u4e2d",
          ],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "final-basic") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const finalText = hasCustomText ? promptText : "Reply exactly with this markdown and nothing else: **OK**";
    return {
      name: "final-basic",
      description: "Send a short prompt, wait for final markdown, then verify final actions are visible.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send",
          args: [
            "-Action",
            "send_text",
            "-Text",
            finalText,
            "-WaitFor",
            "isGenerating=false,testSummary.finalAssistantMessageCount>=1,testSummary.textBlockCount>=1",
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "final-ui",
          args: ["-Endpoint", "/state", "-RequireText", "复制文本,从此处分叉", "-ForbidText", "**OK**", "-RequireDisabledDesc", "输入内容后发送"],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "markdown-final") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const markdownPrompt = hasCustomText
      ? promptText
      : [
          "Reply in markdown only.",
          "Include a level-one heading with text Agent Markdown.",
          "Include one bullet with bold text Bold item.",
          "Include one bullet with inline code text inline code.",
          "Include one Kotlin fenced code block whose only line is: val answer = \"ok\"",
          "Include one link with visible text Codex Docs to https://openai.com.",
          "Do not add any other text.",
        ].join("\n");
    return {
      name: "markdown-final",
      description: "Verify a complex final markdown answer renders without exposing raw markdown syntax.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send",
          args: [
            "-Action",
            "send_text",
            "-Text",
            markdownPrompt,
            "-WaitFor",
            "selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.textBlockCount>=1,testSummary.lastAssistantPreview~=Agent Markdown,testSummary.lastAssistantPreview~=val answer",
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "markdown-ui",
          args: [
            "-HideKeyboard",
            "-RequireText",
            "Agent Markdown,Bold item,inline code,Codex Docs",
            "-ForbidText",
            "# Agent Markdown,**Bold item**,```",
            "-RequireDisabledDesc",
            "输入内容后发送",
          ],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "final-actions") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const finalNeedle = hasCustomText ? "OK ACTIONS" : `OK ACTIONS ${promptText}`;
    const finalText = hasCustomText ? promptText : `Reply exactly with this markdown and nothing else: **${finalNeedle}**`;
    return {
      name: "final-actions",
      description: "Send a final reply, then tap copy and fork final assistant actions.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send",
          args: [
            "-Action",
            "send_text",
            "-Text",
            finalText,
            "-WaitFor",
            "isGenerating=false,testSummary.finalAssistantMessageCount>=1,testSummary.textBlockCount>=1",
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
          capture: {
            originalThreadId: "summary.selectedThreadId",
          },
        },
        {
          name: "final-ui",
          args: ["-Endpoint", "/state", "-RequireText", "复制文本,从此处分叉", "-ForbidText", `**${finalNeedle}**`, "-RequireDisabledDesc", "输入内容后发送"],
        },
        {
          name: "tap-copy",
          args: ["-TapDesc", "复制文本", "-RequireText", "复制文本"],
        },
        {
          name: "tap-fork",
          args: ["-TapDesc", "从此处分叉", "-AfterTapWaitMs", "800", "-RequireText", "确认分叉"],
        },
        {
          name: "confirm-fork",
          args: ["-TapText", "确认分叉", "-AfterTapWaitMs", "2500", "-ForbidText", "确认分叉"],
        },
        {
          name: "fork-selected",
          args: [
            "-Endpoint",
            "/state",
            "-WaitFor",
            "selectedThreadId!=${originalThreadId},selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.finalAssistantMessageCount>=1",
            "-TimeoutMs",
            "30000",
          ],
          capture: {
            forkThreadId: "summary.selectedThreadId",
          },
        },
        {
          name: "archive-fork-and-original",
          args: ["-ArchiveThreadIds", "${forkThreadId},${originalThreadId}", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "archive-reconnect-clean") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=,connectionStatus=connected"];
    const nextDraftArgs = [
      "-Action",
      "new_thread",
      "-WaitFor",
      "isNewThreadDraft=true,selectedThreadId=,connectionStatus=connected,isGenerating=false,diagnostics.actionStatus!=failed",
      "-TimeoutMs",
      "30000",
    ];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
      nextDraftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const suffix = Number(options.Now || Date.now());
    const archiveNeedle = `ARCHIVE CLEAN ${suffix}`;
    const finalText = hasCustomText ? promptText : `Reply exactly with this text and nothing else: ${archiveNeedle}`;
    return {
      name: "archive-reconnect-clean",
      description: "Archive the selected thread, then immediately enter a new draft without showing stale archived-thread connection errors.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send",
          args: [
            "-Action",
            "send_text",
            "-Text",
            finalText,
            "-WaitFor",
            `selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.finalAssistantMessageCount=1,testSummary.lastAssistantPreview~=${archiveNeedle}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=,connectionStatus=connected,diagnostics.actionStatus!=failed", "-TimeoutMs", "30000"],
        },
        {
          name: "new-thread-after-archive",
          args: nextDraftArgs,
        },
        {
          name: "draft-ui-clean",
          args: ["-HideKeyboard", "-RequireText", "\u4ece\u8fd9\u91cc\u5f00\u59cb,\u5b8c\u5168\u8bbf\u95ee\u6743\u9650", "-ForbidText", "\u8fde\u63a5\u5f02\u5e38,session "],
        },
      ],
    };
  }

  if (normalized === "command-process") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const commandText = hasCustomText ? promptText : "! echo codexapp-agent-command";
    const outputNeedle = commandText.replace(/^!\s*/, "").split(/\s+/).at(-1) || "codexapp-agent-command";
    return {
      name: "command-process",
      description: "Send a shell command prompt, approve it, then verify command process output.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send-shell",
          args: ["-Action", "send_text", "-Text", commandText, "-WaitFor", "pendingApproval~=shell", "-TimeoutMs", "30000"],
        },
        {
          name: "approve",
          args: ["-Action", "approve", "-WaitFor", "testSummary.commandBlockCount>=1", "-TimeoutMs", "30000"],
        },
        {
          name: "command-finished",
          args: [
            "-WaitFor",
            `isGenerating=false,testSummary.commandBlockCount>=1,testSummary.lastAssistantPreview~=${outputNeedle}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "command-ui",
          args: [
            "-TapDescIfPresent",
            "展开处理过程",
            "-TapTimeoutMs",
            "1200",
            "-AfterTapWaitMs",
            "800",
            "-HideKeyboard",
            "-RequireText",
            "已运行",
          ],
        },
        {
          name: "command-detail-ui",
          args: ["-TapDesc", "展开命令详情", "-AfterTapWaitMs", "800", "-HideKeyboard", "-RequireText", `退出码,${outputNeedle}`],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "process-details") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const processNeedle = "codexapp-agent-process";
    const encodedOutputCommand =
      "Write-Output (-join ([char[]](99,111,100,101,120,97,112,112,45,97,103,101,110,116,45,112,114,111,99,101,115,115)))";
    const prompt = hasCustomText
      ? promptText
      : [
          `Run this PowerShell command exactly once: ${encodedOutputCommand}`,
          "After the command finishes, reply exactly with this markdown and nothing else: **PROCESS DONE**",
        ].join("\n");
    return {
      name: "process-details",
      description: "Run a shell command, wait for a final answer, then verify process expand/detail UI.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send",
          args: [
            "-Action",
            "send_text",
            "-Text",
            prompt,
            "-WaitFor",
            "selectedThreadId!=,isNewThreadDraft=false",
            "-TimeoutMs",
            "30000",
          ],
        },
        {
          name: "approve",
          args: ["-Action", "approve", "-WaitFor", "testSummary.commandBlockCount>=1", "-TimeoutMs", "30000"],
        },
        {
          name: "finished",
          args: [
            "-WaitFor",
            "isGenerating=false,testSummary.commandBlockCount>=1,testSummary.commandOutputBlockCount>=1,testSummary.textBlockCount>=1,testSummary.lastAssistantPreview~=PROCESS DONE",
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "final-ui-collapsed-process",
          args: [
            "-HideKeyboard",
            "-RequireText",
            `\u5df2\u5904\u7406,\u590d\u5236\u6587\u672c,\u4ece\u6b64\u5904\u5206\u53c9,PROCESS DONE`,
            "-ForbidText",
            `**PROCESS DONE**,\u9000\u51fa\u7801,${processNeedle}`,
          ],
        },
        {
          name: "expand-processed",
          args: ["-TapDesc", "\u5c55\u5f00\u5904\u7406\u8fc7\u7a0b", "-AfterTapWaitMs", "800", "-HideKeyboard", "-RequireText", "\u5df2\u8fd0\u884c"],
        },
        {
          name: "expand-command",
          args: [
            "-TapDescIfPresent",
            "\u5c55\u5f00\u547d\u4ee4\u8be6\u60c5",
            "-TapTimeoutMs",
            "1200",
            "-AfterTapWaitMs",
            "800",
            "-HideKeyboard",
            "-RequireText",
            "\u9000\u51fa\u7801,\u8f93\u51fa",
          ],
        },
        {
          name: "expand-command-output",
          args: [
            "-TapDescIfPresent",
            "\u5c55\u5f00\u4ee3\u7801\u5757",
            "-TapTimeoutMs",
            "1200",
            "-AfterTapWaitMs",
            "800",
            "-HideKeyboard",
            "-RequireText",
            processNeedle,
          ],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "drawer-history") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const suffix = Number(options.Now || Date.now());
    const firstTitle = `drawer-a-${suffix}`;
    const secondTitle = `drawer-b-${suffix}`;
    const firstPrompt = "Reply exactly with this markdown and nothing else: FIRST DRAWER OK";
    const secondPrompt = "Reply exactly with this markdown and nothing else: SECOND DRAWER OK";
    return {
      name: "drawer-history",
      description: "Create two threads, switch back through the drawer, then archive both test threads.",
      steps: [
        {
          name: "first-new-thread",
          args: draftArgs,
        },
        {
          name: "first-send",
          args: [
            "-Action",
            "send_text",
            "-Text",
            firstPrompt,
            "-WaitFor",
            "selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.lastAssistantPreview~=FIRST DRAWER OK",
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "first-rename",
          args: ["-Action", "rename_current_thread", "-Name", firstTitle, "-WaitFor", `selectedThreadTitle~=${firstTitle}`],
        },
        {
          name: "second-new-thread",
          args: draftArgs,
        },
        {
          name: "second-send",
          args: [
            "-Action",
            "send_text",
            "-Text",
            secondPrompt,
            "-WaitFor",
            "selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.lastAssistantPreview~=SECOND DRAWER OK",
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "second-rename",
          args: ["-Action", "rename_current_thread", "-Name", secondTitle, "-WaitFor", `selectedThreadTitle~=${secondTitle}`],
        },
        {
          name: "open-drawer",
          args: [
            "-TapDesc",
            "\u6253\u5f00\u62bd\u5c49",
            "-AfterTapWaitMs",
            "800",
            "-HideKeyboard",
            "-RequireText",
            `${firstTitle},${secondTitle}`,
          ],
        },
        {
          name: "select-first-from-drawer",
          args: [
            "-TapText",
            firstTitle,
            "-AfterTapWaitMs",
            "1200",
            "-AfterTapWaitFor",
            `selectedThreadTitle~=${firstTitle}`,
            "-HideKeyboard",
            "-RequireText",
            "FIRST DRAWER OK",
          ],
        },
        {
          name: "archive-first",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
        {
          name: "open-drawer-again",
          args: [
            "-TapDesc",
            "\u6253\u5f00\u62bd\u5c49",
            "-AfterTapWaitMs",
            "800",
            "-HideKeyboard",
            "-RequireText",
            secondTitle,
          ],
        },
        {
          name: "select-second-from-drawer",
          args: [
            "-TapText",
            secondTitle,
            "-AfterTapWaitMs",
            "1200",
            "-AfterTapWaitFor",
            `selectedThreadTitle~=${secondTitle}`,
            "-HideKeyboard",
            "-RequireText",
            "SECOND DRAWER OK",
          ],
        },
        {
          name: "archive-second",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "reconnect-current") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const title = `reconnect-${Number(options.Now || Date.now())}`;
    const prompt = "Reply exactly with this markdown and nothing else: RECONNECT OK";
    return {
      name: "reconnect-current",
      description: "Create a thread, disconnect/reconnect the gateway, then verify the selected final thread is restored.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send",
          args: [
            "-Action",
            "send_text",
            "-Text",
            prompt,
            "-WaitFor",
            "selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.lastAssistantPreview~=RECONNECT OK",
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "rename-current",
          args: ["-Action", "rename_current_thread", "-Name", title, "-WaitFor", `selectedThreadTitle~=${title}`],
        },
        {
          name: "disconnect",
          args: ["-Action", "disconnect_gateway", "-WaitFor", "connectionStatus=disconnected", "-TimeoutMs", "30000"],
        },
        {
          name: "reconnect",
          args: [
            "-Action",
            "reconnect_gateway",
            "-WaitFor",
            `connectionStatus=connected,selectedThreadTitle~=${title},isGenerating=false,testSummary.lastAssistantPreview~=RECONNECT OK`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "reconnected-ui",
          args: ["-HideKeyboard", "-RequireText", `${title},RECONNECT OK`, "-ForbidText", "**RECONNECT OK**"],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "app-restart-current") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const suffix = Number(options.Now || Date.now());
    const title = `app-restart-${suffix}`;
    const finalNeedle = `APP RESTART OK ${suffix}`;
    const prompt = hasCustomText ? promptText : `Reply exactly with this markdown and nothing else: **${finalNeedle}**`;
    return {
      name: "app-restart-current",
      description: "Create a final thread, force-restart the Android app, then verify the selected thread and final UI are restored.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send",
          args: [
            "-Action",
            "send_text",
            "-Text",
            prompt,
            "-WaitFor",
            `selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.finalAssistantMessageCount>=1,testSummary.lastAssistantPreview~=${finalNeedle}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
          capture: {
            restartThreadId: "summary.selectedThreadId",
          },
        },
        {
          name: "rename-current",
          args: ["-Action", "rename_current_thread", "-Name", title, "-WaitFor", `selectedThreadTitle~=${title}`],
        },
        {
          name: "restart-app",
          args: [
            "-RestartApp",
            "-AfterRestartWaitFor",
            `connectionStatus=connected,selectedThreadId=\${restartThreadId},selectedThreadTitle~=${title},isGenerating=false,testSummary.lastAssistantPreview~=${finalNeedle}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "restarted-ui",
          args: [
            "-HideKeyboard",
            "-RequireText",
            `${title},${finalNeedle},\u590d\u5236\u6587\u672c,\u4ece\u6b64\u5904\u5206\u53c9`,
            "-ForbidText",
            `**${finalNeedle}**,\u6b63\u5728\u601d\u8003\u4e2d`,
          ],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "edit-resend") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const suffix = Number(options.Now || Date.now());
    const title = `edit-resend-${suffix}`;
    const beforeNeedle = `EDIT BEFORE ${suffix}`;
    const afterNeedle = `EDIT AFTER ${suffix}`;
    const beforePrompt = `Reply exactly with this text and nothing else: ${beforeNeedle}`;
    const afterPrompt = hasCustomText ? promptText : `Reply exactly with this text and nothing else: ${afterNeedle}`;
    return {
      name: "edit-resend",
      description: "Enter edit-and-resend mode, submit the edited prompt, and verify the old turn is replaced.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send-before",
          args: [
            "-Action",
            "send_text",
            "-Text",
            beforePrompt,
            "-WaitFor",
            `selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.userMessageCount=1,testSummary.finalAssistantMessageCount=1,testSummary.lastAssistantPreview~=${beforeNeedle}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "rename-current",
          args: ["-Action", "rename_current_thread", "-Name", title, "-WaitFor", `selectedThreadTitle~=${title}`],
        },
        {
          name: "enter-edit-resend",
          args: [
            "-Action",
            "edit_and_resend_user_message",
            "-Text",
            afterPrompt,
            "-RollbackNumTurns",
            "1",
            "-WaitFor",
            "pendingEditResend.threadId!=,pendingEditResend.rollbackNumTurns=1,composerText~=Reply exactly",
            "-TimeoutMs",
            "30000",
          ],
        },
        {
          name: "send-edited",
          args: [
            "-Action",
            "send",
            "-WaitFor",
            `isGenerating=false,composerText=,pendingEditResend.threadId=,pendingEditResend.rollbackNumTurns=0,testSummary.userMessageCount=1,testSummary.finalAssistantMessageCount=1,testSummary.lastUserPreview~=${afterNeedle},testSummary.lastAssistantPreview~=${afterNeedle}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "edited-ui",
          args: ["-HideKeyboard", "-RequireText", afterNeedle, "-ForbidText", beforeNeedle],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "resend-user-message") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const suffix = Number(options.Now || Date.now());
    const title = `resend-user-${suffix}`;
    const beforeNeedle = `RESEND BEFORE ${suffix}`;
    const afterNeedle = `RESEND AFTER ${suffix}`;
    const beforePrompt = `Reply exactly with this text and nothing else: ${beforeNeedle}`;
    const afterPrompt = hasCustomText ? promptText : `Reply exactly with this text and nothing else: ${afterNeedle}`;
    return {
      name: "resend-user-message",
      description: "Directly resend a user message with rollback and verify the old turn is replaced.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send-before",
          args: [
            "-Action",
            "send_text",
            "-Text",
            beforePrompt,
            "-WaitFor",
            `selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.userMessageCount=1,testSummary.finalAssistantMessageCount=1,testSummary.lastAssistantPreview~=${beforeNeedle}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "rename-current",
          args: ["-Action", "rename_current_thread", "-Name", title, "-WaitFor", `selectedThreadTitle~=${title}`],
        },
        {
          name: "resend",
          args: [
            "-Action",
            "resend_user_message",
            "-Text",
            afterPrompt,
            "-RollbackNumTurns",
            "1",
            "-WaitFor",
            `isGenerating=false,testSummary.userMessageCount=1,testSummary.finalAssistantMessageCount=1,testSummary.lastUserPreview~=${afterNeedle},testSummary.lastAssistantPreview~=${afterNeedle}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "resent-ui",
          args: ["-HideKeyboard", "-RequireText", afterNeedle, "-ForbidText", beforeNeedle],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "user-message-menu") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const suffix = Number(options.Now || Date.now());
    const title = `user-menu-${suffix}`;
    const originalNeedle = `MENU ORIGINAL ${suffix}`;
    const editedNeedle = `MENU EDITED ${suffix}`;
    const originalPrompt = `Reply exactly with this text and nothing else: ${originalNeedle}`;
    const editAppend = hasCustomText
      ? promptText
      : `\nIgnore the previous target. Reply exactly with this text and nothing else: ${editedNeedle}`;
    return {
      name: "user-message-menu",
      description: "Tap the user message menu, use edit-and-resend, then use resend from the menu.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send-original",
          args: [
            "-Action",
            "send_text",
            "-Text",
            originalPrompt,
            "-WaitFor",
            `selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.userMessageCount=1,testSummary.finalAssistantMessageCount=1,testSummary.lastAssistantPreview~=${originalNeedle}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "rename-current",
          args: ["-Action", "rename_current_thread", "-Name", title, "-WaitFor", `selectedThreadTitle~=${title}`],
        },
        {
          name: "open-user-menu-for-edit",
          args: [
            "-TapDesc",
            "\u7528\u6237\u6d88\u606f\u64cd\u4f5c",
            "-AfterTapWaitMs",
            "800",
            "-HideKeyboard",
            "-RequireText",
            "\u7f16\u8f91\u540e\u91cd\u53d1,\u91cd\u53d1",
          ],
        },
        {
          name: "tap-edit-resend",
          args: [
            "-TapText",
            "\u7f16\u8f91\u540e\u91cd\u53d1",
            "-AfterTapWaitMs",
            "800",
            "-AfterTapWaitFor",
            `pendingEditResend.threadId!=,pendingEditResend.rollbackNumTurns=1,composerText~=${originalNeedle}`,
          ],
        },
        {
          name: "append-edit",
          args: [
            "-Action",
            "insert_composer_text",
            "-Text",
            editAppend,
            "-WaitFor",
            `pendingEditResend.threadId!=,composerText~=${editedNeedle}`,
            "-TimeoutMs",
            "30000",
          ],
        },
        {
          name: "send-edited",
          args: [
            "-Action",
            "send",
            "-WaitFor",
            `isGenerating=false,composerText=,pendingEditResend.threadId=,testSummary.userMessageCount=1,testSummary.finalAssistantMessageCount=1,testSummary.lastUserPreview~=${editedNeedle},testSummary.lastAssistantPreview~=${editedNeedle}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "open-user-menu-for-resend",
          args: [
            "-TapDesc",
            "\u7528\u6237\u6d88\u606f\u64cd\u4f5c",
            "-AfterTapWaitMs",
            "800",
            "-HideKeyboard",
            "-RequireText",
            "\u91cd\u53d1",
          ],
        },
        {
          name: "tap-resend",
          args: [
            "-TapText",
            "\u91cd\u53d1",
            "-TapIndex",
            "1",
            "-AfterTapWaitMs",
            "800",
            "-AfterTapWaitFor",
            `isGenerating=false,testSummary.userMessageCount=1,testSummary.finalAssistantMessageCount=1,testSummary.lastAssistantPreview~=${editedNeedle}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "long-thread-scroll") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const suffix = Number(options.Now || Date.now());
    const title = `scroll-${suffix}`;
    const needleA = `SCROLL A ${suffix}`;
    const needleB = `SCROLL B ${suffix}`;
    const needleC = `SCROLL C ${suffix}`;
    const scrollPrompt = (needle) =>
      [
        `Reply with exactly 10 lines.`,
        `Each line must include this marker: ${needle}.`,
        "Do not use tools or commands.",
      ].join("\n");
    return {
      name: "long-thread-scroll",
      description: "Create a taller multi-turn thread and verify swipe navigation shows older and latest messages.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send-a",
          args: [
            "-Action",
            "send_text",
            "-Text",
            scrollPrompt(needleA),
            "-WaitFor",
            `selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.userMessageCount=1,testSummary.finalAssistantMessageCount=1,testSummary.lastAssistantPreview~=${needleA}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "rename-current",
          args: ["-Action", "rename_current_thread", "-Name", title, "-WaitFor", `selectedThreadTitle~=${title}`],
        },
        {
          name: "send-b",
          args: [
            "-Action",
            "send_text",
            "-Text",
            scrollPrompt(needleB),
            "-WaitFor",
            `isGenerating=false,testSummary.userMessageCount=2,testSummary.finalAssistantMessageCount=2,testSummary.lastAssistantPreview~=${needleB}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "send-c",
          args: [
            "-Action",
            "send_text",
            "-Text",
            scrollPrompt(needleC),
            "-WaitFor",
            `isGenerating=false,testSummary.userMessageCount=3,testSummary.finalAssistantMessageCount=3,testSummary.lastAssistantPreview~=${needleC}`,
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "swipe-to-older",
          args: ["-HideKeyboard", "-Swipe", "down", "-AfterSwipeWaitMs", "1000", "-RequireText", needleA],
        },
        {
          name: "swipe-to-latest",
          args: ["-HideKeyboard", "-Swipe", "up", "-AfterSwipeWaitMs", "1000", "-RequireText", needleC],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  if (normalized === "draft-config") {
    const draftArgs = ["-Action", "new_thread"];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    return {
      name: "draft-config",
      description: "Verify new-thread draft permission defaults and permission mode switching.",
      steps: [
        {
          name: "new-thread-draft",
          args: [
            ...draftArgs,
            "-WaitFor",
            "isNewThreadDraft=true,selectedThreadId=,currentDraft.permissionMode=full-access,currentDraft.sandboxMode=danger-full-access,currentDraft.approvalPolicy=never",
          ],
        },
        {
          name: "switch-default-permission",
          args: [
            "-Action",
            "update_new_thread_draft",
            "-PermissionMode",
            "default",
            "-WaitFor",
            "currentDraft.permissionMode=default,currentDraft.sandboxMode=workspace-write",
            "-TimeoutMs",
            "30000",
          ],
        },
        {
          name: "switch-full-access",
          args: [
            "-Action",
            "update_new_thread_draft",
            "-PermissionMode",
            "full-access",
            "-WaitFor",
            "currentDraft.permissionMode=full-access,currentDraft.sandboxMode=danger-full-access,currentDraft.approvalPolicy=never",
            "-TimeoutMs",
            "30000",
          ],
        },
      ],
    };
  }

  if (normalized === "thread-management") {
    const draftArgs = ["-Action", "new_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId="];
    if (String(options.Cwd || "").trim()) {
      draftArgs.splice(2, 0, "-Cwd", String(options.Cwd));
    }
    const renamedTitle = `agent-debug-thread-${Number(options.Now || Date.now())}`;
    const finalText = hasCustomText ? promptText : "Reply exactly with this markdown and nothing else: **THREAD OK**";
    return {
      name: "thread-management",
      description: "Create a test thread, rename the selected thread, then archive it back to draft.",
      steps: [
        {
          name: "new-thread-draft",
          args: draftArgs,
        },
        {
          name: "send",
          args: [
            "-Action",
            "send_text",
            "-Text",
            finalText,
            "-WaitFor",
            "selectedThreadId!=,isNewThreadDraft=false,isGenerating=false,testSummary.finalAssistantMessageCount>=1",
            "-TimeoutMs",
            String(options.TimeoutMs || 120_000),
          ],
        },
        {
          name: "rename-current",
          args: ["-Action", "rename_current_thread", "-Name", renamedTitle, "-WaitFor", `selectedThreadTitle~=${renamedTitle}`],
        },
        {
          name: "renamed-ui",
          args: ["-HideKeyboard", "-RequireText", `${renamedTitle},THREAD OK`, "-ForbidText", "**THREAD OK**"],
        },
        {
          name: "archive-current",
          args: ["-Action", "archive_current_thread", "-WaitFor", "isNewThreadDraft=true,selectedThreadId=", "-TimeoutMs", "30000"],
        },
      ],
    };
  }

  throw new Error(`Unknown agent debug scenario: ${name}. Available: ${listAgentDebugScenarios().join(", ")}`);
}

export function evaluateUiTextAssertions(xml, requireTextInput, forbidTextInput) {
  const visibleText = extractUiText(xml);
  const required = splitTextAssertions(requireTextInput).map((text) => ({
    text,
    ok: visibleText.includes(text),
  }));
  const forbidden = splitTextAssertions(forbidTextInput).map((text) => ({
    text,
    ok: !visibleText.includes(text),
  }));
  return {
    ok: required.every((item) => item.ok) && forbidden.every((item) => item.ok),
    required,
    forbidden,
    textPreview: visibleText.replace(/\s+/g, " ").trim().slice(0, 1000),
  };
}

export function evaluateUiNodeAssertions(xml, { requireEnabledDesc = "", requireDisabledDesc = "" } = {}) {
  const nodes = extractUiNodes(xml);
  const enabledDesc = splitTextAssertions(requireEnabledDesc).map((text) => {
    const matches = nodes.filter((node) => node.contentDesc.includes(text));
    return {
      text,
      matchCount: matches.length,
      ok: matches.some((node) => node.enabled),
    };
  });
  const disabledDesc = splitTextAssertions(requireDisabledDesc).map((text) => {
    const matches = nodes.filter((node) => node.contentDesc.includes(text));
    return {
      text,
      matchCount: matches.length,
      ok: matches.some((node) => !node.enabled),
    };
  });
  return {
    ok: enabledDesc.every((item) => item.ok) && disabledDesc.every((item) => item.ok),
    enabledDesc,
    disabledDesc,
  };
}

export function extractUiText(xml) {
  return [...String(xml || "").matchAll(/\b(?:text|content-desc)="([^"]*)"/g)]
    .map((match) => decodeXmlAttribute(match[1]))
    .filter(Boolean)
    .join("\n");
}

export function extractUiNodes(xml) {
  return [...String(xml || "").matchAll(/<node\b([^>]*)>/g)]
    .map((match) => parseUiNodeAttributes(match[1]))
    .filter((node) => node.bounds != null);
}

export function findUiTapTarget(xml, { text = "", contentDesc = "", index = 0 } = {}) {
  const textNeedle = String(text || "").trim();
  const descNeedle = String(contentDesc || "").trim();
  const matches = extractUiNodes(xml).filter((node) => {
    const textOk = textNeedle ? node.text.includes(textNeedle) : true;
    const descOk = descNeedle ? node.contentDesc.includes(descNeedle) : true;
    return textOk && descOk && (textNeedle || descNeedle);
  });
  const target = matches[Number(index) || 0];
  if (!target) {
    return {
      ok: false,
      text: textNeedle,
      contentDesc: descNeedle,
      index: Number(index) || 0,
      matchCount: matches.length,
    };
  }
  return {
    ok: true,
    text: target.text,
    contentDesc: target.contentDesc,
    bounds: target.bounds,
    x: Math.round((target.bounds.left + target.bounds.right) / 2),
    y: Math.round((target.bounds.top + target.bounds.bottom) / 2),
    matchCount: matches.length,
  };
}

function parseCondition(expression) {
  const match = /^(.+?)(>=|<=|!=|~=|=|>|<)(.*)$/.exec(expression);
  if (!match) {
    throw new Error(`Invalid wait condition: ${expression}`);
  }
  return {
    expression,
    path: match[1].trim(),
    operator: match[2],
    expected: parseExpected(match[3].trim()),
  };
}

function parseExpected(value) {
  if (value === "true") return true;
  if (value === "false") return false;
  if (value === "null") return null;
  if (/^-?\d+(\.\d+)?$/.test(value)) return Number(value);
  return value;
}

function compareValue(actual, operator, expected) {
  if (operator === "=") return normalizeComparable(actual) === normalizeComparable(expected);
  if (operator === "!=") return normalizeComparable(actual) !== normalizeComparable(expected);
  if (operator === "~=") return String(actual ?? "").includes(String(expected ?? ""));

  const left = Number(actual);
  const right = Number(expected);
  if (!Number.isFinite(left) || !Number.isFinite(right)) return false;
  if (operator === ">") return left > right;
  if (operator === ">=") return left >= right;
  if (operator === "<") return left < right;
  if (operator === "<=") return left <= right;
  return false;
}

function normalizeComparable(value) {
  if (Array.isArray(value)) return value.join(",");
  return String(value ?? "");
}

function readJsonPath(root, path) {
  let current = root;
  for (const segment of splitPath(path)) {
    if (segment === "length") {
      current = Array.isArray(current) || typeof current === "string" ? current.length : undefined;
      continue;
    }
    const arrayMatch = /^(.+)\[(-?\d+)\]$/.exec(segment);
    if (arrayMatch) {
      current = current?.[arrayMatch[1]];
      const index = Number(arrayMatch[2]);
      current = Array.isArray(current) ? current[index < 0 ? current.length + index : index] : undefined;
      continue;
    }
    current = current?.[segment];
  }
  return current;
}

function splitPath(path) {
  return String(path || "")
    .split(".")
    .map((part) => part.trim())
    .filter(Boolean);
}

function splitTextAssertions(input) {
  return String(input || "")
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean);
}

function decodeXmlAttribute(value) {
  return String(value || "")
    .replace(/&quot;/g, "\"")
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}

function parseUiNodeAttributes(rawAttributes) {
  const attributes = {};
  for (const match of String(rawAttributes || "").matchAll(/\b([A-Za-z0-9_-]+)="([^"]*)"/g)) {
    attributes[match[1]] = decodeXmlAttribute(match[2]);
  }
  return {
    text: attributes.text || "",
    contentDesc: attributes["content-desc"] || "",
    enabled: attributes.enabled !== "false",
    bounds: parseBounds(attributes.bounds),
  };
}

function parseBounds(value) {
  const match = /^\[(\d+),(\d+)]\[(\d+),(\d+)]$/.exec(String(value || ""));
  if (!match) return null;
  return {
    left: Number(match[1]),
    top: Number(match[2]),
    right: Number(match[3]),
    bottom: Number(match[4]),
  };
}

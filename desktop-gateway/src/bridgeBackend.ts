import { randomUUID } from "node:crypto";
import { EventEmitter } from "node:events";
import type {
  AppServerApprovalPolicy,
  AppServerSandboxPolicy,
  AppServerThread,
  AppServerThreadItem,
  AppServerThreadStatus,
  JsonRpcNotification,
  JsonRpcServerRequest,
  ThreadResumeResult,
} from "./appServerTypes.js";
import { AppServerClient } from "./appServerClient.js";
import { getThreadStatusType, isThreadActivelyGenerating, resolveDisplayedThreadStatus, resolveLifecycleStatus, resolveThreadSummaryStatus, shouldRetainThreadRuntimeOverlay } from "./threadState.js";
import type {
  ClientSnapshot,
  GatewayChipPayload,
  GatewayBlockPayload,
  GatewayMessagePayload,
  GatewayThreadPayload,
} from "./protocol.js";

const SELF_TEST_EXCLUDE_TITLE = "调研 Codex 安卓壳方案";
const INITIAL_HISTORY_WINDOW = 24;
const HISTORY_WINDOW_STEP = 24;

interface PendingApproval {
  requestId?: string | number;
  kind: "command" | "file" | "permissions" | "gatewayShell";
  text: string;
  command?: string;
}

interface ThreadRuntimeState {
  summary: GatewayThreadPayload;
  thread: AppServerThread | null;
  isSubscribed: boolean;
  lastActivityAtMs: number;
  historyWindow: number;
  currentTurnId: string | null;
  activeAssistantMessageId: string | null;
  liveAssistantItemId: string | null;
  transientOperation: "compact" | "shell" | null;
  pendingApproval: PendingApproval | null;
  stopRequested: boolean;
  isFinalizing: boolean;
  model: string | null;
  instructionSources: string[];
  approvalPolicy: AppServerApprovalPolicy | null;
  approvalsReviewer: string | null;
  sandbox: AppServerSandboxPolicy | null;
  reasoningEffort: string | null;
  snapshot: ClientSnapshot;
}

type ThreadLifecycleStatus = "running" | "idle" | "failed" | "needs_approval";

export class AppServerBridgeBackend {
  private readonly appServer = new AppServerClient();
  private readonly events = new EventEmitter();
  private readonly threads = new Map<string, ThreadRuntimeState>();
  private currentThreadId = "";

  async start(): Promise<void> {
    await this.appServer.start();
    await this.hydrateThreads();
    this.appServer.onNotification((notification) => {
      void this.handleNotification(notification);
    });
    this.appServer.onRequest((request) => {
      this.handleServerRequest(request);
    });
    this.appServer.onExit((event) => {
      this.markAllThreadsFailed(`app-server exited: code=${event.code ?? "null"} signal=${event.signal ?? "null"}`);
    });
  }

  subscribe(listener: () => void): () => void {
    this.events.on("changed", listener);
    return () => this.events.off("changed", listener);
  }

  hasThread(threadId: string): boolean {
    return this.threads.has(threadId);
  }

  getDefaultThreadId(): string {
    const current = this.currentThreadId ? this.threads.get(this.currentThreadId) : null;
    if (current && !current.summary.archived) {
      return this.currentThreadId;
    }
    const active = [...this.threads.values()].find((entry) => !entry.summary.archived)?.summary.id;
    return active || [...this.threads.keys()][0] || "";
  }

  getSnapshot(selectedThreadId?: string): ClientSnapshot {
    const threadId = this.resolveThreadId(selectedThreadId);
    const state = this.threads.get(threadId);
    if (!state) {
      return emptySnapshot();
    }
    state.snapshot.selectedThreadId = threadId;
    return structuredClone(state.snapshot);
  }

  async selectThread(threadId: string): Promise<ClientSnapshot> {
    const resolved = this.resolveThreadId(threadId);
    this.currentThreadId = resolved;
    await this.unsubscribeOtherThreads(resolved);
    await this.resumeThread(resolved);
    this.syncSelectedThread(resolved);
    this.emitChanged();
    return this.getSnapshot(resolved);
  }

  async createThread(): Promise<ClientSnapshot> {
    const cwd = this.threads.get(this.currentThreadId)?.thread?.cwd
      ?? this.threads.get(this.currentThreadId)?.snapshot.cwd
      ?? process.cwd();
    const started = await this.appServer.threadStart(cwd);
    const threadId = started.thread.id;
    this.currentThreadId = threadId;
    const mergedSummaries = dedupeSummaries([
      ...this.buildSummaries(threadId),
      mapThreadToSummary(started.thread),
    ]);
    this.upsertThread(started.thread, mergedSummaries, started);
    this.syncSelectedThread(threadId);
    this.emitChanged();
    return this.getSnapshot(threadId);
  }

  async renameThread(threadId: string, name: string): Promise<ClientSnapshot> {
    const resolved = this.resolveThreadId(threadId);
    const trimmed = name.trim();
    if (!trimmed) {
      return this.getSnapshot(resolved);
    }
    await this.appServer.threadSetName(resolved, trimmed);
    await this.refreshThread(resolved);
    this.syncSelectedThread(resolved);
    this.emitChanged();
    return this.getSnapshot(resolved);
  }

  async archiveThread(threadId: string): Promise<ClientSnapshot> {
    const resolved = this.resolveThreadId(threadId);
    await this.appServer.threadArchive(resolved);
    await this.hydrateThreads();
    const nextThreadId = [...this.threads.values()].find((entry) => !entry.summary.archived)?.summary.id ?? "";
    this.currentThreadId = nextThreadId;
    if (nextThreadId) {
      await this.refreshThread(nextThreadId);
    }
    this.syncSelectedThread(nextThreadId);
    this.emitChanged();
    return this.getSnapshot(nextThreadId);
  }

  async unarchiveThread(threadId: string): Promise<ClientSnapshot> {
    await this.appServer.threadUnarchive(threadId);
    await this.hydrateThreads();
    const resolved = this.resolveThreadId(threadId);
    if (this.hasThread(threadId)) {
      this.currentThreadId = threadId;
      await this.refreshThread(threadId);
      this.syncSelectedThread(threadId);
      this.emitChanged();
      return this.getSnapshot(threadId);
    }
    this.syncSelectedThread(resolved);
    this.emitChanged();
    return this.getSnapshot(resolved);
  }

  async refreshThreads(selectedThreadId?: string): Promise<ClientSnapshot> {
    await this.hydrateThreads();
    const resolved = this.resolveThreadId(selectedThreadId);
    if (resolved) {
      this.currentThreadId = resolved;
      await this.unsubscribeOtherThreads(resolved);
      await this.resumeThread(resolved);
      this.syncSelectedThread(resolved);
    }
    this.emitChanged();
    return this.getSnapshot(resolved);
  }

  async loadOlderMessages(threadId: string): Promise<ClientSnapshot> {
    const resolved = this.resolveThreadId(threadId);
    const state = this.threads.get(resolved);
    if (!state) {
      return this.getSnapshot(resolved);
    }
    state.historyWindow += HISTORY_WINDOW_STEP;
    if (state.thread != null) {
      this.upsertThread(state.thread, null, null, true);
    }
    this.syncSelectedThread(resolved);
    this.emitChanged();
    return this.getSnapshot(resolved);
  }

  async sendPrompt(threadId: string, text: string): Promise<ClientSnapshot> {
    const resolved = this.resolveThreadId(threadId);
    const state = await this.ensureResumed(resolved);
    const trimmed = text.trim();

    appendUserMessage(state, text);
    state.pendingApproval = null;
    state.snapshot.pendingApproval = null;
    state.stopRequested = false;

    if (trimmed === "/compact") {
      state.transientOperation = "compact";
      state.currentTurnId = null;
      state.liveAssistantItemId = null;
      state.activeAssistantMessageId = null;
      state.snapshot.isGenerating = false;
      if (!hasTrailingSystemStatus(state, "已请求压缩上下文")) {
        state.snapshot.messages = state.snapshot.messages.concat(
          systemStatus("已请求压缩上下文")
        );
      }
      void this.appServer.threadCompactStart(resolved).catch((error) => {
          const latest = this.threads.get(resolved);
          if (!latest) {
            return;
          }
          latest.transientOperation = null;
          latest.snapshot.isGenerating = false;
          latest.snapshot.messages = latest.snapshot.messages.concat(
            systemStatus(`上下文压缩失败: ${error instanceof Error ? error.message : "unknown"}`)
          );
          this.updateSummaryStatus(resolved, latest.pendingApproval ? "needs_approval" : "failed");
          this.emitChanged();
        });
      this.emitChanged();
      return this.getSnapshot(resolved);
    }

    if (trimmed.startsWith("!")) {
      state.transientOperation = "shell";
      state.pendingApproval = {
        kind: "gatewayShell",
        text: `允许执行 shell 命令\n${trimmed.substring(1).trim()}`,
        command: trimmed.substring(1).trim(),
      };
      state.snapshot.pendingApproval = state.pendingApproval.text;
      this.updateSummaryStatus(resolved, "needs_approval");
      this.emitChanged();
      return this.getSnapshot(resolved);
    }

    state.transientOperation = null;
    if (state.currentTurnId && state.snapshot.isGenerating) {
      await this.appServer.turnSteer(resolved, state.currentTurnId, text);
      this.emitChanged();
      return this.getSnapshot(resolved);
    }

    const turnId = await this.appServer.turnStart(resolved, text);
    state.currentTurnId = turnId;
    state.snapshot.isGenerating = true;
    ensureActiveAssistantMessage(state, turnId);
    this.updateSummaryStatus(resolved, "running");
    this.emitChanged();
    return this.getSnapshot(resolved);
  }

  async stopTurn(threadId: string): Promise<ClientSnapshot> {
    const resolved = this.resolveThreadId(threadId);
    const state = this.threads.get(resolved);
    if (state?.currentTurnId) {
      await this.appServer.turnInterrupt(resolved, state.currentTurnId);
      state.stopRequested = true;
    }
    return this.getSnapshot(resolved);
  }

  async approveCurrent(threadId: string, allow: boolean): Promise<ClientSnapshot> {
    const resolved = this.resolveThreadId(threadId);
    const state = this.threads.get(resolved);
    const pending = state?.pendingApproval;
    if (!state || !pending) {
      return this.getSnapshot(resolved);
    }

    if (pending.kind === "gatewayShell") {
      state.pendingApproval = null;
      state.snapshot.pendingApproval = null;
      if (!allow) {
        state.snapshot.messages = state.snapshot.messages.concat(systemStatus("审批已拒绝"));
        state.transientOperation = null;
        this.updateSummaryStatus(resolved, "idle");
        this.emitChanged();
        return this.getSnapshot(resolved);
      }

      state.snapshot.messages = state.snapshot.messages.concat(systemStatus("审批已允许"));
      state.snapshot.isGenerating = true;
      this.updateSummaryStatus(resolved, "running");
      this.emitChanged();
      void this.appServer.threadShellCommand(resolved, pending.command ?? "").catch((error) => {
        const latest = this.threads.get(resolved);
        if (!latest) {
          return;
        }
        latest.snapshot.isGenerating = false;
        latest.snapshot.messages = latest.snapshot.messages.concat(
          systemStatus(`shell 命令执行失败: ${error instanceof Error ? error.message : "unknown"}`)
        );
        this.updateSummaryStatus(resolved, latest.pendingApproval ? "needs_approval" : "failed");
        this.emitChanged();
      });
      return this.getSnapshot(resolved);
    }

    this.appServer.respond(pending.requestId!, buildApprovalResponse(pending.kind, allow));
    state.pendingApproval = null;
    state.snapshot.pendingApproval = null;
    state.snapshot.messages = state.snapshot.messages.concat(
      systemStatus(allow ? "审批已允许" : "审批已拒绝")
    );
    this.updateSummaryStatus(resolved, state.snapshot.isGenerating ? "running" : "idle");
    this.emitChanged();
    return this.getSnapshot(resolved);
  }

  private async hydrateThreads(): Promise<void> {
    const activeList = await this.appServer.threadList(false);
    const visibleThreads = activeList.filter((thread) => !isExcludedThread(thread));
    const visibleThreadIds = new Set(visibleThreads.map((thread) => thread.id));
    for (const threadId of [...this.threads.keys()]) {
      if (!visibleThreadIds.has(threadId)) {
        this.threads.delete(threadId);
      }
    }

    const detailedThreads = await this.readThreadDetailsForSummaries(visibleThreads);
    const summaries = buildVisibleThreadSummaries(detailedThreads);

    if (summaries.length === 0) {
      this.currentThreadId = "";
      this.emitChanged();
      return;
    }

    const candidateId =
      this.currentThreadId && summaries.some((thread) => thread.id === this.currentThreadId)
        ? this.currentThreadId
        : summaries[0].id;

    this.currentThreadId = candidateId;
    for (const thread of detailedThreads) {
      this.upsertThread(thread, summaries, null);
    }

    for (const summary of summaries) {
      const existing = this.threads.get(summary.id);
      if (!existing) {
        this.threads.set(summary.id, {
          summary,
          thread: null,
          lastActivityAtMs: summary.updatedAt ?? 0,
          historyWindow: INITIAL_HISTORY_WINDOW,
          currentTurnId: null,
          activeAssistantMessageId: null,
          liveAssistantItemId: null,
          transientOperation: null,
          pendingApproval: null,
          stopRequested: false,
          isFinalizing: false,
          isSubscribed: false,
          model: null,
          instructionSources: [],
          approvalPolicy: null,
          approvalsReviewer: null,
          sandbox: null,
          reasoningEffort: null,
          snapshot: {
            ...emptySnapshot(),
            threads: summaries,
            selectedThreadId: candidateId,
          },
        });
      } else {
        existing.summary = {
          ...summary,
          status: resolveDisplayedThreadStatus(resolveLifecycleStatus(summary.status, existing.pendingApproval != null), {
            isGenerating: existing.snapshot.isGenerating,
            currentTurnId: existing.currentTurnId,
            transientOperation: existing.transientOperation,
            pendingApproval: existing.pendingApproval?.text ?? null,
          }),
        };
        existing.snapshot.threads = summaries;
      }
    }

    this.syncSelectedThread(candidateId);
    this.emitChanged();
  }

  private async readThreadDetailsForSummaries(threads: AppServerThread[]): Promise<AppServerThread[]> {
    const details: AppServerThread[] = [];
    for (const thread of threads) {
      try {
        details.push(await this.appServer.threadRead(thread.id));
      } catch (error) {
        if (!isThreadNotMaterializedError(error)) {
          console.warn(`[gateway] thread/read failed for ${thread.id}: ${error instanceof Error ? error.message : String(error)}`);
        }
        details.push(thread);
      }
    }
    return details;
  }

  private async ensureResumed(threadId: string): Promise<ThreadRuntimeState> {
    await this.resumeThread(threadId);
    const state = this.threads.get(threadId);
    if (state) {
      return state;
    }
    throw new Error(`thread not found after resume: ${threadId}`);
  }

  private async refreshThread(threadId: string): Promise<void> {
    let thread: AppServerThread;
    try {
      thread = await this.appServer.threadRead(threadId);
    } catch (error) {
      if (!isThreadNotMaterializedError(error)) {
        throw error;
      }
      thread = await this.appServer.threadRead(threadId, false);
    }
    const existing = this.threads.get(threadId);
    this.upsertThread(thread, null, null, true);
    const state = this.threads.get(threadId);
    if (state && existing?.isSubscribed) {
      state.isSubscribed = true;
    }
  }

  private async resumeThread(threadId: string): Promise<void> {
    const existing = this.threads.get(threadId);
    if (existing?.isSubscribed) {
      await this.refreshThread(threadId);
      return;
    }
    try {
      const resumed = await this.appServer.threadResume(threadId);
      this.upsertThread(resumed.thread, null, resumed);
      const state = this.threads.get(threadId);
      if (state) {
        state.isSubscribed = true;
      }
    } catch (error) {
      if (!isNoRolloutFoundError(error)) {
        throw error;
      }
      const current = this.threads.get(threadId);
      if (current?.thread != null) {
        current.isSubscribed = true;
        return;
      }
      throw error;
    }
  }

  private async unsubscribeOtherThreads(activeThreadId: string): Promise<void> {
    const staleThreadIds = [...this.threads.values()]
      .filter((entry) => entry.thread != null && entry.thread.id !== activeThreadId && entry.isSubscribed)
      .map((entry) => entry.thread!.id);
    for (const threadId of staleThreadIds) {
      try {
        await this.appServer.threadUnsubscribe(threadId);
      } catch {
        // Ignore unsubscribe failures; the next resume rebinds the stream.
      }
      const state = this.threads.get(threadId);
      if (state) {
        state.isSubscribed = false;
      }
    }
  }

  private async handleNotification(notification: JsonRpcNotification): Promise<void> {
    switch (notification.method) {
      case "thread/status/changed": {
        const { threadId, status } = notification.params as {
          threadId: string;
          status: AppServerThreadStatus;
        };
        const state = this.threads.get(threadId);
        if (!state) {
          return;
        }
        const nextType = getThreadStatusType(status);
        if (nextType === "active") {
          const compactInFlight = state.transientOperation === "compact";
          state.snapshot.isGenerating = !compactInFlight;
          this.updateSummaryStatus(
            threadId,
            compactInFlight ? (state.pendingApproval ? "needs_approval" : "idle") : "running"
          );
          this.emitChanged();
          return;
        }

        if (state.transientOperation === "compact") {
          state.snapshot.isGenerating = false;
          this.updateSummaryStatus(threadId, state.pendingApproval ? "needs_approval" : "idle");
          this.emitChanged();
          return;
        }

        if (state.currentTurnId || state.snapshot.isGenerating || state.stopRequested) {
          await this.finalizeTurnState(threadId);
          return;
        }

        state.snapshot.isGenerating = false;
        this.updateSummaryStatus(threadId, resolveLifecycleStatus(status, state.pendingApproval != null));
        this.emitChanged();
        return;
      }
      case "turn/started": {
        const { threadId, turn } = notification.params as { threadId: string; turn: { id: string; startedAt?: number | null } };
        const state = this.threads.get(threadId);
        if (!state) {
          return;
        }
        state.currentTurnId = turn.id;
        touchThreadActivity(state, turn.startedAt);
        if (state.transientOperation === "compact") {
          return;
        }
        state.transientOperation = null;
        state.snapshot.isGenerating = true;
        ensureActiveAssistantMessage(state, turn.id);
        this.updateSummaryStatus(threadId, "running");
        this.emitChanged();
        return;
      }
      case "turn/completed": {
        const { threadId, turn } = notification.params as { threadId: string; turn: { id: string; status?: string; startedAt?: number | null; completedAt?: number | null } };
        const state = this.threads.get(threadId);
        if (!state) {
          return;
        }
        if (state?.transientOperation === "compact" && turn.status !== "failed") {
          touchThreadActivity(state, turn.completedAt ?? turn.startedAt);
          await this.finalizeCompactState(threadId);
          return;
        }
        touchThreadActivity(state, turn.completedAt ?? turn.startedAt);
        await this.finalizeTurnState(threadId, turn.status);
        return;
      }
      case "item/agentMessage/delta": {
        const { threadId, turnId, itemId, delta } = notification.params as {
          threadId: string;
          turnId: string;
          itemId: string;
          delta: string;
        };
        const state = this.threads.get(threadId);
        if (!state) {
          return;
        }
        if (state.transientOperation === "compact") {
          return;
        }
        state.currentTurnId = turnId;
        state.snapshot.isGenerating = true;
        appendAssistantDelta(state, itemId, delta);
        this.updateSummaryStatus(threadId, "running");
        this.emitChanged();
        return;
      }
      case "item/reasoning/summaryTextDelta": {
        const { threadId, itemId, delta } = notification.params as {
          threadId: string;
          turnId: string;
          itemId: string;
          delta: string;
        };
        const state = this.threads.get(threadId);
        if (!state) {
          return;
        }
        if (state.transientOperation === "compact") {
          return;
        }
        appendOrMergeMessage(
          state,
          itemId,
          "assistant",
          { kind: "reasoning", value: delta },
          true
        );
        this.emitChanged();
        return;
      }
      case "item/commandExecution/outputDelta": {
        const { threadId, itemId, delta } = notification.params as {
          threadId: string;
          turnId: string;
          itemId: string;
          delta: string;
        };
        const state = this.threads.get(threadId);
        if (!state) {
          return;
        }
        if (state.transientOperation === "compact") {
          return;
        }
        replaceOrAppendMessage(state, {
          id: itemId,
          role: "assistant",
          blocks: [
            {
              kind: "commandSummary",
              value: "命令执行中",
            },
            { kind: "commandMeta", value: "命令输出更新中" },
            { kind: "code", language: "shell", value: delta },
          ],
        });
        this.emitChanged();
        return;
      }
      case "item/fileChange/patchUpdated": {
        const { threadId, itemId, changes } = notification.params as {
          threadId: string;
          turnId: string;
          itemId: string;
          changes: Array<{ path?: string; kind?: string; diff?: string | null }>;
        };
        const state = this.threads.get(threadId);
        if (!state) {
          return;
        }
        if (state.transientOperation === "compact") {
          return;
        }
        replaceOrAppendMessage(state, {
          id: itemId,
          role: "assistant",
          blocks: buildFileChangeBlocks(changes, "inProgress"),
        });
        this.emitChanged();
        return;
      }
      case "item/started":
      case "item/completed": {
        const { threadId, item, turnId } = notification.params as {
          threadId: string;
          turnId: string;
          item: AppServerThreadItem;
          startedAtMs?: number;
          completedAtMs?: number;
        };
        const state = this.threads.get(threadId);
        if (!state) {
          return;
        }
        const timestampMs =
          notification.method === "item/started"
            ? (notification.params as { startedAtMs?: number }).startedAtMs
            : (notification.params as { completedAtMs?: number }).completedAtMs;
        touchThreadActivity(state, timestampMs);
        if (state.transientOperation === "compact") {
          state.currentTurnId = turnId;
          if (item.type === "contextCompaction" && notification.method === "item/completed") {
            await this.finalizeCompactState(threadId);
          }
          return;
        }
        state.currentTurnId = turnId;
        mergeThreadItem(state, item, true);
        this.emitChanged();
        return;
      }
      case "serverRequest/resolved": {
        const { threadId } = notification.params as { threadId: string; requestId: string | number };
        const state = this.threads.get(threadId);
        if (!state) {
          return;
        }
        state.pendingApproval = null;
        state.snapshot.pendingApproval = null;
        this.updateSummaryStatus(threadId, state.snapshot.isGenerating ? "running" : "idle");
        this.emitChanged();
        return;
      }
      case "thread/started":
        await this.hydrateThreads();
        return;
      case "thread/archived":
      case "thread/unarchived":
        await this.hydrateThreads();
        return;
      case "thread/compacted": {
        const { threadId } = notification.params as { threadId: string; turnId?: string };
        const state = this.threads.get(threadId);
        if (!state) {
          return;
        }
        state.currentTurnId = null;
        state.liveAssistantItemId = null;
        state.activeAssistantMessageId = null;
        state.snapshot.isGenerating = false;
        pruneCompletedArtifacts(state);
        normalizeCompactMessages(state, true);
        this.updateSummaryStatus(threadId, state.pendingApproval ? "needs_approval" : "idle");
        this.emitChanged();
        return;
      }
      default:
        return;
    }
  }

  private handleServerRequest(request: JsonRpcServerRequest): void {
    switch (request.method) {
      case "item/commandExecution/requestApproval": {
        const params = request.params as {
          threadId: string;
          command?: string | null;
          reason?: string | null;
          cwd?: string | null;
        };
        this.setPendingApproval(params.threadId, {
          requestId: request.id,
          kind: "command",
          text: [params.reason, params.command, params.cwd].filter(Boolean).join("\n") || "命令执行请求审批",
        });
        return;
      }
      case "item/fileChange/requestApproval": {
        const params = request.params as {
          threadId: string;
          reason?: string | null;
          grantRoot?: string | null;
        };
        this.setPendingApproval(params.threadId, {
          requestId: request.id,
          kind: "file",
          text: [params.reason, params.grantRoot].filter(Boolean).join("\n") || "文件改动请求审批",
        });
        return;
      }
      case "item/permissions/requestApproval": {
        const params = request.params as {
          threadId: string;
          reason?: string | null;
          cwd?: string | null;
        };
        this.setPendingApproval(params.threadId, {
          requestId: request.id,
          kind: "permissions",
          text: [params.reason, params.cwd].filter(Boolean).join("\n") || "权限请求审批",
        });
        return;
      }
      default:
        return;
    }
  }

  private upsertThread(
    thread: AppServerThread,
    summaries: GatewayThreadPayload[] | null,
    resume: ThreadResumeResult | null = null,
    preserveLiveMessages = false
  ): void {
    const existing = this.threads.get(thread.id);
    const mergedSummaries = summaries ?? this.buildSummaries(thread.id);
    const allThreadMessages = collectThreadMessages(thread);
    const baseSnapshot = mapThreadToSnapshot(
      thread,
      mergedSummaries,
      thread.id,
      resume ?? toResumeMetadata(existing),
      allThreadMessages,
    );
    const mergedMessages = preserveLiveMessages && existing
      ? mergeSnapshotMessages(allThreadMessages, existing.snapshot.messages)
      : allThreadMessages;
    const historyWindow = existing?.historyWindow ?? INITIAL_HISTORY_WINDOW;
    const resolvedSummaryStatus = resolveThreadSummaryStatus(thread);
    const lastActivityAtMs = Math.max(existing?.lastActivityAtMs ?? 0, getThreadLastActivityAtMs(thread));
    const retainRuntimeOverlay = shouldRetainThreadRuntimeOverlay(thread, existing);
    const runtimeState: ThreadRuntimeState = {
      summary: existing
        ? {
            ...mapThreadToSummary(thread, false, lastActivityAtMs),
            status: resolveDisplayedThreadStatus(resolvedSummaryStatus, {
              isGenerating: retainRuntimeOverlay && existing.snapshot.isGenerating,
              currentTurnId: retainRuntimeOverlay ? existing.currentTurnId : null,
              transientOperation: retainRuntimeOverlay ? existing.transientOperation : null,
              pendingApproval: retainRuntimeOverlay ? existing.pendingApproval?.text ?? null : null,
            }),
          }
        : mapThreadToSummary(thread),
      thread,
      isSubscribed: resume != null || existing?.isSubscribed === true,
      lastActivityAtMs,
      historyWindow: existing?.historyWindow ?? INITIAL_HISTORY_WINDOW,
      currentTurnId: retainRuntimeOverlay ? existing?.currentTurnId ?? null : null,
      activeAssistantMessageId: retainRuntimeOverlay ? existing?.activeAssistantMessageId ?? null : null,
      liveAssistantItemId: retainRuntimeOverlay ? existing?.liveAssistantItemId ?? null : null,
      transientOperation: retainRuntimeOverlay ? existing?.transientOperation ?? null : null,
      pendingApproval: retainRuntimeOverlay ? existing?.pendingApproval ?? null : null,
      stopRequested: retainRuntimeOverlay ? existing?.stopRequested ?? false : false,
      isFinalizing: retainRuntimeOverlay ? existing?.isFinalizing ?? false : false,
      model: resume?.model ?? existing?.model ?? null,
      instructionSources: resume?.instructionSources ?? existing?.instructionSources ?? [],
      approvalPolicy: resume?.approvalPolicy ?? existing?.approvalPolicy ?? null,
      approvalsReviewer: resume?.approvalsReviewer ?? existing?.approvalsReviewer ?? null,
      sandbox: resume?.sandbox ?? existing?.sandbox ?? null,
      reasoningEffort: resume?.reasoningEffort ?? existing?.reasoningEffort ?? null,
      snapshot: {
        ...baseSnapshot,
        messages: trimMessagesToWindow(mergedMessages, historyWindow),
        hasMoreHistory: mergedMessages.length > historyWindow,
        pendingApproval: existing?.pendingApproval?.text ?? null,
      },
    };
    this.threads.set(thread.id, runtimeState);
    this.syncSelectedThread(thread.id);
  }

  private setPendingApproval(threadId: string, approval: PendingApproval): void {
    const state = this.threads.get(threadId);
    if (!state) {
      return;
    }
    state.pendingApproval = approval;
    state.snapshot.pendingApproval = approval.text;
    this.updateSummaryStatus(threadId, "needs_approval");
    this.emitChanged();
  }

  private async finalizeTurnState(threadId: string, turnStatus?: string): Promise<void> {
    const existing = this.threads.get(threadId);
    if (!existing || existing.isFinalizing) {
      return;
    }
    existing.isFinalizing = true;
    const shouldShowStopped = existing.stopRequested;
    try {
      await this.refreshThread(threadId);
      const state = this.threads.get(threadId);
      if (!state) {
        return;
      }
      rebaseSnapshotMessagesFromThread(state);
      state.currentTurnId = null;
      state.activeAssistantMessageId = null;
      state.liveAssistantItemId = null;
      state.transientOperation = null;
      state.snapshot.isGenerating = false;
      state.stopRequested = false;
      if (shouldShowStopped && !hasTrailingSystemStatus(state, "已停止，本轮可继续补充输入。")) {
        state.snapshot.messages = state.snapshot.messages.concat(
          systemStatus("已停止，本轮可继续补充输入。")
        );
      }
      this.updateSummaryStatus(
        threadId,
        turnStatus === "failed" ? "failed" : state.pendingApproval ? "needs_approval" : "idle"
      );
      this.emitChanged();
    } finally {
      const state = this.threads.get(threadId);
      if (state) {
        state.isFinalizing = false;
      }
    }
  }

  private async finalizeCompactState(threadId: string): Promise<void> {
    const existing = this.threads.get(threadId);
    if (!existing || existing.isFinalizing) {
      return;
    }
    existing.isFinalizing = true;
    try {
      await this.refreshThread(threadId);
      const state = this.threads.get(threadId);
      if (!state) {
        return;
      }
      state.currentTurnId = null;
      state.activeAssistantMessageId = null;
      state.liveAssistantItemId = null;
      state.snapshot.isGenerating = false;
      pruneCompletedArtifacts(state);
      normalizeCompactMessages(state, true);
      this.updateSummaryStatus(threadId, state.pendingApproval ? "needs_approval" : "idle");
      this.emitChanged();
    } finally {
      const state = this.threads.get(threadId);
      if (state) {
        state.isFinalizing = false;
      }
    }
  }

  private buildSummaries(preferredThreadId?: string): GatewayThreadPayload[] {
    const items = [...this.threads.values()]
      .filter((entry) => entry.thread != null || entry.snapshot.threads.length > 0)
      .map((entry) => entry.thread
        ? {
            ...mapThreadToSummary(entry.thread, entry.summary.archived, entry.lastActivityAtMs),
            status: resolveDisplayedThreadStatus(resolveThreadSummaryStatus(entry.thread), {
              isGenerating: entry.snapshot.isGenerating,
              currentTurnId: entry.currentTurnId,
              transientOperation: entry.transientOperation,
              pendingApproval: entry.pendingApproval?.text ?? null,
            }),
          }
        : entry.summary)
      .filter((value): value is GatewayThreadPayload => value != null);
    return dedupeSummaries(items);
  }

  private resolveThreadId(threadId?: string): string {
    if (threadId && this.threads.has(threadId)) {
      return threadId;
    }
    return this.getDefaultThreadId();
  }

  private syncSelectedThread(selectedThreadId: string): void {
    const summaries = this.buildSummaries(selectedThreadId);
    for (const [id, state] of this.threads) {
      state.snapshot.selectedThreadId = selectedThreadId;
      state.snapshot.threads = summaries;
      if (id === selectedThreadId) {
        state.snapshot.selectedThreadId = selectedThreadId;
      }
    }
  }

  private updateSummaryStatus(threadId: string, status: ThreadLifecycleStatus): void {
    for (const state of this.threads.values()) {
      if (state.summary.id === threadId) {
        state.summary = { ...state.summary, status };
      }
      state.snapshot.threads = state.snapshot.threads.map((thread) =>
        thread.id === threadId ? { ...thread, status } : thread
      );
    }
  }

  private markAllThreadsFailed(detail: string): void {
    for (const state of this.threads.values()) {
      if (state.snapshot.isGenerating || state.currentTurnId || state.transientOperation) {
        state.snapshot.messages = state.snapshot.messages.concat(systemStatus(detail));
      }
      state.currentTurnId = null;
      state.activeAssistantMessageId = null;
      state.liveAssistantItemId = null;
      state.transientOperation = null;
      state.pendingApproval = null;
      state.snapshot.pendingApproval = null;
      state.snapshot.isGenerating = false;
      state.stopRequested = false;
      state.isFinalizing = false;
      this.updateSummaryStatus(state.summary.id, state.summary.archived ? "idle" : "failed");
    }
    this.emitChanged();
  }

  private emitChanged(): void {
    this.syncSelectedThread(this.currentThreadId || this.getDefaultThreadId());
    this.events.emit("changed");
  }
}

function appendUserMessage(state: ThreadRuntimeState, text: string): void {
  state.snapshot.messages = state.snapshot.messages.concat({
    id: `user-live-${randomUUID()}`,
    role: "user",
    blocks: [{ kind: "text", value: text }],
  });
}

function ensureActiveAssistantMessage(state: ThreadRuntimeState, turnId: string): void {
  const id = `assistant-live-${turnId}`;
  state.activeAssistantMessageId = id;
  state.liveAssistantItemId = null;
  if (state.snapshot.messages.some((message) => message.id === id)) {
    return;
  }
  state.snapshot.messages = state.snapshot.messages.concat({
    id,
    role: "assistant",
    blocks: [{ kind: "status", value: "思考中" }],
  });
}

function appendAssistantDelta(state: ThreadRuntimeState, itemId: string, delta: string): void {
  const messageId = normalizeLiveAssistantMessageId(state, itemId);
  const index = state.snapshot.messages.findIndex((message) => message.id === messageId);
  if (index < 0) {
    state.snapshot.messages = state.snapshot.messages.concat({
      id: messageId,
      role: "assistant",
      blocks: [{ kind: "text", value: delta }],
    });
    return;
  }

  const current = state.snapshot.messages[index];
  const currentText =
    current.blocks.find((block) => block.kind === "text")?.value
      ?? current.blocks.find((block) => block.kind === "status")?.value
      ?? "";
  replaceMessageAt(state, index, {
    ...current,
    blocks: [{ kind: "text", value: currentText + delta }],
  });
}

function appendOrMergeCodeMessage(
  state: ThreadRuntimeState,
  itemId: string,
  delta: string,
  language: string,
  title: string
): void {
  const existing = state.snapshot.messages.find((message) => message.id === itemId);
  if (!existing) {
    state.snapshot.messages = state.snapshot.messages.concat({
      id: itemId,
      role: "assistant",
      blocks: [
        { kind: "text", value: title },
        { kind: "code", language, value: delta },
      ],
    });
    return;
  }
  const code = existing.blocks.find((block) => block.kind === "code");
  const merged: GatewayMessagePayload = {
    ...existing,
    blocks: existing.blocks.map((block) =>
      block.kind === "code" ? { ...block, value: `${code?.value ?? ""}${delta}` } : block
    ),
  };
  replaceOrAppendMessage(state, merged);
}

function appendOrMergeMessage(
  state: ThreadRuntimeState,
  itemId: string,
  role: GatewayMessagePayload["role"],
  block: GatewayMessagePayload["blocks"][number],
  appendToSameKind: boolean
): void {
  const existing = state.snapshot.messages.find((message) => message.id === itemId);
  if (!existing) {
    state.snapshot.messages = state.snapshot.messages.concat({ id: itemId, role, blocks: [block] });
    return;
  }
  const mergedBlocks = appendToSameKind
    ? existing.blocks.map((entry) =>
        entry.kind === block.kind ? { ...entry, value: entry.value + block.value } : entry
      )
    : existing.blocks.concat(block);
  replaceOrAppendMessage(state, { ...existing, blocks: mergedBlocks });
}

function replaceOrAppendMessage(state: ThreadRuntimeState, message: GatewayMessagePayload): void {
  const index = state.snapshot.messages.findIndex((entry) => entry.id === message.id);
  if (index < 0) {
    state.snapshot.messages = state.snapshot.messages.concat(message);
    return;
  }
  replaceMessageAt(state, index, message);
}

function replaceMessageAt(state: ThreadRuntimeState, index: number, message: GatewayMessagePayload): void {
  state.snapshot.messages = state.snapshot.messages.map((entry, current) => (current === index ? message : entry));
}

function mergeThreadItem(state: ThreadRuntimeState, item: AppServerThreadItem, preferLiveAssistantId = false): void {
  if (item.type === "agentMessage") {
    const messageId = preferLiveAssistantId
      ? normalizeLiveAssistantMessageId(state, item.id)
      : item.id;
    state.activeAssistantMessageId = messageId;
    state.liveAssistantItemId = item.id;
    replaceOrAppendMessage(state, {
      id: messageId,
      role: "assistant",
      blocks: [{ kind: "text", value: asString(item.text) }],
    });
    return;
  }

  if (item.type === "commandExecution") {
    replaceOrAppendMessage(state, {
      id: item.id,
      role: "assistant",
      blocks: buildCommandExecutionBlocks(item as Extract<AppServerThreadItem, { type: "commandExecution" }>),
    });
    return;
  }

  if (item.type === "fileChange") {
    replaceOrAppendMessage(state, {
      id: item.id,
      role: "assistant",
      blocks: buildFileChangeBlocks(
        asFileChanges((item as Extract<AppServerThreadItem, { type: "fileChange" }>).changes),
        asString((item as Extract<AppServerThreadItem, { type: "fileChange" }>).status)
      ),
    });
    return;
  }

  const messages = mapItemToMessages(item);
  if (messages.length === 0) {
    return;
  }
  for (const message of messages) {
    if (replaceOptimisticUserMessage(state, message)) {
      continue;
    }
    replaceOrAppendMessage(state, message);
  }
}

interface TextInputEntry {
  type: string;
  text: string;
}

function isTextInputEntry(value: unknown): value is TextInputEntry {
  return typeof value === "object" && value != null && "type" in value && "text" in value;
}

function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

function asStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((entry): entry is string => typeof entry === "string") : [];
}

function asTextInputEntries(value: unknown): TextInputEntry[] {
  return Array.isArray(value)
    ? value.filter((entry): entry is TextInputEntry => isTextInputEntry(entry))
    : [];
}

function asHookPromptFragments(value: unknown): Array<{ type?: string; text?: string }> {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((entry) => {
    if (typeof entry !== "object" || entry == null) {
      return {};
    }
    const candidate = entry as Record<string, unknown>;
    return {
      type: typeof candidate.type === "string" ? candidate.type : undefined,
      text: typeof candidate.text === "string" ? candidate.text : undefined,
    };
  });
}

function asFileChanges(
  value: unknown
): Array<{ path?: string; kind?: string; diff?: string | null }> {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((change) => {
    if (typeof change !== "object" || change == null) {
      return {};
    }
    const candidate = change as Record<string, unknown>;
    return {
      path: typeof candidate.path === "string" ? candidate.path : undefined,
      kind: typeof candidate.kind === "string" ? candidate.kind : undefined,
      diff: typeof candidate.diff === "string" ? candidate.diff : null,
    };
  });
}

function formatFileChanges(changes: Array<{ path?: string; kind?: string; diff?: string | null }>): string {
  return changes
    .map((change) => change.diff?.trim() || `${change.kind ?? "update"} ${change.path ?? "unknown"}`)
    .join("\n");
}

function mapThreadToSnapshot(
  thread: AppServerThread,
  threads: GatewayThreadPayload[],
  selectedThreadId: string,
  resume: Pick<ThreadResumeResult, "model" | "approvalPolicy" | "approvalsReviewer" | "sandbox" | "reasoningEffort" | "instructionSources"> | null,
  allMessages: GatewayMessagePayload[]
): ClientSnapshot {
  const threadIsGenerating = isThreadActivelyGenerating(thread);
  return {
    threads,
    selectedThreadId,
    messages: allMessages,
    hasMoreHistory: allMessages.length > INITIAL_HISTORY_WINDOW,
    pendingApproval: null,
    chips: buildChips(thread, resume),
    slashCommands: ["/compact  压缩上下文", "/goal  设置目标", "! ls  运行 shell 命令"],
    cwd: thread.cwd,
    permissionSummary: buildPermissionSummary(resume?.approvalPolicy ?? null, resume?.sandbox ?? null),
    isGenerating: threadIsGenerating,
  };
}

function mapThreadToSummary(thread: AppServerThread, archived = false, updatedAtOverride?: number): GatewayThreadPayload {
  const grouping = deriveThreadGrouping(thread);
  const status = resolveThreadSummaryStatus(thread);
  const subtitle = buildThreadSubtitle(thread);
  return {
    id: thread.id,
    title: thread.name ?? buildThreadTitle(thread.preview),
    preview: thread.preview,
    subtitle,
    status,
    updatedAt: updatedAtOverride ?? toMillisTimestamp(thread.updatedAt),
    groupKind: grouping.kind,
    groupLabel: grouping.label,
    archived,
  };
}

function mapItemToMessages(item: AppServerThreadItem): GatewayMessagePayload[] {
  switch (item.type) {
    case "userMessage":
      return [
        {
          id: item.id,
          role: "user",
          blocks: asTextInputEntries(item.content)
            .filter((entry) => entry.type === "text")
            .map((entry) => ({ kind: "text", value: entry.text })),
        },
      ];
    case "agentMessage":
      return [{ id: item.id, role: "assistant", blocks: [{ kind: "text", value: asString(item.text) }] }];
    case "reasoning":
      return [
        {
          id: item.id,
          role: "assistant",
          blocks: [
            {
              kind: "reasoning",
              value: [...asStringArray(item.summary), ...asStringArray(item.content)].join("\n") || "思考中",
            },
          ],
        },
      ];
    case "commandExecution":
      return [
        {
          id: item.id,
          role: "assistant",
          blocks: buildCommandExecutionBlocks(item as Extract<AppServerThreadItem, { type: "commandExecution" }>),
        },
      ];
    case "fileChange":
      const fileChangeItem = item as Extract<AppServerThreadItem, { type: "fileChange" }>;
      return [
        {
          id: fileChangeItem.id,
          role: "assistant",
          blocks: buildFileChangeBlocks(fileChangeItem.changes, fileChangeItem.status),
        },
      ];
    case "plan":
      return [{ id: item.id, role: "assistant", blocks: [{ kind: "text", value: asString(item.text) }] }];
    case "mcpToolCall":
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "text", value: `MCP: ${item.server}/${item.tool} · ${item.status}` }],
      }];
    case "dynamicToolCall":
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "text", value: `工具: ${item.namespace ? `${item.namespace}/` : ""}${item.tool} · ${item.status}` }],
      }];
    case "webSearch":
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "text", value: `检索: ${item.query}` }],
      }];
    case "imageView":
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "text", value: `查看图片: ${item.path}` }],
      }];
    case "imageGeneration":
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "text", value: `生成图片 ${item.status}: ${item.savedPath ?? item.result}` }],
      }];
    case "collabAgentToolCall":
      return [{
        id: item.id,
        role: "assistant",
        blocks: [{ kind: "text", value: `协作代理: ${item.tool} · ${item.status}` }],
      }];
    case "hookPrompt":
      return [{
        id: item.id,
        role: "system",
        blocks: [{ kind: "status", value: flattenHookPrompt(asHookPromptFragments(item.fragments)) || "Hook 提示" }],
      }];
    case "enteredReviewMode":
      return [{ id: item.id, role: "system", blocks: [{ kind: "status", value: `进入 review: ${asString(item.review)}` }] }];
    case "exitedReviewMode":
      return [{ id: item.id, role: "system", blocks: [{ kind: "status", value: `退出 review: ${asString(item.review)}` }] }];
    case "contextCompaction":
      return [];
    default:
      return [];
  }
}

function buildThreadTitle(preview: string): string {
  const firstLine = preview.trim().split(/\r?\n/, 1)[0] ?? "Codex 会话";
  return firstLine.slice(0, 32) || "Codex 会话";
}

function buildThreadSubtitle(thread: AppServerThread): string {
  const latestText = extractLatestThreadText(thread);
  if (latestText.length > 0) {
    return latestText;
  }
  return thread.preview.trim();
}

function extractLatestThreadText(thread: AppServerThread): string {
  for (let turnIndex = thread.turns.length - 1; turnIndex >= 0; turnIndex -= 1) {
    const turn = thread.turns[turnIndex];
    for (let itemIndex = turn.items.length - 1; itemIndex >= 0; itemIndex -= 1) {
      const item = turn.items[itemIndex];
      const text = extractThreadItemText(item);
      if (text.length > 0) {
        return text;
      }
    }
  }
  return "";
}

function extractThreadItemText(item: AppServerThreadItem): string {
  switch (item.type) {
    case "userMessage":
      return asTextInputEntries(item.content)
        .map((entry) => entry.text.trim())
        .find((value) => value.length > 0) ?? "";
    case "agentMessage":
      return asString(item.text).trim();
    default:
      return "";
  }
}

function formatCommandResult(item: {
  [key: string]: unknown;
  status?: string;
  exitCode?: number | null;
  durationMs?: number | null;
}): string {
  const parts: string[] = [];
  if (typeof item.exitCode === "number") {
    parts.push(`退出码 ${item.exitCode}`);
  }
  if (typeof item.durationMs === "number") {
    parts.push(`${Math.max(0, Math.round(item.durationMs / 1000))}s`);
  }
  if (parts.length > 0) {
    return parts.join(" · ");
  }
  return item.status ?? "unknown";
}

function deriveThreadGrouping(thread: AppServerThread): { kind: "project" | "chat"; label: string } {
  const cwd = thread.cwd.replaceAll("\\", "/");
  const segments = cwd.split("/").filter(Boolean);
  const leaf = segments.at(-1) ?? "会话";
  const penultimate = segments.at(-2) ?? "";
  const title = thread.name ?? buildThreadTitle(thread.preview);

  if (segments.includes("Codex") || /new-chat|chat/i.test(leaf) || /回复|hello|ok/i.test(title)) {
    return { kind: "chat", label: "普通会话" };
  }

  if (penultimate.toLowerCase() === "home") {
    return { kind: "project", label: leaf };
  }

  if (leaf.length > 0) {
    return { kind: "project", label: leaf };
  }

  return { kind: "chat", label: "普通会话" };
}

function buildChips(
  thread: AppServerThread,
  resume: Pick<ThreadResumeResult, "model" | "reasoningEffort" | "instructionSources"> | null
): GatewayChipPayload[] {
  const chips: GatewayChipPayload[] = [];
  chips.push({ label: resume?.model || thread.modelProvider || "openai", icon: "context" });
  if (resume?.reasoningEffort) {
    chips.push({ label: `reasoning:${resume.reasoningEffort}`, icon: "context" });
  }
  const fileChip = resume?.instructionSources?.[0] ?? thread.cwd;
  chips.push({ label: shrinkPathLabel(fileChip), icon: "file" });
  return chips.slice(0, 3);
}

function systemStatus(text: string): GatewayMessagePayload {
  return {
    id: randomUUID(),
    role: "system",
    blocks: [{ kind: "status", value: text }],
  };
}

function buildApprovalResponse(kind: PendingApproval["kind"], allow: boolean): unknown {
  if (kind === "command") {
    return { decision: allow ? "accept" : "decline" };
  }
  if (kind === "file") {
    return { decision: allow ? "accept" : "decline" };
  }
  return {
    permissions: {
      fileSystem: null,
      network: null,
    },
    scope: "turn",
  };
}

function buildFileChangeBlocks(
  changes: Array<{ path?: string; kind?: string | { type?: string }; diff?: string | null }>,
  status: string
): GatewayBlockPayload[] {
  const summary = summarizeFileChanges(changes, status);
  const details = formatFileChangeDetails(changes);
  return [
    { kind: "commandSummary", value: summary },
    ...details.map((value) => ({ kind: "commandMeta" as const, value })),
  ];
}

function buildCommandExecutionBlocks(item: Extract<AppServerThreadItem, { type: "commandExecution" }>): GatewayBlockPayload[] {
  return [
    {
      kind: "commandSummary",
      value: Array.isArray(item.commandActions) && item.commandActions.length > 0
        ? `已运行 ${item.commandActions.length} 条命令`
        : asString(item.status) === "inProgress"
          ? "命令执行中"
          : `命令 ${asString(item.status)}`,
    },
    { kind: "commandMeta", value: `命令: ${asString(item.command)}` },
    { kind: "commandMeta", value: `结果: ${formatCommandResult(item)}` },
    { kind: "code" as const, language: "shell", value: asString(item.aggregatedOutput, asString(item.status)) },
  ];
}

function summarizeFileChanges(
  changes: Array<{ path?: string; kind?: string | { type?: string }; diff?: string | null }>,
  status: string
): string {
  if (!Array.isArray(changes) || changes.length === 0) {
    return status === "inProgress" ? "文件改动中" : `文件改动 ${status}`;
  }
  const counts = { add: 0, delete: 0, update: 0 };
  for (const change of changes) {
    counts[normalizeChangeKind(change.kind)] += 1;
  }
  const parts: string[] = [];
  if (counts.add > 0) {
    parts.push(`已创建 ${counts.add} 个文件`);
  }
  if (counts.delete > 0) {
    parts.push(`已删除 ${counts.delete} 个文件`);
  }
  if (counts.update > 0) {
    parts.push(`已编辑 ${counts.update} 个文件`);
  }
  return parts.length > 0 ? parts.join(" · ") : `已编辑 ${changes.length} 个文件`;
}

function formatFileChangeDetails(
  changes: Array<{ path?: string; kind?: string | { type?: string }; diff?: string | null }>
): string[] {
  return changes.map((change) => {
    const label = describeChangeKind(change.kind);
    const path = change.path?.trim() || "unknown";
    return `- ${label} \`${path}\``;
  });
}

function normalizeChangeKind(kind: unknown): "add" | "delete" | "update" {
  if (typeof kind === "string") {
    const normalized = kind.toLowerCase();
    if (normalized === "add" || normalized === "delete" || normalized === "update") {
      return normalized;
    }
  }
  if (typeof kind === "object" && kind != null) {
    const candidate = kind as Record<string, unknown>;
    const normalized = typeof candidate.type === "string" ? candidate.type.toLowerCase() : "";
    if (normalized === "add" || normalized === "delete" || normalized === "update") {
      return normalized;
    }
  }
  return "update";
}

function describeChangeKind(kind: unknown): string {
  switch (normalizeChangeKind(kind)) {
    case "add":
      return "创建";
    case "delete":
      return "删除";
    default:
      return "编辑";
  }
}

function flattenHookPrompt(fragments: Array<{ type?: string; text?: string }> | undefined): string {
  if (!Array.isArray(fragments)) {
    return "";
  }
  return fragments
    .map((fragment) => asString(fragment.text))
    .filter((value) => value.length > 0)
    .join("\n");
}

function normalizeLiveAssistantMessageId(state: ThreadRuntimeState, itemId: string): string {
  const current = state.activeAssistantMessageId;
  if (!current) {
    state.activeAssistantMessageId = itemId;
    state.liveAssistantItemId = itemId;
    return itemId;
  }
  if (current === itemId) {
    state.liveAssistantItemId = itemId;
    return current;
  }
  if (current.startsWith("assistant-live-")) {
    renameMessageId(state, current, itemId);
    state.activeAssistantMessageId = itemId;
    state.liveAssistantItemId = itemId;
    return itemId;
  }
  state.liveAssistantItemId = itemId;
  return current;
}

function renameMessageId(state: ThreadRuntimeState, fromId: string, toId: string): void {
  if (fromId === toId) {
    return;
  }
  const fromIndex = state.snapshot.messages.findIndex((message) => message.id === fromId);
  if (fromIndex < 0) {
    return;
  }
  const toIndex = state.snapshot.messages.findIndex((message) => message.id === toId);
  const fromMessage = state.snapshot.messages[fromIndex];
  if (toIndex >= 0) {
    replaceMessageAt(state, toIndex, mergeMessageBlocks(state.snapshot.messages[toIndex], fromMessage));
    state.snapshot.messages = state.snapshot.messages.filter((message) => message.id !== fromId);
    return;
  }
  replaceMessageAt(state, fromIndex, { ...fromMessage, id: toId });
}

function collapseLiveAssistantMessage(state: ThreadRuntimeState): void {
  const liveId = state.activeAssistantMessageId;
  const itemId = state.liveAssistantItemId;
  if (!liveId || !itemId || liveId === itemId) {
    return;
  }
  renameMessageId(state, liveId, itemId);
  state.activeAssistantMessageId = itemId;
}

function pruneCompletedArtifacts(state: ThreadRuntimeState): void {
  const assistantTextIds = new Set(
    state.snapshot.messages
      .filter((message) => message.role === "assistant")
      .filter((message) => message.blocks.some((block) => block.kind === "text" && block.value.trim().length > 0))
      .map((message) => message.id)
  );
  state.snapshot.messages = state.snapshot.messages.filter((message) => {
    if (message.role !== "assistant") {
      return true;
    }
    if (!message.blocks.every((block) => block.kind === "status")) {
      return true;
    }
    const onlyStatus = message.blocks.map((block) => block.value.trim()).join("\n");
    if (onlyStatus !== "思考中") {
      return true;
    }
    return assistantTextIds.has(message.id);
  });
  state.snapshot.messages = state.snapshot.messages.filter((message) => {
    if (!message.id.startsWith("assistant-live-")) {
      return true;
    }
    const onlyStatus = message.blocks.every((block) => block.kind === "status");
    const onlyThinking = onlyStatus && message.blocks.every((block) => block.value.trim() === "思考中");
    return !onlyThinking;
  });
}

function hasTrailingSystemStatus(state: ThreadRuntimeState, value: string): boolean {
  const last = [...state.snapshot.messages].reverse().find((message) => message.role === "system");
  return (last?.blocks ?? []).some((block) => block.kind === "status" && block.value === value);
}

function normalizeCompactMessages(state: ThreadRuntimeState, includeCompletedStatus: boolean): void {
  const isCompactStatus = (message: GatewayMessagePayload): boolean =>
    message.role === "system"
      && message.blocks.length === 1
      && message.blocks[0]?.kind === "status"
      && ["已请求压缩上下文", "上下文已压缩"].includes(message.blocks[0].value.trim());

  let requestedSeen = false;
  let compactedSeen = false;
  let end = state.snapshot.messages.length;
  while (end > 0 && isCompactStatus(state.snapshot.messages[end - 1]!)) {
    const value = state.snapshot.messages[end - 1]!.blocks[0]!.value.trim();
    if (value === "已请求压缩上下文") {
      requestedSeen = true;
    }
    if (value === "上下文已压缩") {
      compactedSeen = true;
    }
    end -= 1;
  }
  state.snapshot.messages = state.snapshot.messages.slice(0, end);
  if (requestedSeen) {
    state.snapshot.messages = state.snapshot.messages.concat(systemStatus("已请求压缩上下文"));
  }
  if (includeCompletedStatus) {
    compactedSeen = true;
  }
  if (compactedSeen) {
    state.snapshot.messages = state.snapshot.messages.concat(systemStatus("上下文已压缩"));
  }
  state.transientOperation = null;
}

function rebaseSnapshotMessagesFromThread(state: ThreadRuntimeState): void {
  if (!state.thread) {
    return;
  }
  const allMessages = collectThreadMessages(state.thread);
  state.snapshot.messages = trimMessagesToWindow(allMessages, state.historyWindow);
  state.snapshot.hasMoreHistory = allMessages.length > state.historyWindow;
}

function collectThreadMessages(thread: AppServerThread): GatewayMessagePayload[] {
  return thread.turns.flatMap((turn) => turn.items).flatMap(mapItemToMessages);
}

function mergeSnapshotMessages(
  baseMessages: GatewayMessagePayload[],
  liveMessages: GatewayMessagePayload[]
): GatewayMessagePayload[] {
  const merged = [...baseMessages];
  const baseHasAnyRealAssistant = baseMessages.some(
    (entry) => entry.role === "assistant" && entry.blocks.some((block) => block.kind === "text" && block.value.trim().length > 0)
  );
  for (const message of liveMessages) {
    const existingIndex = merged.findIndex((entry) => entry.id === message.id);
    if (existingIndex >= 0) {
      merged[existingIndex] = mergeMessageBlocks(merged[existingIndex], message);
      continue;
    }
    if (isOptimisticUserMessage(message) && merged.some((entry) => isSameUserMessage(entry, message))) {
      continue;
    }
    if (message.id.startsWith("assistant-live-")) {
      if (baseHasAnyRealAssistant) {
        continue;
      }
    }
    if (message.role === "assistant" && message.blocks.every((block) => block.kind === "status")) {
      if (baseHasAnyRealAssistant) {
        continue;
      }
      if (merged.some((entry) => entry.role === "assistant" && normalizeMessageText(entry).length === 0)) {
        continue;
      }
    }
    merged.push(message);
  }
  return merged;
}

function replaceOptimisticUserMessage(
  state: ThreadRuntimeState,
  message: GatewayMessagePayload
): boolean {
  if (message.role !== "user") {
    return false;
  }
  const index = state.snapshot.messages.findIndex((entry) => isOptimisticUserMessage(entry) && isSameUserMessage(entry, message));
  if (index < 0) {
    return false;
  }
  replaceMessageAt(state, index, message);
  return true;
}

function isOptimisticUserMessage(message: GatewayMessagePayload): boolean {
  return message.role === "user" && message.id.startsWith("user-live-");
}

function isSameUserMessage(
  left: GatewayMessagePayload,
  right: GatewayMessagePayload
): boolean {
  return left.role === "user"
    && right.role === "user"
    && normalizeMessageText(left) === normalizeMessageText(right)
    && normalizeMessageText(left).length > 0;
}

function normalizeMessageText(message: GatewayMessagePayload): string {
  return message.blocks
    .filter((block) => block.kind === "text")
    .map((block) => block.value.trim())
    .join("\n")
    .trim();
}

function mergeMessageBlocks(primary: GatewayMessagePayload, secondary: GatewayMessagePayload): GatewayMessagePayload {
  const mergedBlocks = [...primary.blocks];
  for (const block of secondary.blocks) {
    const index = mergedBlocks.findIndex((entry) => entry.kind === block.kind);
    if (index < 0) {
      mergedBlocks.push(block);
      continue;
    }
    const current = mergedBlocks[index]!;
    if (block.value.length > current.value.length) {
      mergedBlocks[index] = block;
    }
  }
  return {
    ...primary,
    blocks: mergedBlocks,
  };
}

function toResumeMetadata(
  state: ThreadRuntimeState | undefined
): Pick<ThreadResumeResult, "model" | "approvalPolicy" | "approvalsReviewer" | "sandbox" | "reasoningEffort" | "instructionSources"> | null {
  if (!state) {
    return null;
  }
  return {
    model: state.model ?? state.thread?.modelProvider ?? "openai",
    approvalPolicy: state.approvalPolicy ?? "never",
    approvalsReviewer: (state.approvalsReviewer as ThreadResumeResult["approvalsReviewer"]) ?? "user",
    sandbox: state.sandbox ?? { type: "dangerFullAccess" },
    reasoningEffort: state.reasoningEffort,
    instructionSources: state.instructionSources,
  };
}

function buildPermissionSummary(
  approvalPolicy: AppServerApprovalPolicy | null,
  sandbox: AppServerSandboxPolicy | null
): string {
  const sandboxLabel = sandbox ? mapSandboxLabel(sandbox) : "sandbox:unknown";
  const approvalLabel = approvalPolicy ? mapApprovalLabel(approvalPolicy) : "approval:unknown";
  return `${sandboxLabel} · ${approvalLabel}`;
}

function mapSandboxLabel(sandbox: AppServerSandboxPolicy): string {
  switch (sandbox.type) {
    case "dangerFullAccess":
      return "danger-full-access";
    case "readOnly":
      return sandbox.networkAccess ? "read-only+net" : "read-only";
    case "externalSandbox":
      return sandbox.networkAccess === "enabled" ? "external+net" : "external";
    case "workspaceWrite":
      return sandbox.networkAccess ? "workspace-write+net" : "workspace-write";
    default:
      return "sandbox";
  }
}

function mapApprovalLabel(policy: AppServerApprovalPolicy): string {
  if (typeof policy === "string") {
    return policy;
  }
  return "granular";
}

function shrinkPathLabel(value: string): string {
  const normalized = value.replaceAll("\\", "/");
  const segments = normalized.split("/").filter(Boolean);
  if (segments.length === 0) {
    return value;
  }
  return segments.slice(-2).join("/");
}

function dedupeSummaries(items: GatewayThreadPayload[]): GatewayThreadPayload[] {
  const map = new Map<string, GatewayThreadPayload>();
  for (const item of items) {
    map.set(item.id, item);
  }
  return [...map.values()];
}

function touchThreadActivity(state: ThreadRuntimeState, timestampMs?: number | null): void {
  if (typeof timestampMs !== "number" || !Number.isFinite(timestampMs) || timestampMs <= 0) {
    return;
  }
  state.lastActivityAtMs = Math.max(state.lastActivityAtMs, timestampMs);
  state.summary = {
    ...state.summary,
    updatedAt: state.lastActivityAtMs,
  };
  state.snapshot.threads = state.snapshot.threads.map((thread) =>
    thread.id === state.summary.id ? { ...thread, updatedAt: state.lastActivityAtMs } : thread
  );
}

function toMillisTimestamp(seconds: number): number {
  return seconds > 0 ? seconds * 1000 : 0;
}

function getThreadLastActivityAtMs(thread: AppServerThread): number {
  let latest = 0;
  for (const turn of thread.turns) {
    if (typeof turn.startedAt === "number" && turn.startedAt > 0) {
      latest = Math.max(latest, toMillisTimestamp(turn.startedAt));
    }
    if (typeof turn.completedAt === "number" && turn.completedAt > 0) {
      latest = Math.max(latest, toMillisTimestamp(turn.completedAt));
    }
  }
  return latest > 0 ? latest : toMillisTimestamp(thread.updatedAt);
}

export function buildVisibleThreadSummaries(threads: AppServerThread[]): GatewayThreadPayload[] {
  return threads
    .filter((thread) => !isExcludedThread(thread))
    .map((thread) => mapThreadToSummary(thread, false, getThreadLastActivityAtMs(thread)));
}

function isThreadNotMaterializedError(error: unknown): boolean {
  return error instanceof Error && error.message.includes("not materialized yet");
}

function isNoRolloutFoundError(error: unknown): boolean {
  return error instanceof Error && error.message.includes("no rollout found for thread id");
}

function isExcludedThread(thread: AppServerThread): boolean {
  const title = thread.name ?? buildThreadTitle(thread.preview);
  return title.includes(SELF_TEST_EXCLUDE_TITLE) || thread.preview.includes(SELF_TEST_EXCLUDE_TITLE);
}

function emptySnapshot(): ClientSnapshot {
  return {
    threads: [],
    selectedThreadId: "",
    messages: [],
    hasMoreHistory: false,
    pendingApproval: null,
    chips: [],
    slashCommands: [],
    cwd: "",
    permissionSummary: "",
    isGenerating: false,
  };
}

function trimMessagesToWindow(messages: GatewayMessagePayload[], historyWindow: number): GatewayMessagePayload[] {
  if (historyWindow <= 0 || messages.length <= historyWindow) {
    return messages;
  }
  return messages.slice(messages.length - historyWindow);
}

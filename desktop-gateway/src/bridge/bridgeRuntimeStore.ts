import { EventEmitter } from "node:events";
import {
  emptyConfigOptions,
  type ClientSnapshot,
  type GatewayConfigOptionsPayload,
  type GatewayOperationalNoticePayload,
} from "../protocol.js";
import {
  buildRuntimeSummaries,
  markAllThreadsFailedState,
  syncSelectedThreadSnapshots,
  updateSummaryStatusForThread,
} from "./runtimeSummaryState.js";
import { emptySnapshot } from "./summaries.js";
import {
  getDefaultThreadId,
  resolveThreadId,
} from "./threadSelection.js";
import type {
  ThreadLifecycleStatus,
  ThreadRuntimeState,
} from "./types.js";

export class BridgeRuntimeStore {
  private readonly events = new EventEmitter();
  readonly threads = new Map<string, ThreadRuntimeState>();
  currentThreadId = "";
  selectionVersion = 0;
  configOptions: GatewayConfigOptionsPayload = emptyConfigOptions();
  private operationalNotices: GatewayOperationalNoticePayload[] = [];

  subscribe(listener: () => void): () => void {
    this.events.on("changed", listener);
    return () => this.events.off("changed", listener);
  }

  hasThread(threadId: string): boolean {
    return this.threads.has(threadId);
  }

  getDefaultThreadId(): string {
    return getDefaultThreadId(this.threads, this.currentThreadId);
  }

  getSnapshot(selectedThreadId?: string): ClientSnapshot {
    if (selectedThreadId === "") {
      return structuredClone({
        ...emptySnapshot(),
        threads: buildRuntimeSummaries(this.threads),
        selectedThreadId: "",
        configOptions: this.configOptions,
        operationalNotices: this.consumeOperationalNotices(),
      });
    }

    const threadId = this.resolveThreadId(selectedThreadId);
    const state = this.threads.get(threadId);
    if (!state) {
      return {
        ...emptySnapshot(),
        configOptions: this.configOptions,
        operationalNotices: this.consumeOperationalNotices(),
      };
    }

    state.snapshot.selectedThreadId = threadId;
    return structuredClone({
      ...state.snapshot,
      configOptions: this.configOptions,
      operationalNotices: this.consumeOperationalNotices(),
    });
  }

  resolveThreadId(threadId?: string): string {
    return resolveThreadId(this.threads, this.currentThreadId, threadId);
  }

  syncSelectedThread(selectedThreadId: string): void {
    syncSelectedThreadSnapshots(this.threads, selectedThreadId);
  }

  updateSummaryStatus(threadId: string, status: ThreadLifecycleStatus): void {
    updateSummaryStatusForThread(this.threads, threadId, status);
  }

  markAllThreadsFailed(detail: string): void {
    markAllThreadsFailedState(this.threads, detail);
    this.emitChanged();
  }

  pushOperationalNotice(notice: GatewayOperationalNoticePayload): void {
    this.operationalNotices = this.operationalNotices
      .filter((entry) => entry.id !== notice.id)
      .concat(notice)
      .slice(-6);
  }

  emitChanged(): void {
    this.syncSelectedThread(this.currentThreadId);
    this.events.emit("changed");
  }

  isCurrentSelection(threadId: string): boolean {
    return this.currentThreadId === threadId;
  }

  incrementSelectionVersion(): number {
    this.selectionVersion += 1;
    return this.selectionVersion;
  }

  private consumeOperationalNotices(): GatewayOperationalNoticePayload[] {
    const notices = this.operationalNotices;
    this.operationalNotices = [];
    return notices;
  }
}

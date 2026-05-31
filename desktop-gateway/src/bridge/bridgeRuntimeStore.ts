import { EventEmitter } from "node:events";
import type { ClientSnapshot } from "../protocol.js";
import {
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
    const threadId = this.resolveThreadId(selectedThreadId);
    const state = this.threads.get(threadId);
    if (!state) {
      return emptySnapshot();
    }

    state.snapshot.selectedThreadId = threadId;
    return structuredClone(state.snapshot);
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

  emitChanged(): void {
    this.syncSelectedThread(this.currentThreadId || this.getDefaultThreadId());
    this.events.emit("changed");
  }

  isCurrentSelection(threadId: string): boolean {
    return this.currentThreadId === threadId;
  }

  incrementSelectionVersion(): number {
    this.selectionVersion += 1;
    return this.selectionVersion;
  }
}

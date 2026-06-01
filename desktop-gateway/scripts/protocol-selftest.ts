import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import { AppServerBridgeBackend } from "../src/bridgeBackend.js";
import type {
  AppServerThread,
  JsonRpcNotification,
  JsonRpcServerRequest,
  ThreadResumeResult,
} from "../src/appServerTypes.js";

type Listener<T> = (event: T) => void;

class FakeAppServer {
  private readonly notifications = new EventEmitter();
  private readonly requests = new EventEmitter();
  readonly shellCalls: Array<[string, string]> = [];
  readonly archivedThreadIds: string[] = [];
  readonly rollbackCalls: Array<[string, number]> = [];
  private readonly threads = new Map<string, AppServerThread>();
  private readonly activeThreadIds = new Set<string>();
  private nextThreadNumber = 1;

  async start(): Promise<void> {}

  onNotification(listener: Listener<JsonRpcNotification>): () => void {
    this.notifications.on("notification", listener);
    return () => this.notifications.off("notification", listener);
  }

  onRequest(listener: Listener<JsonRpcServerRequest>): () => void {
    this.requests.on("request", listener);
    return () => this.requests.off("request", listener);
  }

  onExit(): () => void {
    return () => {};
  }

  async threadList(): Promise<AppServerThread[]> {
    return [...this.activeThreadIds].map((threadId) => this.requireThread(threadId));
  }

  async threadRead(threadId: string): Promise<AppServerThread> {
    return this.requireThread(threadId);
  }

  async threadResume(threadId: string): Promise<ThreadResumeResult> {
    const thread = this.requireThread(threadId);
    return {
      thread,
      model: "gpt-5",
      modelProvider: "openai",
      serviceTier: null,
      cwd: thread.cwd,
      instructionSources: [thread.cwd],
      approvalPolicy: "never",
      approvalsReviewer: "user",
      sandbox: { type: "workspaceWrite", networkAccess: false },
      reasoningEffort: null,
    };
  }

  async threadStart(cwd?: string | null): Promise<ThreadResumeResult> {
    const id = `protocol-selftest-${this.nextThreadNumber++}`;
    const thread = createThread(id, cwd ?? "D:/Projects/protocol-selftest");
    this.threads.set(id, thread);
    this.activeThreadIds.add(id);
    return this.threadResume(id);
  }

  async threadFork(threadId: string): Promise<ThreadResumeResult> {
    const source = this.requireThread(threadId);
    const id = `protocol-selftest-${this.nextThreadNumber++}`;
    const thread = createThread(id, source.cwd);
    this.threads.set(id, { ...thread, preview: `forked from ${threadId}` });
    this.activeThreadIds.add(id);
    return this.threadResume(id);
  }

  async threadRollback(threadId: string, numTurns: number): Promise<{ thread: AppServerThread }> {
    this.rollbackCalls.push([threadId, numTurns]);
    return { thread: this.requireThread(threadId) };
  }

  async threadSetName(threadId: string, name: string): Promise<void> {
    const thread = this.requireThread(threadId);
    this.threads.set(threadId, { ...thread, name, updatedAt: Date.now() / 1000 });
  }

  async threadArchive(threadId: string): Promise<void> {
    this.requireThread(threadId);
    this.archivedThreadIds.push(threadId);
    this.activeThreadIds.delete(threadId);
  }

  async threadUnarchive(): Promise<void> {}

  async threadUnsubscribe(): Promise<void> {}

  async turnStart(threadId: string): Promise<string> {
    this.requireThread(threadId);
    return "turn-1";
  }

  async turnInterrupt(): Promise<void> {}

  async turnSteer(): Promise<void> {}

  async threadCompactStart(threadId: string): Promise<void> {
    this.emitNotification({
      method: "thread/compacted",
      params: { threadId, turnId: "compact-turn" },
    });
  }

  async threadShellCommand(threadId: string, command: string): Promise<void> {
    this.shellCalls.push([threadId, command]);
  }

  respond(): void {}

  emitNotification(notification: JsonRpcNotification): void {
    this.notifications.emit("notification", notification);
  }

  emitRequest(request: JsonRpcServerRequest): void {
    this.requests.emit("request", request);
  }

  private requireThread(threadId: string): AppServerThread {
    const thread = this.threads.get(threadId);
    if (!thread) {
      throw new Error(`missing fake thread: ${threadId}`);
    }
    return thread;
  }
}

async function main(): Promise<void> {
  const fake = new FakeAppServer();
  const backend = new AppServerBridgeBackend();
  backend.appServer = fake as never;
  await backend.start();

  const created = await backend.createThread("D:/Projects/protocol-selftest");
  const threadId = created.selectedThreadId;
  assert.ok(threadId.startsWith("protocol-selftest-"));

  await backend.sendPrompt(threadId, "!echo protocol-selftest");
  await backend.approveCurrent(threadId, false);
  assert.deepEqual(fake.shellCalls, []);

  await backend.sendPrompt(threadId, "!echo protocol-selftest");
  await backend.approveCurrent(threadId, true);
  assert.deepEqual(fake.shellCalls, [[threadId, "echo protocol-selftest"]]);

  await backend.handleNotification({
    method: "item/commandExecution/outputDelta",
    params: { threadId, turnId: "turn-1", itemId: "command-1", delta: "one" },
  });
  await backend.handleNotification({
    method: "item/commandExecution/outputDelta",
    params: { threadId, turnId: "turn-1", itemId: "command-1", delta: "\ntwo" },
  });
  assert.equal(
    backend
      .getSnapshot(threadId)
      .messages.find((message) => message.id === "command-1")
      ?.blocks.find((block) => block.kind === "code")?.value,
    "one\ntwo"
  );

  await backend.handleNotification({
    method: "thread/goal/updated",
    params: { threadId, goal: { objective: "protocol selftest", status: "active" } },
  });
  assert.match(
    backend.getSnapshot(threadId).messages.find((message) => message.id === "thread-goal")?.blocks[0]?.value ?? "",
    /protocol selftest/
  );

  await backend.sendPrompt(threadId, "/compact");
  assert.equal(
    backend.getSnapshot(threadId).messages.filter((message) =>
      message.blocks.some((block) => block.kind === "status" && block.value === "上下文已压缩")
    ).length,
    1
  );

  const forked = await backend.forkThread(threadId);
  assert.notEqual(forked.selectedThreadId, threadId);
  assert.match(forked.threads.find((thread) => thread.id === forked.selectedThreadId)?.preview ?? "", /forked from/);

  await backend.sendPrompt(threadId, "/rollback");
  assert.deepEqual(fake.rollbackCalls, [[threadId, 1]]);
  assert.equal(
    backend.getSnapshot(threadId).messages.some((message) =>
      message.blocks.some((block) => block.kind === "status" && block.value === "已回滚最近 1 轮")
    ),
    true
  );

  await backend.archiveThread(threadId);
  assert.deepEqual(fake.archivedThreadIds, [threadId]);
  console.log(`[protocol-selftest] passed thread=${threadId}`);
}

function createThread(id: string, cwd: string): AppServerThread {
  return {
    id,
    preview: id,
    status: "idle",
    cwd,
    updatedAt: Date.now() / 1000,
    name: "Protocol selftest",
    turns: [],
    modelProvider: "openai",
  };
}

await main();

import process from "node:process";
import type {
  AppServerThread,
  JsonRpcNotification,
  JsonRpcServerRequest,
  ThreadForkResponse,
  ThreadRollbackResponse,
  ThreadResumeResult,
  ThreadStartResponse,
} from "./appServerTypes.js";
import { startAppServerSession } from "./appServerLifecycle.js";
import { AppServerTransport } from "./appServerTransport.js";
import {
  archiveThread,
  forkThread,
  listThreads,
  readThread,
  resumeThread,
  rollbackThread,
  setThreadName,
  startThread,
  unarchiveThread,
  unsubscribeThread,
} from "./appServerThreadRpc.js";
import {
  interruptTurn,
  sendThreadShellCommand,
  startThreadCompaction,
  startTurn,
  steerTurn,
} from "./appServerTurnRpc.js";

export class AppServerClient {
  private readonly transport = new AppServerTransport();
  private initialized = false;

  async start(): Promise<void> {
    if (this.initialized) {
      return;
    }
    const init = await startAppServerSession(
      this.transport,
      this.request.bind(this),
      this.notify.bind(this),
      process.platform,
      process.cwd()
    );
    this.initialized = true;
    console.log(`[app-server] initialized userAgent=${init.userAgent}`);
  }

  onNotification(listener: (message: JsonRpcNotification) => void): () => void {
    return this.transport.onNotification(listener);
  }

  onRequest(listener: (message: JsonRpcServerRequest) => void): () => void {
    return this.transport.onRequest(listener);
  }

  onExit(listener: (event: { code: number | null; signal: NodeJS.Signals | null }) => void): () => void {
    return this.transport.onExit((event) => {
      this.initialized = false;
      listener(event);
    });
  }

  async threadList(archived = false): Promise<AppServerThread[]> {
    return await listThreads(this.request.bind(this), archived);
  }

  async threadRead(threadId: string, includeTurns = true): Promise<AppServerThread> {
    return await readThread(this.request.bind(this), threadId, includeTurns);
  }

  async threadResume(threadId: string): Promise<ThreadResumeResult> {
    return await resumeThread(this.request.bind(this), threadId);
  }

  async threadStart(cwd?: string | null): Promise<ThreadStartResponse> {
    return await startThread(this.request.bind(this), cwd);
  }

  async threadFork(threadId: string): Promise<ThreadForkResponse> {
    return await forkThread(this.request.bind(this), threadId);
  }

  async threadRollback(threadId: string, numTurns: number): Promise<ThreadRollbackResponse> {
    return await rollbackThread(this.request.bind(this), threadId, numTurns);
  }

  async threadSetName(threadId: string, name: string): Promise<void> {
    await setThreadName(this.request.bind(this), threadId, name);
  }

  async threadArchive(threadId: string): Promise<void> {
    await archiveThread(this.request.bind(this), threadId);
  }

  async threadUnarchive(threadId: string): Promise<void> {
    await unarchiveThread(this.request.bind(this), threadId);
  }

  async threadUnsubscribe(threadId: string): Promise<void> {
    await unsubscribeThread(this.request.bind(this), threadId);
  }

  async turnStart(threadId: string, text: string): Promise<string> {
    return await startTurn(this.request.bind(this), threadId, text);
  }

  async turnInterrupt(threadId: string, turnId: string): Promise<void> {
    await interruptTurn(this.request.bind(this), threadId, turnId);
  }

  async turnSteer(threadId: string, expectedTurnId: string, text: string): Promise<void> {
    await steerTurn(this.request.bind(this), threadId, expectedTurnId, text);
  }

  async threadCompactStart(threadId: string): Promise<void> {
    await startThreadCompaction(this.request.bind(this), threadId);
  }

  async threadShellCommand(threadId: string, command: string): Promise<void> {
    await sendThreadShellCommand(this.request.bind(this), threadId, command);
  }

  respond(id: string | number, result: unknown): void {
    this.transport.respond(id, result);
  }

  respondError(id: string | number, code: number, message: string, data?: unknown): void {
    this.transport.respondError(id, code, message, data);
  }

  private async request<TParams extends object, TResult = unknown>(
    method: string,
    params: TParams
  ): Promise<TResult> {
    return await this.transport.request<TParams, TResult>(method, params);
  }

  private notify<TParams extends object>(method: string, params: TParams): void {
    this.transport.notify(method, params);
  }
}

import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { EventEmitter } from "node:events";
import process from "node:process";
import type {
  AppServerThread,
  JsonRpcNotification,
  JsonRpcRequest,
  JsonRpcResponse,
  JsonRpcServerRequest,
  ThreadListResult,
  ThreadReadResult,
  ThreadResumeResult,
  ThreadStartResponse,
  TurnStartResult,
} from "./appServerTypes.js";

interface JsonRpcBase {
  jsonrpc?: "2.0";
}

interface JsonRpcErrorPayload {
  code: number;
  message: string;
  data?: unknown;
}

type InboundMessage =
  | (JsonRpcResponse & JsonRpcBase)
  | (JsonRpcNotification & JsonRpcBase)
  | (JsonRpcServerRequest & JsonRpcBase);

const THREAD_LIST_PAGE_SIZE = 100;
const THREAD_LIST_MAX_PAGES = 10;
const THREAD_LIST_SOURCE_KINDS = ["cli", "vscode", "appServer", "unknown"];

function isResponse(message: InboundMessage): message is JsonRpcResponse {
  return "id" in message && !("method" in message);
}

function isServerRequest(message: InboundMessage): message is JsonRpcServerRequest {
  return "id" in message && "method" in message;
}

function isNotification(message: InboundMessage): message is JsonRpcNotification {
  return "method" in message && !("id" in message);
}

function getErrorMessage(error: JsonRpcErrorPayload): string {
  return error.message;
}

export class AppServerClient {
  private child: ChildProcessWithoutNullStreams | null = null;
  private nextId = 1;
  private readonly pending = new Map<
    number,
    {
      resolve: (value: unknown) => void;
      reject: (error: Error) => void;
    }
  >();
  private readonly events = new EventEmitter();
  private stdoutBuffer = "";
  private initialized = false;

  async start(): Promise<void> {
    if (this.child) {
      return;
    }

    const command = process.platform === "win32"
      ? {
          file: "cmd.exe",
          args: ["/d", "/s", "/c", "codex", "app-server"],
        }
      : {
          file: "codex",
          args: ["app-server"],
        };

    this.child = spawn(command.file, command.args, {
      cwd: process.cwd(),
      stdio: "pipe",
      shell: false,
      windowsHide: true,
    });

    this.child.stdout.setEncoding("utf8");
    this.child.stdout.on("data", (chunk: string) => {
      this.stdoutBuffer += chunk;
      this.drainStdout();
    });

    this.child.stderr.setEncoding("utf8");
    this.child.stderr.on("data", (chunk: string) => {
      const text = chunk.trim();
      if (text.length > 0) {
        console.error("[app-server]", text);
      }
    });

    this.child.on("exit", (code, signal) => {
      const error = new Error(`app-server exited: code=${code ?? "null"} signal=${signal ?? "null"}`);
      for (const { reject } of this.pending.values()) {
        reject(error);
      }
      this.pending.clear();
      this.child = null;
      this.initialized = false;
      this.events.emit("exit", { code, signal });
    });

    const init = await this.request<
      {
        clientInfo: {
          name: string;
          version: string;
        };
        capabilities: null;
      },
      { userAgent: string }
    >("initialize", {
      clientInfo: {
        name: "codex-mobile-desktop-gateway",
        version: "0.1.0",
      },
      capabilities: null,
    });
    this.notify("initialized", {});
    this.initialized = true;
    console.log(`[app-server] initialized userAgent=${init.userAgent}`);
  }

  onNotification(listener: (message: JsonRpcNotification) => void): () => void {
    this.events.on("notification", listener);
    return () => this.events.off("notification", listener);
  }

  onRequest(listener: (message: JsonRpcServerRequest) => void): () => void {
    this.events.on("request", listener);
    return () => this.events.off("request", listener);
  }

  onExit(listener: (event: { code: number | null; signal: NodeJS.Signals | null }) => void): () => void {
    this.events.on("exit", listener);
    return () => this.events.off("exit", listener);
  }

  async threadList(archived = false): Promise<AppServerThread[]> {
    const threads: AppServerThread[] = [];
    let cursor: string | null = null;
    for (let page = 0; page < THREAD_LIST_MAX_PAGES; page += 1) {
      const result = (await this.request("thread/list", {
        cursor,
        limit: THREAD_LIST_PAGE_SIZE,
        sortKey: "updated_at",
        sortDirection: "desc",
        sourceKinds: THREAD_LIST_SOURCE_KINDS,
        archived,
      })) as ThreadListResult;
      threads.push(...result.data);
      cursor = result.nextCursor ?? null;
      if (!cursor) {
        break;
      }
    }
    return threads;
  }

  async threadRead(threadId: string, includeTurns = true): Promise<AppServerThread> {
    const result = (await this.request("thread/read", {
      threadId,
      includeTurns,
    })) as ThreadReadResult;
    return result.thread;
  }

  async threadResume(threadId: string, options: { excludeTurns?: boolean } = {}): Promise<ThreadResumeResult> {
    return (await this.request("thread/resume", {
      threadId,
      excludeTurns: options.excludeTurns ?? false,
    })) as ThreadResumeResult;
  }

  async threadStart(cwd?: string | null): Promise<ThreadStartResponse> {
    return (await this.request("thread/start", {
      cwd: cwd ?? null,
    })) as ThreadStartResponse;
  }

  async threadSetName(threadId: string, name: string): Promise<void> {
    await this.request("thread/name/set", {
      threadId,
      name,
    });
  }

  async threadArchive(threadId: string): Promise<void> {
    await this.request("thread/archive", {
      threadId,
    });
  }

  async threadUnarchive(threadId: string): Promise<void> {
    await this.request("thread/unarchive", {
      threadId,
    });
  }

  async threadUnsubscribe(threadId: string): Promise<void> {
    await this.request("thread/unsubscribe", {
      threadId,
    });
  }

  async turnStart(threadId: string, text: string): Promise<string> {
    const result = (await this.request("turn/start", {
      threadId,
      input: [
        {
          type: "text",
          text,
          text_elements: [],
        },
      ],
    })) as TurnStartResult;
    return result.turn.id;
  }

  async turnInterrupt(threadId: string, turnId: string): Promise<void> {
    await this.request("turn/interrupt", {
      threadId,
      turnId,
    });
  }

  async turnSteer(threadId: string, expectedTurnId: string, text: string): Promise<void> {
    await this.request("turn/steer", {
      threadId,
      expectedTurnId,
      input: [
        {
          type: "text",
          text,
          text_elements: [],
        },
      ],
    });
  }

  async threadCompactStart(threadId: string): Promise<void> {
    await this.request("thread/compact/start", {
      threadId,
    });
  }

  async threadShellCommand(threadId: string, command: string): Promise<void> {
    await this.request("thread/shellCommand", {
      threadId,
      command,
    });
  }

  respond(id: string | number, result: unknown): void {
    this.writeMessage({
      jsonrpc: "2.0",
      id,
      result,
    } as JsonRpcResponse);
  }

  private async request<TParams extends object, TResult = unknown>(
    method: string,
    params: TParams
  ): Promise<TResult> {
    const id = this.nextId++;
    const payload: JsonRpcRequest<TParams> = {
      jsonrpc: "2.0",
      id,
      method,
      params,
    };

    return await new Promise<TResult>((resolve, reject) => {
      this.pending.set(id, {
        resolve: (value) => resolve(value as TResult),
        reject,
      });
      this.writeMessage(payload);
    });
  }

  private notify<TParams extends object>(method: string, params: TParams): void {
    this.writeMessage({
      jsonrpc: "2.0",
      method,
      params,
    });
  }

  private writeMessage(message: JsonRpcRequest | JsonRpcNotification | JsonRpcResponse) {
    if (!this.child?.stdin.writable) {
      throw new Error("app-server stdin unavailable");
    }
    this.child.stdin.write(`${JSON.stringify(message)}\n`);
  }

  private drainStdout() {
    while (true) {
      const newlineIndex = this.stdoutBuffer.indexOf("\n");
      if (newlineIndex < 0) {
        return;
      }
      const line = this.stdoutBuffer.slice(0, newlineIndex).trim();
      this.stdoutBuffer = this.stdoutBuffer.slice(newlineIndex + 1);
      if (line.length === 0) {
        continue;
      }
      this.handleMessage(line);
    }
  }

  private handleMessage(line: string) {
    const trimmed = line.trim();
    if (!trimmed.startsWith("{")) {
      return;
    }
    let parsed: InboundMessage;
    try {
      parsed = JSON.parse(trimmed) as InboundMessage;
    } catch (error) {
      console.error("[app-server] invalid json:", line, error);
      return;
    }

    if (isServerRequest(parsed)) {
      this.events.emit("request", parsed);
      return;
    }

    if (isResponse(parsed) && typeof parsed.id === "number") {
      const pending = this.pending.get(parsed.id);
      if (!pending) {
        return;
      }
      this.pending.delete(parsed.id);
      if (parsed.error) {
        pending.reject(new Error(getErrorMessage(parsed.error)));
        return;
      }
      pending.resolve(parsed.result);
      return;
    }

    if (isNotification(parsed)) {
      this.events.emit("notification", parsed);
    }
  }
}

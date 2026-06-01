import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { EventEmitter } from "node:events";
import type {
  JsonRpcNotification,
  JsonRpcRequest,
  JsonRpcResponse,
  JsonRpcServerRequest,
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

export interface AppServerCommand {
  file: string;
  args: string[];
}

type PendingRequest = {
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
};

export function resolveAppServerCommand(platform: NodeJS.Platform): AppServerCommand {
  return platform === "win32"
    ? {
        file: "cmd.exe",
        args: ["/d", "/s", "/c", "codex", "app-server"],
      }
    : {
        file: "codex",
        args: ["app-server"],
      };
}

export function isResponse(message: InboundMessage): message is JsonRpcResponse {
  return "id" in message && !("method" in message);
}

export function isServerRequest(message: InboundMessage): message is JsonRpcServerRequest {
  return "id" in message && "method" in message;
}

export function isNotification(message: InboundMessage): message is JsonRpcNotification {
  return "method" in message && !("id" in message);
}

function getErrorMessage(error: JsonRpcErrorPayload): string {
  return error.message;
}

export class AppServerTransport {
  private child: ChildProcessWithoutNullStreams | null = null;
  private nextId = 1;
  private readonly pending = new Map<number, PendingRequest>();
  private readonly events = new EventEmitter();
  private stdoutBuffer = "";

  start(command: AppServerCommand, cwd: string): boolean {
    if (this.child) {
      return false;
    }

    this.child = spawn(command.file, command.args, {
      cwd,
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
      this.events.emit("exit", { code, signal });
    });
    return true;
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

  async request<TParams extends object, TResult = unknown>(
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

  notify<TParams extends object>(method: string, params: TParams): void {
    this.writeMessage({
      jsonrpc: "2.0",
      method,
      params,
    });
  }

  respond(id: string | number, result: unknown): void {
    this.writeMessage({
      jsonrpc: "2.0",
      id,
      result,
    } as JsonRpcResponse);
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

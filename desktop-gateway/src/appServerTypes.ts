export interface JsonRpcRequest<T = unknown> {
  jsonrpc: "2.0";
  id?: string | number;
  method: string;
  params?: T;
}

export interface JsonRpcResponse<T = unknown> {
  jsonrpc?: "2.0";
  id: string | number;
  result?: T;
  error?: {
    code: number;
    message: string;
    data?: unknown;
  };
}

export interface JsonRpcNotification<T = unknown> {
  jsonrpc?: "2.0";
  method: string;
  params: T;
}

export interface JsonRpcServerRequest<T = unknown> {
  jsonrpc?: "2.0";
  id: string | number;
  method: string;
  params: T;
}

export interface AppServerThread {
  id: string;
  preview: string;
  status: AppServerThreadStatus;
  cwd: string;
  updatedAt: number;
  name: string | null;
  turns: AppServerTurn[];
  modelProvider: string;
}

export interface AppServerTurn {
  id: string;
  status: AppServerTurnStatus;
  itemsView?: "notLoaded" | "summary" | "full";
  items: AppServerThreadItem[];
}

export type AppServerThreadStatus =
  | string
  | { type: "notLoaded" | "idle" | "systemError" }
  | { type: "active"; activeFlags?: string[] };

export type AppServerTurnStatus = string;

export type AppServerThreadItem =
  | {
      type: "userMessage";
      id: string;
      content: Array<{ type: "text"; text: string }>;
    }
  | {
      type: "agentMessage";
      id: string;
      text: string;
      phase?: string | null;
    }
  | {
      type: "reasoning";
      id: string;
      summary?: string[] | null;
      content?: string[] | null;
    }
  | {
      type: "commandExecution";
      id: string;
      command: string;
      aggregatedOutput?: string | null;
      status: string;
    }
  | {
      type: "fileChange";
      id: string;
      changes: Array<{ path?: string; kind?: string; diff?: string | null }>;
      status: string;
    }
  | {
      type: "plan";
      id: string;
      text: string;
    }
  | {
      type: "mcpToolCall";
      id: string;
      server: string;
      tool: string;
      status: string;
      result?: unknown | null;
      error?: unknown | null;
    }
  | {
      type: "dynamicToolCall";
      id: string;
      namespace?: string | null;
      tool: string;
      status: string;
      arguments?: unknown;
      contentItems?: unknown[] | null;
      success?: boolean | null;
    }
  | {
      type: "webSearch";
      id: string;
      query: string;
      action?: unknown | null;
    }
  | {
      type: "imageView";
      id: string;
      path: string;
    }
  | {
      type: "imageGeneration";
      id: string;
      status: string;
      result: string;
      revisedPrompt?: string | null;
      savedPath?: string | null;
    }
  | {
      type: "collabAgentToolCall";
      id: string;
      tool: string;
      status: string;
      senderThreadId?: string;
      receiverThreadIds?: string[];
      prompt?: string | null;
    }
  | {
      type: "hookPrompt";
      id: string;
      fragments?: Array<{ type?: string; text?: string }>;
    }
  | {
      type: "enteredReviewMode" | "exitedReviewMode" | "contextCompaction";
      id: string;
      review?: string;
    }
  | ({
      type: string;
      id: string;
    } & Record<string, unknown>);

export type AppServerApprovalPolicy =
  | "untrusted"
  | "on-failure"
  | "on-request"
  | "never"
  | {
      granular: {
        sandbox_approval: boolean;
        rules: boolean;
        skill_approval: boolean;
        request_permissions: boolean;
        mcp_elicitations: boolean;
      };
    };

export type AppServerSandboxPolicy =
  | { type: "dangerFullAccess" }
  | { type: "readOnly"; networkAccess: boolean }
  | { type: "externalSandbox"; networkAccess: "restricted" | "enabled" }
  | {
      type: "workspaceWrite";
      writableRoots: string[];
      networkAccess: boolean;
      excludeTmpdirEnvVar: boolean;
      excludeSlashTmp: boolean;
    };

export interface InitializeResult {
  userAgent: string;
  codexHome: string;
  platformFamily: string;
  platformOs: string;
}

export interface ThreadListResult {
  data: AppServerThread[];
  nextCursor: string | null;
}

export interface ThreadReadResult {
  thread: AppServerThread;
}

export interface ThreadResumeResult {
  thread: AppServerThread;
  model: string;
  modelProvider: string;
  serviceTier: string | null;
  cwd: string;
  instructionSources: string[];
  approvalPolicy: AppServerApprovalPolicy;
  approvalsReviewer: "user" | "auto_review" | "guardian_subagent";
  sandbox: AppServerSandboxPolicy;
  reasoningEffort: string | null;
}

export interface ThreadStartResponse extends ThreadResumeResult {}

export interface TurnStartResult {
  turn: AppServerTurn;
}

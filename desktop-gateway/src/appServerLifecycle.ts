import type { AppServerTransport } from "./appServerTransport.js";
import { resolveAppServerCommand } from "./appServerTransport.js";

interface InitializeResult {
  userAgent: string;
}

type RequestFn = <TParams extends object, TResult = unknown>(
  method: string,
  params: TParams
) => Promise<TResult>;

type NotifyFn = <TParams extends object>(method: string, params: TParams) => void;

export async function startAppServerSession(
  transport: AppServerTransport,
  request: RequestFn,
  notify: NotifyFn,
  platform: NodeJS.Platform,
  cwd: string
): Promise<InitializeResult> {
  transport.start(resolveAppServerCommand(platform), cwd);

  const init = await request<
    {
      clientInfo: {
        name: string;
        version: string;
      };
      capabilities: null;
    },
    InitializeResult
  >("initialize", {
    clientInfo: {
      name: "codexapp-desktop-gateway",
      version: "0.1.0",
    },
    capabilities: null,
  });
  notify("initialized", {});
  return init;
}

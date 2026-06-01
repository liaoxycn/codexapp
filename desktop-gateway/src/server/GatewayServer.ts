import process from "node:process";
import { createServer } from "node:http";
import { WebSocketServer } from "ws";
import { sendStatus } from "./backendActions.js";
import {
  attachGatewayClientSocket,
  createGatewayClientContext,
  logGatewayConnectionPathMismatch,
} from "./clientConnection.js";
import { GatewayBackendController } from "./GatewayBackendController.js";
import { isExpectedGatewayPath, normalizeGatewayPath } from "./gatewayPaths.js";
import { handlePokeHttpRequest, restartCodexDesktop } from "./httpPoke.js";
import type { ClientContext, GatewayServerOptions } from "./types.js";

export class GatewayServer {
  private readonly clients = new Set<ClientContext>();
  private readonly controller: GatewayBackendController;

  constructor(private readonly options: GatewayServerOptions) {
    this.controller = new GatewayBackendController(options);
  }

  async start(): Promise<void> {
    await this.controller.startRealBackend();
    const expectedPath = normalizeGatewayPath(this.options.path);
    const server = createServer((request, response) => {
      void handlePokeHttpRequest(request, response, (payload) => restartCodexDesktop(payload));
    });
    const wss = new WebSocketServer({ noServer: true });

    server.on("upgrade", (request, socket, head) => {
      if (!isExpectedGatewayPath(request.url, expectedPath)) {
        socket.destroy();
        return;
      }
      wss.handleUpgrade(request, socket, head, (ws) => {
        wss.emit("connection", ws, request);
      });
    });

    wss.on("connection", (socket, request) => {
      logGatewayConnectionPathMismatch(request, expectedPath);
      console.log("[gateway] android client connected");
      const context = createGatewayClientContext({
        backend: () => this.controller.backend(),
        socket,
        sendSnapshot: (nextContext, snapshot) => this.controller.sendSnapshot(nextContext, snapshot),
      });
      this.clients.add(context);
      sendStatus(socket, {
        type: "status",
        status: "connecting",
        detail: "desktop gateway 已连接，等待 hello",
      });
      attachGatewayClientSocket({
        context,
        handleMessage: (nextContext, raw) => this.controller.handleMessage(nextContext, raw),
        onClosed: (nextContext) => {
          this.clients.delete(nextContext);
        },
      });
    });

    server.listen(this.options.port, this.options.host, () => {
      console.log(`[gateway] listening on ws://0.0.0.0:${this.options.port}${this.options.path}`);
      console.log(`[gateway] listening on http://0.0.0.0:${this.options.port}/poke`);
      console.log(`[gateway] pair token ${this.options.pairToken ? "enabled" : "disabled"}`);
      console.log(`[gateway] backend ${this.controller.backendLabel()}`);
    });

    wss.on("error", (error) => {
      console.error("[gateway] server error:", error.message);
    });

    process.on("SIGINT", () => {
      for (const client of this.clients) {
        client.socket.close(1001, "server shutdown");
      }
      wss.close();
      server.close();
      process.exit(0);
    });
  }
}

#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { execSync } from "child_process";
import { eventMonitor } from "./event-monitor.js";
import { registerWebviewTools } from "./tools/webview.js";
import { registerImageTools } from "./tools/image.js";
import { registerDomTools } from "./tools/dom.js";
import { registerViewTools } from "./tools/view.js";
import { registerInspectorTools } from "./tools/inspector.js";

try {
  execSync("adb forward tcp:8080 tcp:8080", { stdio: "ignore" });
} catch {
  // adb not available or no device connected, ignore
}

const server = new McpServer({
  name: "client-tools",
  version: "0.1.0",
});

registerWebviewTools(server);
registerImageTools(server);
registerDomTools(server);
registerViewTools(server);
registerInspectorTools(server);

eventMonitor.start();

const transport = new StdioServerTransport();
await server.connect(transport);

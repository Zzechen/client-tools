#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { registerWebviewTools } from "./tools/webview.js";
import { registerImageTools } from "./tools/image.js";
import { registerDomTools } from "./tools/dom.js";
import { registerViewTools } from "./tools/view.js";
import { registerInspectorTools } from "./tools/inspector.js";
import { registerPageTools } from "./tools/page.js";
import { registerDesignTools } from "./tools/design.js";
import { registerMockTools } from "./tools/mock.js";

const server = new McpServer({
  name: "client-tools",
  version: "0.1.0",
});

registerWebviewTools(server);
registerImageTools(server);
registerDomTools(server);
registerViewTools(server);
registerInspectorTools(server);
registerPageTools(server);
registerDesignTools(server);
registerMockTools(server);

const transport = new StdioServerTransport();
await server.connect(transport);

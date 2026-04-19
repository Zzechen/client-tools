# MCP Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 TypeScript 实现一个 MCP Server，通过 stdio transport 将 Android SDK 的 13 个 HTTP 接口封装为 MCP 工具函数，供 Claude / Cursor / Codex 等 Agent 调用。

**Architecture:** `index.ts` 注册 stdio transport 和所有工具；`sdk-client.ts` 统一封装 HTTP 请求（含超时/错误处理）；`event-monitor.ts` 后台维护 SSE 长连接缓存最新页面事件；工具按模块拆分到 `tools/` 下 5 个文件，每个文件注册工具并调用 sdk-client。

**Tech Stack:** Node.js 18+、`@modelcontextprotocol/sdk`、`zod`、TypeScript 5.x

---

## 文件结构

| 操作 | 路径 | 职责 |
|------|------|------|
| 新增 | `mcp/package.json` | 依赖声明，bin 入口 |
| 新增 | `mcp/tsconfig.json` | TypeScript 配置 |
| 新增 | `mcp/src/sdk-client.ts` | HTTP 请求封装，统一超时/错误处理 |
| 新增 | `mcp/src/event-monitor.ts` | SSE 后台连接，缓存最新事件 |
| 新增 | `mcp/src/tools/webview.ts` | 注册 push_html / show_webview / hide_overlay / adjust_overlay |
| 新增 | `mcp/src/tools/image.ts` | 注册 push_image / show_image |
| 新增 | `mcp/src/tools/dom.ts` | 注册 dom_all / dom_by_id |
| 新增 | `mcp/src/tools/view.ts` | 注册 get_node / modify_view |
| 新增 | `mcp/src/tools/inspector.ts` | 注册 list_files / list_images / get_last_event |
| 新增 | `mcp/src/index.ts` | 入口：创建 MCP Server，注册所有工具，启动 stdio |

---

### Task 1: 项目脚手架（package.json + tsconfig）

**Files:**
- Create: `mcp/package.json`
- Create: `mcp/tsconfig.json`

- [ ] **Step 1: 创建 mcp/package.json**

```json
{
  "name": "@client-tools/mcp",
  "version": "0.1.0",
  "description": "MCP Server for Client Tools Android SDK",
  "type": "module",
  "main": "./dist/index.js",
  "bin": {
    "client-tools-mcp": "./dist/index.js"
  },
  "scripts": {
    "build": "tsc",
    "dev": "node --loader ts-node/esm src/index.ts",
    "start": "node dist/index.js"
  },
  "dependencies": {
    "@modelcontextprotocol/sdk": "^1.10.2",
    "zod": "^3.23.8"
  },
  "devDependencies": {
    "@types/node": "^22.0.0",
    "typescript": "^5.5.0"
  }
}
```

- [ ] **Step 2: 创建 mcp/tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "Node16",
    "moduleResolution": "Node16",
    "outDir": "./dist",
    "rootDir": "./src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist"]
}
```

- [ ] **Step 3: 安装依赖**

```bash
cd mcp && npm install
```

Expected: `node_modules/` 创建，无报错

- [ ] **Step 4: Commit**

```bash
git add mcp/package.json mcp/tsconfig.json mcp/package-lock.json
git commit -m "feat(mcp): init TypeScript project scaffold"
```

---

### Task 2: sdk-client.ts（HTTP 请求封装）

**Files:**
- Create: `mcp/src/sdk-client.ts`

- [ ] **Step 1: 创建 mcp/src/sdk-client.ts**

```typescript
const PORT = process.env.CLIENT_TOOLS_PORT ?? "8080";
const BASE_URL = `http://127.0.0.1:${PORT}`;
const DEFAULT_TIMEOUT_MS = 5000;
const DOM_TIMEOUT_MS = 8000;

export class SdkUnreachableError extends Error {
  constructor(cause: unknown) {
    super(`SDK unreachable: ${cause instanceof Error ? cause.message : String(cause)}`);
  }
}

async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } catch (e) {
    if ((e as Error).name === "AbortError") throw new SdkUnreachableError("request timed out");
    throw new SdkUnreachableError(e);
  } finally {
    clearTimeout(timer);
  }
}

export async function sdkGet(path: string): Promise<unknown> {
  const timeoutMs = path.startsWith("/dom") ? DOM_TIMEOUT_MS : DEFAULT_TIMEOUT_MS;
  const res = await fetchWithTimeout(`${BASE_URL}${path}`, { method: "GET" }, timeoutMs);
  return res.json();
}

export async function sdkPost(path: string, body: unknown): Promise<unknown> {
  const res = await fetchWithTimeout(
    `${BASE_URL}${path}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
    DEFAULT_TIMEOUT_MS
  );
  return res.json();
}

export function sdkEventUrl(): string {
  return `${BASE_URL}/api/events`;
}
```

- [ ] **Step 2: 编译验证**

```bash
cd mcp && npx tsc --noEmit
```

Expected: 无报错

- [ ] **Step 3: Commit**

```bash
git add mcp/src/sdk-client.ts
git commit -m "feat(mcp): add sdk-client with HTTP request helpers"
```

---

### Task 3: event-monitor.ts（SSE 后台连接）

**Files:**
- Create: `mcp/src/event-monitor.ts`

- [ ] **Step 1: 创建 mcp/src/event-monitor.ts**

```typescript
import { sdkEventUrl } from "./sdk-client.js";

interface PageChangedEvent {
  event: string;
  activityName: string;
  timestamp: string;
}

class EventMonitor {
  private lastEvent: PageChangedEvent | null = null;
  private retryDelayMs = 1000;
  private stopped = false;

  start(): void {
    this.connect();
  }

  stop(): void {
    this.stopped = true;
  }

  getLastEvent(): PageChangedEvent | null {
    return this.lastEvent;
  }

  private connect(): void {
    if (this.stopped) return;

    fetch(sdkEventUrl())
      .then(async (res) => {
        if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`);
        this.retryDelayMs = 1000; // 连接成功重置退避
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop() ?? "";
          for (const line of lines) {
            if (line.startsWith("data:")) {
              try {
                const data = JSON.parse(line.slice(5).trim());
                if (data.event === "page_changed") {
                  this.lastEvent = data as PageChangedEvent;
                }
              } catch {
                // ignore malformed line
              }
            }
          }
        }
      })
      .catch(() => {
        // 连接失败，指数退避重连
      })
      .finally(() => {
        if (!this.stopped) {
          setTimeout(() => this.connect(), this.retryDelayMs);
          this.retryDelayMs = Math.min(this.retryDelayMs * 2, 30000);
        }
      });
  }
}

export const eventMonitor = new EventMonitor();
```

- [ ] **Step 2: 编译验证**

```bash
cd mcp && npx tsc --noEmit
```

Expected: 无报错

- [ ] **Step 3: Commit**

```bash
git add mcp/src/event-monitor.ts
git commit -m "feat(mcp): add event-monitor with SSE reconnect and backoff"
```

---

### Task 4: tools/webview.ts

**Files:**
- Create: `mcp/src/tools/webview.ts`

- [ ] **Step 1: 创建 mcp/src/tools/webview.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkPost } from "../sdk-client.js";

export function registerWebviewTools(server: McpServer): void {
  server.tool(
    "push_html",
    "推送 HTML 内容到设备 WebView 叠加层并自动显示",
    {
      tag: z.string().describe("页面标识，如 login、home"),
      html: z.string().describe("完整 HTML 内容"),
      timestamp: z.string().optional().describe("时间戳，格式 MMdd-HHmm，缺省自动生成"),
    },
    async ({ tag, html, timestamp }) => {
      const result = await sdkPost("/webview/push-html", { tag, html, timestamp });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );

  server.tool(
    "show_webview",
    "切换显示设备上已保存的 HTML 文件",
    {
      tag: z.string().describe("页面标识"),
      timestamp: z.string().describe("时间戳，格式 MMdd-HHmm"),
    },
    async ({ tag, timestamp }) => {
      const result = await sdkPost("/webview/show", { tag, timestamp });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );

  server.tool(
    "hide_overlay",
    "隐藏 WebView 或图片叠加层",
    {
      type: z.enum(["webview", "image"]).optional().describe("缺省隐藏当前 activeTab"),
    },
    async ({ type }) => {
      const result = await sdkPost("/inspector/hide", type ? { type } : {});
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );

  server.tool(
    "adjust_overlay",
    "调整叠加层偏移量（增量 dp）和透明度（绝对值 0~1）",
    {
      type: z.enum(["webview", "image"]).optional().describe("缺省操作当前 activeTab"),
      offsetX: z.number().optional().describe("X 轴偏移增量，单位 dp"),
      offsetY: z.number().optional().describe("Y 轴偏移增量，单位 dp"),
      opacity: z.number().min(0).max(1).optional().describe("透明度绝对值 0.0~1.0"),
    },
    async ({ type, offsetX, offsetY, opacity }) => {
      const result = await sdkPost("/inspector/adjust", { type, offsetX, offsetY, opacity });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );
}
```

- [ ] **Step 2: 编译验证**

```bash
cd mcp && npx tsc --noEmit
```

Expected: 无报错

- [ ] **Step 3: Commit**

```bash
git add mcp/src/tools/webview.ts
git commit -m "feat(mcp): add webview tools (push_html, show_webview, hide_overlay, adjust_overlay)"
```

---

### Task 5: tools/image.ts

**Files:**
- Create: `mcp/src/tools/image.ts`

- [ ] **Step 1: 创建 mcp/src/tools/image.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkPost } from "../sdk-client.js";

export function registerImageTools(server: McpServer): void {
  server.tool(
    "push_image",
    "推送 base64 编码图片到设备叠加层并自动显示",
    {
      tag: z.string().describe("图片标识，如 login、home"),
      image: z.string().describe("base64 编码的图片内容"),
      ext: z.enum(["png", "jpg"]).optional().describe("图片格式，缺省 png"),
      timestamp: z.string().optional().describe("时间戳，格式 MMdd-HHmm，缺省自动生成"),
    },
    async ({ tag, image, ext, timestamp }) => {
      const result = await sdkPost("/inspector/push-image", { tag, image, ext, timestamp });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );

  server.tool(
    "show_image",
    "切换显示设备上已保存的图片",
    {
      tag: z.string().describe("图片标识"),
      timestamp: z.string().describe("时间戳，格式 MMdd-HHmm"),
    },
    async ({ tag, timestamp }) => {
      const result = await sdkPost("/inspector/show-image", { tag, timestamp });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );
}
```

- [ ] **Step 2: 编译验证**

```bash
cd mcp && npx tsc --noEmit
```

Expected: 无报错

- [ ] **Step 3: Commit**

```bash
git add mcp/src/tools/image.ts
git commit -m "feat(mcp): add image tools (push_image, show_image)"
```

---

### Task 6: tools/dom.ts

**Files:**
- Create: `mcp/src/tools/dom.ts`

- [ ] **Step 1: 创建 mcp/src/tools/dom.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkGet } from "../sdk-client.js";

export function registerDomTools(server: McpServer): void {
  server.tool(
    "dom_all",
    "返回 WebView 中所有 DOM 节点，坐标为屏幕绝对坐标（含 WebView 偏移换算）",
    {},
    async () => {
      const result = await sdkGet("/dom/all");
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );

  server.tool(
    "dom_by_id",
    "按 id 查询 WebView 中单个 DOM 节点的屏幕坐标和尺寸",
    {
      id: z.string().describe("DOM 元素的 id 属性值"),
    },
    async ({ id }) => {
      const result = await sdkGet(`/dom/${encodeURIComponent(id)}`);
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );
}
```

- [ ] **Step 2: 编译验证**

```bash
cd mcp && npx tsc --noEmit
```

Expected: 无报错

- [ ] **Step 3: Commit**

```bash
git add mcp/src/tools/dom.ts
git commit -m "feat(mcp): add dom tools (dom_all, dom_by_id)"
```

---

### Task 7: tools/view.ts

**Files:**
- Create: `mcp/src/tools/view.ts`

- [ ] **Step 1: 创建 mcp/src/tools/view.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkGet, sdkPost } from "../sdk-client.js";

const ViewPropsSchema = z.object({
  marginTopDiffDp: z.number().optional(),
  marginBottomDiffDp: z.number().optional(),
  marginLeftDiffDp: z.number().optional(),
  marginRightDiffDp: z.number().optional(),
  paddingTopDiffDp: z.number().optional(),
  paddingBottomDiffDp: z.number().optional(),
  paddingLeftDiffDp: z.number().optional(),
  paddingRightDiffDp: z.number().optional(),
  widthDp: z.number().optional(),
  heightDp: z.number().optional(),
}).describe("View 布局属性，margin/padding 为差值（dp），width/height 为绝对值（dp）");

export function registerViewTools(server: McpServer): void {
  server.tool(
    "get_node",
    "查询 Android 原生 View 节点的屏幕位置和尺寸",
    {
      id: z.string().describe("Android View 的 resource id（不含包名前缀，如 btn_login）"),
    },
    async ({ id }) => {
      const result = await sdkGet(`/api/nodes/${encodeURIComponent(id)}`);
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );

  server.tool(
    "modify_view",
    "修改 Android View 的布局属性（margin/padding/size），单位 dp",
    {
      id: z.string().describe("Android View 的 resource id"),
      props: ViewPropsSchema,
    },
    async ({ id, props }) => {
      const result = await sdkPost("/api/modify", { id, props });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );
}
```

- [ ] **Step 2: 编译验证**

```bash
cd mcp && npx tsc --noEmit
```

Expected: 无报错

- [ ] **Step 3: Commit**

```bash
git add mcp/src/tools/view.ts
git commit -m "feat(mcp): add view tools (get_node, modify_view)"
```

---

### Task 8: tools/inspector.ts

**Files:**
- Create: `mcp/src/tools/inspector.ts`

- [ ] **Step 1: 创建 mcp/src/tools/inspector.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { sdkGet } from "../sdk-client.js";
import { eventMonitor } from "../event-monitor.js";

export function registerInspectorTools(server: McpServer): void {
  server.tool(
    "list_files",
    "返回设备上已保存的 HTML 文件列表",
    {},
    async () => {
      const result = await sdkGet("/webview/files");
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );

  server.tool(
    "list_images",
    "返回设备上已保存的图片列表",
    {},
    async () => {
      const result = await sdkGet("/inspector/images");
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );

  server.tool(
    "get_last_event",
    "返回最新页面切换事件快照（由后台 SSE 连接缓存）。无事件时返回 { event: null }",
    {},
    async () => {
      const lastEvent = eventMonitor.getLastEvent();
      const result = lastEvent ?? { event: null };
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );
}
```

- [ ] **Step 2: 编译验证**

```bash
cd mcp && npx tsc --noEmit
```

Expected: 无报错

- [ ] **Step 3: Commit**

```bash
git add mcp/src/tools/inspector.ts
git commit -m "feat(mcp): add inspector tools (list_files, list_images, get_last_event)"
```

---

### Task 9: index.ts（入口 + 错误处理）

**Files:**
- Create: `mcp/src/index.ts`

- [ ] **Step 1: 创建 mcp/src/index.ts**

```typescript
#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { SdkUnreachableError } from "./sdk-client.js";
import { eventMonitor } from "./event-monitor.js";
import { registerWebviewTools } from "./tools/webview.js";
import { registerImageTools } from "./tools/image.js";
import { registerDomTools } from "./tools/dom.js";
import { registerViewTools } from "./tools/view.js";
import { registerInspectorTools } from "./tools/inspector.js";

const server = new McpServer({
  name: "client-tools",
  version: "0.1.0",
});

registerWebviewTools(server);
registerImageTools(server);
registerDomTools(server);
registerViewTools(server);
registerInspectorTools(server);

// 全局工具错误处理：捕获 SdkUnreachableError，返回 isError 而非崩溃
server.server.setRequestHandler(
  { method: "tools/call" } as never,
  async (request, extra, next: (...args: unknown[]) => Promise<unknown>) => {
    try {
      return await (next as (...args: unknown[]) => Promise<unknown>)(request, extra);
    } catch (e) {
      if (e instanceof SdkUnreachableError) {
        return {
          isError: true,
          content: [{ type: "text", text: e.message }],
        };
      }
      throw e;
    }
  }
);

eventMonitor.start();

const transport = new StdioServerTransport();
await server.connect(transport);
```

注意：`@modelcontextprotocol/sdk` 的 `McpServer` 已内置 tool 级错误处理——工具 handler 内 throw 的错误会自动包装为 `isError: true` 响应。因此实际上不需要手动 setRequestHandler，直接在每个工具 handler 中 catch `SdkUnreachableError` 即可。将 index.ts 简化为：

```typescript
#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { eventMonitor } from "./event-monitor.js";
import { registerWebviewTools } from "./tools/webview.js";
import { registerImageTools } from "./tools/image.js";
import { registerDomTools } from "./tools/dom.js";
import { registerViewTools } from "./tools/view.js";
import { registerInspectorTools } from "./tools/inspector.js";

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
```

同时在每个工具 handler 外层加 try-catch（以 webview.ts 的 push_html 为例，其他工具同理）：

```typescript
async ({ tag, html, timestamp }) => {
  try {
    const result = await sdkPost("/webview/push-html", { tag, html, timestamp });
    return { content: [{ type: "text", text: JSON.stringify(result) }] };
  } catch (e) {
    return {
      isError: true,
      content: [{ type: "text", text: e instanceof Error ? e.message : String(e) }],
    };
  }
}
```

**实际执行时**：先写简洁版 index.ts（不含 setRequestHandler），再在 Task 4-8 的每个工具 handler 加 try-catch。

- [ ] **Step 2: 在 tools/webview.ts 每个 handler 加 try-catch**

打开 `mcp/src/tools/webview.ts`，将 4 个工具的 handler 都包裹 try-catch：

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkPost } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerWebviewTools(server: McpServer): void {
  server.tool(
    "push_html",
    "推送 HTML 内容到设备 WebView 叠加层并自动显示",
    {
      tag: z.string().describe("页面标识，如 login、home"),
      html: z.string().describe("完整 HTML 内容"),
      timestamp: z.string().optional().describe("时间戳，格式 MMdd-HHmm，缺省自动生成"),
    },
    async ({ tag, html, timestamp }) => {
      try {
        const result = await sdkPost("/webview/push-html", { tag, html, timestamp });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "show_webview",
    "切换显示设备上已保存的 HTML 文件",
    {
      tag: z.string().describe("页面标识"),
      timestamp: z.string().describe("时间戳，格式 MMdd-HHmm"),
    },
    async ({ tag, timestamp }) => {
      try {
        const result = await sdkPost("/webview/show", { tag, timestamp });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "hide_overlay",
    "隐藏 WebView 或图片叠加层",
    {
      type: z.enum(["webview", "image"]).optional().describe("缺省隐藏当前 activeTab"),
    },
    async ({ type }) => {
      try {
        const result = await sdkPost("/inspector/hide", type ? { type } : {});
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "adjust_overlay",
    "调整叠加层偏移量（增量 dp）和透明度（绝对值 0~1）",
    {
      type: z.enum(["webview", "image"]).optional().describe("缺省操作当前 activeTab"),
      offsetX: z.number().optional().describe("X 轴偏移增量，单位 dp"),
      offsetY: z.number().optional().describe("Y 轴偏移增量，单位 dp"),
      opacity: z.number().min(0).max(1).optional().describe("透明度绝对值 0.0~1.0"),
    },
    async ({ type, offsetX, offsetY, opacity }) => {
      try {
        const result = await sdkPost("/inspector/adjust", { type, offsetX, offsetY, opacity });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

- [ ] **Step 3: 在 tools/image.ts、dom.ts、view.ts、inspector.ts 同样加 try-catch**

对 `image.ts`：

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkPost } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerImageTools(server: McpServer): void {
  server.tool(
    "push_image",
    "推送 base64 编码图片到设备叠加层并自动显示",
    {
      tag: z.string().describe("图片标识，如 login、home"),
      image: z.string().describe("base64 编码的图片内容"),
      ext: z.enum(["png", "jpg"]).optional().describe("图片格式，缺省 png"),
      timestamp: z.string().optional().describe("时间戳，格式 MMdd-HHmm，缺省自动生成"),
    },
    async ({ tag, image, ext, timestamp }) => {
      try {
        const result = await sdkPost("/inspector/push-image", { tag, image, ext, timestamp });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "show_image",
    "切换显示设备上已保存的图片",
    {
      tag: z.string().describe("图片标识"),
      timestamp: z.string().describe("时间戳，格式 MMdd-HHmm"),
    },
    async ({ tag, timestamp }) => {
      try {
        const result = await sdkPost("/inspector/show-image", { tag, timestamp });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

对 `dom.ts`：

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkGet } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerDomTools(server: McpServer): void {
  server.tool(
    "dom_all",
    "返回 WebView 中所有 DOM 节点，坐标为屏幕绝对坐标（含 WebView 偏移换算）",
    {},
    async () => {
      try {
        const result = await sdkGet("/dom/all");
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "dom_by_id",
    "按 id 查询 WebView 中单个 DOM 节点的屏幕坐标和尺寸",
    {
      id: z.string().describe("DOM 元素的 id 属性值"),
    },
    async ({ id }) => {
      try {
        const result = await sdkGet(`/dom/${encodeURIComponent(id)}`);
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

对 `view.ts`：

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkGet, sdkPost } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

const ViewPropsSchema = z.object({
  marginTopDiffDp: z.number().optional(),
  marginBottomDiffDp: z.number().optional(),
  marginLeftDiffDp: z.number().optional(),
  marginRightDiffDp: z.number().optional(),
  paddingTopDiffDp: z.number().optional(),
  paddingBottomDiffDp: z.number().optional(),
  paddingLeftDiffDp: z.number().optional(),
  paddingRightDiffDp: z.number().optional(),
  widthDp: z.number().optional(),
  heightDp: z.number().optional(),
}).describe("View 布局属性，margin/padding 为差值（dp），width/height 为绝对值（dp）");

export function registerViewTools(server: McpServer): void {
  server.tool(
    "get_node",
    "查询 Android 原生 View 节点的屏幕位置和尺寸",
    {
      id: z.string().describe("Android View 的 resource id（不含包名前缀，如 btn_login）"),
    },
    async ({ id }) => {
      try {
        const result = await sdkGet(`/api/nodes/${encodeURIComponent(id)}`);
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "modify_view",
    "修改 Android View 的布局属性（margin/padding/size），单位 dp",
    {
      id: z.string().describe("Android View 的 resource id"),
      props: ViewPropsSchema,
    },
    async ({ id, props }) => {
      try {
        const result = await sdkPost("/api/modify", { id, props });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

对 `inspector.ts`：

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { sdkGet } from "../sdk-client.js";
import { eventMonitor } from "../event-monitor.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerInspectorTools(server: McpServer): void {
  server.tool(
    "list_files",
    "返回设备上已保存的 HTML 文件列表",
    {},
    async () => {
      try {
        const result = await sdkGet("/webview/files");
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "list_images",
    "返回设备上已保存的图片列表",
    {},
    async () => {
      try {
        const result = await sdkGet("/inspector/images");
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "get_last_event",
    "返回最新页面切换事件快照（由后台 SSE 连接缓存）。无事件时返回 { event: null }",
    {},
    async () => {
      const lastEvent = eventMonitor.getLastEvent();
      const result = lastEvent ?? { event: null };
      return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
    }
  );
}
```

- [ ] **Step 4: 创建最终版 mcp/src/index.ts**

```typescript
#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { eventMonitor } from "./event-monitor.js";
import { registerWebviewTools } from "./tools/webview.js";
import { registerImageTools } from "./tools/image.js";
import { registerDomTools } from "./tools/dom.js";
import { registerViewTools } from "./tools/view.js";
import { registerInspectorTools } from "./tools/inspector.js";

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
```

- [ ] **Step 5: 全量编译**

```bash
cd mcp && npm run build
```

Expected: `dist/` 目录生成，`dist/index.js` 存在，无 TS 错误

- [ ] **Step 6: 冒烟测试——列出所有工具**

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | node dist/index.js 2>/dev/null
```

Expected: 返回包含 13 个工具的 JSON（push_html / show_webview / hide_overlay / adjust_overlay / push_image / show_image / dom_all / dom_by_id / get_node / modify_view / list_files / list_images / get_last_event）

- [ ] **Step 7: Commit**

```bash
git add mcp/src/index.ts mcp/src/tools/webview.ts mcp/src/tools/image.ts mcp/src/tools/dom.ts mcp/src/tools/view.ts mcp/src/tools/inspector.ts mcp/dist/
git commit -m "feat(mcp): wire all tools in index.ts and build dist"
```

---

### Task 10: 接入验证（连接真机端到端测试）

**Files:** 无新增，验证整体

- [ ] **Step 1: 确保设备已连接并 adb forward**

```bash
adb devices          # 应显示设备
adb forward tcp:8080 tcp:8080
```

- [ ] **Step 2: 运行 App**

在设备上启动 com.clienttools.demo，确保 HTTP Server 在 8080 端口监听。

- [ ] **Step 3: 用 MCP Inspector 测试工具调用**

```bash
npx @modelcontextprotocol/inspector node /path/to/client-tools/mcp/dist/index.js
```

在浏览器打开 Inspector UI，依次调用：
1. `list_files` → 应返回 `{"code":0,"data":{"files":[...]}}`
2. `push_html`，参数 `{"tag":"mcp-test","html":"<h1 id='t'>MCP</h1>"}` → 应在设备上显示 WebView
3. `dom_all` → 应返回含 `t` 节点的列表
4. `dom_by_id`，参数 `{"id":"t"}` → 应返回单节点
5. `get_last_event` → 返回 `{"event":null}` 或最新事件

- [ ] **Step 4: 测试设备不可达时的错误响应**

停止 adb forward 后调用 `list_files`：

```bash
adb forward --remove tcp:8080
echo '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_files","arguments":{}}}' | node dist/index.js 2>/dev/null
```

Expected: `{"isError":true,"content":[{"type":"text","text":"SDK unreachable: ..."}]}`

- [ ] **Step 5: 在 Claude Code 配置中注册**

在 `~/.claude/claude_desktop_config.json`（或 Claude Code settings）中添加：

```json
{
  "mcpServers": {
    "client-tools": {
      "command": "node",
      "args": ["/Users/zzc/Desktop/works/client-tools/mcp/dist/index.js"],
      "env": { "CLIENT_TOOLS_PORT": "8080" }
    }
  }
}
```

重启 Claude Code，确认 `client-tools` MCP Server 出现在工具列表中。

- [ ] **Step 6: 推送**

```bash
git push
```

---

## 自检清单

**Spec 覆盖：**
- ✅ 4 个 WebView 工具（Task 4/9）
- ✅ 2 个图片工具（Task 5/9）
- ✅ 2 个 DOM 工具（Task 6/9）
- ✅ 2 个 View 工具（Task 7/9）
- ✅ 3 个列表/事件工具（Task 8/9）
- ✅ sdk-client 统一超时（5s 默认，8s DOM）（Task 2）
- ✅ event-monitor 指数退避重连（Task 3）
- ✅ 网络不可达 isError 响应（Task 9 Step 2-3）
- ✅ CLIENT_TOOLS_PORT 环境变量（Task 2）
- ✅ 本地构建发布方式（Task 10 Step 5）

**类型一致性：**
- `errResult` 在每个 tools/*.ts 中独立定义（避免跨文件依赖），签名一致
- `McpServer` 从 `@modelcontextprotocol/sdk/server/mcp.js` 导入，所有 tools 文件一致
- `sdkGet` / `sdkPost` 从 `../sdk-client.js` 导入，路径一致

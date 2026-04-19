# MCP Server 设计 Spec

## 背景

封装 Android SDK HTTP 接口（localhost:8080），通过 MCP 协议暴露为工具函数，供 Claude、Cursor、Codex 等 Agent 直接调用，无需手动拼 HTTP 请求。本地 stdio transport，团队成员各自运行。

---

## 架构总览

```
mcp/
├── src/
│   ├── index.ts          // 入口：注册 stdio transport + 所有工具
│   ├── sdk-client.ts     // 封装所有 HTTP 请求到 localhost:8080
│   ├── event-monitor.ts  // 后台 SSE 连接，缓存最新事件快照
│   └── tools/
│       ├── webview.ts    // push_html, show_webview, hide_overlay, adjust_overlay
│       ├── image.ts      // push_image, show_image
│       ├── dom.ts        // dom_all, dom_by_id
│       ├── view.ts       // get_node, modify_view
│       └── inspector.ts  // list_files, list_images, get_last_event
├── package.json
└── tsconfig.json
```

**数据流：**
```
Agent → MCP stdio → index.ts → sdk-client.ts → HTTP localhost:8080 → Android SDK
                                event-monitor.ts ←SSE── /api/events（后台常驻）
```

**端口配置：** 默认 8080，可通过环境变量 `CLIENT_TOOLS_PORT` 覆盖。

---

## 技术栈

| 项 | 选型 |
|----|------|
| 运行时 | Node.js 18+ |
| MCP SDK | `@modelcontextprotocol/sdk` |
| Transport | stdio only（兼容 Claude Desktop / Claude Code / Cursor / Codex） |
| 入参校验 | `zod` |
| HTTP 客户端 | Node.js 原生 `fetch`（Node 18+ 内置） |
| TypeScript | 5.x，编译到 `dist/` |

---

## 工具清单

### WebView 叠加

#### `push_html`
推送 HTML 内容到设备 WebView 叠加层并自动显示。

入参：
```typescript
{ tag: string, html: string, timestamp?: string }
```
→ `POST /webview/push-html`

#### `show_webview`
切换显示已保存的 HTML 文件。

入参：
```typescript
{ tag: string, timestamp: string }
```
→ `POST /webview/show`

#### `hide_overlay`
隐藏叠加层（WebView 或图片）。

入参：
```typescript
{ type?: "webview" | "image" }  // 缺省隐藏当前 activeTab
```
→ `POST /inspector/hide`

#### `adjust_overlay`
调整叠加层偏移量（增量）和透明度（绝对值）。

入参：
```typescript
{
  type?: "webview" | "image",
  offsetX?: number,   // dp，增量
  offsetY?: number,   // dp，增量
  opacity?: number    // 0.0~1.0，绝对值
}
```
→ `POST /inspector/adjust`

---

### 图片叠加

#### `push_image`
推送 base64 编码图片到设备叠加层并自动显示。

入参：
```typescript
{ tag: string, image: string, ext?: "png" | "jpg", timestamp?: string }
```
→ `POST /inspector/push-image`

#### `show_image`
切换显示已保存的图片。

入参：
```typescript
{ tag: string, timestamp: string }
```
→ `POST /inspector/show-image`

---

### DOM 查询

#### `dom_all`
返回 WebView 中所有 DOM 节点，坐标为屏幕绝对坐标（含 WebView 偏移换算）。

入参：无

→ `GET /dom/all`

出参示例：
```json
{ "code": 0, "data": { "count": 6, "nodes": [{ "id": "btn", "tagName": "button", "x": 120, "y": 340, "width": 67, "height": 22, "text": "OK" }] } }
```

#### `dom_by_id`
按 id 查询单个 DOM 节点。

入参：
```typescript
{ id: string }
```
→ `GET /dom/:id`

---

### View 树

#### `get_node`
查询 Android 原生 View 节点的屏幕位置和属性。

入参：
```typescript
{ id: string }
```
→ `GET /api/nodes/:id`

#### `modify_view`
修改 Android View 的布局属性（margin/padding/size，单位 dp）。

入参：
```typescript
{
  id: string,
  props: {
    marginTopDiffDp?: number,
    marginBottomDiffDp?: number,
    marginLeftDiffDp?: number,
    marginRightDiffDp?: number,
    paddingTopDiffDp?: number,
    paddingBottomDiffDp?: number,
    paddingLeftDiffDp?: number,
    paddingRightDiffDp?: number,
    widthDp?: number,
    heightDp?: number
  }
}
```
→ `POST /api/modify`

---

### 列表与事件

#### `list_files`
返回设备上已保存的 HTML 文件列表。

入参：无

→ `GET /webview/files`

#### `list_images`
返回设备上已保存的图片列表。

入参：无

→ `GET /inspector/images`

#### `get_last_event`
返回最新页面切换事件快照（由后台 SSE 连接缓存）。无事件时返回 `{ event: null }`。

入参：无

出参示例：
```json
{ "event": "page_changed", "activityName": "LoginActivity", "timestamp": "0419-1603" }
```

---

## 错误处理

**网络不可达**（设备未连接 / adb forward 未执行）：
```json
{ "isError": true, "content": [{ "type": "text", "text": "SDK unreachable: connect ECONNREFUSED 127.0.0.1:8080" }] }
```

**SDK 业务错误**（code≠0）：透传 SDK 返回的 `{ code, message }`，不抛异常，由 Agent 自行决策。

**超时：** 所有 HTTP 请求超时 5s，DOM 查询超时 8s（JS 注入需额外时间）。

**event-monitor 重连：** SSE 断开后指数退避重连（1s → 2s → 4s → ... 上限 30s），`get_last_event` 在未连接期间返回上次缓存值。

---

## 发布方式

```json
{
  "name": "@client-tools/mcp",
  "bin": { "client-tools-mcp": "./dist/index.js" }
}
```

团队成员本地 `npm run build` 后，在 MCP 配置中填入：

```json
{
  "mcpServers": {
    "client-tools": {
      "command": "node",
      "args": ["/path/to/client-tools/mcp/dist/index.js"],
      "env": { "CLIENT_TOOLS_PORT": "8080" }
    }
  }
}
```

本期不发布 npm，本地构建直接使用。

---

## 不在本次范围内

- npm 发包
- SSE transport（远程部署）
- iOS SDK 接口封装
- 工具返回值的 schema 验证（Agent 直接消费 SDK JSON）

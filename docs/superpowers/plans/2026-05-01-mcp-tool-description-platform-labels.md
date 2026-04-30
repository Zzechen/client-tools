# MCP 工具描述平台标注 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 MCP server 所有工具描述统一加平台标注，修正错误的 Android 专属描述，补全通用工具的两端说明。

**Architecture:** 纯字符串编辑，涉及 `mcp/src/tools/` 下 5 个 TypeScript 文件。无逻辑变更，无 proto 改动。用 `tsc` 编译通过作为验证手段。

**Tech Stack:** TypeScript, Zod, @modelcontextprotocol/sdk

---

## 文件映射

| 文件 | 变更内容 |
|------|---------|
| `mcp/src/tools/page.ts` | 修正 3 个工具描述 + 补全 2 个 id 参数说明 |
| `mcp/src/tools/view.ts` | `capture_view` 描述加标注 |
| `mcp/src/tools/dom.ts` | 2 个工具描述加标注 |
| `mcp/src/tools/inspector.ts` | 1 个工具描述加标注 |
| `mcp/src/tools/image.ts` | 3 个工具描述加标注 |
| `mcp/src/tools/webview.ts` | 4 个工具描述加标注 |

---

### Task 1: 修正 page.ts — 3 个工具描述 + id 参数说明

**Files:**
- Modify: `mcp/src/tools/page.ts`

- [ ] **Step 1: 编辑 page.ts**

将 `mcp/src/tools/page.ts` 中第 20、27、35 行附近的三处改为：

```typescript
  server.tool("get_current_page", "查询当前页面名称（Android/iOS 通用）", {}, async () => {
```

```typescript
  server.tool("click_view", "点击指定 id 的 View（Android/iOS 通用）", { id: z.string().describe("View 的 id（Android resource id 不含包名前缀，iOS 为 accessibilityIdentifier）") }, async ({ id }) => {
```

```typescript
  server.tool(
    "scroll_view",
    "滚动指定 id 的 View，单位 dp（Android/iOS 通用）",
    {
      id: z.string().describe("View 的 id（Android resource id 不含包名前缀，iOS 为 accessibilityIdentifier）"),
      dx: z.number().describe("横向滚动量，dp，正值向左滚"),
      dy: z.number().describe("竖向滚动量，dp，正值向上滚"),
    },
```

- [ ] **Step 2: 编译验证**

```bash
cd mcp && npm run build
```

期望：无报错，`dist/` 正常生成。

- [ ] **Step 3: Commit**

```bash
git add mcp/src/tools/page.ts
git commit -m "fix(mcp): fix page.ts tool descriptions to Android/iOS universal"
```

---

### Task 2: view.ts — capture_view 加标注

**Files:**
- Modify: `mcp/src/tools/view.ts`

- [ ] **Step 1: 编辑 view.ts**

将第 25 行：

```typescript
    "截取指定 View 的截图，返回 PNG 图片供视觉分析",
```

改为：

```typescript
    "截取指定 View 的截图，返回 PNG 图片供视觉分析（Android/iOS 通用）",
```

- [ ] **Step 2: 编译验证**

```bash
cd mcp && npm run build
```

期望：无报错。

- [ ] **Step 3: Commit**

```bash
git add mcp/src/tools/view.ts
git commit -m "fix(mcp): mark capture_view as Android/iOS universal"
```

---

### Task 3: dom.ts + inspector.ts 加标注

**Files:**
- Modify: `mcp/src/tools/dom.ts`
- Modify: `mcp/src/tools/inspector.ts`

- [ ] **Step 1: 编辑 dom.ts**

```typescript
// dom_all 描述改为：
    "返回 WebView 中所有 DOM 节点，坐标为屏幕绝对坐标（含 WebView 偏移换算）（Android/iOS 通用）",

// dom_by_id 描述改为：
    "按 id 查询 WebView 中单个 DOM 节点的屏幕坐标和尺寸（Android/iOS 通用）",
```

- [ ] **Step 2: 编辑 inspector.ts**

```typescript
// list_files 描述改为：
    "返回设备上已保存的 HTML 文件列表（Android/iOS 通用）",
```

- [ ] **Step 3: 编译验证**

```bash
cd mcp && npm run build
```

期望：无报错。

- [ ] **Step 4: Commit**

```bash
git add mcp/src/tools/dom.ts mcp/src/tools/inspector.ts
git commit -m "fix(mcp): mark dom and inspector tools as Android/iOS universal"
```

---

### Task 4: image.ts 加标注

**Files:**
- Modify: `mcp/src/tools/image.ts`

- [ ] **Step 1: 编辑 image.ts**

```typescript
// push_image 描述改为：
    "推送图片到设备叠加层并自动显示（Android/iOS 通用）。优先使用 file 参数（本地绝对路径），其次 image base64 字符串",

// show_image 描述改为：
    "切换显示设备上已保存的图片（Android/iOS 通用）",

// list_images 描述改为：
    "返回设备上已保存的图片列表（Android/iOS 通用）",
```

- [ ] **Step 2: 编译验证**

```bash
cd mcp && npm run build
```

期望：无报错。

- [ ] **Step 3: Commit**

```bash
git add mcp/src/tools/image.ts
git commit -m "fix(mcp): mark image tools as Android/iOS universal"
```

---

### Task 5: webview.ts 加标注

**Files:**
- Modify: `mcp/src/tools/webview.ts`

- [ ] **Step 1: 编辑 webview.ts**

```typescript
// push_html 描述改为：
    "推送 HTML 到设备 WebView 叠加层并自动显示（Android/iOS 通用）。优先使用 file 参数（本地绝对路径），其次 html 字符串",

// show_webview 描述改为：
    "切换显示设备上已保存的 HTML 文件（Android/iOS 通用）",

// hide_overlay 描述改为：
    "隐藏 WebView 叠加层（Android/iOS 通用）",

// adjust_overlay 描述改为：
    "调整叠加层偏移量（增量 dp）和透明度（绝对值 0~1）（Android/iOS 通用）",
```

- [ ] **Step 2: 编译验证**

```bash
cd mcp && npm run build
```

期望：无报错。

- [ ] **Step 3: Commit**

```bash
git add mcp/src/tools/webview.ts
git commit -m "fix(mcp): mark webview tools as Android/iOS universal"
```

---

### Task 6: 更新 todo 任务状态

- [ ] **Step 1: 将 task #1 标记为 completed**

使用 TaskUpdate 工具：
```json
{ "taskId": "1", "status": "completed" }
```

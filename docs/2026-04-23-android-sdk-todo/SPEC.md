# Android SDK TODO：新增 API + MCP + Skill 适配

**日期**：2026-04-23  
**来源**：brainstorming  
**范围**：Android SDK 新增 API + MCP 工具 + Skill 适配

---

## 一、背景

现有 Android SDK 依赖 SSE 页面切换事件推送，存在复杂度高、实际作用有限的问题。

**改进方向**：
- 删除 SSE 事件推送
- 新增 `get_current_page` 接口（AI 主动拉取）
- 新增 `click`、`scroll` 接口（AI 可操控 View）
- MCP 和 Skill 适配新接口

---

## 二、Android SDK 改动

### 2.1 删除 SSE 事件推送

**删除内容**：
- `GET /api/events` 端点
- `EventManager` 类（`SseEventManager`）
- `PageChangeListener` 中的事件推送逻辑

**保留内容**：
- `PageChangeListener` 可保留，用于内部记录当前页面（供 `get_current_page` 使用）

### 2.2 新增 API

| 端点 | 方法 | 请求体 | 响应 | 说明 |
|------|------|--------|------|------|
| `/api/page/current` | GET | — | `ApiResponse<PageInfo>` | 查询当前页面 |
| `/api/click` | POST | `{"id": "xxx"}` | `ApiResponse<ClickResult>` | 点击指定 View |
| `/api/scroll` | POST | `{"id": "xxx", "dx": 0, "dy": -100}` | `ApiResponse<ScrollResult>` | 滚动指定 View |

### 2.3 数据结构

```kotlin
// GET /api/page/current
data class PageInfo(
    val pageName: String,      // Activity 完整类名，如 "com.clienttools.demo.LoginActivity"
    val timestamp: String     // 格式 MMdd-HHmm，如 "0423-2236"
)

// POST /api/click
data class ClickResult(
    val id: String            // 被点击的 View id
)

// POST /api/scroll
data class ScrollResult(
    val id: String,           // 被滚动的 View id
    val dx: Float,           // dp，横向滚动量
    val dy: Float            // dp，竖向滚动量
)
```

### 2.4 Click 实现

```kotlin
// ViewModifier.kt 新增
fun click(view: View) {
    view.performClick()
}
```

- 同步执行，立即返回
- 不等待 UI 响应
- AI 自己决定后续操作

### 2.5 Scroll 实现

```kotlin
// ViewModifier.kt 新增
fun scroll(view: View, dxDp: Float, dyDp: Float) {
    val dxPx = (dxDp * view.context.resources.displayMetrics.density).toInt()
    val dyPx = (dyDp * view.context.resources.displayMetrics.density).toInt()
    view.scrollBy(dxPx, dyPx)
}
```

- 单位 dp，内部转换为 px
- 与 Android `scrollBy(int x, int y)` 行为一致

### 2.6 涉及文件

```
packages/android/sdk/src/main/kotlin/com/clienttools/sdk/
├── http/
│   └── ApiHandler.kt           # 新增路由：/page/current, /click, /scroll
├── runtime/
│   └── ViewModifier.kt         # 新增 click(), scroll()
└── listener/
    └── PageChangeListener.kt    # 删除事件推送，保留页面记录
```

---

## 三、MCP 改动

### 3.1 删除内容

- `event-monitor.ts` — 删除 SSE 事件监听

### 3.2 新增工具

| 工具名 | 参数 | 说明 |
|--------|------|------|
| `get_current_page` | — | 查询当前页面 |
| `click_view` | `id: string` | 点击指定 View |
| `scroll_view` | `id: string, dx: number, dy: number` | 滚动指定 View |

### 3.3 工具定义

```typescript
// mcp/src/tools/page.ts (新增)
server.tool(
  "get_current_page",
  "查询当前 Android 页面的 Activity 名称",
  {},
  async () => {
    try {
      const result = await sdkGet("/api/page/current");
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    } catch (e) { return errResult(e); }
  }
);

server.tool(
  "click_view",
  "点击指定 id 的 Android View",
  { id: z.string() },
  async ({ id }) => {
    try {
      const result = await sdkPost("/api/click", { id });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    } catch (e) { return errResult(e); }
  }
);

server.tool(
  "scroll_view",
  "滚动指定 id 的 Android View，单位 dp",
  {
    id: z.string(),
    dx: z.number().describe("横向滚动量，dp，正值向左滚"),
    dy: z.number().describe("竖向滚动量，dp，正值向上滚"),
  },
  async ({ id, dx, dy }) => {
    try {
      const result = await sdkPost("/api/scroll", { id, dx, dy });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    } catch (e) { return errResult(e); }
  }
);
```

### 3.4 涉及文件

```
packages/client-tools/mcp/src/
├── event-monitor.ts           # 删除
├── tools/
│   └── page.ts               # 新增（get_current_page, click_view, scroll_view）
└── index.ts                  # 删除 eventMonitor 引用
```

---

## 四、Skill 改动

### 4.1 client-tools-inspect

**删除**：
- SSE `page_changed` 事件订阅

**新增**：
- inspect 开始前调用 `get_current_page()` 确认当前页面

**调整后的 inspect 工作流**：

```
1. [AI] 调用 get_current_page() → 确认当前页面
2. [AI] 获取 DOM + View 数据（get_all_nodes）
3. [AI] 逐个核对节点
4. [AI] 差异时调用 modify_view
5. [AI] 可选：click_view / scroll_view 辅助操作（独立功能，不在流程内约束）
```

### 4.2 click / scroll 定位

click 和 scroll 作为**独立功能**，不在 inspect 流程内约束。

AI 在以下场景可自行调用：
- scroll：某个 View 被遮挡，需要滚动后继续核对
- click：需要触发某个按钮验证行为

---

## 五、总结

| 模块 | 改动 |
|------|------|
| Android SDK | 删除 SSE，新增 /page/current、/click、/scroll |
| MCP | 删除 event-monitor，新增 3 个工具 |
| Skill | inspect 删除 SSE，新增页面确认；click/scroll 独立存在 |

---

## 六、测试验证

### 6.1 Android SDK 测试

```bash
# 测试 get_current_page
curl http://localhost:8080/api/page/current

# 测试 click
curl -X POST http://localhost:8080/api/click \
  -H "Content-Type: application/json" \
  -d '{"id":"btn_login"}'

# 测试 scroll
curl -X POST http://localhost:8080/api/scroll \
  -H "Content-Type: application/json" \
  -d '{"id":"content_list","dx":0,"dy":-100}'
```

### 6.2 MCP 工具测试

```bash
# 启动 MCP Server
npm run build && npm start

# 测试 get_current_page
echo '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_current_page","arguments":{}}}' | node dist/index.js

# 测试 click_view
echo '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"click_view","arguments":{"id":"btn_login"}}}' | node dist/index.js

# 测试 scroll_view
echo '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"scroll_view","arguments":{"id":"content_list","dx":0,"dy":-100}}}' | node dist/index.js
```

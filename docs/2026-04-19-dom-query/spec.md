# DOM 树查询接口设计 Spec

## 背景

在现有 Inspector WebView 叠加层基础上，增加 DOM 树查询能力。通过向 WebView 注入 JS 的方式获取 DOM 节点数据，并将坐标换算为屏幕绝对坐标（含 WebView 当前偏移量）。供 AI 工具链（MCP Server）调用，实现视觉核对与精准定位。

---

## 架构总览

```
inspector/
├── DomQueryService.kt     // 新增：JS 注入 + 坐标换算 + 超时控制
├── DomNodeInfo.kt         // 新增：节点数据类
├── InspectorApiHandler.kt // 扩展：新增 /dom/all、/dom/:id 路由
└── HttpServer.kt          // 新增路由注册
```

**数据流**：
```
HTTP GET /dom/all
  → InspectorApiHandler
  → DomQueryService.queryAll(webView, offsetX, offsetY)
  → webView.evaluateJavascript(JS_ALL)
  → suspendCoroutine + withTimeout(timeoutMs)
  → JSON.parse → List<DomNodeInfo>
  → 坐标换算（加 WebView 偏移 + WebView 自身位置）
  → 返回 JSON response
```

---

## DomNodeInfo 数据类

```kotlin
data class DomNodeInfo(
    val id: String,
    val tagName: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val text: String
)
```

- `id`：元素 `id` 属性，无则为空字符串
- `tagName`：原始标签名（小写，如 `"div"`、`"button"`）
- `x`、`y`：屏幕绝对坐标（px），包含 WebView 位置 + WebView 当前偏移量
- `width`、`height`：元素尺寸（px）
- `text`：`innerText`，超过 200 字符截断

---

## DomQueryService

```kotlin
class DomQueryService(
    private val timeoutMs: Long = 3000L
) {
    suspend fun queryAll(webView: WebView, webViewOffsetX: Int, webViewOffsetY: Int): List<DomNodeInfo>
    suspend fun queryById(webView: WebView, id: String, webViewOffsetX: Int, webViewOffsetY: Int): DomNodeInfo?
}
```

### 坐标换算

```
screenX = webViewLeft + webViewScrollX + elementLeft + webViewOffsetX_px
screenY = webViewTop  + webViewScrollY + elementTop  + webViewOffsetY_px
```

- `webViewLeft/Top`：WebView 在屏幕上的位置（`getLocationOnScreen`）
- `webViewScrollX/Y`：WebView 内容滚动量
- `elementLeft/Top`：`getBoundingClientRect()` 返回的相对视口坐标
- `webViewOffsetX/Y_px`：`InspectorViewModel.webView.offsetX/Y`（dp → px 换算）

### 超时与错误处理

- 使用 `withTimeout(timeoutMs)` 包裹 `suspendCoroutine`
- 超时抛 `TimeoutCancellationException` → HTTP 返回 `code=2, message="timeout"`
- JS 返回 `null` / 解析失败 → HTTP 返回 `code=1, message="parse error"`

### 注入 JS（/dom/all）

```javascript
(function() {
  var nodes = [];
  var all = document.querySelectorAll('*');
  for (var i = 0; i < all.length; i++) {
    var el = all[i];
    var r = el.getBoundingClientRect();
    nodes.push({
      id: el.id || '',
      tagName: el.tagName.toLowerCase(),
      x: Math.round(r.left),
      y: Math.round(r.top),
      width: Math.round(r.width),
      height: Math.round(r.height),
      text: (el.innerText || '').substring(0, 200)
    });
  }
  return JSON.stringify(nodes);
})()
```

### 注入 JS（/dom/:id）

```javascript
(function() {
  var el = document.getElementById('{id}');
  if (!el) return null;
  var r = el.getBoundingClientRect();
  return JSON.stringify({
    id: el.id || '',
    tagName: el.tagName.toLowerCase(),
    x: Math.round(r.left),
    y: Math.round(r.top),
    width: Math.round(r.width),
    height: Math.round(r.height),
    text: (el.innerText || '').substring(0, 200)
  });
})()
```

---

## HTTP 接口

### GET `/dom/all`

返回 WebView 中所有可见 DOM 节点（含坐标换算）。

Response：
```json
{
  "code": 0,
  "data": {
    "count": 42,
    "nodes": [
      {
        "id": "login-btn",
        "tagName": "button",
        "x": 120,
        "y": 340,
        "width": 200,
        "height": 48,
        "text": "登录"
      }
    ]
  }
}
```

### GET `/dom/:id`

按 id 查询单个节点。

Response（找到）：
```json
{
  "code": 0,
  "data": {
    "id": "login-btn",
    "tagName": "button",
    "x": 120,
    "y": 340,
    "width": 200,
    "height": 48,
    "text": "登录"
  }
}
```

Response（未找到）：
```json
{"code": 1, "message": "not found"}
```

### 错误码

| code | 含义 |
|------|------|
| 0 | 成功 |
| 1 | 节点未找到 / JS 解析失败 |
| 2 | JS 执行超时（默认 3000ms） |
| 3 | WebView 未就绪（无当前文件） |

---

## 超时配置

默认 3000ms，可在 `DomQueryService` 构造时传入 `timeoutMs` 参数调整。`ClientToolsSDK` 初始化时使用默认值。

---

## 不在本次范围内

- `querySelectorAll` 按 CSS 选择器查询
- 节点属性（class、style、data-* 等）
- DOM 变更监听（MutationObserver）
- 节点可见性过滤（visibility/display 判断）

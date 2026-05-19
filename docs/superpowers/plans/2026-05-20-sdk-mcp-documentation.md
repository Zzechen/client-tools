# SDK & MCP 说明文档 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 README.md（导航入口）、docs/mcp-tools.md（22 个 MCP 工具参考）、docs/sdk-http-api.md（SDK HTTP 接口参考），并在 CLAUDE.md 中追加文档同步规则。

**Architecture:** README.md 作为顶层导航；docs/mcp-tools.md 和 docs/sdk-http-api.md 各自完整、独立；CLAUDE.md 规则确保后续代码变更时文档同步更新。

**Tech Stack:** Markdown 文档，无代码变更。

---

### Task 1: 更新 CLAUDE.md，追加文档同步规则

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 在 CLAUDE.md 末尾追加以下内容**

在文件末尾（`## Superpowers 文档路径` 章节之后）追加：

```markdown

## 文档同步约定

修改以下代码时，必须同步更新对应文档：

- 修改 `mcp/src/tools/` 下任何工具（新增/删除/改参数）→ 同步更新 `docs/mcp-tools.md`
- 修改 Android/iOS HttpServer 路由（新增/删除/改接口）→ 同步更新 `docs/sdk-http-api.md`
- 修改项目整体结构或新增模块 → 同步更新 `README.md`
```

- [ ] **Step 2: 提交**

```bash
git add CLAUDE.md
git commit -m "docs: add documentation sync rules to CLAUDE.md"
```

---

### Task 2: 新建 docs/mcp-tools.md

**Files:**
- Create: `docs/mcp-tools.md`

- [ ] **Step 1: 写入以下完整内容**

```markdown
# MCP Tools Reference

本文档描述所有可用的 MCP 工具。MCP Server 通过 HTTP 与设备端 SDK 通信（端口 8080）。

## 概览

| 分组 | 工具 | 用途 |
|------|------|------|
| 页面/节点 | `get_current_page` | 查询当前页面名称 |
| | `get_node` | 查询单个 View 的位置和尺寸 |
| | `get_all_nodes` | 获取页面所有 View 节点快照 |
| | `capture_view` | 截取指定 View 的截图 |
| 交互 | `click_view` | 点击指定 View |
| | `scroll_view` | 滚动指定 View |
| 视图修改 | `modify_view_android` | 修改 Android View 布局属性 |
| | `modify_view_ios` | 修改 iOS UIView transform/文字属性 |
| WebView 覆层 | `push_html` | 推送 HTML 到 WebView 覆层并显示 |
| | `show_webview` | 切换显示已保存的 HTML |
| | `hide_overlay` | 隐藏 WebView 覆层 |
| | `adjust_overlay` | 调整覆层位置和透明度 |
| | `list_files` | 列出已保存的 HTML 文件 |
| 图片覆层 | `push_image` | 推送图片到覆层并显示 |
| | `show_image` | 切换显示已保存的图片 |
| | `list_images` | 列出已保存的图片 |
| DOM 查询 | `dom_all` | 获取 WebView 所有 DOM 节点 |
| | `dom_by_id` | 按 id 查询单个 DOM 节点 |
| Mock | `mock_add` | 从 JSON 文件注册 HTTP mock 规则 |
| | `mock_list` | 列出所有 mock 规则 |
| | `mock_delete` | 按 id 删除 mock 规则 |
| | `mock_clear` | 清空所有 mock 规则 |

---

## 页面/节点

### get_current_page

查询当前页面名称（Android Activity 名 / iOS ViewController 类名）。Android/iOS 通用。

**参数：** 无

**返回：**
```json
{"pageName": "MainActivity", "timestamp": "1716192000000"}
```

---

### get_node

查询单个原生 View 节点的屏幕位置和尺寸。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | Android: resource id（不含 `@id/` 前缀）；iOS: accessibilityIdentifier |

**返回：** Node 对象
```json
{
  "id": "login_btn_submit",
  "type": "CONTAINER",
  "screenX": 20.0,
  "screenY": 400.0,
  "widthDp": 335.0,
  "heightDp": 48.0,
  "visibility": 0,
  "isEnabled": true,
  "translateX": 0.0,
  "translateY": 0.0,
  "scaleX": 1.0,
  "scaleY": 1.0
}
```

> `visibility`: 0=VISIBLE, 4=INVISIBLE, 8=GONE

---

### get_all_nodes

获取当前页面所有原生 View 节点的快照。Android/iOS 通用。

**参数：** 无

**返回：** Node 数组（格式同 `get_node`）

---

### capture_view

截取指定 View 的截图，返回 PNG 图片供视觉分析。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | View 的 id |
| save_dir | string | 否 | 若提供，保存截图到该目录，文件名为 `{id}_{timestamp}.png` |

**返回：** PNG 图片（MCP image 类型，mimeType: image/png）

---

## 交互

### click_view

点击指定 id 的 View。默认点击 View 中心，可通过 centerOffsetX/Y 偏移触点。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | View 的 id |
| centerOffsetX | number | 否 | 触点相对 View 中心的横向偏移（dp），正右，默认 0 |
| centerOffsetY | number | 否 | 触点相对 View 中心的纵向偏移（dp），正下，默认 0 |

**返回：**
```json
{"id": "login_btn_submit"}
```

---

### scroll_view

滚动指定 id 的 View。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | View 的 id |
| dx | number | 是 | 横向滚动量（dp），正值向左滚 |
| dy | number | 是 | 竖向滚动量（dp），正值向上滚 |

**返回：**
```json
{"id": "home_list", "dx": 0, "dy": 100}
```

---

## 视图修改

### modify_view_android

修改 Android View 的布局属性。margin/padding 为增量，size 为绝对值。传 text 则断言为 TextView 子类，否则整体拒绝。**仅 Android。**

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | Android View 的 resource id（不含 `@id/` 前缀） |
| margin | object | 否 | margin 增量（dp） |
| margin.topDiffDp | number | 否 | 上 margin 增量 |
| margin.bottomDiffDp | number | 否 | 下 margin 增量 |
| margin.leftDiffDp | number | 否 | 左 margin 增量 |
| margin.rightDiffDp | number | 否 | 右 margin 增量 |
| padding | object | 否 | padding 增量（dp），字段同 margin |
| size | object | 否 | 尺寸设置 |
| size.width | number \| "wrap_content" | 否 | 宽度（dp 数值或 "wrap_content"） |
| size.height | number \| "wrap_content" | 否 | 高度（dp 数值或 "wrap_content"） |
| text | object | 否 | 文字属性，断言为 TextView |
| text.letterSpacingEm | number | 否 | 字间距（em） |
| text.lineSpacingExtraDp | number | 否 | 额外行间距（dp） |
| text.includeFontPadding | boolean | 否 | 是否包含字体内边距 |

**返回：** `"ok"` 或错误信息字符串

---

### modify_view_ios

修改 iOS UIView 的 transform（位移/缩放）或文字属性。传 text 则断言为 UILabel，否则整体拒绝。**仅 iOS。**

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | iOS View 的 accessibilityIdentifier |
| props | object | 是 | 属性对象 |
| props.translateXDp | number | 否 | X 轴位移绝对值（dp），屏幕空间，不受 scale 影响 |
| props.translateYDp | number | 否 | Y 轴位移绝对值（dp），屏幕空间，不受 scale 影响 |
| props.scaleX | number | 否 | X 轴缩放绝对值，1.0 为原始大小 |
| props.scaleY | number | 否 | Y 轴缩放绝对值，1.0 为原始大小 |
| props.text | object | 否 | 文字属性，断言为 UILabel |
| props.text.content | string | 否 | 替换 UILabel 文案内容 |
| props.text.letterSpacingEm | number | 否 | 字间距（em） |
| props.text.lineSpacingExtraDp | number | 否 | 额外行间距（dp） |

**返回：** `"ok"` 或错误信息字符串

---

## WebView 覆层

### push_html

推送 HTML 到设备 WebView 覆层并自动显示。优先使用 file 参数（本地绝对路径），其次 html 字符串。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tag | string | 是 | 页面标识，如 `login`、`home` |
| file | string | 否 | 本地 HTML 文件的绝对路径，优先于 html |
| html | string | 否 | 完整 HTML 内容字符串 |
| timestamp | string | 否 | 时间戳，格式 `MMdd-HHmm`，缺省自动生成 |

> `file` 和 `html` 至少提供一个。

**返回：**
```json
{"tag": "login", "filePath": "/data/user/0/com.example/files/webview/login_0520-1430.html"}
```

---

### show_webview

切换显示设备上已保存的 HTML 文件。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tag | string | 是 | 页面标识 |
| timestamp | string | 是 | 时间戳，格式 `MMdd-HHmm` |

**返回：** `"ok"`

---

### hide_overlay

隐藏 WebView 覆层。Android/iOS 通用。

**参数：** 无

**返回：** `"ok"`

---

### adjust_overlay

调整 WebView 覆层的偏移量（增量 dp）和透明度（绝对值 0~1）。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| offsetX | number | 否 | X 轴偏移增量（dp） |
| offsetY | number | 否 | Y 轴偏移增量（dp） |
| opacity | number | 否 | 透明度绝对值 0.0~1.0 |

**返回：** `"ok"`

---

### list_files

列出设备上已保存的 HTML 文件。Android/iOS 通用。

**参数：** 无

**返回：**
```json
[
  {"tag": "login", "timestamp": "0520-1430", "filePath": "...", "isCurrent": true},
  {"tag": "home",  "timestamp": "0520-1431", "filePath": "...", "isCurrent": false}
]
```

---

## 图片覆层

### push_image

推送图片到设备覆层并自动显示。优先使用 file 参数（本地绝对路径），其次 image base64 字符串。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tag | string | 是 | 图片标识，如 `login`、`home` |
| file | string | 否 | 本地图片文件的绝对路径（png/jpg），优先于 image |
| image | string | 否 | base64 编码的图片内容 |
| ext | "png" \| "jpg" | 否 | 图片格式，缺省 png；使用 file 时自动推断 |
| timestamp | string | 否 | 时间戳，格式 `MMdd-HHmm`，缺省自动生成 |

**返回：**
```json
{"tag": "login", "filePath": "...", "fileSize": 102400}
```

---

### show_image

切换显示设备上已保存的图片。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tag | string | 是 | 图片标识 |
| timestamp | string | 是 | 时间戳，格式 `MMdd-HHmm` |

**返回：**
```json
{"tag": "login", "opacity": 0.5}
```

---

### list_images

列出设备上已保存的图片。Android/iOS 通用。

**参数：** 无

**返回：**
```json
[
  {"tag": "login", "timestamp": "0520-1430", "ext": "png", "size": 102400, "isCurrent": true}
]
```

---

## DOM 查询

### dom_all

返回 WebView 中所有 DOM 节点，坐标为屏幕绝对坐标（含 WebView 偏移换算）。Android/iOS 通用。

**参数：** 无

**返回：**
```json
[
  {"id": "title",      "tag": "h1",     "text": "登录", "x": 20.0, "y": 100.0, "width": 335.0, "height": 40.0},
  {"id": "submit_btn", "tag": "button", "text": "提交", "x": 20.0, "y": 500.0, "width": 335.0, "height": 48.0}
]
```

---

### dom_by_id

按 id 查询 WebView 中单个 DOM 节点的屏幕坐标和尺寸。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | DOM 元素的 id 属性值 |

**返回：** DomNode 对象（格式同 `dom_all` 的单条）

---

## Mock

### mock_add

从本地 JSON 文件注册一条 HTTP mock 规则，返回生成的规则 id。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | string | 是 | 规则 JSON 文件的绝对路径 |

**JSON 文件格式：**
```json
{
  "url": "/api/login",
  "method": "POST",
  "status": 200,
  "headers": {"Content-Type": "application/json"},
  "body": "{\"token\": \"mock-token\"}",
  "delay_ms": 500,
  "error": ""
}
```

**返回：**
```json
{"id": "mock_abc123", "url": "/api/login", "method": "POST"}
```

---

### mock_list

列出所有当前生效的 mock 规则。Android/iOS 通用。

**参数：** 无

**返回：**
```json
[
  {
    "id": "mock_abc123",
    "url": "/api/login",
    "method": "POST",
    "status": 200,
    "headers": {"Content-Type": "application/json"},
    "body": "{\"token\": \"mock-token\"}",
    "delayMs": 500,
    "error": ""
  }
]
```

---

### mock_delete

按 id 删除一条 mock 规则。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | 规则 id，由 `mock_add` 返回 |

**返回：**
```json
{"success": true}
```

---

### mock_clear

清空所有 mock 规则。Android/iOS 通用。

**参数：** 无

**返回：**
```json
{"cleared_count": 3}
```
```

- [ ] **Step 2: 提交**

```bash
git add docs/mcp-tools.md
git commit -m "docs: add MCP tools reference"
```

---

### Task 3: 新建 docs/sdk-http-api.md

**Files:**
- Create: `docs/sdk-http-api.md`

- [ ] **Step 1: 写入以下完整内容**

```markdown
# SDK HTTP API Reference

SDK 在设备上启动 HTTP Server，MCP Server 通过 `http://localhost:8080` 调用。

## 通用说明

- **端口：** 8080
- **数据格式：** Protocol Buffers（`Content-Type: application/x-protobuf`）
  - 例外：`/inspector/*` 和 `/dom/*` 接口使用 JSON
- **通用响应字段（ResponseMeta）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int32 | 0 = 成功，非 0 = 错误 |
| message | string | 错误信息（成功时为空） |
| sdkVersion | int32 | SDK 版本号 |
| device.screenWidthDp | float | 屏幕宽度（dp） |
| device.screenHeightDp | float | 屏幕高度（dp） |
| device.density | float | 屏幕密度 |

---

## 接口概览

| 路径 | 方法 | Android | iOS | 说明 |
|------|------|:-------:|:---:|------|
| `/api/page/current` | GET | ✓ | ✓ | 获取当前页面名 |
| `/api/nodes/all` | GET | ✓ | ✓ | 获取所有 View 节点 |
| `/api/nodes/{id}` | GET | ✓ | ✓ | 获取单个 View 节点 |
| `/api/click` | POST | ✓ | ✓ | 点击 View |
| `/api/scroll` | POST | ✓ | ✓ | 滚动 View |
| `/api/capture/{id}` | GET | ✓ | ✓ | 截取 View 截图 |
| `/api/modify/android` | POST | ✓ | — | 修改 Android View 属性 |
| `/api/modify/ios` | POST | — | ✓ | 修改 iOS View 属性 |
| `/webview/files` | GET | ✓ | ✓ | 列出 HTML 文件 |
| `/webview/push-html` | POST | ✓ | ✓ | 推送 HTML |
| `/webview/show` | POST | ✓ | ✓ | 显示 HTML |
| `/webview/hide` | POST | ✓ | ✓ | 隐藏 WebView 覆层 |
| `/webview/adjust` | POST | ✓ | ✓ | 调整 WebView 覆层 |
| `/inspector/push-image` | POST | ✓ | ✓ | 推送图片 |
| `/inspector/show-image` | POST | ✓ | ✓ | 显示图片 |
| `/inspector/images` | GET | ✓ | ✓ | 列出图片 |
| `/inspector/hide` | POST | ✓ | ✓ | 隐藏图片覆层 |
| `/inspector/adjust` | POST | ✓ | ✓ | 调整图片覆层 |
| `/dom/all` | GET | ✓ | ✓ | 获取 WebView DOM 节点 |
| `/dom/{id}` | GET | ✓ | ✓ | 获取单个 DOM 节点 |
| `/mock/rules` | POST | ✓ | ✓ | 添加 mock 规则 |
| `/mock/rules` | GET | ✓ | ✓ | 列出 mock 规则 |
| `/mock/rules/{id}` | DELETE | ✓ | ✓ | 删除 mock 规则 |
| `/mock/rules` | DELETE | ✓ | ✓ | 清空 mock 规则 |

---

## 接口详情

### GET /api/page/current

获取当前页面名称（Android: Activity 类名；iOS: ViewController 类名）。

**响应：** `PageResponse`
```
meta: ResponseMeta
data.pageName: string    // 页面类名
data.timestamp: string   // 毫秒时间戳字符串
```

---

### GET /api/nodes/all

获取当前页面所有原生 View 节点快照。

**响应：** `NodeListResponse`
```
meta: ResponseMeta
data.nodes: Node[]    // 见「数据模型 → Node」
```

---

### GET /api/nodes/{id}

获取单个 View 节点的位置和尺寸。`id` 需 URL encode。

**响应：** `NodeResponse`
```
meta: ResponseMeta
data: Node
```

---

### POST /api/click

点击指定 View。

**请求体：** `ClickRequest`
```
id: string                         // View id
center_offset_x: FloatValue（可选）  // 横向偏移 dp，正右，默认 0
center_offset_y: FloatValue（可选）  // 纵向偏移 dp，正下，默认 0
```

**响应：** `ClickResponse`
```
meta: ResponseMeta
data.id: string    // 被点击的 View id
```

---

### POST /api/scroll

滚动指定 View。

**请求体：** `ScrollRequest`
```
id: string    // View id
dx: float     // 横向滚动量（dp），正值向左
dy: float     // 竖向滚动量（dp），正值向上
```

**响应：** `ScrollResponse`
```
meta: ResponseMeta
data.id: string
data.dx: float
data.dy: float
```

---

### GET /api/capture/{id}

截取指定 View 的截图，返回 PNG 二进制。`id` 需 URL encode。

**响应：** `CaptureResponse`
```
meta: ResponseMeta
image_png: bytes    // PNG 二进制
```

---

### POST /api/modify/android

修改 Android View 布局属性。**仅 Android。**

**请求体：** `ModifyViewAndroidRequest`
```
id: string
props.margin.top_diff_dp:    FloatValue（可选）
props.margin.bottom_diff_dp: FloatValue（可选）
props.margin.left_diff_dp:   FloatValue（可选）
props.margin.right_diff_dp:  FloatValue（可选）
props.padding.*:             FloatValue（同 margin，可选）
props.size.width_dp / width_wrap_content:   oneof（可选）
props.size.height_dp / height_wrap_content: oneof（可选）
props.text.letter_spacing_em:     FloatValue（可选）
props.text.line_spacing_extra_dp: FloatValue（可选）
props.text.include_font_padding:  BoolValue（可选）
```

**响应：** `ModifyResponse`
```
meta: ResponseMeta
message: string    // "ok" 或错误描述
```

---

### POST /api/modify/ios

修改 iOS UIView transform 或文字属性。**仅 iOS。**

**请求体：** `ModifyViewIosRequest`
```
id: string
props.translate_x_dp:         FloatValue（可选）  // X 轴位移绝对值（dp）
props.translate_y_dp:         FloatValue（可选）  // Y 轴位移绝对值（dp）
props.scale_x:                FloatValue（可选）  // X 轴缩放，1.0 = 原始
props.scale_y:                FloatValue（可选）  // Y 轴缩放，1.0 = 原始
props.text.content:           StringValue（可选） // 替换 UILabel 文案
props.text.letter_spacing_em: FloatValue（可选）
props.text.line_spacing_extra_dp: FloatValue（可选）
```

**响应：** `ModifyResponse`（同 modify/android）

---

### GET /webview/files

列出设备上已保存的 HTML 文件。

**响应：** `FileListResponse`（JSON）
```json
{
  "meta": { "code": 0 },
  "data": {
    "files": [
      {"tag": "login", "timestamp": "0520-1430", "filePath": "...", "isCurrent": true}
    ]
  }
}
```

---

### POST /webview/push-html

推送 HTML 内容到 WebView 覆层并显示。

**请求体：** `PushHtmlRequest`
```
tag: string        // 页面标识
timestamp: string  // 格式 MMdd-HHmm
html: bytes        // HTML 内容（UTF-8 编码）
```

**响应：** `PushHtmlResponse`
```
meta: ResponseMeta
data.tag: string
data.timestamp: string
data.file_path: string    // 设备上的保存路径
```

---

### POST /webview/show

切换显示已保存的 HTML。

**请求体：** `WebviewShowRequest`
```
tag: string
timestamp: string
```

**响应：** `SimpleResponse`（仅 meta）

---

### POST /webview/hide

隐藏 WebView 覆层。

**请求体：** 空

**响应：** `SimpleResponse`

---

### POST /webview/adjust

调整 WebView 覆层的偏移和透明度。

**请求体：** `WebviewAdjustRequest`
```
offset_x: float    // X 轴偏移增量（dp）
offset_y: float    // Y 轴偏移增量（dp）
opacity: float     // 透明度绝对值 0.0~1.0
```

**响应：** `SimpleResponse`

---

### POST /inspector/push-image

推送图片到覆层并显示。

**请求体：** `PushImageRequest`（protobuf）
```
tag: string
timestamp: string    // 格式 MMdd-HHmm
image: bytes         // 图片二进制
ext: string          // "png" 或 "jpg"
```

**响应：** `PushImageResponse`（JSON）
```json
{
  "meta": { "code": 0 },
  "data": {"tag": "login", "timestamp": "0520-1430", "filePath": "...", "fileSize": 102400}
}
```

---

### POST /inspector/show-image

切换显示已保存的图片。

**请求体：** `ShowImageRequest`（protobuf）
```
tag: string
timestamp: string
```

**响应：** `ShowImageResponse`（JSON）
```json
{
  "meta": { "code": 0 },
  "data": {"tag": "login", "timestamp": "0520-1430", "opacity": 0.5, "offsetX": 0, "offsetY": 0}
}
```

---

### GET /inspector/images

列出已保存的图片。

**响应：** `ImageListResponse`（JSON）
```json
{
  "meta": { "code": 0 },
  "data": {
    "images": [
      {"tag": "login", "timestamp": "0520-1430", "ext": "png", "size": 102400, "isCurrent": true}
    ]
  }
}
```

---

### POST /inspector/hide

隐藏图片或 WebView 覆层。

**请求体：** `HideRequest`（JSON）
```json
{"type": "image"}
```

> `type`: `"image"` | `"webview"` | `""`（空 = 按当前 activeTab 判断）

**响应：** `SimpleResponse`

---

### POST /inspector/adjust

调整图片或 WebView 覆层的位置和透明度。

**请求体：** `InspectorAdjustRequest`（JSON）
```json
{
  "type": "image",
  "offset_x": 0,
  "offset_y": 0,
  "opacity": 0.8
}
```

> `type`: `"image"` | `"webview"`

**响应：** `InspectorAdjustResponse`（JSON）
```json
{
  "meta": { "code": 0 },
  "data": {"offsetX": 0, "offsetY": 0, "opacity": 0.8}
}
```

---

### GET /dom/all

获取 WebView 中所有 DOM 节点，坐标为屏幕绝对坐标（含 WebView 偏移换算）。

**响应：** `DomAllResponse`（JSON）
```json
{
  "meta": { "code": 0 },
  "data": {
    "nodes": [
      {"id": "title", "tag": "h1", "text": "登录", "x": 20.0, "y": 100.0, "width": 335.0, "height": 40.0}
    ]
  }
}
```

---

### GET /dom/{id}

按 id 查询单个 DOM 节点。`id` 需 URL encode。

**响应：** `DomNodeResponse`（JSON）
```json
{
  "meta": { "code": 0 },
  "data": {"id": "title", "tag": "h1", "text": "登录", "x": 20.0, "y": 100.0, "width": 335.0, "height": 40.0}
}
```

---

### POST /mock/rules

添加一条 HTTP mock 规则。

**请求体：** `AddMockRuleRequest`（protobuf）
```
url: string
method: string               // "GET" | "POST" 等
delay_ms: int64              // 延迟毫秒
error: string                // 非空则返回网络错误（忽略 status/body）
status: int32                // HTTP 状态码，如 200
headers: map<string,string>
body: string
```

**响应：** `MockRuleResponse`
```
meta: ResponseMeta
data: MockRule（含 id 字段）
```

---

### GET /mock/rules

列出所有生效的 mock 规则。

**响应：** `MockRuleListResponse`
```
meta: ResponseMeta
data.rules: MockRule[]
```

---

### DELETE /mock/rules/{id}

按 id 删除一条 mock 规则。

**响应：** `SimpleResponse`

---

### DELETE /mock/rules

清空所有 mock 规则。

**响应：** `ClearMockRulesResponse`
```
meta: ResponseMeta
cleared_count: int32    // 被清空的规则数量
```

---

## 数据模型

### Node

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | View 标识 |
| type | enum | CONTAINER(0) / TEXT(1) / IMAGE(2) / LIST(3) |
| screenX | float | 屏幕 X 坐标（dp） |
| screenY | float | 屏幕 Y 坐标（dp） |
| widthDp | float | 宽度（dp） |
| heightDp | float | 高度（dp） |
| attrs | NodeAttrs | 类型专属属性（见下） |
| customAttrs | map | 自定义标签键值对 |
| visibility | int32 | 0=VISIBLE, 4=INVISIBLE, 8=GONE |
| isEnabled | bool | 是否可交互 |
| translateX | float | X 轴位移（dp） |
| translateY | float | Y 轴位移（dp） |
| scaleX | float | X 轴缩放 |
| scaleY | float | Y 轴缩放 |

**NodeAttrs（按 type 区分）：**

| type | 字段 |
|------|------|
| TEXT | fontSize(float), color(string), fontWeight(string) |
| IMAGE | scaleType(string) |
| LIST | itemSpacing(float), orientation(string) |
| CONTAINER | paddingTop/Bottom/Left/Right(float) |

### DomNode

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | DOM 元素 id 属性 |
| tag | string | HTML 标签名，如 `div`、`button` |
| text | string | 文本内容 |
| x | float | 屏幕 X 坐标（dp） |
| y | float | 屏幕 Y 坐标（dp） |
| width | float | 宽度（dp） |
| height | float | 高度（dp） |

### MockRule

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 规则 id（由 SDK 生成） |
| url | string | 匹配的 URL 路径 |
| method | string | HTTP 方法 |
| delayMs | int64 | 响应延迟（毫秒） |
| error | string | 非空则模拟网络错误 |
| status | int32 | HTTP 状态码 |
| headers | map | 响应 headers |
| body | string | 响应体字符串 |
```

- [ ] **Step 2: 提交**

```bash
git add docs/sdk-http-api.md
git commit -m "docs: add SDK HTTP API reference"
```

---

### Task 4: 新建/覆盖 README.md

**Files:**
- Create/Overwrite: `README.md`

- [ ] **Step 1: 写入以下完整内容**

```markdown
# client-tools

AI Coding 客户端页面开发增强套件，目标是让 AI 高质量完成「设计稿 → 运行时」的实现，并提供运行时视觉核对与循环修正能力。

## 架构

```
App (Android / iOS)
  └── SDK（HTTP :8080）
        └── MCP Server
              └── AI (Claude)
```

App 内嵌 SDK，SDK 暴露 HTTP 接口；MCP Server 将接口封装为 MCP 工具，供 AI 直接调用。

## 文档导航

| 文档 | 内容 |
|------|------|
| [MCP Tools](docs/mcp-tools.md) | 22 个 MCP 工具的参数与返回值，AI 调用参考 |
| [SDK HTTP API](docs/sdk-http-api.md) | SDK HTTP 接口完整参考，含 Android/iOS 对比 |
| [接入指南](docs/integration.md) | App 集成 SDK 的步骤 |

## 目录结构

```
clients/
  android/sdk/     — Android SDK（.aar）
  android/demo/    — Android 接入示例
  ios/sdk/         — iOS SDK（CocoaPod）
  ios/demo/        — iOS 接入示例
mcp/               — MCP Server（TypeScript）
proto/             — Protocol Buffer 定义
docs/              — 文档
skill/             — client-tools-inspect 技能
tests/             — 测试
```
```

- [ ] **Step 2: 提交**

```bash
git add README.md
git commit -m "docs: add README with project overview and navigation"
```

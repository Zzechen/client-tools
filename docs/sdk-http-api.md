# SDK HTTP API Reference

SDK 在设备上启动 HTTP Server，MCP Server 通过 `http://localhost:8080` 调用。

## 通用说明

- **端口：** 8080
- **数据格式：** Protocol Buffers（`Content-Type: application/x-protobuf`），所有接口统一使用
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
| `/api/modify` | POST | ✓ | ✓ | 修改 View 位置/尺寸/文案 |
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

### POST /api/modify

修改 View 的位置（translation）、尺寸（scale）或文案。Android/iOS 通用，pivot 固定左上角。

**请求体：** `ModifyViewRequest`
```
id: string
move.dx: FloatValue（可选）      // 横向偏移增量（dp），正右
move.dy: FloatValue（可选）      // 纵向偏移增量（dp），正下
size.width:  FloatValue（可选）  // 目标宽度（dp），绝对值
size.height: FloatValue（可选）  // 目标高度（dp），绝对值
text.content: string（可选）     // 替换文案，断言 view 为 TextView/UILabel/UITextField
```

**响应：** `ModifyResponse`
```
meta: ResponseMeta
message: string    // "ok" 或错误描述
```

---

### GET /webview/files

列出设备上已保存的 HTML 文件。

**响应：** `FileListResponse`
```
meta: ResponseMeta
data.files: FileItem[]    // tag, timestamp, filePath, isCurrent
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

**请求体：** `PushImageRequest`
```
tag: string
timestamp: string    // 格式 MMdd-HHmm
image: bytes         // 图片二进制
ext: string          // "png" 或 "jpg"
```

**响应：** `PushImageResponse`
```
meta: ResponseMeta
data.tag: string
data.timestamp: string
data.file_path: string
data.file_size: int64
```

---

### POST /inspector/show-image

切换显示已保存的图片。

**请求体：** `ShowImageRequest`
```
tag: string
timestamp: string
```

**响应：** `ShowImageResponse`
```
meta: ResponseMeta
data.tag: string
data.timestamp: string
data.opacity: float
data.offset_x: float
data.offset_y: float
```

---

### GET /inspector/images

列出已保存的图片。

**响应：** `ImageListResponse`
```
meta: ResponseMeta
data.images: ImageItem[]    // tag, timestamp, ext, size, isCurrent
```

---

### POST /inspector/hide

隐藏图片或 WebView 覆层。

**请求体：** `HideRequest`
```
type: string    // "image" | "webview" | ""（空 = 按当前 activeTab 判断）
```

**响应：** `SimpleResponse`

---

### POST /inspector/adjust

调整图片或 WebView 覆层的位置和透明度。

**请求体：** `InspectorAdjustRequest`
```
type: string      // "image" | "webview" | ""（空 = 按当前 activeTab 判断）
offset_x: float   // X 轴偏移增量（dp）
offset_y: float   // Y 轴偏移增量（dp）
opacity: float    // 透明度绝对值 0.0~1.0
```

**响应：** `InspectorAdjustResponse`
```
meta: ResponseMeta
data.offset_x: float
data.offset_y: float
data.opacity: float
```

---

### GET /dom/all

获取 WebView 中所有 DOM 节点，坐标为屏幕绝对坐标（含 WebView 偏移换算）。

**响应：** `DomAllResponse`
```
meta: ResponseMeta
data.nodes: DomNode[]    // 见「数据模型 → DomNode」
```

---

### GET /dom/{id}

按 id 查询单个 DOM 节点。`id` 需 URL encode。

**响应：** `DomNodeResponse`
```
meta: ResponseMeta
data: DomNode
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

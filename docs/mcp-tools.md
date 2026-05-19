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

# MCP 工具描述平台标注 — Design Spec

**日期：** 2026-05-01

## 背景

MCP server 的部分工具描述中错误地写死了 "Android"，实际上 iOS SDK 已实现相同的 HTTP 路由。需要全面审查并统一标注规则，让 AI 调用时能准确判断工具适用平台。

## 标注原则

- **仅 Android**：描述中保留 "Android"（如 `modify_view_android`）
- **仅 iOS**：描述中保留 "iOS"（如 `modify_view_ios`）
- **两端通用**：描述末尾加 `（Android/iOS 通用）`
- **id 参数**：凡工具接受 `id` 且适用两端，需说明 Android 用 resource id（不含包名前缀），iOS 用 `accessibilityIdentifier`

## 变更清单

### view.ts

| 工具 | 动作 |
|------|------|
| `capture_view` | 描述加 `（Android/iOS 通用）` |
| `get_node` | 已有标注，不动 |
| `get_all_nodes` | 已有标注，不动 |
| `modify_view_android` | Android 专属，不动 |
| `modify_view_ios` | iOS 专属，不动 |

### page.ts

| 工具 | 动作 |
|------|------|
| `get_current_page` | 描述从"查询当前 Android 页面名称"改为"查询当前页面名称（Android/iOS 通用）" |
| `click_view` | 描述改为"（Android/iOS 通用）"；`id` 参数补全两端说明 |
| `scroll_view` | 描述改为"（Android/iOS 通用）"；`id` 参数补全两端说明 |

### dom.ts

| 工具 | 动作 |
|------|------|
| `dom_all` | 描述加 `（Android/iOS 通用）` |
| `dom_by_id` | 描述加 `（Android/iOS 通用）` |

### inspector.ts

| 工具 | 动作 |
|------|------|
| `list_files` | 描述加 `（Android/iOS 通用）` |

### image.ts

| 工具 | 动作 |
|------|------|
| `push_image` | 描述加 `（Android/iOS 通用）` |
| `show_image` | 描述加 `（Android/iOS 通用）` |
| `list_images` | 描述加 `（Android/iOS 通用）` |

### webview.ts

| 工具 | 动作 |
|------|------|
| `push_html` | 描述加 `（Android/iOS 通用）` |
| `show_webview` | 描述加 `（Android/iOS 通用）` |
| `hide_overlay` | 描述加 `（Android/iOS 通用）` |
| `adjust_overlay` | 描述加 `（Android/iOS 通用）` |

### design.ts

| 工具 | 动作 |
|------|------|
| `extract_view_layout` | 描述已包含 "Android/iOS"，不动 |

## 不在范围内

- 修改工具的参数结构或业务逻辑
- 新增工具
- 修改 proto / SDK 代码

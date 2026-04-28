# iOS InspectorPanel 设计文档

> **For agentic workers:** 使用 `superpowers:writing-plans` 将本 spec 转化为实现计划。

**目标**：在 iOS SDK 中实现与 Android 视觉对齐的 InspectorPanel 浮动控制面板，全局悬浮于所有页面之上，无需宿主 App 逐页接入。

---

## 背景

Android 端通过 `InspectorPage.attach()` 将面板注入每个 Activity。iOS 端已有独立 `UIWindow`（`windowLevel: .alert - 1`），`OverlayManager` 已管理 WebView 和 ImageView 两个覆盖层。InspectorPanel 直接加入同一个 UIWindow，天然悬浮全局，无需 per-ViewController 挂载。

---

## 架构

### 文件清单

| 文件 | 职责 |
|------|------|
| `Sources/Inspector/InspectorPanel.swift` | 逻辑层：订阅 ViewModel、处理用户交互 |
| `Sources/Inspector/InspectorPanelView.swift` | 视图层：纯 UIKit 构建面板 UI |

`OverlayManager` 负责在 `ensureWindow()` 时创建 `InspectorPanel` 并添加到 rootViewController.view。

### 数据流

```
用户操作 → InspectorPanel → InspectorViewModel → OverlayManager → UIImageView / WKWebView
                                                                 ↑
HTTP API (push-image / adjust / hide 等) ────────────────────────┘
```

`InspectorPanel` 只写 ViewModel，不直接操作覆盖层视图。

---

## UI 规格（对齐 Android）

### 悬浮按钮

- 圆形，直径 40pt，右下角，margin 16pt
- 文字：⚙，白色，18sp
- 背景：`#6200EE`，圆形
- 支持拖拽（UIPanGestureRecognizer）；拖拽距离 < 8pt 视为点击，切换面板显示/隐藏

### 面板

- 宽 288pt，高度自适应（UIStackView 纵向）
- 圆角 12pt，背景 `#12122A`，阴影
- 初始位置：屏幕中央，支持拖拽移动（面板顶栏为拖拽手柄）

### 顶栏

- 高 44pt，背景 `#6200EE`
- 左侧：「⬡  Inspector」白色 13sp bold
- 右侧：✕ 关闭按钮，点击隐藏面板

### Tab 行

- 高 36pt，三等分：WebView / 图片 / 状态
- 激活态：背景 `#6200EE`，文字白色
- 非激活态：背景 `#1E1E3A`，文字 `#BB86FC`
- 切换 Tab 同步更新 `viewModel.activeTab`

### WebView Section（Tab = WebView 时显示）

- Section 标题行（可折叠）：「▶  WebView」，背景 `#1A1A30`，文字 `#BB86FC`，12sp bold
- 展开内容（默认折叠）：
  - 当前文件标签：`当前：{tag}  {timestamp}` 或 `当前：无`，灰色 11sp
  - 「选择本地文件」按钮，背景 `#6200EE`，高 34pt
  - 点击弹出 `UIAlertController` actionSheet 列表，展示所有已保存 HTML，当前项标 ★

### 图片 Section（Tab = 图片 时显示）

- Section 标题行（可折叠）：「▶  图片文件」
- 展开内容（默认折叠）：
  - 当前图片标签：`当前：{tag}  {timestamp}` 或 `当前：无`
  - 「选择本地图片」按钮
  - 点击弹出 `UIAlertController` actionSheet 列表，展示所有已保存图片（`ImageFileStore.getAllImages()`），当前项标 ★

### 状态 Section（Tab = 状态 时显示）

- 无折叠，直接展示：
  - `● HTTP Server: {port}`，绿色（`#4CAF50`），12sp monospace
  - `当前页面：{pageName}`，灰色 11sp monospace（来自 `ClientToolsSDK.shared.getCurrentPage()`）
  - 标签「iproxy 转发：」+ 命令文字块：`iproxy 8080 8080`（iOS 对应 Android 的 adb forward）

### 调整 Section（WebView / 图片 Tab 均显示，可折叠）

- 档位行：1pt / 10pt / 50pt 三个按钮，激活态 `#6200EE`，默认选中 10pt
- 方向键行：◀ △ ▽ ▶，点击累加 offsetX/offsetY
- 透明度：Label（`透明度：50%`）+ UISlider（0~1，步进连续）
- 偏移显示：`偏移：X: 0pt  Y: 0pt`，灰色 11sp

### 控制 Section（WebView / 图片 Tab 均显示，可折叠）

- 「显示」按钮（`#6200EE`）+ 「隐藏」按钮（`#1E1E3A`），左右各占一半，高 38pt

---

## OverlayManager 改动

`ensureWindow()` 末尾增加：

```swift
let panel = InspectorPanel(
    viewModel: viewModel,
    imageFileStore: ClientToolsSDK.shared.imageFileStore,
    htmlFileStore: overlayManager.fileStore,
    port: /* SDK 启动端口，需从 ClientToolsSDK 传入 */
)
panel.attach(to: vc.view)
self.inspectorPanel = panel
```

`InspectorPanel` 为 `OverlayManager` 的私有属性，持有引用防止被释放。

---

## 接入方式

宿主 App 无需任何额外代码。调用 `ClientToolsSDK.shared.start(port: 8080)` 后，SDK 自动在首次需要时创建 UIWindow，InspectorPanel 随之出现。

---

## 颜色常量

| 名称 | 值 |
|------|-----|
| 主紫 | `#6200EE` |
| 暗背景 | `#1E1E3A` |
| 面板背景 | `#12122A` |
| Section 背景 | `#1A1A30` |
| Section 内容背景 | `#0D0D1A` |
| 浅紫文字 | `#BB86FC` |
| 绿色状态 | `#4CAF50` |
| 灰色辅助文字 | `#9E9E9E` |

---

## 范围说明

- **本 spec 不包含**：WebView 文件列表 API（`GET /webview/files`）、DOM 查询接口——这些是独立 P0 任务
- 状态 Tab 的「当前页面」依赖 `PageTracker`（已实现），直接调用即可
- 不实现「StatusTab 显示网络请求」等扩展功能

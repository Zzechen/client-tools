# iOS SDK Inspector 核心能力（Spec A）

## 背景

Android SDK 已有完整的 Inspector 图片覆盖层能力（ImageFileStore + ImageOverlayManager + InspectorViewModel + HTTP 接口）。iOS SDK 目前只有 `OverlayManager`（WebView 叠加，状态直接存实例变量），缺少：

- InspectorViewModel（统一状态中枢）
- ImageFileStore（图片文件存储）
- Image 叠加层渲染
- `/inspector/*` HTTP 接口

本 spec 补齐以上能力，为 Spec B（InspectorPanel UI）打基础。

## 目标

1. 引入 `InspectorViewModel` 统一管理 WebView 和 Image 叠加层状态
2. 重构 `OverlayManager`：WebView + Image 共用一个 UIWindow，由 ViewModel 驱动
3. 新增 `ImageFileStore` 管理图片文件
4. 新增 `/inspector/*` HTTP 接口（JSON），与 Android 完全对齐

## 设计

### 架构

```
InspectorViewModel
  ├── webViewState: WebViewState
  ├── imageState: ImageState
  └── activeTab: ActiveTab

OverlayManager（重构）
  ├── 持有 InspectorViewModel
  ├── 一个共用 UIWindow（windowLevel .alert - 1）
  │   ├── WKWebView（由 webViewState 驱动）
  │   └── UIImageView（由 imageState 驱动）
  └── 通过 didSet 监听 ViewModel → 主线程更新 UI

ImageFileStore（新增）
  └── 存图片到 Documents/client_tools/images/{tag}/{tag}_{timestamp}.{ext}

HttpServer
  └── /inspector/* → InspectorApiHandler（新增，JSON）

ClientToolsSDK
  └── 持有 InspectorViewModel + ImageFileStore
```

### InspectorViewModel

```swift
struct FileInfo {
    let tag: String
    let timestamp: String
    let filePath: String
}

struct ImageInfo {
    let tag: String
    let timestamp: String
    let filePath: String
    let ext: String
}

struct WebViewState {
    var currentFile: FileInfo? = nil
    var isVisible: Bool = false
    var offsetX: Float = 0
    var offsetY: Float = 0
    var opacity: Float = 0.5
}

struct ImageState {
    var currentImage: ImageInfo? = nil
    var isVisible: Bool = false
    var offsetX: Float = 0
    var offsetY: Float = 0
    var opacity: Float = 0.5
}

enum ActiveTab { case webview, image }

class InspectorViewModel {
    var webViewState: WebViewState { didSet { onWebViewStateChanged?(webViewState) } }
    var imageState: ImageState     { didSet { onImageStateChanged?(imageState) } }
    var activeTab: ActiveTab = .webview

    var onWebViewStateChanged: ((WebViewState) -> Void)?
    var onImageStateChanged: ((ImageState) -> Void)?
}
```

### OverlayManager 重构

- 持有 `InspectorViewModel`，注册 `onWebViewStateChanged` 和 `onImageStateChanged` 回调
- 共用一个 `overlayWindow: UIWindow`，内含 `WKWebView` + `UIImageView`
- `WKWebView` 全屏，`UIImageView` 全屏（contentMode = `.scaleAspectFit`）
- WebView state 变化 → 更新 WKWebView frame/alpha/visibility/loadFile
- Image state 变化 → 更新 UIImageView frame/alpha/visibility/image
- 现有公开方法（`showFile`、`hide`、`adjust`）改为写入 ViewModel，保持向后兼容

### ImageFileStore

对齐 Android `ImageFileStore`：

```swift
class ImageFileStore {
    func saveImage(tag: String, timestamp: String, bytes: Data, ext: String) -> ImageInfo?
    func getAllImages() -> [ImageInfo]   // 按 timestamp 倒序
    func getFilePath(tag: String, timestamp: String) -> String?
    func deleteAll() -> Bool
    func generateTimestamp() -> String  // "MMdd-HHmm"
}
```

存储路径：`Documents/client_tools/images/{tag}/{tag}_{timestamp}.{ext}`  
支持格式：`png`、`jpg`、`jpeg`

### HTTP 接口

路由前缀 `/inspector/`，请求/响应均为 JSON，与 Android `InspectorApiHandler` 完全对齐。

#### POST /inspector/push-image

请求：
```json
{ "tag": "login", "image": "<base64>", "ext": "png", "timestamp": "0427-1430" }
```
- `ext` 默认 `png`，`timestamp` 不传则自动生成

响应：
```json
{ "code": 0, "message": "success", "data": { "tag": "login", "timestamp": "0427-1430", "filePath": "...", "fileSize": 12345 } }
```
副作用：保存文件 + 更新 `imageState.currentImage` + 设 `isVisible = true`

#### POST /inspector/show-image

请求：`{ "tag": "login", "timestamp": "0427-1430" }`

响应：`{ "code": 0, "data": { "tag": "...", "timestamp": "...", "opacity": 0.5, "offsetX": 0, "offsetY": 0 } }`

副作用：更新 `imageState.currentImage` + 设 `isVisible = true`

#### GET /inspector/images

响应：
```json
{ "code": 0, "data": { "images": [ { "tag": "login", "timestamp": "0427-1430", "ext": "png", "size": 12345, "isCurrent": true } ] } }
```

#### POST /inspector/hide

请求：`{ "type": "image" }` 或 `{ "type": "webview" }` 或 `{}`（不传按 `activeTab`）

响应：`{ "code": 0, "message": "success" }`

副作用：对应 state 设 `isVisible = false`

#### POST /inspector/adjust

请求：`{ "type": "image", "offsetX": 10, "offsetY": 0, "opacity": 0.7 }`
- `offsetX`/`offsetY` 为增量（累加）
- `opacity` 为绝对值，范围 0~1，不传则不修改

响应：`{ "code": 0, "data": { "offsetX": 10, "offsetY": 0, "opacity": 0.7 } }`

### 错误处理

| 情况 | code | HTTP |
|------|------|------|
| 缺少必填字段 | 400 | 400 |
| 图片/文件未找到 | 404 | 404 |
| 保存失败 | 500 | 500 |
| base64 解码失败 | 400 | 400 |

### 文件改动

| 操作 | 路径 |
|------|------|
| 新增 | `Sources/Inspector/InspectorViewModel.swift` |
| 新增 | `Sources/Inspector/ImageFileStore.swift` |
| 新增 | `Sources/HttpServer/Inspector/InspectorApiHandler.swift` |
| 重构 | `Sources/Overlay/OverlayManager.swift` |
| 修改 | `Sources/HttpServer/HttpServer.swift` |
| 修改 | `Sources/ClientToolsSDK.swift` |

## 平台差异说明

| 对比项 | Android | iOS |
|--------|---------|-----|
| 状态观察 | `MutableStateFlow` + `collect` | `didSet` + 回调闭包 |
| 图片渲染 | `FitWidthImageView` + `BitmapFactory` | `UIImageView` + `UIImage(contentsOfFile:)` |
| 主线程切换 | `withContext(Dispatchers.Main)` | `DispatchQueue.main.async` |
| 文件存储路径 | `cacheDir/inspector-images/` | `Documents/client_tools/images/` |
| HTTP 框架 | NanoHTTPD | NWListener（已有） |

## 不在范围内

- InspectorPanel UI（Spec B）
- WebView 文件列表接口（`/webview/files`，单独任务）
- DOM 查询接口（单独任务）
- Image 缩放适配（`FitWidthImageView` 等比宽度适配，Spec B 可按需加）

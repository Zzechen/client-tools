# iOS SDK Inspector 核心能力实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 iOS SDK 补齐 Inspector 图片覆盖层核心能力：InspectorViewModel、ImageFileStore、OverlayManager 重构、`/inspector/*` HTTP 接口。

**Architecture:** 引入 `InspectorViewModel` 作为状态中枢，`OverlayManager` 重构为观察 ViewModel 驱动共用 UIWindow 内的 WKWebView + UIImageView，新增 `InspectorApiHandler` 处理 JSON 格式的 `/inspector/*` 路由。

**Tech Stack:** Swift, UIKit, WKWebKit, Foundation, NWListener（已有 HTTP Server）

---

## 文件改动概览

| 操作 | 文件 | 职责 |
|------|------|------|
| 新增 | `clients/ios/sdk/Sources/Inspector/InspectorViewModel.swift` | 状态结构体 + ViewModel（didSet 回调） |
| 新增 | `clients/ios/sdk/Sources/Inspector/ImageFileStore.swift` | 图片文件存取 |
| 新增 | `clients/ios/sdk/Sources/HttpServer/Inspector/InspectorApiHandler.swift` | `/inspector/*` JSON 接口处理 |
| 重构 | `clients/ios/sdk/Sources/Overlay/OverlayManager.swift` | 由 ViewModel 驱动，共用 UIWindow |
| 修改 | `clients/ios/sdk/Sources/HttpServer/HttpServer.swift` | 新增 `/inspector/*` 路由 + JSON 发送方法 |
| 修改 | `clients/ios/sdk/Sources/ClientToolsSDK.swift` | 持有 InspectorViewModel + ImageFileStore |

---

### Task 1: InspectorViewModel

**Files:**
- Create: `clients/ios/sdk/Sources/Inspector/InspectorViewModel.swift`

- [ ] **Step 1: 创建文件，写入完整实现**

```swift
import Foundation

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
    var webViewState: WebViewState = WebViewState() { didSet { onWebViewStateChanged?(webViewState) } }
    var imageState: ImageState = ImageState()       { didSet { onImageStateChanged?(imageState) } }
    var activeTab: ActiveTab = .webview

    var onWebViewStateChanged: ((WebViewState) -> Void)?
    var onImageStateChanged: ((ImageState) -> Void)?
}
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
```

预期：`** BUILD SUCCEEDED **`

- [ ] **Step 3: Commit**

```bash
git add clients/ios/sdk/Sources/Inspector/InspectorViewModel.swift
git commit -m "feat(ios-sdk): add InspectorViewModel with WebViewState and ImageState"
```

---

### Task 2: ImageFileStore

**Files:**
- Create: `clients/ios/sdk/Sources/Inspector/ImageFileStore.swift`

- [ ] **Step 1: 创建文件，写入完整实现**

```swift
import Foundation

class ImageFileStore {

    private let fileManager = FileManager.default
    private lazy var baseDir: URL = {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("client_tools/images")
    }()

    init() {
        try? fileManager.createDirectory(at: baseDir, withIntermediateDirectories: true)
    }

    func saveImage(tag: String, timestamp: String, bytes: Data, ext: String) -> ImageInfo? {
        let tagDir = baseDir.appendingPathComponent(tag)
        do {
            try fileManager.createDirectory(at: tagDir, withIntermediateDirectories: true)
            let fileURL = tagDir.appendingPathComponent("\(tag)_\(timestamp).\(ext)")
            try bytes.write(to: fileURL)
            return ImageInfo(tag: tag, timestamp: timestamp, filePath: fileURL.path, ext: ext)
        } catch {
            print("[ImageFileStore] saveImage error: \(error)")
            return nil
        }
    }

    func getAllImages() -> [ImageInfo] {
        var result: [ImageInfo] = []
        guard let tagDirs = try? fileManager.contentsOfDirectory(at: baseDir, includingPropertiesForKeys: nil) else { return [] }
        for tagDir in tagDirs {
            guard (try? tagDir.resourceValues(forKeys: [.isDirectoryKey]))?.isDirectory == true else { continue }
            guard let files = try? fileManager.contentsOfDirectory(at: tagDir, includingPropertiesForKeys: nil) else { continue }
            for file in files {
                let ext = file.pathExtension.lowercased()
                guard ext == "png" || ext == "jpg" || ext == "jpeg" else { continue }
                guard let timestamp = parseTimestamp(file.lastPathComponent) else { continue }
                result.append(ImageInfo(tag: tagDir.lastPathComponent, timestamp: timestamp, filePath: file.path, ext: ext))
            }
        }
        return result.sorted { $0.timestamp > $1.timestamp }
    }

    func getFilePath(tag: String, timestamp: String) -> String? {
        let tagDir = baseDir.appendingPathComponent(tag)
        guard let files = try? fileManager.contentsOfDirectory(at: tagDir, includingPropertiesForKeys: nil) else { return nil }
        return files.first { f in
            let ext = f.pathExtension.lowercased()
            return (ext == "png" || ext == "jpg" || ext == "jpeg") && parseTimestamp(f.lastPathComponent) == timestamp
        }?.path
    }

    func deleteAll() -> Bool {
        do {
            try fileManager.removeItem(at: baseDir)
            try fileManager.createDirectory(at: baseDir, withIntermediateDirectories: true)
            return true
        } catch {
            print("[ImageFileStore] deleteAll error: \(error)")
            return false
        }
    }

    func generateTimestamp() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMdd-HHmm"
        return formatter.string(from: Date())
    }

    private func parseTimestamp(_ filename: String) -> String? {
        let pattern = #"_(\d{4}-\d{4})\.\w+$"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: filename, range: NSRange(filename.startIndex..., in: filename)),
              let range = Range(match.range(at: 1), in: filename) else { return nil }
        return String(filename[range])
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
```

预期：`** BUILD SUCCEEDED **`

- [ ] **Step 3: Commit**

```bash
git add clients/ios/sdk/Sources/Inspector/ImageFileStore.swift
git commit -m "feat(ios-sdk): add ImageFileStore for inspector image management"
```

---

### Task 3: OverlayManager 重构

**Files:**
- Modify: `clients/ios/sdk/Sources/Overlay/OverlayManager.swift`

现有 `OverlayManager` 的状态（offsetX/offsetY/opacity）直接存实例变量，需重构为由 `InspectorViewModel` 驱动，WebView + Image 共用一个 UIWindow。

- [ ] **Step 1: 完整替换 OverlayManager.swift**

```swift
import UIKit
import WebKit

public class OverlayManager {

    public static let overlayTag = 998

    private var viewModel: InspectorViewModel
    private var overlayWindow: UIWindow?
    private var webView: WKWebView?
    private var imageView: UIImageView?

    public let fileStore = HtmlFileStore()

    public init(viewModel: InspectorViewModel) {
        self.viewModel = viewModel
        setupObservers()
    }

    private func setupObservers() {
        viewModel.onWebViewStateChanged = { [weak self] state in
            DispatchQueue.main.async { self?.applyWebViewState(state) }
        }
        viewModel.onImageStateChanged = { [weak self] state in
            DispatchQueue.main.async { self?.applyImageState(state) }
        }
    }

    // MARK: - 公开方法（向后兼容）

    public func showFile(at fileURL: URL, opacity: Float) {
        let info = FileInfo(tag: "", timestamp: "", filePath: fileURL.path)
        viewModel.webViewState = WebViewState(currentFile: info, isVisible: true,
            offsetX: viewModel.webViewState.offsetX,
            offsetY: viewModel.webViewState.offsetY,
            opacity: opacity)
    }

    public func hide() {
        viewModel.webViewState.isVisible = false
    }

    public func adjust(offsetX: Float?, offsetY: Float?, opacity: Float?) {
        var s = viewModel.webViewState
        if let ox = offsetX { s.offsetX = ox }
        if let oy = offsetY { s.offsetY = oy }
        if let op = opacity { s.opacity = op.clamped(to: 0...1) }
        viewModel.webViewState = s
    }

    // MARK: - 状态应用

    private func applyWebViewState(_ state: WebViewState) {
        ensureWindow()
        guard let wv = webView else { return }
        wv.isHidden = !state.isVisible
        wv.alpha = CGFloat(state.opacity)
        updateFrame(of: wv, offsetX: state.offsetX, offsetY: state.offsetY)
        if state.isVisible, let filePath = state.currentFile?.filePath {
            let fileURL = URL(fileURLWithPath: filePath)
            wv.loadFileURL(fileURL, allowingReadAccessTo: fileURL.deletingLastPathComponent())
        }
        hideWindowIfBothHidden()
    }

    private func applyImageState(_ state: ImageState) {
        ensureWindow()
        guard let iv = imageView else { return }
        iv.isHidden = !state.isVisible
        iv.alpha = CGFloat(state.opacity)
        updateFrame(of: iv, offsetX: state.offsetX, offsetY: state.offsetY)
        if state.isVisible, let filePath = state.currentImage?.filePath {
            iv.image = UIImage(contentsOfFile: filePath)
        }
        hideWindowIfBothHidden()
    }

    private func hideWindowIfBothHidden() {
        let wvHidden = webView?.isHidden ?? true
        let ivHidden = imageView?.isHidden ?? true
        overlayWindow?.isHidden = wvHidden && ivHidden
    }

    // MARK: - UIWindow 管理

    private func ensureWindow() {
        guard overlayWindow == nil else { return }
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }

        let window = UIWindow(windowScene: windowScene)
        window.windowLevel = .alert - 1
        window.tag = OverlayManager.overlayTag

        let vc = UIViewController()
        vc.view.backgroundColor = .clear
        window.rootViewController = vc
        window.isHidden = true
        window.makeKeyAndVisible()
        overlayWindow = window

        let wv = WKWebView(frame: .zero)
        wv.isOpaque = false
        wv.backgroundColor = .clear
        wv.scrollView.isScrollEnabled = false
        wv.isHidden = true
        vc.view.addSubview(wv)
        webView = wv

        let iv = UIImageView(frame: .zero)
        iv.contentMode = .scaleAspectFit
        iv.isHidden = true
        vc.view.addSubview(iv)
        imageView = iv
    }

    private func updateFrame(of view: UIView, offsetX: Float, offsetY: Float) {
        guard let screen = overlayWindow?.screen else { return }
        view.frame = CGRect(
            x: CGFloat(offsetX),
            y: CGFloat(offsetY),
            width: screen.bounds.width,
            height: screen.bounds.height
        )
    }
}

private extension Float {
    func clamped(to range: ClosedRange<Float>) -> Float {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
```

预期：`** BUILD SUCCEEDED **`

- [ ] **Step 3: Commit**

```bash
git add clients/ios/sdk/Sources/Overlay/OverlayManager.swift
git commit -m "refactor(ios-sdk): OverlayManager driven by InspectorViewModel, shared UIWindow for WebView+Image"
```

---

### Task 4: ClientToolsSDK 更新

**Files:**
- Modify: `clients/ios/sdk/Sources/ClientToolsSDK.swift`

- [ ] **Step 1: 完整替换 ClientToolsSDK.swift**

```swift
import UIKit

public class ClientToolsSDK {

    public static let shared = ClientToolsSDK()

    private var httpServer: HttpServer?
    private var pageTracker: PageTracker?
    private var _overlayManager: OverlayManager?
    private var isRunning = false

    let inspectorViewModel = InspectorViewModel()
    let imageFileStore = ImageFileStore()

    private init() {}

    public func start(port: Int = 8080) {
        #if DEBUG
        guard !isRunning else { return }
        isRunning = true
        startHttpServer(port: port)
        startPageTracking()
        startOverlayManager()
        print("[ClientToolsSDK] started on port \(port)")
        #endif
    }

    private func startHttpServer(port: Int) {
        httpServer = HttpServer(port: port)
        httpServer?.start()
    }

    private func startPageTracking() {
        pageTracker = PageTracker()
        pageTracker?.start()
    }

    private func startOverlayManager() {
        _overlayManager = OverlayManager(viewModel: inspectorViewModel)
    }

    public func getCurrentPage() -> (pageName: String, timestamp: String) {
        return pageTracker?.getCurrentPage() ?? ("", "")
    }

    public func overlayManager() -> OverlayManager? {
        return _overlayManager
    }

    public func recordPageChange(_ pageName: String) {
        pageTracker?.recordPageChange(pageName)
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
```

预期：`** BUILD SUCCEEDED **`

- [ ] **Step 3: Commit**

```bash
git add clients/ios/sdk/Sources/ClientToolsSDK.swift
git commit -m "feat(ios-sdk): ClientToolsSDK holds InspectorViewModel and ImageFileStore"
```

---

### Task 5: InspectorApiHandler

**Files:**
- Create: `clients/ios/sdk/Sources/HttpServer/Inspector/InspectorApiHandler.swift`

- [ ] **Step 1: 创建文件，写入完整实现**

```swift
import Foundation

class InspectorApiHandler {

    private let viewModel: InspectorViewModel
    private let imageFileStore: ImageFileStore

    init(viewModel: InspectorViewModel, imageFileStore: ImageFileStore) {
        self.viewModel = viewModel
        self.imageFileStore = imageFileStore
    }

    // MARK: - push-image

    func handlePushImage(_ body: Data) -> (Int, String) {
        guard let obj = try? JSONSerialization.jsonObject(with: body) as? [String: Any],
              let tag = obj["tag"] as? String,
              let imageBase64 = obj["image"] as? String else {
            return (400, #"{"code":400,"message":"Missing tag or image"}"#)
        }
        let ext = (obj["ext"] as? String)?.lowercased() ?? "png"
        let timestamp = (obj["timestamp"] as? String) ?? imageFileStore.generateTimestamp()

        guard let bytes = Data(base64Encoded: imageBase64) else {
            return (400, #"{"code":400,"message":"Invalid base64"}"#)
        }
        guard let saved = imageFileStore.saveImage(tag: tag, timestamp: timestamp, bytes: bytes, ext: ext) else {
            return (500, #"{"code":500,"message":"Failed to save image"}"#)
        }

        viewModel.imageState = ImageState(
            currentImage: saved,
            isVisible: true,
            offsetX: viewModel.imageState.offsetX,
            offsetY: viewModel.imageState.offsetY,
            opacity: viewModel.imageState.opacity
        )
        viewModel.activeTab = .image

        let json = #"{"code":0,"message":"success","data":{"tag":"\#(tag)","timestamp":"\#(timestamp)","filePath":"\#(saved.filePath)","fileSize":\#(bytes.count)}}"#
        return (200, json)
    }

    // MARK: - show-image

    func handleShowImage(_ body: Data) -> (Int, String) {
        guard let obj = try? JSONSerialization.jsonObject(with: body) as? [String: Any],
              let tag = obj["tag"] as? String,
              let timestamp = obj["timestamp"] as? String else {
            return (400, #"{"code":400,"message":"Missing tag or timestamp"}"#)
        }
        guard let filePath = imageFileStore.getFilePath(tag: tag, timestamp: timestamp) else {
            return (404, #"{"code":404,"message":"Image not found"}"#)
        }
        let ext = URL(fileURLWithPath: filePath).pathExtension.lowercased()
        let info = ImageInfo(tag: tag, timestamp: timestamp, filePath: filePath, ext: ext)

        viewModel.imageState = ImageState(
            currentImage: info,
            isVisible: true,
            offsetX: viewModel.imageState.offsetX,
            offsetY: viewModel.imageState.offsetY,
            opacity: viewModel.imageState.opacity
        )
        viewModel.activeTab = .image

        let s = viewModel.imageState
        let json = #"{"code":0,"message":"success","data":{"tag":"\#(tag)","timestamp":"\#(timestamp)","opacity":\#(s.opacity),"offsetX":\#(s.offsetX),"offsetY":\#(s.offsetY)}}"#
        return (200, json)
    }

    // MARK: - images

    func handleGetImages() -> (Int, String) {
        let currentImage = viewModel.imageState.currentImage
        let images = imageFileStore.getAllImages()
        let size: (String) -> Int = { path in
            (try? FileManager.default.attributesOfItem(atPath: path)[.size] as? Int) ?? 0
        }
        let items = images.map { img -> String in
            let isCurrent = img.tag == currentImage?.tag && img.timestamp == currentImage?.timestamp
            return #"{"tag":"\#(img.tag)","timestamp":"\#(img.timestamp)","ext":"\#(img.ext)","size":\#(size(img.filePath)),"isCurrent":\#(isCurrent)}"#
        }.joined(separator: ",")
        return (200, #"{"code":0,"data":{"images":[\#(items)]}}"#)
    }

    // MARK: - hide

    func handleHide(_ body: Data) -> (Int, String) {
        let obj = (try? JSONSerialization.jsonObject(with: body) as? [String: Any]) ?? [:]
        let typeStr = obj["type"] as? String
        let isImage = typeStr == "image" || (typeStr == nil && viewModel.activeTab == .image)
        if isImage {
            viewModel.imageState.isVisible = false
        } else {
            viewModel.webViewState.isVisible = false
        }
        return (200, #"{"code":0,"message":"success"}"#)
    }

    // MARK: - adjust

    func handleAdjust(_ body: Data) -> (Int, String) {
        guard let obj = try? JSONSerialization.jsonObject(with: body) as? [String: Any] else {
            return (400, #"{"code":400,"message":"Invalid JSON"}"#)
        }
        let typeStr = obj["type"] as? String
        let dx = (obj["offsetX"] as? NSNumber)?.floatValue ?? 0
        let dy = (obj["offsetY"] as? NSNumber)?.floatValue ?? 0
        let opacity = (obj["opacity"] as? NSNumber)?.floatValue

        let isImage = typeStr == "image" || (typeStr == nil && viewModel.activeTab == .image)
        if isImage {
            var s = viewModel.imageState
            s.offsetX += dx; s.offsetY += dy
            if let op = opacity { s.opacity = min(max(op, 0), 1) }
            viewModel.imageState = s
            return (200, #"{"code":0,"data":{"offsetX":\#(s.offsetX),"offsetY":\#(s.offsetY),"opacity":\#(s.opacity)}}"#)
        } else {
            var s = viewModel.webViewState
            s.offsetX += dx; s.offsetY += dy
            if let op = opacity { s.opacity = min(max(op, 0), 1) }
            viewModel.webViewState = s
            return (200, #"{"code":0,"data":{"offsetX":\#(s.offsetX),"offsetY":\#(s.offsetY),"opacity":\#(s.opacity)}}"#)
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
```

预期：`** BUILD SUCCEEDED **`

- [ ] **Step 3: Commit**

```bash
git add clients/ios/sdk/Sources/HttpServer/Inspector/InspectorApiHandler.swift
git commit -m "feat(ios-sdk): add InspectorApiHandler for /inspector/* JSON endpoints"
```

---

### Task 6: HttpServer 新增 /inspector/* 路由

**Files:**
- Modify: `clients/ios/sdk/Sources/HttpServer/HttpServer.swift`

- [ ] **Step 1: 在 HttpServer 顶部新增 JSON 发送方法，在 processRequest 新增路由**

在 `sendError` 方法之后，插入：

```swift
    private func sendJson(_ json: String, statusCode: Int = 200, connection: NWConnection) {
        let data = json.data(using: .utf8) ?? Data()
        let header = "HTTP/1.1 \(statusCode) OK\r\nContent-Type: application/json\r\nContent-Length: \(data.count)\r\n\r\n"
        var full = header.data(using: .utf8)!
        full.append(data)
        connection.send(content: full, completion: .contentProcessed { _ in connection.cancel() })
    }
```

在 `processRequest` 的 `switch` 中，在现有 `case ("POST", "/webview/adjust"):` 之后、`default:` 之前，插入：

```swift
        case ("POST", "/inspector/push-image"):
            handleInspector(connection: connection) { $0.handlePushImage(bodyData) }
        case ("POST", "/inspector/show-image"):
            handleInspector(connection: connection) { $0.handleShowImage(bodyData) }
        case ("GET", "/inspector/images"):
            handleInspector(connection: connection) { $0.handleGetImages() }
        case ("POST", "/inspector/hide"):
            handleInspector(connection: connection) { $0.handleHide(bodyData) }
        case ("POST", "/inspector/adjust"):
            handleInspector(connection: connection) { $0.handleAdjust(bodyData) }
```

在文件末尾 `}` 之前插入：

```swift
    private func handleInspector(connection: NWConnection, handler: (InspectorApiHandler) -> (Int, String)) {
        let apiHandler = InspectorApiHandler(
            viewModel: ClientToolsSDK.shared.inspectorViewModel,
            imageFileStore: ClientToolsSDK.shared.imageFileStore
        )
        let (statusCode, json) = handler(apiHandler)
        sendJson(json, statusCode: statusCode, connection: connection)
    }
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
```

预期：`** BUILD SUCCEEDED **`

- [ ] **Step 3: Commit**

```bash
git add clients/ios/sdk/Sources/HttpServer/HttpServer.swift
git commit -m "feat(ios-sdk): add /inspector/* routes to HttpServer"
```

---

### Task 7: 端到端验证

**Files:** 无代码改动

- [ ] **Step 1: 在模拟器运行 Demo，push 一张截图作为 image**

先用 MCP `capture_view` 截图保存到本地：
```
capture_view(id="login_root", save_dir="/tmp/inspector_test")
```
得到文件路径，如 `/tmp/inspector_test/login_root_xxx.png`

- [ ] **Step 2: base64 编码图片并 push**

```bash
IMG=$(base64 /tmp/inspector_test/login_root_*.png)
curl -s -X POST http://127.0.0.1:8080/inspector/push-image \
  -H "Content-Type: application/json" \
  -d "{\"tag\":\"login\",\"image\":\"$IMG\",\"ext\":\"png\"}"
```

预期响应：`{"code":0,"message":"success","data":{...}}`，模拟器上出现图片叠加层。

- [ ] **Step 3: 查询图片列表**

```bash
curl -s http://127.0.0.1:8080/inspector/images
```

预期：返回包含刚 push 图片的列表，`isCurrent: true`。

- [ ] **Step 4: 调整偏移**

```bash
curl -s -X POST http://127.0.0.1:8080/inspector/adjust \
  -H "Content-Type: application/json" \
  -d '{"type":"image","offsetX":20,"offsetY":0,"opacity":0.6}'
```

预期：图片叠加层向右移动 20pt，透明度变为 0.6。

- [ ] **Step 5: 隐藏**

```bash
curl -s -X POST http://127.0.0.1:8080/inspector/hide \
  -H "Content-Type: application/json" \
  -d '{"type":"image"}'
```

预期：图片叠加层消失，响应 `{"code":0,"message":"success"}`。

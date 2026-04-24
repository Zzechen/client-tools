# iOS SDK — Implementation Plan

**日期**：2026-04-24  
**范围**：iOS UIKit SDK 开发  
**目标**：与 Android SDK 功能对齐

---

## 技术选型

| 组件 | 技术 |
|------|------|
| HTTP Server | GCDWebServer |
| WebView | WKWebView |
| JSON 序列化 | Codable |
| View 遍历 | UIKit Runtime (subviews) |
| 约束修改 | NSLayoutConstraint |
| 包管理 | CocoaPods |
| USB 隧道 | iproxy（usbmuxd） |

---

## 通信架构

```
┌──────────────────┐         USB (iproxy)          ┌──────────────────┐
│    Mac (MCP)     │  ←── localhost:8080 ──→      │  iOS 设备 (SDK)   │
│                  │                               │  GCDWebServer     │
│  MCP Server      │                               │  port: 8080       │
└──────────────────┘                               └──────────────────┘
```

**连接步骤：**

```bash
# 1. iOS 设备上运行 SDK HTTP Server（SDK 自动启动）
# 2. Mac 上运行 iproxy 建立 USB 隧道
iproxy 8080 8080

# 3. MCP Server 连接 localhost:8080（自动透传到 iOS 设备）
```

**与 Android 的差异：**

| 平台 | 隧道方式 | 命令 |
|------|---------|------|
| Android 模拟器 | adb forward | `adb forward tcp:8080 tcp:8080` |
| Android 真机 | 网络直连 或 adb reverse | `adb reverse tcp:8080 tcp:8080` |
| iOS | USB 隧道 | `iproxy 8080 8080` |

**iproxy 说明：**
- 包含在 usbmuxd 中（Xcode Command Line Tools 自带）
- `iproxy <localPort> <devicePort>` 建立本地端口到 iOS设备的 TCP 隧道
- 设备需通过 USB 连接 Mac，无需与 Mac 同网络
- 模拟器和真机通用

---

## 项目结构

```
packages/ios/
└── sdk/
    ├── ClientToolsSDK.podspec
    ├── Sources/
    │   ├── ClientToolsSDK.swift           # SDK 入口
    │   ├── HttpServer/
    │   │   ├── HttpServer.swift          # GCDWebServer 封装
    │   │   ├── ApiHandler.swift          # 路由处理
    │   │   ├── ApiResponse.swift         # 响应格式
    │   │   └── Pages/
    │   │       ├── PageCurrentHandler.swift
    │   │       ├── ClickHandler.swift
    │   │       └── ScrollHandler.swift
    │   │
    │   ├── ViewQuery/
    │   │   ├── ViewQueryService.swift   # View 查询入口
    │   │   ├── ViewNode.swift           # 数据结构
    │   │   ├── ViewHashGenerator.swift  # ID 生成
    │   │   ├── ViewTraverser.swift      # 遍历
    │   │   ├── StyleQuerier.swift       # 样式查询
    │   │   └── ViewTypeMapper.swift     # 类型映射
    │   │
    │   ├── ViewModify/
    │   │   ├── ViewModifyService.swift  # 修改入口
    │   │   ├── FrameModifier.swift      # frame 修改
    │   │   ├── ConstraintModifier.swift # margin 修改
    │   │   └── PaddingModifier.swift    # padding 修改
    │   │
    │   ├── Overlay/
    │   │   ├── OverlayManager.swift     # 叠加层管理
    │   │   └── HtmlFileStore.swift     # HTML 文件存储
    │   │
    │   ├── PageTracking/
    │   │   ├── PageTracker.swift        # 页面跟踪
    │   │   └── ViewControllerSwizzling.swift
    │   │
    │   └── Model/
    │       ├── PageInfo.swift
    │       ├── ClickRequest.swift
    │       ├── ScrollRequest.swift
    │       ├── ModifyRequest.swift
    │       └── ...
    │
    └── Resources/
        └── Info.plist
```

---

## Phase 1：项目初始化

### Task 1.1：创建 podspec

**文件**：`packages/ios/sdk/ClientToolsSDK.podspec`

```ruby
Pod::Spec.new do |s|
  s.name             = 'ClientToolsSDK'
  s.version          = '1.0.0'
  s.summary          = 'AI Coding Client Tools SDK for iOS'
  s.description      = 'Runtime view inspection and modification for iOS apps'
  s.homepage         = 'https://gitee.com/zzcm1259/client-tools'
  s.license         = { :type => 'MIT' }
  s.author          = { 'zzc' => 'zzcm1259@qq.com' }
  s.source           = { :git => '', :tag => s.version.to_s }
  s.ios.deployment_target = '14.0'
  s.swift_version    = '5.0'
  s.source_files     = 'Sources/**/*.swift'
  s.frameworks      = 'UIKit', 'WebKit'
  s.library         = 'objc'
  s.xcconfig        = { 'OTHER_LDFLAGS' => '-ObjC' }

  s.dependency 'GCDWebServer', '~> 3.5'
end
```

---

### Task 1.2：创建 Info.plist

**文件**：`packages/ios/sdk/Resources/Info.plist`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "...">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>$(DEVELOPMENT_LANGUAGE)</string>
    <key>CFBundleExecutable</key>
    <string>$(EXECUTABLE_NAME)</string>
    <key>CFBundleIdentifier</key>
    <string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>$(PRODUCT_NAME)</string>
    <key>CFBundlePackageType</key>
    <string>FMWK</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>NSAppTransportSecurity</key>
    <dict>
        <key>NSAllowsArbitraryLoads</key>
        <true/>
    </dict>
    <key>NSLocalNetworkUsageDescription</key>
    <string>ClientToolsSDK requires local network access to communicate with the MCP server.</string>
</dict>
</plist>
```

---

### Task 1.3：创建 SDK 入口

**文件**：`packages/ios/sdk/Sources/ClientToolsSDK.swift`

```swift
import UIKit

public class ClientToolsSDK {

    public static let shared = ClientToolsSDK()

    private var httpServer: HttpServer?
    private var pageTracker: PageTracker?
    private var overlayManager: OverlayManager?
    private var isRunning = false

    private init() {}

    /// 启动 SDK（DEBUG 模式下生效，生产环境自动跳过）
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
        overlayManager = OverlayManager()
    }

    public func getCurrentPage() -> (pageName: String, timestamp: String) {
        return pageTracker?.getCurrentPage() ?? ("", "")
    }

    public func overlayManager() -> OverlayManager? {
        return overlayManager
    }
}
```

**宿主调用示例**：

```swift
// AppDelegate.swift
import ClientToolsSDK

func application(_ application: UIApplication,
                 didFinishLaunchingWithOptions ...) -> Bool {
    let port = (Bundle.main.object(forInfoDictionaryKey: "ClientToolsSDKPort") as? Int) ?? 8080
    ClientToolsSDK.shared.start(port: port)
    return true
}
```

---

## Phase 2：数据模型

### Task 2.1：ViewNode

**文件**：`packages/ios/sdk/Sources/ViewQuery/ViewNode.swift`

```swift
import Foundation

public struct ViewNode: Codable {
    public let id: String
    public let type: String          // TEXT, IMAGE, LIST, CONTAINER
    public let screenX: Float       // dp
    public let screenY: Float       // dp
    public let widthDp: Float      // dp
    public let heightDp: Float      // dp
    public let attrs: NodeAttrs?    // TextAttrs, ImageAttrs, etc.

    public init(id: String, type: String, screenX: Float, screenY: Float,
                widthDp: Float, heightDp: Float, attrs: NodeAttrs?) {
        self.id = id
        self.type = type
        self.screenX = screenX
        self.screenY = screenY
        self.widthDp = widthDp
        self.heightDp = heightDp
        self.attrs = attrs
    }
}

public enum NodeAttrs: Codable {
    case text(TextAttrs)
    case image(ImageAttrs)
    case list(ListAttrs)
    case none

    private enum CodingKeys: String, CodingKey {
        case type, data
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let type = try container.decode(String.self, forKey: .type)
        switch type {
        case "text": self = .text(try container.decode(TextAttrs.self, forKey: .data))
        case "image": self = .image(try container.decode(ImageAttrs.self, forKey: .data))
        case "list": self = .list(try container.decode(ListAttrs.self, forKey: .data))
        default: self = .none
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .text(let attrs): try container.encode("text", forKey: .type); try container.encode(attrs, forKey: .data)
        case .image(let attrs): try container.encode("image", forKey: .type); try container.encode(attrs, forKey: .data)
        case .list(let attrs): try container.encode("list", forKey: .type); try container.encode(attrs, forKey: .data)
        case .none: try container.encode("none", forKey: .type)
        }
    }
}

public struct TextAttrs: Codable {
    public let fontSize: Float?
    public let color: String?
    public let fontWeight: String?

    public init(fontSize: Float? = nil, color: String? = nil, fontWeight: String? = nil) {
        self.fontSize = fontSize
        self.color = color
        self.fontWeight = fontWeight
    }
}

public struct ImageAttrs: Codable {
    public let scaleType: String?

    public init(scaleType: String? = nil) {
        self.scaleType = scaleType
    }
}

public struct ListAttrs: Codable {
    public let itemSpacing: Float?
    public let orientation: String?

    public init(itemSpacing: Float? = nil, orientation: String? = nil) {
        self.itemSpacing = itemSpacing
        self.orientation = orientation
    }
}
```

---

### Task 2.2：API Response

**文件**：`packages/ios/sdk/Sources/HttpServer/ApiResponse.swift`

```swift
import Foundation

public struct ApiResponse<T: Codable>: Codable {
    public let code: Int
    public let message: String
    public let sdkVersion: Int
    public let data: T?

    public init(code: Int, message: String, data: T?) {
        self.code = code
        self.message = message
        self.sdkVersion = 1
        self.data = data
    }

    public static func success(_ data: T) -> ApiResponse<T> {
        return ApiResponse(code: 0, message: "success", data: data)
    }

    public static func error(_ message: String) -> ApiResponse<T> {
        return ApiResponse(code: -1, message: message, data: nil)
    }
}
```

---

### Task 2.3：Request/Result 模型

**文件**：`packages/ios/sdk/Sources/Model/Models.swift`

```swift
import Foundation

public struct PageInfo: Codable {
    public let pageName: String
    public let timestamp: String

    public init(pageName: String, timestamp: String) {
        self.pageName = pageName
        self.timestamp = timestamp
    }
}

public struct ClickRequest: Codable {
    public let id: String

    public init(id: String) {
        self.id = id
    }
}

public struct ClickResult: Codable {
    public let id: String

    public init(id: String) {
        self.id = id
    }
}

public struct ScrollRequest: Codable {
    public let id: String
    public let dx: Float
    public let dy: Float

    public init(id: String, dx: Float, dy: Float) {
        self.id = id
        self.dx = dx
        self.dy = dy
    }
}

public struct ScrollResult: Codable {
    public let id: String
    public let dx: Float
    public let dy: Float

    public init(id: String, dx: Float, dy: Float) {
        self.id = id
        self.dx = dx
        self.dy = dy
    }
}

public struct ModifyRequest: Codable {
    public let id: String
    public let props: ModifyProps

    public init(id: String, props: ModifyProps) {
        self.id = id
        self.props = props
    }
}

public struct ModifyProps: Codable {
    public let marginTopDiffDp: Float?
    public let marginBottomDiffDp: Float?
    public let marginLeftDiffDp: Float?
    public let marginRightDiffDp: Float?
    public let paddingTopDiffDp: Float?
    public let paddingBottomDiffDp: Float?
    public let paddingLeftDiffDp: Float?
    public let paddingRightDiffDp: Float?
    public let widthDp: String?
    public let heightDp: String?

    public init(marginTopDiffDp: Float? = nil, marginBottomDiffDp: Float? = nil,
                 marginLeftDiffDp: Float? = nil, marginRightDiffDp: Float? = nil,
                 paddingTopDiffDp: Float? = nil, paddingBottomDiffDp: Float? = nil,
                 paddingLeftDiffDp: Float? = nil, paddingRightDiffDp: Float? = nil,
                 widthDp: String? = nil, heightDp: String? = nil) {
        // assign all...
    }
}

public struct OverlayShowRequest: Codable {
    public let url: String
    public let opacity: Float

    public init(url: String, opacity: Float) {
        self.url = url
        self.opacity = opacity
    }
}

public struct OverlayOpacityRequest: Codable {
    public let opacity: Float

    public init(opacity: Float) {
        self.opacity = opacity
    }
}

public struct PushHtmlRequest: Codable {
    public let tag: String
    public let html: String

    public init(tag: String, html: String) {
        self.tag = tag
        self.html = html
    }
}

public struct PushHtmlResult: Codable {
    public let url: String

    public init(url: String) {
        self.url = url
    }
}
```

---

## Phase 3：HTTP Server

### Task 3.1：HttpServer

**文件**：`packages/ios/sdk/Sources/HttpServer/HttpServer.swift`

```swift
import Foundation
import GCDWebServer

class HttpServer {

    private let server = GCDWebServer()
    private let port: Int
    private let viewQueryService = ViewQueryService()
    private let viewModifyService = ViewModifyService()

    init(port: Int = 8080) {
        self.port = port
        setupRoutes()
    }

    func start() {
        server.start(options: [
            GCDWebServerOption.port: port,
            GCDWebServerOption.bindToLocalhost: false,
            GCDWebServerOption.loggingIdentifier: "ClientToolsSDK"
        ])
    }

    private func setupRoutes() {
        // GET /api/page/current
        server.addHandler(forMethod: "GET", path: "/api/page/current", request: GCDWebServerRequest.self) { _ in
            return self.handlePageCurrent()
        }

        // GET /api/nodes/all
        server.addHandler(forMethod: "GET", path: "/api/nodes/all", request: GCDWebServerRequest.self) { _ in
            return self.handleNodesAll()
        }

        // GET /api/nodes/{id}
        server.addHandler(forMethod: "GET", pathRegex: "/api/nodes/.+", request: GCDWebServerRequest.self) { request in
            return self.handleNodeById(request)
        }

        // POST /api/click
        server.addHandler(forMethod: "POST", path: "/api/click", request: GCDWebServerRequest.self) { request in
            return self.handleClick(request)
        }

        // POST /api/scroll
        server.addHandler(forMethod: "POST", path: "/api/scroll", request: GCDWebServerRequest.self) { request in
            return self.handleScroll(request)
        }

        // POST /api/modify
        server.addHandler(forMethod: "POST", path: "/api/modify", request: GCDWebServerRequest.self) { request in
            return self.handleModify(request)
        }

        // POST /api/overlay/show
        server.addHandler(forMethod: "POST", path: "/api/overlay/show", request: GCDWebServerRequest.self) { request in
            return self.handleOverlayShow(request)
        }

        // POST /api/overlay/hide
        server.addHandler(forMethod: "POST", path: "/api/overlay/hide", request: GCDWebServerRequest.self) { _ in
            return self.handleOverlayHide()
        }

        // POST /api/overlay/opacity
        server.addHandler(forMethod: "POST", path: "/api/overlay/opacity", request: GCDWebServerRequest.self) { request in
            return self.handleOverlayOpacity(request)
        }

        // POST /webview/push-html
        server.addHandler(forMethod: "POST", path: "/webview/push-html", request: GCDWebServerRequest.self) { request in
            return self.handlePushHtml(request)
        }
    }
}
```

### Task 3.2：PageCurrentHandler

**文件**：`packages/ios/sdk/Sources/HttpServer/Pages/PageCurrentHandler.swift`

```swift
extension HttpServer {

    func handlePageCurrent() -> GCDWebServerResponse {
        let pageInfo = ClientToolsSDK.shared.getCurrentPage()
        let response = ApiResponse.success(PageInfo(pageName: pageInfo.pageName, timestamp: pageInfo.timestamp))
        return jsonResponse(response)
    }
}
```

### Task 3.3：ClickHandler

**文件**：`packages/ios/sdk/Sources/HttpServer/Pages/ClickHandler.swift`

```swift
extension HttpServer {

    func handleClick(_ request: GCDWebServerRequest) -> GCDWebServerResponse {
        guard let data = request.data,
              let clickRequest = try? JSONDecoder().decode(ClickRequest.self, from: data) else {
            return errorResponse("Invalid request")
        }

        let view = ViewFinder.shared.findView(byId: clickRequest.id)
        guard let view = view else {
            return errorResponse("View not found", code: 404)
        }

        view.sendActions(for: .touchUpInside)
        let result = ClickResult(id: clickRequest.id)
        return jsonResponse(ApiResponse.success(result))
    }
}
```

### Task 3.4：ScrollHandler

**文件**：`packages/ios/sdk/Sources/HttpServer/Pages/ScrollHandler.swift`

```swift
extension HttpServer {

    func handleScroll(_ request: GCDWebServerRequest) -> GCDWebServerResponse {
        guard let data = request.data,
              let scrollRequest = try? JSONDecoder().decode(ScrollRequest.self, from: data) else {
            return errorResponse("Invalid request")
        }

        let view = ViewFinder.shared.findView(byId: scrollRequest.id)
        guard let scrollView = view as? UIScrollView else {
            return errorResponse("View is not a scroll view", code: 400)
        }

        let dx = CGFloat(scrollRequest.dx)
        let dy = CGFloat(scrollRequest.dy)
        scrollView.setContentOffset(
            CGPoint(x: scrollView.contentOffset.x + dx, y: scrollView.contentOffset.y + dy),
            animated: false
        )

        let result = ScrollResult(id: scrollRequest.id, dx: scrollRequest.dx, dy: scrollRequest.dy)
        return jsonResponse(ApiResponse.success(result))
    }

    func handleOverlayShow(_ request: GCDWebServerRequest) -> GCDWebServerResponse {
        guard let data = request.data,
              let showRequest = try? JSONDecoder().decode(OverlayShowRequest.self, from: data) else {
            return errorResponse("Invalid request")
        }

        ClientToolsSDK.shared.overlayManager()?.show(url: showRequest.url, opacity: showRequest.opacity)
        return jsonResponse(ApiResponse.success(["success": true] as [String: Any]))
    }

    func handleOverlayHide() -> GCDWebServerResponse {
        ClientToolsSDK.shared.overlayManager()?.hide()
        return jsonResponse(ApiResponse.success(["success": true] as [String: Any]))
    }

    func handleOverlayOpacity(_ request: GCDWebServerRequest) -> GCDWebServerResponse {
        guard let data = request.data,
              let opacityRequest = try? JSONDecoder().decode(OverlayOpacityRequest.self, from: data) else {
            return errorResponse("Invalid request")
        }

        ClientToolsSDK.shared.overlayManager()?.setOpacity(opacityRequest.opacity)
        return jsonResponse(ApiResponse.success(["success": true] as [String: Any]))
    }

    func handlePushHtml(_ request: GCDWebServerRequest) -> GCDWebServerResponse {
        guard let data = request.data,
              let pushRequest = try? JSONDecoder().decode(PushHtmlRequest.self, from: data) else {
            return errorResponse("Invalid request")
        }

        let timestamp = ISO8601DateFormatter().string(from: Date()).replacingOccurrences(of: ":", with: "-")
        let url = ClientToolsSDK.shared.overlayManager()?.fileStore.save(tag: pushRequest.tag, timestamp: timestamp, html: pushRequest.html)

        if let url = url {
            return jsonResponse(ApiResponse.success(PushHtmlResult(url: url.absoluteString)))
        } else {
            return errorResponse("Failed to save HTML")
        }
    }
}
```

---

## Phase 3.5：Overlay 叠加层

### Task 3.5.1：OverlayHandler

**文件**：`packages/ios/sdk/Sources/HttpServer/Pages/OverlayHandler.swift`

Overlay 相关 handler 已内联在 HttpServer 中（见 Phase 3 HttpServer 完整代码）。

### Task 3.5.2：OverlayManager 改造

现有 OverlayManager 需要新增 `fileStore` 属性：

**文件**：`packages/ios/sdk/Sources/Overlay/OverlayManager.swift`

```swift
class OverlayManager {

    static let overlayTag = 998  // 全局唯一的 tag，用于遍历时排除
    let fileStore = HtmlFileStore()
    private var overlayView: UIView?

    func show(url: String, opacity: Float) {
        hide()

        guard let window = getKeyWindow() else { return }

        let configuration = WKWebViewConfiguration()
        webView = WKWebView(frame: UIScreen.main.bounds, configuration: configuration)
        webView?.alpha = CGFloat(opacity)
        webView?.load(URLRequest(url: URL(string: url)!))

        overlayView = webView
        overlayView?.tag = Self.overlayTag
        window.addSubview(overlayView!)
    }

    func hide() {
        overlayView?.removeFromSuperview()
        overlayView = nil
        webView = nil
    }

    func setOpacity(_ opacity: Float) {
        webView?.alpha = CGFloat(opacity)
    }

    private func getKeyWindow() -> UIWindow? {
        return UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
    }
}
```

---

## Phase 4：View 查询

### Task 4.1：ViewHashGenerator

**文件**：`packages/ios/sdk/Sources/ViewQuery/ViewHashGenerator.swift`

```swift
import UIKit

class ViewHashGenerator {

    static func generateId(for view: UIView, path: String = "") -> String {
        // 优先使用 accessibilityIdentifier
        if let identifier = view.accessibilityIdentifier, !identifier.isEmpty {
            return identifier
        }

        // 降级：Runtime Hash
        let className = String(describing: type(of: view))
        let address = String(format: "%p", view)
        let hash = "\(className)_\(address)"

        if path.isEmpty {
            return hash
        }
        return "\(hash)_\(path)"
    }
}
```

---

### Task 4.2：ViewTraverser

**文件**：`packages/ios/sdk/Sources/ViewQuery/ViewTraverser.swift`

```swift
import UIKit

class ViewTraverser {

    static func traverse(_ view: UIView, path: String = "") -> [ViewNode] {
        var nodes: [ViewNode] = []

        for (index, subview) in view.subviews.enumerated() {
            // 跳过叠加层（通过 tag 过滤）
            if subview.tag == OverlayManager.overlayTag { continue }

            let childPath = path.isEmpty ? "\(index)" : "\(path).\(index)"
            let viewId = ViewHashGenerator.generateId(for: subview, path: childPath)

            let node = ViewNode(
                id: viewId,
                type: ViewTypeMapper.map(subview),
                screenX: Float(subview.frame.origin.x) / Float(UIScreen.main.scale),
                screenY: Float(subview.frame.origin.y) / Float(UIScreen.main.scale),
                widthDp: Float(subview.frame.width) / Float(UIScreen.main.scale),
                heightDp: Float(subview.frame.height) / Float(UIScreen.main.scale),
                attrs: StyleQuerier.query(subview)
            )

            nodes.append(node)
            nodes.append(contentsOf: traverse(subview, path: childPath))
        }

        return nodes
    }

    static func traverseFromWindow() -> [ViewNode] {
        var result: [ViewNode] = []
        DispatchQueue.main.sync {
            guard let window = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .flatMap({ $0.windows })
                .first(where: { $0.isKeyWindow }) else {
                return
            }
            result = traverse(window)
        }
        return result
    }
}
```

---

### Task 4.3：ViewTypeMapper

**文件**：`packages/ios/sdk/Sources/ViewQuery/ViewTypeMapper.swift`

```swift
import UIKit

class ViewTypeMapper {

    static func map(_ view: UIView) -> String {
        switch view {
        case is UILabel, is UITextField, is UITextView, is UIButton:
            return "TEXT"
        case is UIImageView:
            return "IMAGE"
        case is UITableView, is UICollectionView:
            return "LIST"
        default:
            return "CONTAINER"
        }
    }
}
```

---

### Task 4.4：StyleQuerier

**文件**：`packages/ios/sdk/Sources/ViewQuery/StyleQuerier.swift`

```swift
import UIKit

class StyleQuerier {

    static func query(_ view: UIView) -> NodeAttrs? {
        switch view {
        case let label as UILabel:
            let font = label.font
            let color = label.textColor
            return .text(TextAttrs(
                fontSize: Float(font?.pointSize ?? 0),
                color: color?.toHex(),
                fontWeight: font.fontDescriptor.fontAttributes[.traits] as? String
            ))

        case let textField as UITextField:
            let font = textField.font
            let color = textField.textColor
            return .text(TextAttrs(
                fontSize: Float(font?.pointSize ?? 0),
                color: color?.toHex(),
                fontWeight: nil
            ))

        case let imageView as UIImageView:
            return .image(ImageAttrs(
                scaleType: "\(imageView.contentMode)"
            ))

        case let tableView as UITableView:
            return .list(ListAttrs(
                itemSpacing: Float(tableView.rowHeight),
                orientation: "vertical"
            ))

        default:
            return nil
        }
    }
}

// MARK: - UIColor to Hex
extension UIColor {
    func toHex() -> String? {
        guard let components = cgColor.components, components.count >= 3 else { return nil }
        let r = Float(components[0])
        let g = Float(components[1])
        let b = Float(components[2])
        return String(format: "#%02X%02X%02X", Int(r * 255), Int(g * 255), Int(b * 255))
    }
}
```

---

### Task 4.5：ViewQueryService

**文件**：`packages/ios/sdk/Sources/ViewQuery/ViewQueryService.swift`

```swift
import UIKit

class ViewQueryService {

    func getAllNodes() -> [ViewNode] {
        return ViewTraverser.traverseFromWindow()
    }

    func getNode(byId id: String) -> ViewNode? {
        let allNodes = getAllNodes()
        return allNodes.first { $0.id == id }
    }

    func findView(byId id: String) -> UIView? {
        var result: UIView?
        DispatchQueue.main.sync {
            guard let window = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .flatMap({ $0.windows })
                .first(where: { $0.isKeyWindow }) else {
                return
            }
            result = findView(in: window, byId: id)
        }
        return result
    }

    private func findView(in view: UIView, byId id: String) -> UIView? {
        if ViewHashGenerator.generateId(for: view) == id {
            return view
        }

        for subview in view.subviews {
            if let found = findView(in: subview, byId: id) {
                return found
            }
        }

        return nil
    }
}
```

---

## Phase 5：View 修改

### Task 5.1：FrameModifier

**文件**：`packages/ios/sdk/Sources/ViewModify/FrameModifier.swift`

```swift
import UIKit

class FrameModifier {

    static func modifyFrame(_ view: UIView, widthDp: String?, heightDp: String?) {
        let scale = CGFloat(UIScreen.main.scale)

        if let widthStr = widthDp {
            if widthStr == "wrap_content" {
                view.sizeToFit()
            } else if let width = CGFloat(widthStr.dropLast(2)).flatMap({ CGFloat($0) }) {
                view.frame.size.width = width * scale
            }
        }

        if let heightStr = heightDp {
            if heightStr == "wrap_content" {
                view.sizeToFit()
            } else if let height = CGFloat(heightStr.dropLast(2)).flatMap({ CGFloat($0) }) {
                view.frame.size.height = height * scale
            }
        }
    }
}
```

---

### Task 5.2：ConstraintModifier

**文件**：`packages/ios/sdk/Sources/ViewModify/ConstraintModifier.swift`

```swift
import UIKit

class ConstraintModifier {

    static func modifyMargin(_ view: UIView, attribute: NSLayoutConstraint.Attribute, constant: CGFloat) {
        // 1. 遍历父视图的外部约束（加在 superview 上的）
        if let superview = view.superview {
            if let constraint = superview.constraints.first(where: {
                ($0.firstItem as? UIView) === view && $0.firstAttribute == attribute
            }) {
                constraint.constant = constant
                return
            }
        }

        // 2. 遍历目标视图自身的内部约束（加在 view 上的，如 height/width）
        if let constraint = view.constraints.first(where: { $0.firstAttribute == attribute }) {
            constraint.constant = constant
            return
        }

        // 3. 通过私有属性查找（KVC 访问 _constraints）
        if let allConstraints = view.value(forKey: "_constraints") as? [NSLayoutConstraint] {
            if let constraint = allConstraints.first(where: { $0.firstAttribute == attribute }) {
                constraint.constant = constant
            }
        }
    }

    static func modifyMarginTop(_ view: UIView, diffDp: CGFloat) {
        modifyMargin(view, attribute: .top, constant: diffDp * UIScreen.main.scale)
    }

    static func modifyMarginBottom(_ view: UIView, diffDp: CGFloat) {
        modifyMargin(view, attribute: .bottom, constant: diffDp * UIScreen.main.scale)
    }

    static func modifyMarginLeading(_ view: UIView, diffDp: CGFloat) {
        modifyMargin(view, attribute: .leading, constant: diffDp * UIScreen.main.scale)
    }

    static func modifyMarginTrailing(_ view: UIView, diffDp: CGFloat) {
        modifyMargin(view, attribute: .trailing, constant: diffDp * UIScreen.main.scale)
    }
}
```

---

### Task 5.3：PaddingModifier

**文件**：`packages/ios/sdk/Sources/ViewModify/PaddingModifier.swift`

```swift
import UIKit

class PaddingModifier {

    static func modifyPadding(_ view: UIView, insets: UIEdgeInsets) {
        switch view {
        case let textField as UITextField:
            textField.contentEdgeInsets = insets
        case let textView as UITextView:
            textView.textContainerInset = UIEdgeInsets(
                top: insets.top, left: insets.left, bottom: insets.bottom, right: insets.right
            )
        case let button as UIButton:
            button.contentEdgeInsets = insets
        default:
            break
        }
    }
}
```

---

### Task 5.4：ViewModifyService

**文件**：`packages/ios/sdk/Sources/ViewModify/ViewModifyService.swift`

```swift
import UIKit

class ViewModifyService {

    private let viewQueryService = ViewQueryService()

    func modify(id: String, props: ModifyProps) -> Bool {
        guard let view = viewQueryService.findView(byId: id) else {
            return false
        }

        let scale = CGFloat(UIScreen.main.scale)

        // margin 修改
        if let top = props.marginTopDiffDp {
            ConstraintModifier.modifyMarginTop(view, diffDp: top)
        }
        if let bottom = props.marginBottomDiffDp {
            ConstraintModifier.modifyMarginBottom(view, diffDp: bottom)
        }
        if let leading = props.marginLeftDiffDp {
            ConstraintModifier.modifyMarginLeading(view, diffDp: leading)
        }
        if let trailing = props.marginRightDiffDp {
            ConstraintModifier.modifyMarginTrailing(view, diffDp: trailing)
        }

        // padding 修改
        let hasPadding = props.paddingTopDiffDp != nil || props.paddingBottomDiffDp != nil ||
                         props.paddingLeftDiffDp != nil || props.paddingRightDiffDp != nil
        if hasPadding {
            let insets = UIEdgeInsets(
                top: (props.paddingTopDiffDp ?? 0) * scale,
                left: (props.paddingLeftDiffDp ?? 0) * scale,
                bottom: (props.paddingBottomDiffDp ?? 0) * scale,
                right: (props.paddingRightDiffDp ?? 0) * scale
            )
            PaddingModifier.modifyPadding(view, insets: insets)
        }

        // frame 修改
        FrameModifier.modifyFrame(view, widthDp: props.widthDp, heightDp: props.heightDp)

        return true
    }
}
```

---

## Phase 6：页面跟踪

### Task 6.1：ViewControllerSwizzling

**文件**：`packages/ios/sdk/Sources/PageTracking/ViewControllerSwizzling.swift`

```swift
import UIKit

class ViewControllerSwizzling {

    static func swizzle() {
        let originalSelector = #selector(UIViewController.viewDidAppear(_:))
        let swizzledSelector = #selector(UIViewController.ct_viewDidAppear(_:))

        guard let originalMethod = class_getInstanceMethod(UIViewController.self, originalSelector),
              let swizzledMethod = class_getInstanceMethod(UIViewController.self, swizzledSelector) else {
            return
        }

        method_exchangeImplementations(originalMethod, swizzledMethod)
    }

    @objc func ct_viewDidAppear(_ animated: Bool) {
        self.ct_viewDidAppear(animated)

        let className = String(describing: type(of: self))
        ClientToolsSDK.shared.recordPageChange(className)
    }
}
```

---

### Task 6.2：PageTracker

**文件**：`packages/ios/sdk/Sources/PageTracking/PageTracker.swift`

```swift
import Foundation
import UIKit

class PageTracker {

    private var currentPageName: String = ""
    private var lastChangeTime: String = ""

    func start() {
        ViewControllerSwizzling.swizzle()
    }

    func recordPageChange(_ pageName: String) {
        currentPageName = pageName
        let formatter = DateFormatter()
        formatter.dateFormat = "MMdd-HHmm"
        lastChangeTime = formatter.string(from: Date())
    }

    func getCurrentPage() -> (pageName: String, timestamp: String) {
        return (currentPageName, lastChangeTime)
    }
}
```

---

## Phase 7：Overlay

### Task 7.1：OverlayManager

**文件**：`packages/ios/sdk/Sources/Overlay/OverlayManager.swift`

```swift
import UIKit
import WebKit

class OverlayManager {

    private var webView: WKWebView?
    private var overlayWindow: UIWindow?
    private let fileStore = HtmlFileStore()

    func show(url: String, opacity: Float) {
        hide()

        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
            return
        }

        overlayWindow = UIWindow(windowScene: windowScene)
        overlayWindow?.windowLevel = .alert - 1

        let configuration = WKWebViewConfiguration()
        webView = WKWebView(frame: UIScreen.main.bounds, configuration: configuration)
        webView?.alpha = CGFloat(opacity)
        webView?.load(URLRequest(url: URL(string: url)!))

        let vc = UIViewController()
        vc.view.addSubview(webView!)
        overlayWindow?.rootViewController = vc
        overlayWindow?.makeKeyAndVisible()
    }

    func hide() {
        overlayWindow?.isHidden = true
        overlayWindow = nil
        webView = nil
    }

    func setOpacity(_ opacity: Float) {
        webView?.alpha = CGFloat(opacity)
    }
}
```

---

### Task 7.2：HtmlFileStore

**文件**：`packages/ios/sdk/Sources/Overlay/HtmlFileStore.swift`

```swift
import Foundation

class HtmlFileStore {

    private let fileManager = FileManager.default
    private lazy var baseDir: URL = {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("client_tools/overlay")
    }()

    init() {
        try? fileManager.createDirectory(at: baseDir, withIntermediateDirectories: true)
    }

    func save(tag: String, timestamp: String, html: String) -> URL? {
        let filename = "\(tag)_\(timestamp).html"
        let fileURL = baseDir.appendingPathComponent(filename)

        do {
            try html.write(to: fileURL, atomically: true, encoding: .utf8)
            return fileURL
        } catch {
            print("[HtmlFileStore] save error: \(error)")
            return nil
        }
    }
}
```

---

## 执行顺序

```
Phase 1：项目初始化
  Task 1.1 → 1.2 → 1.3
        ↓
Phase 2：数据模型
  Task 2.1 → 2.2 → 2.3
        ↓
Phase 3：HTTP Server
  Task 3.1 → 3.2 → 3.3 → 3.4
        ↓
Phase 4：View 查询
  Task 4.1 → 4.2 → 4.3 → 4.4 → 4.5
        ↓
Phase 5：View 修改
  Task 5.1 → 5.2 → 5.3 → 5.4
        ↓
Phase 6：页面跟踪
  Task 6.1 → 6.2
        ↓
Phase 7：Overlay
  Task 7.1 → 7.2
```

---

## 验证方式

```bash
# 1. 进入 SDK 目录
cd packages/ios/sdk

# 2. 安装依赖
pod install

# 3. 构建验证
xcodebuild -workspace ClientToolsSDK.xcworkspace -scheme ClientToolsSDK -sdk iphonesimulator build

# 4. 运行 Demo App 测试
# 打开 Demo 项目，连接真机或模拟器
# 访问 http://localhost:8080/api/page/current
```

---

## 预计工作量

| Phase | 任务数 | 复杂度 |
|-------|--------|--------|
| 项目初始化 | 3 | 低 |
| 数据模型 | 3 | 中 |
| HTTP Server | 4 | 中 |
| View 查询 | 5 | 高 |
| View 修改 | 4 | 高 |
| 页面跟踪 | 2 | 中 |
| Overlay | 2 | 中 |

**总计**：约 **3-4 天**

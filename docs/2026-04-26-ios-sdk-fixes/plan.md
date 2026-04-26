# iOS SDK Bug Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 iOS SDK 中的 P0/P1 问题，使 click/scroll/modify/overlay API 可正常工作，并与 Android 协议对齐。

**Architecture:** 改动集中在 iOS SDK 的 Swift 层，不涉及 KMP/MCP。修复顺序：先数据模型，再 View 查询/修改逻辑，最后补齐 HTTP 路由和 Demo 启动。

**Tech Stack:** Swift, UIKit, Network framework (NWListener), WKWebKit

---

## 涉及文件

- Modify: `packages/ios/sdk/Sources/Model/Models.swift`
- Modify: `packages/ios/sdk/Sources/ViewQuery/ViewNode.swift`
- Modify: `packages/ios/sdk/Sources/ViewQuery/ViewTraverser.swift`
- Modify: `packages/ios/sdk/Sources/ViewQuery/ViewQueryService.swift`
- Modify: `packages/ios/sdk/Sources/ViewModify/ViewModifyService.swift`
- Modify: `packages/ios/sdk/Sources/ViewModify/ConstraintModifier.swift`
- Modify: `packages/ios/sdk/Sources/Overlay/OverlayManager.swift`
- Modify: `packages/ios/sdk/Sources/HttpServer/HttpServer.swift`
- Modify: `packages/ios/sdk/Sources/PageTracking/ViewControllerSwizzling.swift`
- Modify: `packages/ios/demo/Sources/ClientToolsDemo/AppDelegate.swift`

---

### Task 1: 补齐 Models.swift 缺失字段

**Files:**
- Modify: `packages/ios/sdk/Sources/Model/Models.swift`

**背景：** `ModifyProps` 缺少 `letterSpacingEm`、`lineSpacingExtraDp`、`includeFontPadding` 三个字段（Android `ViewProps` 已有）。同时 `OverlayShowRequest` 的字段与实际 API 不对应（Android 用 tag+html，不是 url+opacity），需替换为正确的 Webview 请求模型。

- [ ] **Step 1: 更新 ModifyProps，补齐文字属性字段**

将 `packages/ios/sdk/Sources/Model/Models.swift` 中 `ModifyProps` 替换为：

```swift
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
    public let letterSpacingEm: Float?
    public let lineSpacingExtraDp: Float?
    public let includeFontPadding: Bool?
}
```

- [ ] **Step 2: 替换 Overlay 相关请求模型**

将文件末尾的 `OverlayShowRequest`、`OverlayOpacityRequest`、`PushHtmlRequest`、`PushHtmlResult` 全部替换为：

```swift
public struct WebviewPushHtmlRequest: Codable {
    public let tag: String
    public let html: String
    public let timestamp: String
}

public struct WebviewShowRequest: Codable {
    public let tag: String
    public let timestamp: String
}

public struct WebviewAdjustRequest: Codable {
    public let offsetX: Float?
    public let offsetY: Float?
    public let opacity: Float?
}
```

- [ ] **Step 3: 提交**

```bash
git add packages/ios/sdk/Sources/Model/Models.swift
git commit -m "fix(ios/sdk): add text props to ModifyProps, fix overlay request models"
```

---

### Task 2: 修复 NodeAttrs 序列化格式（扁平化）

**Files:**
- Modify: `packages/ios/sdk/Sources/ViewQuery/ViewNode.swift`

**背景：** 当前 `NodeAttrs` 编码为嵌套格式 `{"type":"text","data":{...}}`，Android 用 kotlinx.serialization sealed class 产生的是扁平格式 `{"type":"text","fontSize":14,...}`。MCP 工具按 Android 格式解析，iOS 的嵌套格式会导致解析失败。同时补齐 `ViewNode` 的 `visibility` 和 `isEnabled` 字段。

- [ ] **Step 1: 更新 ViewNode，补 visibility/isEnabled**

将 `ViewNode` struct 替换为：

```swift
public struct ViewNode: Codable {
    public let id: String
    public let type: String
    public let screenX: Float
    public let screenY: Float
    public let widthDp: Float
    public let heightDp: Float
    public let visibility: Int      // 0=visible, 4=invisible, 8=gone
    public let isEnabled: Bool
    public let attrs: NodeAttrs?

    public init(id: String, type: String, screenX: Float, screenY: Float,
                widthDp: Float, heightDp: Float, visibility: Int, isEnabled: Bool,
                attrs: NodeAttrs?) {
        self.id = id
        self.type = type
        self.screenX = screenX
        self.screenY = screenY
        self.widthDp = widthDp
        self.heightDp = heightDp
        self.visibility = visibility
        self.isEnabled = isEnabled
        self.attrs = attrs
    }
}
```

- [ ] **Step 2: 改 NodeAttrs 为扁平序列化格式**

将 `NodeAttrs` enum 及其 `TextAttrs`/`ImageAttrs`/`ListAttrs` 全部替换为：

```swift
public enum NodeAttrs: Codable {
    case text(TextAttrs)
    case image(ImageAttrs)
    case list(ListAttrs)

    private enum CodingKeys: String, CodingKey {
        case type
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let type = try container.decode(String.self, forKey: .type)
        switch type {
        case "text":  self = .text(try TextAttrs(from: decoder))
        case "image": self = .image(try ImageAttrs(from: decoder))
        case "list":  self = .list(try ListAttrs(from: decoder))
        default: throw DecodingError.dataCorruptedError(forKey: .type, in: container, debugDescription: "Unknown type")
        }
    }

    public func encode(to encoder: Encoder) throws {
        switch self {
        case .text(let attrs):  try attrs.encode(to: encoder)
        case .image(let attrs): try attrs.encode(to: encoder)
        case .list(let attrs):  try attrs.encode(to: encoder)
        }
    }
}

public struct TextAttrs: Codable {
    public let type: String = "text"
    public let fontSize: Float?
    public let color: String?
    public let fontWeight: String?

    enum CodingKeys: String, CodingKey {
        case type, fontSize, color, fontWeight
    }
}

public struct ImageAttrs: Codable {
    public let type: String = "image"
    public let scaleType: String?

    enum CodingKeys: String, CodingKey {
        case type, scaleType
    }
}

public struct ListAttrs: Codable {
    public let type: String = "list"
    public let itemSpacing: Float?
    public let orientation: String?

    enum CodingKeys: String, CodingKey {
        case type, itemSpacing, orientation
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add packages/ios/sdk/Sources/ViewQuery/ViewNode.swift
git commit -m "fix(ios/sdk): flatten NodeAttrs serialization to match Android format, add visibility/isEnabled"
```

---

### Task 3: 修复坐标计算和 ID 匹配

**Files:**
- Modify: `packages/ios/sdk/Sources/ViewQuery/ViewTraverser.swift`
- Modify: `packages/ios/sdk/Sources/ViewQuery/ViewQueryService.swift`

**背景：** `ViewTraverser` 用 `frame.origin / scale` 计算屏幕坐标，`frame` 已是 point 单位，除以 scale 会让坐标缩小为 1/2 或 1/3。应用 `view.convert(origin, to: nil)` 转换到屏幕坐标（point = dp）。`ViewQueryService.findView` 调用 `generateId(for: view)` 不传 path，与 `traverse` 传 path 产生的 ID 不一致，需改为遍历所有节点对比 ID 的方式查找。

- [ ] **Step 1: 修复 ViewTraverser 坐标计算，补 visibility/isEnabled**

将 `traverse` 方法中节点创建部分替换：

```swift
static func traverse(_ view: UIView, path: String = "") -> [ViewNode] {
    var nodes: [ViewNode] = []

    for (index, subview) in view.subviews.enumerated() {
        if subview.tag == OverlayManager.overlayTag { continue }

        let childPath = path.isEmpty ? "\(index)" : "\(path).\(index)"
        let viewId = ViewHashGenerator.generateId(for: subview, path: childPath)

        let origin = subview.convert(CGPoint.zero, to: nil)
        let visibilityCode: Int = subview.isHidden ? 8 : (subview.alpha == 0 ? 4 : 0)

        let node = ViewNode(
            id: viewId,
            type: ViewTypeMapper.map(subview),
            screenX: Float(origin.x),
            screenY: Float(origin.y),
            widthDp: Float(subview.bounds.width),
            heightDp: Float(subview.bounds.height),
            visibility: visibilityCode,
            isEnabled: subview.isUserInteractionEnabled,
            attrs: StyleQuerier.query(subview)
        )

        nodes.append(node)
        nodes.append(contentsOf: traverse(subview, path: childPath))
    }

    return nodes
}
```

- [ ] **Step 2: 修复 ViewQueryService.findView**

将 `findView(byId:)` 及其辅助方法替换为：遍历所有节点（复用 `getAllNodes()`），用节点 ID 匹配后再从 window 中找对应 UIView。由于 `getAllNodes()` 返回的 `ViewNode` 有正确 ID，需要同步维护一个 id → UIView 的映射：

```swift
class ViewQueryService {

    func getAllNodes() -> [ViewNode] {
        return ViewTraverser.traverseFromWindow()
    }

    func getNode(byId id: String) -> ViewNode? {
        return getAllNodes().first { $0.id == id }
    }

    func findView(byId id: String) -> UIView? {
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow }) else { return nil }
        return findView(in: window, byId: id, path: "")
    }

    private func findView(in view: UIView, byId id: String, path: String) -> UIView? {
        for (index, subview) in view.subviews.enumerated() {
            if subview.tag == OverlayManager.overlayTag { continue }
            let childPath = path.isEmpty ? "\(index)" : "\(path).\(index)"
            let viewId = ViewHashGenerator.generateId(for: subview, path: childPath)
            if viewId == id { return subview }
            if let found = findView(in: subview, byId: id, path: childPath) { return found }
        }
        return nil
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add packages/ios/sdk/Sources/ViewQuery/ViewTraverser.swift \
        packages/ios/sdk/Sources/ViewQuery/ViewQueryService.swift
git commit -m "fix(ios/sdk): fix screenX/Y coordinate calculation, fix findView ID matching"
```

---

### Task 4: 修复 margin/padding 单位双倍问题

**Files:**
- Modify: `packages/ios/sdk/Sources/ViewModify/ConstraintModifier.swift`
- Modify: `packages/ios/sdk/Sources/ViewModify/ViewModifyService.swift`

**背景：** `ConstraintModifier.modifyMarginTop` 等方法调用时已将 dp 乘以 scale（`diffDp * UIScreen.main.scale`），传入 `modifyMargin` 的 `constant` 已是 pixel。但 Auto Layout 约束的 `constant` 单位是 point，不应乘以 scale，乘了之后实际效果是预期的 2x 或 3x。`ViewModifyService` 的 padding 计算同理。

- [ ] **Step 1: 修复 ConstraintModifier，调用侧不乘 scale**

将 `ConstraintModifier` 的四个 public 方法改为直接传 dp（不乘 scale）：

```swift
static func modifyMarginTop(_ view: UIView, diffDp: CGFloat) {
    modifyMargin(view, attribute: .top, constant: diffDp)
}

static func modifyMarginBottom(_ view: UIView, diffDp: CGFloat) {
    modifyMargin(view, attribute: .bottom, constant: -diffDp)
}

static func modifyMarginLeading(_ view: UIView, diffDp: CGFloat) {
    modifyMargin(view, attribute: .leading, constant: diffDp)
}

static func modifyMarginTrailing(_ view: UIView, diffDp: CGFloat) {
    modifyMargin(view, attribute: .trailing, constant: -diffDp)
}
```

同时删除 `modifyMargin` 内部的 `let scale = UIScreen.main.scale`（dead variable）：

```swift
static func modifyMargin(_ view: UIView, attribute: NSLayoutConstraint.Attribute, constant: CGFloat) {
    if let superview = view.superview {
        if let constraint = superview.constraints.first(where: {
            ($0.firstItem as? UIView) === view && $0.firstAttribute == attribute
        }) {
            constraint.constant += constant
            return
        }
        if let constraint = superview.constraints.first(where: {
            ($0.secondItem as? UIView) === view && $0.secondAttribute == attribute
        }) {
            constraint.constant -= constant
            return
        }
    }
    if let constraint = view.constraints.first(where: { $0.firstAttribute == attribute }) {
        constraint.constant += constant
        return
    }
    addConstraint(to: view, attribute: attribute, constant: constant)
}
```

- [ ] **Step 2: 修复 ViewModifyService padding 计算，切主线程**

将 `ViewModifyService.modify` 替换为：

```swift
func modify(id: String, props: ModifyProps) -> Bool {
    guard let view = viewQueryService.findView(byId: id) else { return false }

    DispatchQueue.main.async {
        if let top = props.marginTopDiffDp {
            ConstraintModifier.modifyMarginTop(view, diffDp: CGFloat(top))
        }
        if let bottom = props.marginBottomDiffDp {
            ConstraintModifier.modifyMarginBottom(view, diffDp: CGFloat(bottom))
        }
        if let leading = props.marginLeftDiffDp {
            ConstraintModifier.modifyMarginLeading(view, diffDp: CGFloat(leading))
        }
        if let trailing = props.marginRightDiffDp {
            ConstraintModifier.modifyMarginTrailing(view, diffDp: CGFloat(trailing))
        }

        let hasPadding = props.paddingTopDiffDp != nil || props.paddingBottomDiffDp != nil ||
                         props.paddingLeftDiffDp != nil || props.paddingRightDiffDp != nil
        if hasPadding {
            let insets = UIEdgeInsets(
                top: CGFloat(props.paddingTopDiffDp ?? 0),
                left: CGFloat(props.paddingLeftDiffDp ?? 0),
                bottom: CGFloat(props.paddingBottomDiffDp ?? 0),
                right: CGFloat(props.paddingRightDiffDp ?? 0)
            )
            PaddingModifier.modifyPadding(view, insets: insets)
        }

        FrameModifier.modifyFrame(view, widthDp: props.widthDp, heightDp: props.heightDp)
        view.setNeedsLayout()
        view.layoutIfNeeded()
    }
    return true
}
```

- [ ] **Step 3: 提交**

```bash
git add packages/ios/sdk/Sources/ViewModify/ConstraintModifier.swift \
        packages/ios/sdk/Sources/ViewModify/ViewModifyService.swift
git commit -m "fix(ios/sdk): fix margin/padding dp scale double-multiply, dispatch UI ops to main thread"
```

---

### Task 5: 修复 OverlayManager，补 offset 支持

**Files:**
- Modify: `packages/ios/sdk/Sources/Overlay/OverlayManager.swift`

**背景：** `show(url:)` 强制解包 URL 可能 crash；缺少 `offsetX`/`offsetY` 调整能力（Android API 支持 `adjust` 修改偏移）；需要补 `show(fileURL:)` 接受本地文件 URL。

- [ ] **Step 1: 重写 OverlayManager**

将 `OverlayManager.swift` 完整替换为：

```swift
import UIKit
import WebKit

public class OverlayManager {

    public static let overlayTag = 998

    private var webView: WKWebView?
    private var overlayWindow: UIWindow?
    private var currentOpacity: Float = 0.5
    private var offsetX: CGFloat = 0
    private var offsetY: CGFloat = 0
    public let fileStore = HtmlFileStore()

    public init() {}

    public func showFile(at fileURL: URL, opacity: Float) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.ensureWebView(opacity: opacity)
            self.webView?.loadFileURL(fileURL, allowingReadAccessTo: fileURL.deletingLastPathComponent())
        }
    }

    public func hide() {
        DispatchQueue.main.async { [weak self] in
            self?.overlayWindow?.isHidden = true
            self?.overlayWindow = nil
            self?.webView = nil
        }
    }

    public func adjust(offsetX: Float?, offsetY: Float?, opacity: Float?) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if let ox = offsetX { self.offsetX = CGFloat(ox) }
            if let oy = offsetY { self.offsetY = CGFloat(oy) }
            if let op = opacity {
                self.currentOpacity = op
                self.webView?.alpha = CGFloat(op)
            }
            self.updateWebViewFrame()
        }
    }

    private func ensureWebView(opacity: Float) {
        if webView == nil {
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
            overlayWindow = UIWindow(windowScene: windowScene)
            overlayWindow?.windowLevel = .alert - 1
            overlayWindow?.tag = OverlayManager.overlayTag

            let wv = WKWebView(frame: .zero)
            wv.isOpaque = false
            wv.backgroundColor = .clear
            wv.scrollView.isScrollEnabled = false
            webView = wv

            let vc = UIViewController()
            vc.view.backgroundColor = .clear
            vc.view.addSubview(wv)
            overlayWindow?.rootViewController = vc
            overlayWindow?.makeKeyAndVisible()
        }
        currentOpacity = opacity
        webView?.alpha = CGFloat(opacity)
        updateWebViewFrame()
    }

    private func updateWebViewFrame() {
        guard let screen = overlayWindow?.screen else { return }
        webView?.frame = CGRect(
            x: offsetX,
            y: offsetY,
            width: screen.bounds.width,
            height: screen.bounds.height
        )
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add packages/ios/sdk/Sources/Overlay/OverlayManager.swift
git commit -m "fix(ios/sdk): fix OverlayManager force-unwrap crash, add offset adjust support"
```

---

### Task 6: 防重复 swizzle

**Files:**
- Modify: `packages/ios/sdk/Sources/PageTracking/ViewControllerSwizzling.swift`

**背景：** `swizzle()` 无保护，多次调用会把方法再换回来导致 tracking 失效。

- [ ] **Step 1: 加 once 保护**

将 `ViewControllerSwizzling` 替换为：

```swift
import UIKit

class ViewControllerSwizzling {

    private static var isSwizzled = false

    static func swizzle() {
        guard !isSwizzled else { return }
        isSwizzled = true

        let originalSelector = #selector(UIViewController.viewDidAppear(_:))
        let swizzledSelector = #selector(UIViewController.ct_viewDidAppear(_:))

        guard let originalMethod = class_getInstanceMethod(UIViewController.self, originalSelector),
              let swizzledMethod = class_getInstanceMethod(UIViewController.self, swizzledSelector) else {
            return
        }
        method_exchangeImplementations(originalMethod, swizzledMethod)
    }
}

extension UIViewController {
    @objc func ct_viewDidAppear(_ animated: Bool) {
        self.ct_viewDidAppear(animated)
        let className = String(describing: type(of: self))
        ClientToolsSDK.shared.recordPageChange(className)
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add packages/ios/sdk/Sources/PageTracking/ViewControllerSwizzling.swift
git commit -m "fix(ios/sdk): prevent double swizzle in ViewControllerSwizzling"
```

---

### Task 7: 补全 HttpServer 路由（click/scroll/modify/webview）

**Files:**
- Modify: `packages/ios/sdk/Sources/HttpServer/HttpServer.swift`

**背景：** 当前路由只有 3 个端点。`handleClick`/`handleScroll`/`handleModify` 方法已存在但从未被路由调用；webview overlay 端点完全缺失。同时 click/scroll 需要切主线程。

- [ ] **Step 1: 补全 processRequest switch**

将 `processRequest` 方法中的 switch 替换为：

```swift
private func processRequest(_ request: String) -> String {
    let lines = request.components(separatedBy: "\r\n")
    guard let firstLine = lines.first else { return errorJson("Empty request") }

    let parts = firstLine.components(separatedBy: " ")
    guard parts.count >= 2 else { return errorJson("Invalid request") }

    let method = parts[0]
    let path = parts[1]

    var body = ""
    if let bodyStart = request.range(of: "\r\n\r\n") {
        body = String(request[bodyStart.upperBound...])
    }

    switch (method, path) {
    case ("GET", "/api/page/current"):
        return handlePageCurrent()
    case ("GET", "/api/nodes/all"):
        return handleNodesAll()
    case ("POST", "/api/click"):
        return handleClick(body)
    case ("POST", "/api/scroll"):
        return handleScroll(body)
    case ("POST", "/api/modify"):
        return handleModify(body)
    case ("POST", "/webview/push-html"):
        return handleWebviewPushHtml(body)
    case ("POST", "/webview/show"):
        return handleWebviewShow(body)
    case ("POST", "/webview/hide"):
        return handleWebviewHide()
    case ("POST", "/webview/adjust"):
        return handleWebviewAdjust(body)
    default:
        if method == "GET" && path.hasPrefix("/api/nodes/") {
            let nodeId = String(path.dropFirst("/api/nodes/".count))
            return handleNodeById(nodeId)
        }
        return errorJson("Not found", code: 404)
    }
}
```

- [ ] **Step 2: 修复 handleClick/handleScroll 切主线程**

将 `handleClick` 替换为：

```swift
private func handleClick(_ body: String) -> String {
    guard let data = body.data(using: .utf8),
          let req = try? JSONDecoder().decode(ClickRequest.self, from: data) else {
        return errorJson("Invalid request")
    }
    guard let view = viewQueryService.findView(byId: req.id) else {
        return errorJson("View not found", code: 404)
    }
    DispatchQueue.main.async {
        if let control = view as? UIControl {
            control.sendActions(for: .touchUpInside)
        } else {
            view.gestureRecognizers?.forEach { gr in
                if let tap = gr as? UITapGestureRecognizer {
                    tap.state == .possible ? tap.setValue(UIGestureRecognizer.State.ended.rawValue, forKey: "state") : ()
                }
            }
        }
    }
    return jsonString(ApiResponse.success(ClickResult(id: req.id)))
}
```

将 `handleScroll` 替换为：

```swift
private func handleScroll(_ body: String) -> String {
    guard let data = body.data(using: .utf8),
          let req = try? JSONDecoder().decode(ScrollRequest.self, from: data) else {
        return errorJson("Invalid request")
    }
    guard let view = viewQueryService.findView(byId: req.id),
          let scrollView = view as? UIScrollView else {
        return errorJson("View is not a scroll view", code: 400)
    }
    DispatchQueue.main.async {
        scrollView.setContentOffset(
            CGPoint(x: scrollView.contentOffset.x + CGFloat(req.dx),
                    y: scrollView.contentOffset.y + CGFloat(req.dy)),
            animated: false
        )
    }
    return jsonString(ApiResponse.success(ScrollResult(id: req.id, dx: req.dx, dy: req.dy)))
}
```

- [ ] **Step 3: 添加 Webview overlay 处理方法**

在 `HttpServer` class 末尾（`// MARK: - Helpers` 之前）添加：

```swift
private func handleWebviewPushHtml(_ body: String) -> String {
    guard let data = body.data(using: .utf8),
          let req = try? JSONDecoder().decode(WebviewPushHtmlRequest.self, from: data),
          let overlayManager = ClientToolsSDK.shared.overlayManager() else {
        return errorJson("Invalid request")
    }
    guard let fileURL = overlayManager.fileStore.save(tag: req.tag, timestamp: req.timestamp, html: req.html) else {
        return errorJson("Failed to save HTML file")
    }
    overlayManager.showFile(at: fileURL, opacity: 0.5)
    let result = ["tag": req.tag, "timestamp": req.timestamp, "filePath": fileURL.path]
    return jsonString(ApiResponse.success(result))
}

private func handleWebviewShow(_ body: String) -> String {
    guard let data = body.data(using: .utf8),
          let req = try? JSONDecoder().decode(WebviewShowRequest.self, from: data),
          let overlayManager = ClientToolsSDK.shared.overlayManager() else {
        return errorJson("Invalid request")
    }
    if let fileURL = overlayManager.fileStore.findFile(tag: req.tag, timestamp: req.timestamp) {
        overlayManager.showFile(at: fileURL, opacity: 0.5)
        return "{\"code\":0,\"message\":\"success\",\"sdkVersion\":1,\"data\":null}"
    }
    return errorJson("File not found", code: 404)
}

private func handleWebviewHide() -> String {
    ClientToolsSDK.shared.overlayManager()?.hide()
    return "{\"code\":0,\"message\":\"success\",\"sdkVersion\":1,\"data\":null}"
}

private func handleWebviewAdjust(_ body: String) -> String {
    guard let data = body.data(using: .utf8),
          let req = try? JSONDecoder().decode(WebviewAdjustRequest.self, from: data),
          let overlayManager = ClientToolsSDK.shared.overlayManager() else {
        return errorJson("Invalid request")
    }
    overlayManager.adjust(offsetX: req.offsetX, offsetY: req.offsetY, opacity: req.opacity)
    return "{\"code\":0,\"message\":\"success\",\"sdkVersion\":1,\"data\":null}"
}
```

- [ ] **Step 4: 提交**

```bash
git add packages/ios/sdk/Sources/HttpServer/HttpServer.swift
git commit -m "fix(ios/sdk): add click/scroll/modify/webview routes, dispatch UI ops to main thread"
```

---

### Task 8: HtmlFileStore 补 findFile 方法

**Files:**
- Modify: `packages/ios/sdk/Sources/Overlay/HtmlFileStore.swift`

**背景：** Task 7 的 `handleWebviewShow` 需要调用 `fileStore.findFile(tag:timestamp:)`，但 `HtmlFileStore` 目前只有 `save` 方法。

- [ ] **Step 1: 补 findFile 方法**

在 `HtmlFileStore.save` 方法后添加：

```swift
public func findFile(tag: String, timestamp: String) -> URL? {
    let filename = "\(tag)_\(timestamp).html"
    let fileURL = baseDir.appendingPathComponent(filename)
    return fileManager.fileExists(atPath: fileURL.path) ? fileURL : nil
}
```

- [ ] **Step 2: 提交**

```bash
git add packages/ios/sdk/Sources/Overlay/HtmlFileStore.swift
git commit -m "feat(ios/sdk): add findFile to HtmlFileStore"
```

---

### Task 9: Demo 启动 SDK

**Files:**
- Modify: `packages/ios/demo/Sources/ClientToolsDemo/AppDelegate.swift`

**背景：** `AppDelegate.application(_:didFinishLaunchingWithOptions:)` 返回 true 但没有启动 SDK，HTTP Server 不会运行。

- [ ] **Step 1: 在 AppDelegate 中启动 SDK**

将 `AppDelegate.swift` 替换为：

```swift
import UIKit
import ClientToolsSDK

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        ClientToolsSDK.shared.start()
        return true
    }

    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }

    func application(_ application: UIApplication, didDiscardSceneSessions sceneSessions: Set<UISceneSession>) {
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add packages/ios/demo/Sources/ClientToolsDemo/AppDelegate.swift
git commit -m "fix(ios/demo): start ClientToolsSDK in AppDelegate"
```

---

## 自查

**Spec 覆盖：**
- P0-1 路由补全 ✅ Task 7
- P0-2 主线程安全 ✅ Task 4（modify）、Task 7（click/scroll）、Task 5（overlay）
- P0-3 坐标计算 ✅ Task 3
- P0-4 ID 匹配 ✅ Task 3
- P1-1 NodeAttrs 序列化 ✅ Task 2
- P1-2 visibility/isEnabled ✅ Task 2、Task 3
- P1-3 margin scale ✅ Task 4
- P1-4 padding scale ✅ Task 4
- P1-5 webview overlay ✅ Task 5、7、8
- P1-6 Demo 启动 ✅ Task 9
- P2-1 swizzle 防重复 ✅ Task 6
- P2-2 OverlayManager 强制解包 ✅ Task 5

**类型一致性：**
- `WebviewPushHtmlRequest` 在 Task 1 定义，Task 7 使用 ✅
- `WebviewShowRequest` 在 Task 1 定义，Task 7 使用 ✅
- `WebviewAdjustRequest` 在 Task 1 定义，Task 7 使用 ✅
- `HtmlFileStore.findFile` 在 Task 8 定义，Task 7 使用 ✅
- `OverlayManager.showFile(at:opacity:)` 在 Task 5 定义，Task 7 使用 ✅
- `OverlayManager.adjust(offsetX:offsetY:opacity:)` 在 Task 5 定义，Task 7 使用 ✅

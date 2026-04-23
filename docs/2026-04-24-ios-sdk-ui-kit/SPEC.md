# iOS SDK (UIKit) 技术方案

**日期**：2026-04-24  
**范围**：iOS UIKit SDK 实现方案  
**目标**：与 Android SDK 功能对齐

---

## 一、架构概览

```
┌─────────────────────────────────────────────────────────┐
│                      iOS App                            │
│  ┌─────────────────────────────────────────────────┐   │
│  │              ClientToolsSDK                       │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────────────┐ │   │
│  │  │HTTPServer│  │ViewQuery│  │ViewModifier    │ │   │
│  │  │GCDWebServ│  │Service  │  │(frame/constraint│ │   │
│  │  │:8080    │  │         │  │ constant)       │ │   │
│  │  └─────────┘  └─────────┘  └─────────────────┘ │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────────────┐ │   │
│  │  │OverlayMGR│  │ViewHash│  │StyleQuery      │ │   │
│  │  │WKWebView│  │Generator│  │(font/color/etc)│ │   │
│  │  └─────────┘  └─────────┘  └─────────────────┘ │   │
│  └─────────────────────────────────────────────────┘   │
│                         │                               │
│  ┌─────────────────────────────────────────────────┐   │
│  │           WKWebView (HTML 叠加层)                │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
         │
         │ adb forward tcp:8080 tcp:8080
         ▼
┌─────────────────────────────────────────────────────────┐
│                    PC (MCP Server)                      │
└─────────────────────────────────────────────────────────┘
```

---

## 二、技术选型

| 组件 | 技术 | 说明 |
|------|------|------|
| HTTP Server | GCDWebServer | 轻量级嵌入式 HTTP Server |
| WebView | WKWebView | 系统内置 |
| JSON 序列化 | Codable | Swift 内置 |
| View 遍历 | UIKit Runtime | subviews 递归遍历 |
| 约束修改 | NSLayoutConstraint | constraint.constant |
| ViewInspector | ❌ 不使用 | 仅用于 SwiftUI，不用于 UIKit |

---

## 三、HTTP Server

### 3.1 技术实现

使用 **GCDWebServer**（BSD License），纯 Swift/ObjC 嵌入式 HTTP Server。

```swift
import GCDWebServer

class HttpServer {
    private let server = GCDWebServer()
    
    func start() {
        server.addDefaultHandler(forMethod: "GET", request: GCDWebServerRequest.self) { request in
            return self.handleGet(request)
        }
        server.addDefaultHandler(forMethod: "POST", request: GCDWebServerRequest.self) { request in
            return self.handlePost(request)
        }
        server.start(options: [
            GCDWebServerOption.port: 8080,
            GCDWebServerOption.bindToLocalhost: false
        ])
    }
}
```

### 3.2 API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/page/current` | GET | 获取当前页面 |
| `/api/nodes/all` | GET | 获取所有 View 节点 |
| `/api/nodes/{id}` | GET | 按 ID 获取单个节点 |
| `/api/modify` | POST | 修改 View 属性 |
| `/api/click` | POST | 点击 View |
| `/api/scroll` | POST | 滚动 View |
| `/api/overlay/show` | POST | 显示 WebView 叠加 |
| `/api/overlay/hide` | POST | 隐藏 WebView 叠加 |
| `/api/overlay/opacity` | POST | 调整透明度 |
| `/webview/push-html` | POST | 推送 HTML |
| `/inspector/push-image` | POST | 推送图片 |

### 3.3 Response 格式

与 Android SDK 完全一致：

```json
{
  "code": 0,
  "message": "success",
  "sdkVersion": 1,
  "data": { ... }
}
```

---

## 四、View 查询

### 4.1 ID 生成策略

**优先级**：
1. `accessibilityIdentifier`（宿主设置，语义化）
2. Runtime Hash（自动生成兜底）

```swift
func generateViewId(_ view: UIView, path: String = "") -> String {
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
```

### 4.2 遍历方式

从 `UIWindow` 的 rootViewController 开始，递归遍历所有 subviews：

```swift
func traverseView(_ view: UIView, path: String = "") -> [ViewNode] {
    var nodes: [ViewNode] = []
    
    for (index, subview) in view.subviews.enumerated() {
        let childPath = path.isEmpty ? "\(index)" : "\(path).\(index)"
        let viewId = generateViewId(subview, path: childPath)
        let node = ViewNode(
            id: viewId,
            type: mapViewType(subview),
            screenX: subview.frame.origin.x,
            screenY: subview.frame.origin.y,
            widthDp: subview.frame.width,
            heightDp: subview.frame.height,
            attrs: queryStyleAttributes(subview)
        )
        nodes.append(node)
        nodes.append(contentsOf: traverseView(subview, path: childPath))
    }
    return nodes
}
```

### 4.3 View 类型映射

| UIKit 控件 | type | 说明 |
|------------|------|------|
| UILabel | TEXT | |
| UITextField | TEXT | |
| UITextView | TEXT | |
| UIButton | TEXT | |
| UIImageView | IMAGE | |
| UITableView | LIST | |
| UICollectionView | LIST | |
| UIView | CONTAINER | |
| 其他 | CONTAINER | |

---

## 五、View 修改

### 5.1 修改类型

| 修改项 | 实现方式 | 代码示例 |
|--------|---------|---------|
| frame | 直接赋值 | `view.frame = newFrame` |
| bounds | 直接赋值 | `view.bounds = newBounds` |
| center | 直接赋值 | `view.center = newCenter` |
| alpha | 直接赋值 | `view.alpha = 0.5` |
| isHidden | 直接赋值 | `view.isHidden = true` |
| margin | constraint.constant | 遍历找到约束，改 constant |
| padding | contentEdgeInsets | 仅 UITextField/UITextView/UButton |

### 5.2 margin 修改

iOS 的 margin 通过 Auto Layout 约束实现。SDK 通过遍历 `superview.constraints` 找到相关约束：

```swift
func modifyMargin(view: UIView, attribute: NSLayoutConstraint.Attribute, constant: CGFloat) {
    guard let superview = view.superview else { return }
    
    // 找到约束：superview.attr == view.attr * multiplier + constant
    if let constraint = superview.constraints.first(where: {
        $0.firstItem as? UIView === view && $0.firstAttribute == attribute
    }) {
        constraint.constant = constant
    }
}
```

### 5.3 padding 修改

仅部分控件支持：

| 控件 | 属性 | 说明 |
|------|------|------|
| UITextField | `contentEdgeInsets` | |
| UITextView | `textContainerInset` | |
| UIButton | `contentEdgeInsets` | |
| UILabel | ❌ | 无内置 padding |

---

## 六、样式属性查询

### 6.1 支持的属性

| 属性 | 控件 | 获取方式 |
|------|------|---------|
| font | UILabel, UITextField | `label.font.pointSize` |
| textColor | UILabel, UITextField | `label.textColor` (转 hex) |
| text | UILabel, UITextField | `label.text` |
| backgroundColor | 所有 UIView | `view.backgroundColor` |
| alpha | 所有 UIView | `view.alpha` |
| image | UIImageView | `imageView.image` |

### 6.2 数据转换

```swift
func queryStyleAttributes(_ view: UIView) -> NodeAttrs? {
    switch view {
    case let label as UILabel:
        let font = label.font
        let color = label.textColor
        return TextAttrs(
            fontSize: font?.pointSize ?? 0,
            color: color.toHex(),
            fontWeight: font.fontDescriptor.fontAttributes[.traits]
        )
    case let imageView as UIImageView:
        return ImageAttrs(scaleType: "\(imageView.contentMode)")
    default:
        return nil
    }
}
```

---

## 七、WebView 叠加

### 7.1 实现方式

使用 **WKWebView**，通过 `UIWindow` 添加浮层：

```swift
class OverlayManager {
    private var webView: WKWebView?
    private var overlayWindow: UIWindow?
    
    func show(url: String, opacity: Float) {
        // 创建独立窗口
        overlayWindow = UIWindow()
        overlayWindow?.windowLevel = .alert - 1
        
        // 创建 WKWebView
        webView = WKWebView(frame: UIScreen.main.bounds)
        webView?.load(URLRequest(url: URL(string: url)!))
        webView?.alpha = CGFloat(opacity)
        
        overlayWindow?.rootViewController = UIViewController()
        overlayWindow?.rootViewController?.view.addSubview(webView!)
        overlayWindow?.makeKeyAndVisible()
    }
    
    func hide() {
        overlayWindow?.isHidden = true
        webView = nil
    }
    
    func setOpacity(_ opacity: Float) {
        webView?.alpha = CGFloat(opacity)
    }
}
```

### 7.2 本地 HTML 文件

与 Android 一致，SDK 保存 HTML 到 Documents 目录：

```
Documents/
└── client_tools/
    └── overlay/
        ├── login_0417-1423.html
        └── home_0418-0930.html
```

---

## 八、Click & Scroll

### 8.1 click

```swift
func click(viewId: String) -> Bool {
    guard let view = findView(byId: viewId) else { return false }
    let point = CGPoint(x: view.bounds.midX, y: view.bounds.midY)
    view.sendActions(for: .touchUpInside)
    return true
}
```

### 8.2 scroll

```swift
func scroll(viewId: String, dx: CGFloat, dy: CGFloat) -> Bool {
    guard let scrollView = findView(byId: viewId) as? UIScrollView else { return false }
    scrollView.setContentOffset(
        CGPoint(x: scrollView.contentOffset.x + dx, y: scrollView.contentOffset.y + dy),
        animated: false
    )
    return true
}
```

---

## 九、页面切换感知

### 9.1 方案

与 Android 一致，**不做主动推送**，由 AI 主动调用 `GET /api/page/current` 查询。

### 9.2 实现

SDK 内部通过 **Runtime Method Swizzling** hook `UIViewController.viewDidAppear()`，记录当前页面：

```swift
// Swizzling hook
extension UIViewController {
    @objc func ct_viewDidAppear(_ animated: Bool) {
        self.ct_viewDidAppear(animated)
        ClientToolsSDK.shared.recordPageChange(String(describing: type(of: self)))
    }
}
```

---

## 十、SDK 集成

### 10.1 接入方式

**方式 A：静态库**
```swift
// Podfile
pod 'ClientToolsSDK'
```

**方式 B：SPM**
```swift
// Package.swift
.package(url: "https://gitee.com/zzcm1259/client-tools.git", from: "1.0.0")
```

### 10.2 初始化

```swift
// AppDelegate.swift
import ClientToolsSDK

func application(_ application: UIApplication, 
                 didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
    ClientToolsSDK.shared.init()
    return true
}
```

### 10.3 宿主要求

| 要求 | 说明 | 侵入性 |
|------|------|--------|
| 设置 accessibilityIdentifier | 给需要操控的 View 设置 ID | 必须 |
| DEBUG 模式 | SDK 仅在 DEBUG 下运行 | 无影响 |

---

## 十一、与 Android 对比

| 能力 | Android | iOS UIKit | 状态 |
|------|---------|-----------|------|
| HTTP Server | Nanohttpd | GCDWebServer | ✅ 对齐 |
| WebView 叠加 | WebView | WKWebView | ✅ 对齐 |
| View 查询 | DecorView 遍历 | subviews 遍历 | ✅ 对齐 |
| View ID | android:id | accessibilityIdentifier | ✅ 对齐 |
| 样式属性 | font/color/size | font/color/size | ✅ 对齐 |
| 修改 frame | LayoutParams | frame 赋值 | ✅ 对齐 |
| 修改 margin | LayoutParams | constraint.constant | ✅ 对齐 |
| 修改 padding | setPadding() | contentEdgeInsets | ⚠️ 部分控件 |
| click | performClick() | sendActions | ✅ 对齐 |
| scroll | scrollBy() | setContentOffset | ✅ 对齐 |
| 页面感知 | ActivityLifecycle | Swizzling | ✅ 对齐 |

---

## 十二、限制与注意事项

1. **margin/padding**：仅支持有约束/insets API 的控件
2. **SwiftUI**：不支持纯 SwiftUI，仅支持 UIKit
3. **私有约束**：`translatesAutoresizingMaskIntoConstraints` 为 false 时约束生效
4. **跨视图层级**：如果 View 被其他视图遮挡，可能影响操作

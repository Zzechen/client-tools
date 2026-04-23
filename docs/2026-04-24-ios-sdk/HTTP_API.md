# iOS SDK — HTTP API 设计

**日期**：2026-04-24

---

## 一、技术实现

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

---

## 二、API 端点

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

---

## 三、Response 格式

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

## 四、Click & Scroll API

### 4.1 click

```json
// POST /api/click
{ "id": "login_btn" }
```

```swift
func click(viewId: String) -> Bool {
    guard let view = findView(byId: viewId) else { return false }
    view.sendActions(for: .touchUpInside)
    return true
}
```

### 4.2 scroll

```json
// POST /api/scroll
{ "id": "content_list", "dx": 0, "dy": -100 }
```

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

## 五、页面切换感知

### 5.1 方案

与 Android 一致，**不做主动推送**，由 AI 主动调用 `GET /api/page/current` 查询。

### 5.2 实现

SDK 内部通过 **Runtime Method Swizzling** hook `UIViewController.viewDidAppear()`：

```swift
// Swizzling hook
extension UIViewController {
    @objc func ct_viewDidAppear(_ animated: Bool) {
        self.ct_viewDidAppear(animated)
        ClientToolsSDK.shared.recordPageChange(String(describing: type(of: self)))
    }
}
```

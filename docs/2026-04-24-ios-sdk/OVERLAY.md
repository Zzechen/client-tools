# iOS SDK — WebView 叠加设计

**日期**：2026-04-24

---

## 一、实现方式

使用 **WKWebView**，通过独立的 `UIWindow` 添加浮层：

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

---

## 二、本地 HTML 文件存储

与 Android 一致，SDK 保存 HTML 到 Documents 目录：

```
Documents/
└── client_tools/
    └── overlay/
        ├── login_0417-1423.html
        └── home_0418-0930.html
```

---

## 三、API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/overlay/show` | POST | 显示 WebView 叠加 |
| `/api/overlay/hide` | POST | 隐藏 WebView 叠加 |
| `/api/overlay/opacity` | POST | 调整透明度 |
| `/webview/push-html` | POST | 推送 HTML 到设备 |
| `/inspector/push-image` | POST | 推送图片到设备 |

---

## 四、显示请求体

```json
POST /api/overlay/show
{
  "url": "file:///path/to/login_0417-1423.html",
  "opacity": 0.5
}
```

---

## 五、与 Android 对齐

| 功能 | Android | iOS | 说明 |
|------|---------|-----|------|
| WebView 组件 | WebView | WKWebView | 系统内置 |
| 浮窗层级 | WindowManager | UIWindow | 实现不同，效果相同 |
| 文件存储 | 文件系统 | 文件系统 | 相同 |
| 透明度 | ✅ | ✅ | |
| 偏移调整 | ✅ | ✅ | |

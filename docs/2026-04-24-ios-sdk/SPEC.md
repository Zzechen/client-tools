# iOS SDK 技术方案

**日期**：2026-04-24  
**范围**：iOS UIKit SDK  
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
│  │  │GCDWebServ│  │Service │  │(frame/constraint│ │   │
│  │  │:8080    │  │         │  │ constant)       │ │   │
│  │  └─────────┘  └─────────┘  └─────────────────┘ │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────────────┐ │   │
│  │  │OverlayMGR│  │ViewHash │  │StyleQuery      │ │   │
│  │  │WKWebView │  │Generator│  │(font/color/etc)│ │   │
│  │  └─────────┘  └─────────┘  └─────────────────┘ │   │
│  └─────────────────────────────────────────────────┘   │
│                         │                              │
│  ┌─────────────────────────────────────────────────┐  │
│  │           WKWebView (HTML 叠加层)                 │  │
│  └─────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
         │
         │ iproxy 8080 8080 (USB tunnel)
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

详细设计：
- [HTTP_API.md](./HTTP_API.md) — HTTP 接口设计
- [VIEW_QUERY.md](./VIEW_QUERY.md) — View 查询设计
- [VIEW_MODIFY.md](./VIEW_MODIFY.md) — View 修改设计
- [OVERLAY.md](./OVERLAY.md) — WebView 叠加设计

---

## 三、与 Android 对比

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

## 四、限制与注意事项

1. **margin/padding**：仅支持有约束/insets API 的控件
2. **SwiftUI**：不支持纯 SwiftUI，仅支持 UIKit
3. **私有约束**：`translatesAutoresizingMaskIntoConstraints` 为 false 时约束生效
4. **跨视图层级**：如果 View 被其他视图遮挡，可能影响操作

## 五、SDK 初始化

**方式**：宿主在 AppDelegate/SceneDelegate 中手动调用

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

**Info.plist 配置**：
```xml
<key>ClientToolsSDKPort</key>
<integer>8080</integer>
```

**说明**：GCDWebServer 只在 DEBUG 模式下启动，打包后自动不生效。

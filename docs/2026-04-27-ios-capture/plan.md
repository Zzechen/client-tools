# iOS SDK capture 接口实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 iOS SDK HTTP Server 新增 `GET /api/capture/{id}`，截取指定 View 为 PNG 并以 protobuf 返回。

**Architecture:** 复用现有 `ViewQueryService.findView(byId:)` 定位 View，在主线程用 `UIGraphicsImageRenderer` + `layer.render(in:)` 截图，`HttpServer` 新增路由调用并返回 `Clienttools_CaptureResponse`。

**Tech Stack:** Swift, UIKit, UIGraphicsImageRenderer, DispatchSemaphore, SwiftProtobuf

---

## 文件改动概览

| 文件 | 操作 |
|------|------|
| `clients/ios/sdk/Sources/ViewQuery/ViewQueryService.swift` | 新增 `captureView(id:) -> Data?` 方法 |
| `clients/ios/sdk/Sources/HttpServer/HttpServer.swift` | 新增 `/api/capture/` 路由及 handler |

---

### Task 1: ViewQueryService 新增 captureView 方法

**Files:**
- Modify: `clients/ios/sdk/Sources/ViewQuery/ViewQueryService.swift`

> **注意：** 此方法在之前对话中已被误写入文件，需先确认当前状态再决定是否跳过。

- [ ] **Step 1: 确认当前文件状态**

```bash
grep -n "captureView" clients/ios/sdk/Sources/ViewQuery/ViewQueryService.swift
```

预期输出（已存在）：
```
58:    func captureView(id: String) -> Data? {
```

如果已存在且内容与 Step 2 一致，直接跳到 Step 3。

- [ ] **Step 2: 若不存在，在 `findView(byId:)` 方法之前插入以下代码**

在 `clients/ios/sdk/Sources/ViewQuery/ViewQueryService.swift` 的 `func findView(byId id: String)` 之前插入：

```swift
    func captureView(id: String) -> Data? {
        guard let view = findView(byId: id) else { return nil }
        if view.bounds.width == 0 || view.bounds.height == 0 { return nil }

        var result: Data?
        let sema = DispatchSemaphore(value: 0)
        DispatchQueue.main.async {
            let renderer = UIGraphicsImageRenderer(bounds: view.bounds)
            let image = renderer.image { ctx in
                view.layer.render(in: ctx.cgContext)
            }
            result = image.pngData()
            sema.signal()
        }
        sema.wait()
        return result
    }

```

- [ ] **Step 3: 验证文件编译（检查语法）**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -20
```

预期：`BUILD SUCCEEDED`

- [ ] **Step 4: Commit**

```bash
git add clients/ios/sdk/Sources/ViewQuery/ViewQueryService.swift
git commit -m "feat(ios-sdk): add captureView method to ViewQueryService"
```

---

### Task 2: HttpServer 新增 /api/capture/ 路由

**Files:**
- Modify: `clients/ios/sdk/Sources/HttpServer/HttpServer.swift`

- [ ] **Step 1: 在 processRequest 的 default 分支之前，追加 capture 前缀匹配**

找到 `HttpServer.swift` 中的 `processRequest` 方法，在：

```swift
        default:
            if method == "GET" && path.hasPrefix("/api/nodes/") {
```

改为：

```swift
        default:
            if method == "GET" && path.hasPrefix("/api/capture/") {
                let nodeId = String(path.dropFirst("/api/capture/".count))
                handleCaptureView(nodeId, connection: connection)
            } else if method == "GET" && path.hasPrefix("/api/nodes/") {
```

- [ ] **Step 2: 在文件末尾 `}` 前新增 handleCaptureView 方法**

在 `HttpServer.swift` 最后一个 `}` 之前插入：

```swift
    private func handleCaptureView(_ id: String, connection: NWConnection) {
        guard let data = viewQueryService.captureView(id: id) else {
            sendError(code: 404, message: "View not found or has no size", httpCode: 404, connection: connection)
            return
        }
        var resp = Clienttools_CaptureResponse()
        resp.meta = okMeta()
        resp.imagePng = data
        sendProto(resp, connection: connection)
    }
```

- [ ] **Step 3: 验证编译**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -20
```

预期：`BUILD SUCCEEDED`

- [ ] **Step 4: Commit**

```bash
git add clients/ios/sdk/Sources/HttpServer/HttpServer.swift
git commit -m "feat(ios-sdk): add GET /api/capture/{id} route to HttpServer"
```

---

### Task 3: 手动验证接口可用

**Files:** 无代码改动

- [ ] **Step 1: 在模拟器中运行 Demo App**

用 Xcode 打开 `clients/ios/demo/ClientToolsDemo.xcworkspace`，选择模拟器，运行。

- [ ] **Step 2: 获取某个 View 的 id**

```bash
curl -s http://localhost:8080/api/nodes/all | xxd | head -20
```

或通过 MCP 工具 `get_all_nodes` 获取一个有效的 view id。

- [ ] **Step 3: 请求截图接口**

```bash
curl -s http://localhost:8080/api/capture/<view_id> -o /tmp/capture_resp.bin
ls -la /tmp/capture_resp.bin
```

预期：文件大小 > 0（包含 protobuf 响应）

- [ ] **Step 4: 通过 MCP capture_view 工具验证（如可用）**

在 Claude Code 中调用：
```
capture_view(id="<view_id>")
```

预期：返回截图图片。

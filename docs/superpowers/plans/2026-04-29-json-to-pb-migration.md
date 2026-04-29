# JSON → Protobuf 全量迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `/inspector/*`、`/webview/files`、`/dom/*` 共 8 条路由从 JSON 迁移到 Protobuf，消除 Android/iOS/MCP 三层的格式混用。

**Architecture:** 新建 `proto/inspector.proto` 定义所有新 message；Android 将 `InspectorApiHandler` 合并进 `ApiHandler`；iOS 重写 `InspectorApiHandler` 方法签名，补齐 `/webview/files`；MCP 全部换用 `sdkGet`/`sdkPost` + proto schema。

**Tech Stack:** Protocol Buffers (buf v2)、Kotlin/Android、Swift/iOS、TypeScript/MCP

---

## 文件变更清单

| 操作 | 文件 |
|------|------|
| 新建 | `proto/inspector.proto` |
| 新建 | `clients/android/sdk/src/main/proto/inspector.proto` |
| 新建（生成） | `clients/ios/sdk/Sources/Generated/inspector.pb.swift` |
| 新建（生成） | `mcp/src/generated/inspector_pb.ts` |
| 修改 | `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt` |
| 删除 | `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt` |
| 修改 | `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt` |
| 修改 | `clients/ios/sdk/Sources/HttpServer/Inspector/InspectorApiHandler.swift` |
| 修改 | `clients/ios/sdk/Sources/HttpServer/HttpServer.swift` |
| 修改 | `clients/ios/sdk/Sources/Overlay/OverlayManager.swift` |
| 修改 | `mcp/src/tools/inspector.ts` |
| 修改 | `mcp/src/tools/image.ts` |
| 修改 | `mcp/src/tools/dom.ts` |
| 修改 | `mcp/src/sdk-client.ts` |

---

## Task 1: 编写 inspector.proto 并生成代码

**Files:**
- Create: `proto/inspector.proto`
- Create: `clients/android/sdk/src/main/proto/inspector.proto`（内容与上同）

- [ ] **Step 1: 创建 proto/inspector.proto**

```protobuf
syntax = "proto3";
package clienttools;
option java_package = "com.clienttools.sdk.proto";
option java_multiple_files = true;
import "api.proto";

// /webview/files
message FileItem          { string tag = 1; string timestamp = 2; string file_path = 3; bool is_current = 4; }
message FileListResult    { repeated FileItem files = 1; }
message FileListResponse  { ResponseMeta meta = 1; FileListResult data = 2; }

// /inspector/push-image
message PushImageRequest  { string tag = 1; string timestamp = 2; bytes image = 3; string ext = 4; }
message PushImageResult   { string tag = 1; string timestamp = 2; string file_path = 3; int64 file_size = 4; }
message PushImageResponse { ResponseMeta meta = 1; PushImageResult data = 2; }

// /inspector/show-image
message ShowImageRequest  { string tag = 1; string timestamp = 2; }
message ShowImageResult   { string tag = 1; string timestamp = 2; float opacity = 3; float offset_x = 4; float offset_y = 5; }
message ShowImageResponse { ResponseMeta meta = 1; ShowImageResult data = 2; }

// /inspector/images
message ImageItem         { string tag = 1; string timestamp = 2; string ext = 3; int64 size = 4; bool is_current = 5; }
message ImageListResult   { repeated ImageItem images = 1; }
message ImageListResponse { ResponseMeta meta = 1; ImageListResult data = 2; }

// /inspector/hide  (type: "image" | "webview" | "" 空=按当前 activeTab 判断)
message HideRequest       { string type = 1; }

// /inspector/adjust
message InspectorAdjustRequest  { string type = 1; float offset_x = 2; float offset_y = 3; float opacity = 4; }
message InspectorAdjustResult   { float offset_x = 1; float offset_y = 2; float opacity = 3; }
message InspectorAdjustResponse { ResponseMeta meta = 1; InspectorAdjustResult data = 2; }

// /dom/all
message DomNode           { string id = 1; string tag = 2; string text = 3; float x = 4; float y = 5; float width = 6; float height = 7; }
message DomNodeList       { repeated DomNode nodes = 1; }
message DomAllResponse    { ResponseMeta meta = 1; DomNodeList data = 2; }

// /dom/:id
message DomNodeResponse   { ResponseMeta meta = 1; DomNode data = 2; }
```

- [ ] **Step 2: 同步到 Android proto 目录**

```bash
cp proto/inspector.proto clients/android/sdk/src/main/proto/inspector.proto
```

- [ ] **Step 3: 运行 buf generate**

```bash
cd proto && buf generate
```

预期输出：无报错，生成：
- `clients/ios/sdk/Sources/Generated/inspector.pb.swift`
- `mcp/src/generated/inspector_pb.ts`

- [ ] **Step 4: 验证生成文件存在且包含预期类型**

```bash
grep -l "FileListResponse\|PushImageRequest\|DomNode" \
  clients/ios/sdk/Sources/Generated/inspector.pb.swift \
  mcp/src/generated/inspector_pb.ts
```

预期：两个文件都被列出。

- [ ] **Step 5: Commit**

```bash
git add proto/inspector.proto \
  clients/android/sdk/src/main/proto/inspector.proto \
  clients/ios/sdk/Sources/Generated/inspector.pb.swift \
  mcp/src/generated/inspector_pb.ts
git commit -m "feat(proto): add inspector.proto — image/dom/files message definitions"
```

---

## Task 2: Android — ApiHandler 新增 8 个 PB handler

**Files:**
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt`

- [ ] **Step 1: 在 ApiHandler.kt 中新增 handleWebviewFiles**

在现有 `handleWebviewHide()` 之后添加（替换原来通过 `InspectorFileStore` 拼 JSON 的实现）：

```kotlin
fun handleWebviewFiles(fileStore: InspectorFileStore): NanoHTTPD.Response {
    return try {
        val files = fileStore.getAllFiles()
        val currentFile = ClientToolsSDK.getTop()?.viewModel?.webView?.value?.currentFile
        val items = files.map { f ->
            val isCurrent = currentFile?.tag == f.tag && currentFile?.timestamp == f.timestamp
            FileItem.newBuilder()
                .setTag(f.tag)
                .setTimestamp(f.timestamp)
                .setFilePath(f.fileUrl)
                .setIsCurrent(isCurrent)
                .build()
        }
        val result = FileListResult.newBuilder().addAllFiles(items).build()
        val resp = FileListResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
        okResponse(resp.toByteArray())
    } catch (e: Exception) {
        Log.e("ApiHandler", "handleWebviewFiles", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}
```

- [ ] **Step 2: 新增 handlePushImage**

```kotlin
fun handlePushImage(bodyBytes: ByteArray, imageFileStore: ImageFileStore): NanoHTTPD.Response {
    return try {
        val req = PushImageRequest.parseFrom(bodyBytes)
        val bytes = req.image.toByteArray()
        val timestamp = req.timestamp.ifEmpty { imageFileStore.generateTimestamp() }
        val saved = imageFileStore.saveImage(req.tag, timestamp, bytes, req.ext.ifEmpty { "png" })
            ?: return errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "Failed to save image")
        ClientToolsSDK.getTop()?.viewModel?.let { vm ->
            vm.image.value = vm.image.value.copy(currentImage = saved, isVisible = true)
        }
        val result = PushImageResult.newBuilder()
            .setTag(req.tag).setTimestamp(timestamp)
            .setFilePath(saved.filePath).setFileSize(bytes.size.toLong())
            .build()
        val resp = PushImageResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
        okResponse(resp.toByteArray())
    } catch (e: Exception) {
        Log.e("ApiHandler", "handlePushImage", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}
```

- [ ] **Step 3: 新增 handleShowImage**

```kotlin
fun handleShowImage(bodyBytes: ByteArray, imageFileStore: ImageFileStore): NanoHTTPD.Response {
    return try {
        val req = ShowImageRequest.parseFrom(bodyBytes)
        val filePath = imageFileStore.getFilePath(req.tag, req.timestamp)
            ?: return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Image not found")
        val ext = java.io.File(filePath).extension.lowercase()
        ClientToolsSDK.getTop()?.viewModel?.let { vm ->
            vm.image.value = vm.image.value.copy(
                currentImage = ImageInfo(req.tag, req.timestamp, filePath, ext),
                isVisible = true
            )
        }
        val s = ClientToolsSDK.getTop()?.viewModel?.image?.value ?: ImageState()
        val result = ShowImageResult.newBuilder()
            .setTag(req.tag).setTimestamp(req.timestamp)
            .setOpacity(s.opacity).setOffsetX(s.offsetX).setOffsetY(s.offsetY)
            .build()
        val resp = ShowImageResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
        okResponse(resp.toByteArray())
    } catch (e: Exception) {
        Log.e("ApiHandler", "handleShowImage", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}
```

- [ ] **Step 4: 新增 handleGetImages**

```kotlin
fun handleGetImages(imageFileStore: ImageFileStore): NanoHTTPD.Response {
    return try {
        val vmCurrentImage = ClientToolsSDK.getTop()?.viewModel?.image?.value?.currentImage
        val images = imageFileStore.getAllImages()
        val items = images.map { img ->
            val isCurrent = vmCurrentImage?.tag == img.tag && vmCurrentImage?.timestamp == img.timestamp
            val size = java.io.File(img.filePath).length()
            ImageItem.newBuilder()
                .setTag(img.tag).setTimestamp(img.timestamp)
                .setExt(img.ext).setSize(size).setIsCurrent(isCurrent)
                .build()
        }
        val result = ImageListResult.newBuilder().addAllImages(items).build()
        val resp = ImageListResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
        okResponse(resp.toByteArray())
    } catch (e: Exception) {
        Log.e("ApiHandler", "handleGetImages", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}
```

- [ ] **Step 5: 新增 handleInspectorHide**

```kotlin
fun handleInspectorHide(bodyBytes: ByteArray): NanoHTTPD.Response {
    return try {
        val req = HideRequest.parseFrom(bodyBytes)
        val vm = ClientToolsSDK.getTop()?.viewModel
        when (req.type) {
            "image"   -> vm?.image?.value = vm?.image?.value?.copy(isVisible = false) ?: ImageState()
            "webview" -> vm?.webView?.value = vm?.webView?.value?.copy(isVisible = false) ?: WebViewState()
            else -> when (vm?.activeTab?.value) {
                ActiveTab.IMAGE -> vm.image.value = vm.image.value.copy(isVisible = false)
                else -> vm?.webView?.value = vm?.webView?.value?.copy(isVisible = false) ?: WebViewState()
            }
        }
        val resp = SimpleResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
        okResponse(resp.toByteArray())
    } catch (e: Exception) {
        Log.e("ApiHandler", "handleInspectorHide", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}
```

- [ ] **Step 6: 新增 handleInspectorAdjust**

```kotlin
fun handleInspectorAdjust(bodyBytes: ByteArray): NanoHTTPD.Response {
    return try {
        val req = InspectorAdjustRequest.parseFrom(bodyBytes)
        val vm = ClientToolsSDK.getTop()?.viewModel
        val isImage = req.type == "image" || (req.type.isEmpty() && vm?.activeTab?.value == ActiveTab.IMAGE)
        val result: InspectorAdjustResult
        if (isImage) {
            val s = vm?.image?.value ?: ImageState()
            val newState = s.copy(
                offsetX = s.offsetX + req.offsetX,
                offsetY = s.offsetY + req.offsetY,
                opacity = if (req.opacity > 0f) req.opacity.coerceIn(0f, 1f) else s.opacity
            )
            vm?.image?.value = newState
            result = InspectorAdjustResult.newBuilder()
                .setOffsetX(newState.offsetX).setOffsetY(newState.offsetY).setOpacity(newState.opacity).build()
        } else {
            val s = vm?.webView?.value ?: WebViewState()
            val newState = s.copy(
                offsetX = s.offsetX + req.offsetX,
                offsetY = s.offsetY + req.offsetY,
                opacity = if (req.opacity > 0f) req.opacity.coerceIn(0f, 1f) else s.opacity
            )
            vm?.webView?.value = newState
            result = InspectorAdjustResult.newBuilder()
                .setOffsetX(newState.offsetX).setOffsetY(newState.offsetY).setOpacity(newState.opacity).build()
        }
        val resp = InspectorAdjustResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
        okResponse(resp.toByteArray())
    } catch (e: Exception) {
        Log.e("ApiHandler", "handleInspectorAdjust", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}
```

- [ ] **Step 7: 新增 handleDomAll 和 handleDomById（迁移自 InspectorApiHandler）**

```kotlin
suspend fun handleDomAll(webView: android.webkit.WebView?): NanoHTTPD.Response {
    if (webView == null) return errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "webview not ready")
    val vm = ClientToolsSDK.getTop()?.viewModel
    val offsetX = vm?.webView?.value?.offsetX ?: 0f
    val offsetY = vm?.webView?.value?.offsetY ?: 0f
    return try {
        val nodes = DomQueryService(timeoutMs = 3000L).queryAll(webView, offsetX, offsetY)
        val domNodes = nodes.map { n ->
            DomNode.newBuilder()
                .setId(n.id).setTag(n.tagName).setText(n.text)
                .setX(n.x).setY(n.y).setWidth(n.width).setHeight(n.height)
                .build()
        }
        val nodeList = DomNodeList.newBuilder().addAllNodes(domNodes).build()
        val resp = DomAllResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(nodeList).build()
        okResponse(resp.toByteArray())
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "dom query timeout")
    } catch (e: Exception) {
        Log.e("ApiHandler", "handleDomAll", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}

suspend fun handleDomById(webView: android.webkit.WebView?, id: String): NanoHTTPD.Response {
    if (webView == null) return errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "webview not ready")
    val vm = ClientToolsSDK.getTop()?.viewModel
    val offsetX = vm?.webView?.value?.offsetX ?: 0f
    val offsetY = vm?.webView?.value?.offsetY ?: 0f
    return try {
        val n = DomQueryService(timeoutMs = 3000L).queryById(webView, id, offsetX, offsetY)
            ?: return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "dom node not found")
        val domNode = DomNode.newBuilder()
            .setId(n.id).setTag(n.tagName).setText(n.text)
            .setX(n.x).setY(n.y).setWidth(n.width).setHeight(n.height)
            .build()
        val resp = DomNodeResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(domNode).build()
        okResponse(resp.toByteArray())
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "dom query timeout")
    } catch (e: Exception) {
        Log.e("ApiHandler", "handleDomById $id", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}
```

- [ ] **Step 8: 编译验证**

```bash
cd clients/android && ./gradlew :sdk:assembleDebug
```

预期：BUILD SUCCESSFUL，无编译错误。

- [ ] **Step 9: Commit**

```bash
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt
git commit -m "feat(android): add PB handlers for inspector/dom/files in ApiHandler"
```

---

## Task 3: Android — HttpServer 路由切换 + 删除 InspectorApiHandler

**Files:**
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`
- Delete: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt`

- [ ] **Step 1: 更新 HttpServer.kt 路由**

在 `HttpServer.kt` 的 `serve()` 方法中，将以下路由全部替换：

旧代码（`/webview/files` 路由）：
```kotlin
method == Method.GET && uri == "/webview/files" ->
    ApiHandler.handleWebviewFiles(ClientToolsSDK.fileStore)
```
保持不变，但 `ApiHandler.handleWebviewFiles` 现在返回 PB 响应，无需改路由本身。

将 `/inspector/*` 和 `/dom/*` 路由从调用 `inspectorHandler()` 改为调用 `ApiHandler`：

```kotlin
method == Method.POST && uri == "/inspector/push-image" ->
    ApiHandler.handlePushImage(readBodyBytes(session), ClientToolsSDK.imageFileStore)

method == Method.POST && uri == "/inspector/show-image" ->
    ApiHandler.handleShowImage(readBodyBytes(session), ClientToolsSDK.imageFileStore)

method == Method.GET && uri == "/inspector/images" ->
    ApiHandler.handleGetImages(ClientToolsSDK.imageFileStore)

method == Method.POST && uri == "/inspector/hide" ->
    ApiHandler.handleInspectorHide(readBodyBytes(session))

method == Method.POST && uri == "/inspector/adjust" ->
    ApiHandler.handleInspectorAdjust(readBodyBytes(session))

method == Method.GET && uri == "/dom/all" -> {
    val webView = ClientToolsSDK.getTop()?.renderer?.webView
    kotlinx.coroutines.runBlocking {
        ApiHandler.handleDomAll(webView)
    }
}

method == Method.GET && uri.startsWith("/dom/") -> {
    val id = uri.removePrefix("/dom/")
    val webView = ClientToolsSDK.getTop()?.renderer?.webView
    kotlinx.coroutines.runBlocking {
        ApiHandler.handleDomById(webView, id)
    }
}
```

同时删除 `private fun inspectorHandler()` 方法（不再需要）。

- [ ] **Step 2: 删除 InspectorApiHandler.kt**

```bash
rm clients/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt
```

- [ ] **Step 3: 编译验证**

```bash
cd clients/android && ./gradlew :sdk:assembleDebug
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt
git rm clients/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt
git commit -m "refactor(android): route inspector/dom to ApiHandler, delete InspectorApiHandler"
```

---

## Task 4: iOS — OverlayManager DOM 查询改返回 DomNode 列表

**Files:**
- Modify: `clients/ios/sdk/Sources/Overlay/OverlayManager.swift`

- [ ] **Step 1: 修改 queryDomAll 签名和实现**

将现有：
```swift
public func queryDomAll(completion: @escaping (String) -> Void)
```
改为：
```swift
public func queryDomAll(completion: @escaping ([Clienttools_DomNode]) -> Void) {
    queryDomRaw(js: domAllJS()) { json in
        let nodes = Self.parseDomNodes(from: json)
        completion(nodes)
    }
}
```

- [ ] **Step 2: 修改 queryDomById 签名和实现**

将现有：
```swift
public func queryDomById(_ id: String, completion: @escaping (String) -> Void)
```
改为：
```swift
public func queryDomById(_ id: String, completion: @escaping (Clienttools_DomNode?) -> Void) {
    let escaped = id.replacingOccurrences(of: "\"", with: "\\\"")
    queryDomRaw(js: domByIdJS(escaped)) { json in
        let node = Self.parseDomNode(from: json)
        completion(node)
    }
}
```

- [ ] **Step 3: 将原 queryDom 重命名为 queryDomRaw，添加解析辅助方法**

将 `private func queryDom(js:completion:)` 重命名为 `queryDomRaw`，签名不变（仍返回 String）：

```swift
private func queryDomRaw(js: String, completion: @escaping (String) -> Void) {
    // 原 queryDom 的实现，保持不变
}
```

新增解析方法：

```swift
private static func parseDomNodes(from json: String) -> [Clienttools_DomNode] {
    guard let data = json.data(using: .utf8),
          let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
    return arr.compactMap { parseDomNodeDict($0) }
}

private static func parseDomNode(from json: String) -> Clienttools_DomNode? {
    guard let data = json.data(using: .utf8),
          let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
    return parseDomNodeDict(dict)
}

private static func parseDomNodeDict(_ d: [String: Any]) -> Clienttools_DomNode? {
    guard let id = d["id"] as? String else { return nil }
    var node = Clienttools_DomNode()
    node.id   = id
    node.tag  = (d["tag"] as? String) ?? ""
    node.text = (d["text"] as? String) ?? ""
    node.x    = Float((d["x"] as? NSNumber)?.doubleValue ?? 0)
    node.y    = Float((d["y"] as? NSNumber)?.doubleValue ?? 0)
    node.width  = Float((d["width"]  as? NSNumber)?.doubleValue ?? 0)
    node.height = Float((d["height"] as? NSNumber)?.doubleValue ?? 0)
    return node
}
```

- [ ] **Step 4: iOS 编译验证**

在 Xcode 或命令行确认编译通过（如用 xcodebuild，或在 Xcode 中 Cmd+B 检查无红色报错）。

- [ ] **Step 5: Commit**

```bash
git add clients/ios/sdk/Sources/Overlay/OverlayManager.swift
git commit -m "refactor(ios): queryDomAll/queryDomById return [DomNode] instead of JSON string"
```

---

## Task 5: iOS — InspectorApiHandler 重写为 PB 响应

**Files:**
- Modify: `clients/ios/sdk/Sources/HttpServer/Inspector/InspectorApiHandler.swift`

- [ ] **Step 1: 修改 InspectorApiHandler 方法签名**

将文件完整重写为如下实现（所有方法从 `-> (Int, String)` 改为接收 `connection: NWConnection`，内部直接调用传入的 `sendProto`/`sendError`）：

```swift
import Foundation
import Network
import SwiftProtobuf

class InspectorApiHandler {

    private let viewModel: InspectorViewModel
    private let imageFileStore: ImageFileStore
    private let sendProto: (SwiftProtobuf.Message, Int, NWConnection) -> Void
    private let sendError: (Int32, String, Int, NWConnection) -> Void

    init(
        viewModel: InspectorViewModel,
        imageFileStore: ImageFileStore,
        sendProto: @escaping (SwiftProtobuf.Message, Int, NWConnection) -> Void,
        sendError: @escaping (Int32, String, Int, NWConnection) -> Void
    ) {
        self.viewModel = viewModel
        self.imageFileStore = imageFileStore
        self.sendProto = sendProto
        self.sendError = sendError
    }

    // MARK: - push-image

    func handlePushImage(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_PushImageRequest(serializedBytes: body) else {
            sendError(400, "Invalid request", 400, connection); return
        }
        let bytes = Data(req.image)
        let timestamp = req.timestamp.isEmpty ? imageFileStore.generateTimestamp() : req.timestamp
        let ext = req.ext.isEmpty ? "png" : req.ext
        guard let saved = imageFileStore.saveImage(tag: req.tag, timestamp: timestamp, bytes: bytes, ext: ext) else {
            sendError(500, "Failed to save image", 500, connection); return
        }
        viewModel.imageState = ImageState(
            currentImage: saved, isVisible: true,
            offsetX: viewModel.imageState.offsetX,
            offsetY: viewModel.imageState.offsetY,
            opacity: viewModel.imageState.opacity
        )
        viewModel.activeTab = .image
        var result = Clienttools_PushImageResult()
        result.tag = req.tag; result.timestamp = timestamp
        result.filePath = saved.filePath; result.fileSize = Int64(bytes.count)
        var resp = Clienttools_PushImageResponse()
        resp.meta = okMeta(); resp.data = result
        sendProto(resp, 200, connection)
    }

    // MARK: - show-image

    func handleShowImage(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_ShowImageRequest(serializedBytes: body) else {
            sendError(400, "Invalid request", 400, connection); return
        }
        guard let filePath = imageFileStore.getFilePath(tag: req.tag, timestamp: req.timestamp) else {
            sendError(404, "Image not found", 404, connection); return
        }
        let ext = URL(fileURLWithPath: filePath).pathExtension.lowercased()
        let info = ImageInfo(tag: req.tag, timestamp: req.timestamp, filePath: filePath, ext: ext)
        viewModel.imageState = ImageState(
            currentImage: info, isVisible: true,
            offsetX: viewModel.imageState.offsetX,
            offsetY: viewModel.imageState.offsetY,
            opacity: viewModel.imageState.opacity
        )
        viewModel.activeTab = .image
        let s = viewModel.imageState
        var result = Clienttools_ShowImageResult()
        result.tag = req.tag; result.timestamp = req.timestamp
        result.opacity = s.opacity; result.offsetX = s.offsetX; result.offsetY = s.offsetY
        var resp = Clienttools_ShowImageResponse()
        resp.meta = okMeta(); resp.data = result
        sendProto(resp, 200, connection)
    }

    // MARK: - images

    func handleGetImages(connection: NWConnection) {
        let currentImage = viewModel.imageState.currentImage
        let images = imageFileStore.getAllImages()
        let items: [Clienttools_ImageItem] = images.map { img in
            let isCurrent = img.tag == currentImage?.tag && img.timestamp == currentImage?.timestamp
            let size = (try? FileManager.default.attributesOfItem(atPath: img.filePath)[.size] as? Int) ?? 0
            var item = Clienttools_ImageItem()
            item.tag = img.tag; item.timestamp = img.timestamp
            item.ext = img.ext; item.size = Int64(size); item.isCurrent = isCurrent
            return item
        }
        var result = Clienttools_ImageListResult()
        result.images = items
        var resp = Clienttools_ImageListResponse()
        resp.meta = okMeta(); resp.data = result
        sendProto(resp, 200, connection)
    }

    // MARK: - hide

    func handleHide(_ body: Data, connection: NWConnection) {
        let req = (try? Clienttools_HideRequest(serializedBytes: body)) ?? Clienttools_HideRequest()
        switch req.type {
        case "image":
            viewModel.imageState.isVisible = false
        case "webview":
            viewModel.webViewState.isVisible = false
        default:
            if viewModel.activeTab == .image {
                viewModel.imageState.isVisible = false
            } else {
                viewModel.webViewState.isVisible = false
            }
        }
        var resp = Clienttools_SimpleResponse()
        resp.meta = okMeta()
        sendProto(resp, 200, connection)
    }

    // MARK: - adjust

    func handleAdjust(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_InspectorAdjustRequest(serializedBytes: body) else {
            sendError(400, "Invalid request", 400, connection); return
        }
        let isImage = req.type == "image" || (req.type.isEmpty && viewModel.activeTab == .image)
        var result = Clienttools_InspectorAdjustResult()
        if isImage {
            var s = viewModel.imageState
            s.offsetX += req.offsetX; s.offsetY += req.offsetY
            if req.opacity > 0 { s.opacity = min(max(req.opacity, 0), 1) }
            viewModel.imageState = s
            result.offsetX = s.offsetX; result.offsetY = s.offsetY; result.opacity = s.opacity
        } else {
            var s = viewModel.webViewState
            s.offsetX += req.offsetX; s.offsetY += req.offsetY
            if req.opacity > 0 { s.opacity = min(max(req.opacity, 0), 1) }
            viewModel.webViewState = s
            result.offsetX = s.offsetX; result.offsetY = s.offsetY; result.opacity = s.opacity
        }
        var resp = Clienttools_InspectorAdjustResponse()
        resp.meta = okMeta(); resp.data = result
        sendProto(resp, 200, connection)
    }

    // MARK: - private

    private func okMeta() -> Clienttools_ResponseMeta {
        var meta = Clienttools_ResponseMeta()
        meta.code = 0; meta.message = "success"
        return meta
    }
}
```

- [ ] **Step 2: iOS 编译验证**（编译器会报 HttpServer.swift 调用处不匹配，预期失败，下一步修复）

- [ ] **Step 3: Commit**

```bash
git add clients/ios/sdk/Sources/HttpServer/Inspector/InspectorApiHandler.swift
git commit -m "refactor(ios): InspectorApiHandler methods use PB + NWConnection"
```

---

## Task 6: iOS — HttpServer 路由更新 + 新增 /webview/files

**Files:**
- Modify: `clients/ios/sdk/Sources/HttpServer/HttpServer.swift`

- [ ] **Step 1: 更新 inspectorHandler 初始化，传入 sendProto/sendError 闭包**

将 `HttpServer` 中 `inspectorHandler` 的 lazy 属性改为：

```swift
private lazy var inspectorHandler = InspectorApiHandler(
    viewModel: ClientToolsSDK.shared.inspectorViewModel,
    imageFileStore: ClientToolsSDK.shared.imageFileStore,
    sendProto: { [weak self] msg, code, conn in self?.sendProto(msg, statusCode: code, connection: conn) },
    sendError: { [weak self] code, msg, httpCode, conn in self?.sendError(code: code, message: msg, httpCode: httpCode, connection: conn) }
)
```

- [ ] **Step 2: 替换 /inspector/* 路由调用方式**

将 `processRequest` 中原来 `sendJson` 模式全部替换：

```swift
case ("POST", "/inspector/push-image"):
    inspectorHandler.handlePushImage(bodyData, connection: connection)
case ("POST", "/inspector/show-image"):
    inspectorHandler.handleShowImage(bodyData, connection: connection)
case ("GET", "/inspector/images"):
    inspectorHandler.handleGetImages(connection: connection)
case ("POST", "/inspector/hide"):
    inspectorHandler.handleHide(bodyData, connection: connection)
case ("POST", "/inspector/adjust"):
    inspectorHandler.handleAdjust(bodyData, connection: connection)
```

- [ ] **Step 3: 新增 /webview/files 路由**

在 `processRequest` 的 switch 中新增：

```swift
case ("GET", "/webview/files"):
    handleWebviewFiles(connection: connection)
```

并在 `HttpServer` 中新增 handler：

```swift
private func handleWebviewFiles(connection: NWConnection) {
    guard let overlayManager = ClientToolsSDK.shared.overlayManager() else {
        sendError(code: 400, message: "OverlayManager not ready", httpCode: 503, connection: connection); return
    }
    let files = overlayManager.fileStore.getAllFiles()
    let currentFile = ClientToolsSDK.shared.inspectorViewModel.webViewState.currentFile
    let items: [Clienttools_FileItem] = files.map { f in
        var item = Clienttools_FileItem()
        item.tag = f.tag; item.timestamp = f.timestamp
        item.filePath = f.filePath
        item.isCurrent = f.tag == currentFile?.tag && f.timestamp == currentFile?.timestamp
        return item
    }
    var result = Clienttools_FileListResult()
    result.files = items
    var resp = Clienttools_FileListResponse()
    resp.meta = okMeta(); resp.data = result
    sendProto(resp, connection: connection)
}
```

- [ ] **Step 4: 更新 /dom/all 和 /dom/:id 响应为 PB**

将 `handleDomAll` 和 `handleDomById` 改为：

```swift
private func handleDomAll(connection: NWConnection) {
    guard let overlayManager = ClientToolsSDK.shared.overlayManager() else {
        sendError(code: 503, message: "OverlayManager not ready", httpCode: 503, connection: connection); return
    }
    overlayManager.queryDomAll { nodes in
        var nodeList = Clienttools_DomNodeList()
        nodeList.nodes = nodes
        var resp = Clienttools_DomAllResponse()
        resp.meta = self.okMeta(); resp.data = nodeList
        self.sendProto(resp, connection: connection)
    }
}

private func handleDomById(_ id: String, connection: NWConnection) {
    guard let overlayManager = ClientToolsSDK.shared.overlayManager() else {
        sendError(code: 503, message: "OverlayManager not ready", httpCode: 503, connection: connection); return
    }
    overlayManager.queryDomById(id) { node in
        guard let node = node else {
            self.sendError(code: 404, message: "DOM node not found", httpCode: 404, connection: connection); return
        }
        var resp = Clienttools_DomNodeResponse()
        resp.meta = self.okMeta(); resp.data = node
        self.sendProto(resp, connection: connection)
    }
}
```

- [ ] **Step 5: iOS 编译验证**

在 Xcode 中 Cmd+B 或命令行验证，预期无编译错误。

- [ ] **Step 6: Commit**

```bash
git add clients/ios/sdk/Sources/HttpServer/HttpServer.swift
git commit -m "feat(ios): route inspector/dom/files to PB handlers, add /webview/files"
```

---

## Task 7: MCP — 更新 dom.ts、inspector.ts、image.ts

**Files:**
- Modify: `mcp/src/tools/dom.ts`
- Modify: `mcp/src/tools/inspector.ts`
- Modify: `mcp/src/tools/image.ts`

- [ ] **Step 1: 更新 dom.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkGet } from "../sdk-client.js";
import { DomAllResponseSchema, DomNodeResponseSchema } from "../generated/inspector_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerDomTools(server: McpServer): void {
  server.tool(
    "dom_all",
    "返回 WebView 中所有 DOM 节点，坐标为屏幕绝对坐标（含 WebView 偏移换算）",
    {},
    async () => {
      try {
        const res = await sdkGet("/dom/all", DomAllResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify(res.data?.nodes) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "dom_by_id",
    "按 id 查询 WebView 中单个 DOM 节点的屏幕坐标和尺寸",
    { id: z.string().describe("DOM 元素的 id 属性值") },
    async ({ id }) => {
      try {
        const res = await sdkGet(`/dom/${encodeURIComponent(id)}`, DomNodeResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify(res.data) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

- [ ] **Step 2: 更新 inspector.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { sdkGet } from "../sdk-client.js";
import { FileListResponseSchema } from "../generated/inspector_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerInspectorTools(server: McpServer): void {
  server.tool(
    "list_files",
    "返回设备上已保存的 HTML 文件列表",
    {},
    async () => {
      try {
        const res = await sdkGet("/webview/files", FileListResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify(res.data?.files) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

- [ ] **Step 3: 更新 image.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { readFileSync } from "fs";
import { extname } from "path";
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost } from "../sdk-client.js";
import {
  PushImageRequestSchema,
  PushImageResponseSchema,
  ShowImageRequestSchema,
  ShowImageResponseSchema,
  ImageListResponseSchema,
} from "../generated/inspector_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerImageTools(server: McpServer): void {
  server.tool(
    "push_image",
    "推送图片到设备叠加层并自动显示。优先使用 file 参数（本地绝对路径），其次 image base64 字符串",
    {
      tag: z.string().describe("图片标识，如 login、home"),
      file: z.string().optional().describe("本地图片文件的绝对路径（png/jpg），优先于 image 参数"),
      image: z.string().optional().describe("base64 编码的图片内容"),
      ext: z.enum(["png", "jpg"]).optional().describe("图片格式，缺省 png；使用 file 时自动推断"),
      timestamp: z.string().optional().describe("时间戳，格式 MMdd-HHmm，缺省自动生成"),
    },
    async ({ tag, file, image, ext, timestamp }) => {
      try {
        let imageBytes: Uint8Array;
        let imageExt = ext ?? "png";
        if (file) {
          imageBytes = new Uint8Array(readFileSync(file));
          const e = extname(file).slice(1).toLowerCase();
          imageExt = (e === "jpg" || e === "jpeg") ? "jpg" : "png";
        } else if (image) {
          imageBytes = Uint8Array.from(Buffer.from(image, "base64"));
        } else {
          throw new Error("需要提供 file 或 image 参数");
        }
        const ts = timestamp ?? new Date().toISOString().slice(0, 16).replace(/[-:T]/g, "").slice(2, 12);
        const req = create(PushImageRequestSchema, { tag, timestamp: ts, image: imageBytes, ext: imageExt });
        const res = await sdkPost("/inspector/push-image", PushImageRequestSchema, req, PushImageResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ tag: res.data?.tag, filePath: res.data?.filePath }) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "show_image",
    "切换显示设备上已保存的图片",
    {
      tag: z.string().describe("图片标识"),
      timestamp: z.string().describe("时间戳，格式 MMdd-HHmm"),
    },
    async ({ tag, timestamp }) => {
      try {
        const req = create(ShowImageRequestSchema, { tag, timestamp });
        const res = await sdkPost("/inspector/show-image", ShowImageRequestSchema, req, ShowImageResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ tag: res.data?.tag, opacity: res.data?.opacity }) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "list_images",
    "返回设备上已保存的图片列表",
    {},
    async () => {
      try {
        const res = await sdkGet("/inspector/images", ImageListResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify(res.data?.images) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

- [ ] **Step 4: TypeScript 编译验证**

```bash
cd mcp && npm run build
```

预期：无编译错误。

- [ ] **Step 5: Commit**

```bash
git add mcp/src/tools/dom.ts mcp/src/tools/inspector.ts mcp/src/tools/image.ts
git commit -m "feat(mcp): migrate dom/inspector/image tools to Protobuf"
```

---

## Task 8: MCP — 清理 sdk-client.ts 中的 Raw 方法

**Files:**
- Modify: `mcp/src/sdk-client.ts`

- [ ] **Step 1: 确认 sdkGetRaw / sdkPostRaw 无残留调用**

```bash
grep -r "sdkGetRaw\|sdkPostRaw" mcp/src/tools/
```

预期：无输出。

- [ ] **Step 2: 删除 sdk-client.ts 中的 sdkGetRaw 和 sdkPostRaw 函数**

移除以下两个函数（约 20 行）：

```typescript
// 删除这两个函数:
export async function sdkGetRaw(path: string): Promise<unknown> { ... }
export async function sdkPostRaw(path: string, body: unknown): Promise<unknown> { ... }
```

- [ ] **Step 3: TypeScript 编译验证**

```bash
cd mcp && npm run build
```

预期：无编译错误，无对 `sdkGetRaw`/`sdkPostRaw` 的未解析引用。

- [ ] **Step 4: Commit**

```bash
git add mcp/src/sdk-client.ts
git commit -m "refactor(mcp): remove sdkGetRaw/sdkPostRaw, all endpoints now use Protobuf"
```

---

## Task 9: 协作测试 — Android 端功能验证（需用户配合）

> **此任务需要用户在 Android 设备/模拟器上运行 demo App，与 AI 协作验证。**

**Files:** 无代码改动，仅测试验证。

- [ ] **Step 1: 构建并安装 Android demo**

```bash
cd clients/android && ./gradlew :demo:assembleDebug
# 用户将 APK 安装到设备或模拟器，启动 App 到登录页
```

- [ ] **Step 2: 验证 GET /webview/files（PB 响应）**

在 MCP 中调用 `list_files` 工具，或用 curl 验证：
```bash
adb forward tcp:8080 tcp:8080
curl -s http://127.0.0.1:8080/webview/files | xxd | head -3
# 预期：Content-Type: application/x-protobuf，返回二进制数据（非 { 开头的 JSON）
```
MCP 工具调用：`list_files` → 预期返回文件列表数组（或空数组）。

- [ ] **Step 3: 验证 POST /inspector/push-image（PB 请求+响应）**

MCP 调用 `push_image`（提供一张测试图片路径），预期：
- 返回 `{ tag, filePath }` 不报错
- App 上叠加层显示图片

- [ ] **Step 4: 验证 GET /inspector/images**

MCP 调用 `list_images`，预期返回包含刚才 push 的图片的列表。

- [ ] **Step 5: 验证 POST /inspector/show-image**

MCP 调用 `show_image`（使用上一步的 tag/timestamp），预期 App 叠加层切换显示该图片。

- [ ] **Step 6: 验证 POST /inspector/hide**

通过 MCP（如有 hide_overlay 工具）或 curl 发送 PB 请求：
```bash
# 用 Python 快速生成 PB 请求（HideRequest type="image"）
python3 -c "
import struct
# HideRequest: field 1 (string type='image') → tag=1, wire=2, value='image'
data = b'\x0a\x05image'
import urllib.request, urllib.error
req = urllib.request.Request('http://127.0.0.1:8080/inspector/hide', data=data, method='POST')
req.add_header('Content-Type', 'application/x-protobuf')
urllib.request.urlopen(req)
print('ok')
"
```
预期：App 图片叠加层隐藏，响应为 PB（非 JSON）。

- [ ] **Step 7: 验证 GET /dom/all（需先 push-html）**

先通过 `push_html` 推送一个测试 HTML，再调用 `dom_all`，预期返回 DOM 节点数组（PB 格式，MCP 工具自动解码输出 JSON）。

- [ ] **Step 8: 验证 GET /dom/:id**

调用 `dom_by_id`（提供 HTML 中某个元素的 id），预期返回该节点的坐标和尺寸，无报错。

- [ ] **Step 9: 记录结果**

与用户逐项确认每个接口返回数据正确，有任何异常记录到对话中并修复后重测。

---

## Task 10: 协作测试 — iOS 端功能验证（需用户配合）

> **此任务需要用户在 iOS 设备/模拟器上运行 demo App，与 AI 协作验证。**

**Files:** 无代码改动，仅测试验证。

- [ ] **Step 1: 编译并运行 iOS demo**

在 Xcode 中 Cmd+R 运行 `clients/ios/demo`，App 启动到登录页。
确认 SDK HTTP Server 在 8080 端口已启动（控制台输出 `[HttpServer] listening on port 8080`）。

- [ ] **Step 2: 验证 GET /webview/files（iOS 新增接口）**

MCP 调用 `list_files`，预期返回文件列表（或空数组），不报错。
（此接口 iOS 之前没有，是本次新增，重点验证。）

- [ ] **Step 3: 验证 POST /inspector/push-image**

MCP 调用 `push_image`，预期 App 叠加层显示图片，返回 `{ tag, filePath }` 不报错。

- [ ] **Step 4: 验证 GET /inspector/images**

MCP 调用 `list_images`，预期返回包含刚才图片的列表。

- [ ] **Step 5: 验证 POST /inspector/show-image**

MCP 调用 `show_image`（使用上一步的 tag/timestamp），预期 App 切换显示该图片。

- [ ] **Step 6: 验证 POST /inspector/hide 和 /inspector/adjust**

通过调用对应 MCP 工具验证 hide 隐藏叠加层、adjust 调整偏移功能正常。

- [ ] **Step 7: 验证 GET /dom/all 和 GET /dom/:id**

先 `push_html` 推送测试 HTML，再分别调用 `dom_all` 和 `dom_by_id`，验证 PB 响应能正确解码为节点列表和单节点数据。

- [ ] **Step 8: 记录结果**

与用户逐项确认，有异常修复后重测。

---

## 自检结果

**Spec 覆盖检查：**
- ✅ 8 条路由全部迁移（Task 1-8）
- ✅ Android InspectorApiHandler 合并删除（Task 2-3）
- ✅ iOS 补齐 /webview/files（Task 6）
- ✅ sdkGetRaw/sdkPostRaw 清理（Task 8）
- ✅ 协作测试覆盖 Android + iOS 所有接口（Task 9-10）

**类型一致性检查：**
- `Clienttools_DomNode` 在 Task 4（OverlayManager）和 Task 6（HttpServer）中类型名一致
- `InspectorAdjustRequest/Response` 在 Task 5（iOS handler）和 Task 2（Android）名称一致
- MCP 中 `DomAllResponseSchema`、`FileListResponseSchema` 等均来自 Task 1 生成的 `inspector_pb.ts`

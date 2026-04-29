# Spec: JSON → Protobuf 全量迁移

## 背景

当前 `/inspector/*`、`/webview/files`、`/dom/*` 共 8 条路由仍使用 JSON 通信，与其余接口的 Protobuf 风格不一致。本次一次性完成全量迁移，消除混用状态。

## 目标

- 所有 HTTP 接口统一使用 Protobuf（请求体 + 响应体）
- Android `InspectorApiHandler` 合并进 `ApiHandler`，消除双 handler 并存
- iOS 补齐缺失的 `GET /webview/files` 接口
- MCP Server 全部改用 `sdkGet`/`sdkPost` + proto schema，不保留任何 `sdkGetRaw`/`sdkPostRaw`

## 需迁移的接口

| 路由 | 方法 | 平台 | 当前格式 |
|------|------|------|----------|
| `GET /webview/files` | GET | Android（iOS 补新增） | 响应 JSON |
| `POST /inspector/push-image` | POST | Android + iOS | 请求+响应 JSON |
| `POST /inspector/show-image` | POST | Android + iOS | 请求+响应 JSON |
| `GET /inspector/images` | GET | Android + iOS | 响应 JSON |
| `POST /inspector/hide` | POST | Android + iOS | 请求+响应 JSON |
| `POST /inspector/adjust` | POST | Android + iOS | 请求+响应 JSON |
| `GET /dom/all` | GET | Android + iOS | 响应 JSON |
| `GET /dom/:id` | GET | Android + iOS | 响应 JSON |

路由路径不变，只替换数据格式。

## Proto 设计

### 新建 `proto/inspector.proto`

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

// /inspector/hide  （type: "image" | "webview" | "" 空=自动判断）
message HideRequest       { string type = 1; }

// /inspector/adjust
message InspectorAdjustRequest  { string type = 1; float offset_x = 2; float offset_y = 3; float opacity = 4; }
message InspectorAdjustResult   { float offset_x = 1; float offset_y = 2; float opacity = 3; }
message InspectorAdjustResponse { ResponseMeta meta = 1; InspectorAdjustResult data = 2; }

// /dom/all 和 /dom/:id
message DomNode           { string id = 1; string tag = 2; string text = 3; float x = 4; float y = 5; float width = 6; float height = 7; }
message DomNodeList       { repeated DomNode nodes = 1; }
message DomAllResponse    { ResponseMeta meta = 1; DomNodeList data = 2; }
message DomNodeResponse   { ResponseMeta meta = 1; DomNode data = 2; }
```

`api.proto` 不需要改动。`inspector.proto` 单向 import `api.proto` 获取 `ResponseMeta`，不存在循环依赖。

`buf generate` 后生成：
- iOS Swift → `clients/ios/sdk/Sources/Generated/`
- MCP TypeScript → `mcp/src/generated/`
- Android 由 Gradle 自动生成（同步更新 `clients/android/sdk/src/main/proto/inspector.proto`）

## Android 改动

### `ApiHandler.kt`

新增以下方法，替换 `InspectorApiHandler` 中对应的 JSON 实现：

- `handleWebviewFiles()` — 响应 `FileListResponse` PB
- `handlePushImage(bodyBytes)` — 解析 `PushImageRequest`，响应 `PushImageResponse`
- `handleShowImage(bodyBytes)` — 解析 `ShowImageRequest`，响应 `ShowImageResponse`
- `handleGetImages()` — 响应 `ImageListResponse`
- `handleInspectorHide(bodyBytes)` — 解析 `HideRequest`，响应 `SimpleResponse`
- `handleInspectorAdjust(bodyBytes)` — 解析 `InspectorAdjustRequest`，响应 `InspectorAdjustResponse`
- `handleDomAll(webView)` — 查询 DOM，组装 `DomAllResponse`
- `handleDomById(webView, id)` — 查询单节点，组装 `DomNodeResponse`

### `InspectorApiHandler.kt`

删除整个文件，全部逻辑迁移进 `ApiHandler.kt`。

### `HttpServer.kt`

- `/webview/files`：改调 `ApiHandler.handleWebviewFiles()`（原来调旧 handler）
- `/inspector/push-image`、`/inspector/show-image`：`readBody` → `readBodyBytes`，改调新方法
- `/inspector/images`、`/inspector/hide`、`/inspector/adjust`：同上
- `/dom/all`、`/dom/:id`：改调 `ApiHandler` 中新的 DOM 方法

## iOS 改动

### `InspectorApiHandler.swift`

所有方法签名从 `-> (Int, String)` 改为接收 `NWConnection`，内部调用 `sendProto`。具体：

- `handlePushImage(_ body: Data, connection:)` — 解析 `PushImageRequest`，响应 PB
- `handleShowImage(_ body: Data, connection:)` — 解析 `ShowImageRequest`，响应 PB
- `handleGetImages(connection:)` — 响应 `ImageListResponse` PB
- `handleHide(_ body: Data, connection:)` — 解析 `HideRequest`，响应 `SimpleResponse` PB
- `handleAdjust(_ body: Data, connection:)` — 解析 `InspectorAdjustRequest`，响应 `InspectorAdjustResponse` PB

`sendProto` 复用 `HttpServer` 已有的私有方法，通过参数传入 connection。

### `HttpServer.swift`

- `/inspector/*` 路由：移除 `let (code, json) = …; sendJson(json, …)` 模式，改为直接调用 `inspectorHandler.handleXxx(body, connection: connection)`
- 新增 `case ("GET", "/webview/files"):` 路由，调用 `handleWebviewFiles(connection:)`
- `/dom/all`、`/dom/:id`：`handleDomAll`/`handleDomById` 改为响应 `DomAllResponse`/`DomNodeResponse` PB（`OverlayManager.queryDomAll` 回调结果从 JSON 字符串改为 `[DomNode]`，由 handler 组装 proto）

### `OverlayManager.swift`

- `queryDomAll(completion: ([Clienttools_DomNode]) -> Void)` — JS 执行后将 JSON 解析为 proto message 列表再回调
- `queryDomById(_ id:, completion: (Clienttools_DomNode?) -> Void)` — 同上，单节点

## MCP Server 改动

### `mcp/src/generated/`

`buf generate` 后新增 `inspector_pb.ts`，包含所有新 message 的 schema。

### `sdk-client.ts`

移除 `sdkGetRaw` 和 `sdkPostRaw`（或保留但标记废弃），确认无 inspector/dom 调用后删除。

### `mcp/src/tools/inspector.ts`

- `list_files`：改用 `sdkGet("/webview/files", FileListResponseSchema)`
- 移除 `list_images`（移入 `image.ts`，或在此保留并改用 PB）

### `mcp/src/tools/image.ts`

- `push_image`：`sdkPostRaw` → `sdkPost`，请求用 `PushImageRequest`（image 字段为 `Uint8Array`，base64 decode 后传入）
- `show_image`：`sdkPostRaw` → `sdkPost`，请求用 `ShowImageRequest`
- 新增 `list_images`（从 inspector.ts 移入）：`sdkGet("/inspector/images", ImageListResponseSchema)`

### `mcp/src/tools/dom.ts`

- `dom_all`：`sdkGetRaw` → `sdkGet("/dom/all", DomAllResponseSchema)`，返回 `res.data?.nodes`
- `dom_by_id`：`sdkGetRaw` → `sdkGet`，返回 `res.data`

## 实现顺序

1. 编写 `proto/inspector.proto`，同步到 Android proto 目录，运行 `buf generate`
2. Android：更新 `ApiHandler.kt`，删除 `InspectorApiHandler.kt`，更新 `HttpServer.kt`
3. iOS：更新 `OverlayManager.swift`，重写 `InspectorApiHandler.swift`，更新 `HttpServer.swift`
4. MCP：更新 `image.ts`、`dom.ts`、`inspector.ts`，清理 `sdk-client.ts`

## 验收标准

- `buf generate` 无报错
- Android `./gradlew :sdk:assembleDebug` 无报错
- iOS 编译无报错
- MCP 所有工具（`push_image`、`show_image`、`list_images`、`list_files`、`dom_all`、`dom_by_id`、`hide_overlay`（inspector 版）、`adjust_overlay`（inspector 版））调用返回正常数据，无 JSON parse 路径
- `sdkGetRaw`/`sdkPostRaw` 在 inspector/dom 路径上无调用残留

# Plan B：SDK ↔ MCP 通信迁移至 Protocol Buffers — 设计规格

**目标：** 将 Android SDK、iOS SDK、MCP 之间的 HTTP 通信 Body 从 JSON 改为 protobuf binary，以同一份 `.proto` schema 作为各端数据契约，彻底消除字段漂移。

**工具链：** buf + protobuf-kotlin（Android）+ SwiftProtobuf（iOS）+ @bufbuild/protobuf（MCP/TypeScript）

---

## 1. Proto Schema 结构

Proto 文件统一放项目根 `proto/` 目录，与任何端解耦：

```
proto/
├── buf.yaml           # buf 工具链配置
├── buf.gen.yaml       # 代码生成配置（三端 plugin）
├── node.proto         # Node, NodeType, TextAttrs, ImageAttrs, ListAttrs, ContainerAttrs
├── modify.proto       # ModifyViewRequest, ViewProps
├── page.proto         # PageInfo, PageChangedEvent, DeviceInfo
└── api.proto          # ApiResponse（通用响应包装）
```

### buf.yaml

```yaml
version: v2
modules:
  - path: proto
lint:
  use: [DEFAULT]
breaking:
  use: [FILE]
```

### buf.gen.yaml

```yaml
version: v2
plugins:
  - plugin: buf.build/protocolbuffers/kotlin
    out: clients/android/sdk/src/main/kotlin
  - plugin: buf.build/apple/swift
    out: clients/ios/sdk/Sources/Generated
  - plugin: buf.build/bufbuild/es
    out: mcp/src/generated
    opt: target=ts
```

### 各端生成产物路径

| 端 | 生成路径 |
|----|---------|
| Android | `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/generated/` |
| iOS | `clients/ios/sdk/Sources/Generated/` |
| MCP | `mcp/src/generated/` |

生成代码**不提交到 git**，纳入 `.gitignore`，构建时自动生成。

### Proto 定义（关键字段）

**node.proto**
```protobuf
syntax = "proto3";
package clienttools;

enum NodeType { CONTAINER = 0; TEXT = 1; IMAGE = 2; LIST = 3; }

message TextAttrs { float font_size = 1; string color = 2; string font_weight = 3; }
message ImageAttrs { string scale_type = 1; }
message ListAttrs { float item_spacing = 1; string orientation = 2; }
message ContainerAttrs { float padding_top = 1; float padding_bottom = 2; float padding_left = 3; float padding_right = 4; }

message NodeAttrs {
  oneof attrs { TextAttrs text = 1; ImageAttrs image = 2; ListAttrs list = 3; ContainerAttrs container = 4; }
}

message Node {
  string id = 1;
  NodeType type = 2;
  float screen_x = 3;
  float screen_y = 4;
  float width_dp = 5;
  float height_dp = 6;
  NodeAttrs attrs = 7;
  map<string, string> custom_attrs = 8;
  int32 visibility = 9;
  bool is_enabled = 10;
}

message NodeList { repeated Node nodes = 1; }
```

**modify.proto**
```protobuf
syntax = "proto3";
package clienttools;
import "google/protobuf/wrappers.proto";

message ViewProps {
  google.protobuf.FloatValue margin_top_diff_dp = 1;
  google.protobuf.FloatValue margin_bottom_diff_dp = 2;
  google.protobuf.FloatValue margin_left_diff_dp = 3;
  google.protobuf.FloatValue margin_right_diff_dp = 4;
  google.protobuf.FloatValue padding_top_diff_dp = 5;
  google.protobuf.FloatValue padding_bottom_diff_dp = 6;
  google.protobuf.FloatValue padding_left_diff_dp = 7;
  google.protobuf.FloatValue padding_right_diff_dp = 8;
  google.protobuf.StringValue width_dp = 9;
  google.protobuf.StringValue height_dp = 10;
  google.protobuf.FloatValue letter_spacing_em = 11;
  google.protobuf.FloatValue line_spacing_extra_dp = 12;
  google.protobuf.BoolValue include_font_padding = 13;
}

message ModifyViewRequest { string id = 1; ViewProps props = 2; }
message ClickRequest { string id = 1; }
message ClickResult { string id = 1; }
message ScrollRequest { string id = 1; float dx = 2; float dy = 3; }
message ScrollResult { string id = 1; float dx = 2; float dy = 3; }
```

**page.proto**
```protobuf
syntax = "proto3";
package clienttools;

message DeviceInfo { float screen_width_dp = 1; float screen_height_dp = 2; float density = 3; }
message PageInfo { string page_name = 1; string timestamp = 2; }
message PageChangedEvent { string page_name = 1; int64 timestamp = 2; }
```

**api.proto**
```protobuf
syntax = "proto3";
package clienttools;
import "node.proto";
import "page.proto";

message DeviceInfo { float screen_width_dp = 1; float screen_height_dp = 2; float density = 3; }
message Empty {}

// 每个接口有独立的 Response message，避免使用 google.protobuf.Any
message PageResponse   { int32 code = 1; string message = 2; int32 sdk_version = 3; DeviceInfo device = 4; PageInfo data = 5; }
message NodeResponse   { int32 code = 1; string message = 2; int32 sdk_version = 3; DeviceInfo device = 4; Node data = 5; }
message NodeListResponse { int32 code = 1; string message = 2; int32 sdk_version = 3; DeviceInfo device = 4; NodeList data = 5; }
message ModifyResponse { int32 code = 1; string message = 2; int32 sdk_version = 3; DeviceInfo device = 4; }
message ClickResponse  { int32 code = 1; string message = 2; int32 sdk_version = 3; DeviceInfo device = 4; ClickResult data = 5; }
message ScrollResponse { int32 code = 1; string message = 2; int32 sdk_version = 3; DeviceInfo device = 4; ScrollResult data = 5; }
message SimpleResponse { int32 code = 1; string message = 2; int32 sdk_version = 3; DeviceInfo device = 4; }

message PushHtmlRequest { string tag = 1; string timestamp = 2; bytes html = 3; }
message PushHtmlResult  { string tag = 1; string timestamp = 2; string file_path = 3; }
message PushHtmlResponse { int32 code = 1; string message = 2; int32 sdk_version = 3; DeviceInfo device = 4; PushHtmlResult data = 5; }
message WebviewShowRequest   { string tag = 1; string timestamp = 2; }
message WebviewAdjustRequest { float offset_x = 1; float offset_y = 2; float opacity = 3; }
```

---

## 2. HTTP 协议变更

### Content-Type

- 所有请求和响应 Body：`Content-Type: application/x-protobuf`
- 请求 Header 附加：`X-CT-Proto-Version: 1`
- SDK 收到未知版本号返回 HTTP 400

### 接口覆盖（全量）

| 接口 | 请求 proto | 响应 message |
|------|-----------|-------------|
| GET /api/page/current | 无 Body | `PageResponse` |
| GET /api/nodes/all | 无 Body | `NodeListResponse` |
| GET /api/nodes/:id | 无 Body | `NodeResponse` |
| POST /api/modify | `ModifyViewRequest` | `ModifyResponse` |
| POST /api/click | `ClickRequest` | `ClickResponse` |
| POST /api/scroll | `ScrollRequest` | `ScrollResponse` |
| POST /webview/push-html | `PushHtmlRequest` | `PushHtmlResponse` |
| POST /webview/show | `WebviewShowRequest` | `SimpleResponse` |
| POST /webview/hide | 无 Body | `SimpleResponse` |
| POST /webview/adjust | `WebviewAdjustRequest` | `SimpleResponse` |

GET 接口无请求 Body，响应改为 protobuf binary。`push-html` 的 html 内容在 proto 里定义为 `bytes`，传 UTF-8 编码。

---

## 3. 各端实现

### Android（protobuf-kotlin）

**依赖：**
```kotlin
// clients/android/sdk/build.gradle.kts
plugins {
    id("com.google.protobuf") version "0.9.4"
}
dependencies {
    implementation("com.google.protobuf:protobuf-kotlin:4.26.1")
    implementation("com.google.protobuf:protobuf-java-util:4.26.1")
}
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.26.1" }
    generateProtoTasks { all().forEach { it.builtins { remove("java") }; it.plugins { id("kotlin") } } }
}
```

**代码变更：**
- `ApiHandler.kt`：请求 Body 用 `XxxRequest.parseFrom(inputStream)` 解析，响应用 `apiResponse.toByteArray()` 序列化，`mimeType` 改为 `"application/x-protobuf"`
- 删除 `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/models/` 目录（Node、ViewProps 等手写类）
- `ViewQueryService`、`ViewModifier` 等改为引用 proto 生成类

### MCP（@bufbuild/protobuf）

**依赖：**
```json
"@bufbuild/protobuf": "^2.2.0",
"@bufbuild/protobuf-es": "^2.2.0"
```

**代码变更：**
- `sdk-client.ts`：`sdkGet` 响应改为 `res.arrayBuffer()` → `fromBinary(ApiResponseSchema, buffer)`；`sdkPost` body 改为 `toBinary(schema, msg)`，header 加 `Content-Type: application/x-protobuf`
- 各 tool 文件从 `mcp/src/generated/` import proto schema 类型，删除手写 TypeScript interface

### iOS（SwiftProtobuf）

**依赖（Podfile）：**
```ruby
pod 'SwiftProtobuf', '~> 1.28'
```

**代码变更：**
- `HttpServer.swift`：请求 Body 用 `try XxxRequest(serializedBytes: bodyData)` 解析，响应用 `try response.serializedData()`，`Content-Type` 改为 `"application/x-protobuf"`
- 删除 `clients/ios/sdk/Sources/Model/Models.swift` 中手写 Swift struct，改用生成类

---

## 4. 构建与 CI

### 本地开发前置

```bash
brew install bufbuild/buf/buf
```

### 生成命令

```bash
# 项目根执行，一次生成三端
buf generate
```

### 各端构建集成

- **Android**：`build.gradle.kts` 的 protobuf plugin 在 `compileKotlin` 前自动生成
- **iOS**：`Podfile` 的 `pre_install` hook 执行 `buf generate`
- **MCP**：`package.json` 的 `"prebuild": "buf generate"` script

### .gitignore 新增

```
# Proto generated code
clients/android/sdk/src/main/kotlin/com/clienttools/sdk/generated/
clients/ios/sdk/Sources/Generated/
mcp/src/generated/
```

### CI Breaking Change 检测

```bash
buf lint
buf breaking --against '.git#branch=main'
```

字段删除或重命名时 CI 阻断合并。

---

## 5. 实施顺序

1. **proto schema**：写 `.proto` 文件，`buf generate` 验证三端生成无误
2. **Android SDK**：接入 protobuf-kotlin，迁移 ApiHandler，删除手写模型，本地编译+功能验证
3. **MCP**：切换 sdk-client.ts，Android + MCP 联调验证所有接口
4. **iOS SDK**：接入 SwiftProtobuf，迁移 HttpServer，iOS + MCP 联调验证
5. **端到端验证**：三端联通，运行完整 inspect 流程

---

## 6. 不在范围内

- Python preprocess 脚本（后续整体删除，不迁移）
- SSE 事件流（`/api/events`，保持现有实现）
- `/api/capture/:id` 图片接口（返回 binary image，不适合 ApiResponse 包装，保持现有实现）

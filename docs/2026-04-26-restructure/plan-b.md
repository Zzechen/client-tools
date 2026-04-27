# Plan B：SDK ↔ MCP 通信迁移至 Protocol Buffers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android SDK、iOS SDK、MCP 三端的 HTTP 通信 Body 从 JSON 改为 protobuf binary，以统一 `.proto` schema 作为跨端数据契约。

**Architecture:** 在项目根 `proto/` 目录维护 `.proto` 文件，用 `buf generate` 一次生成三端代码；Android 用 protobuf-kotlin，iOS 用 SwiftProtobuf，MCP 用 @bufbuild/protobuf-es；所有接口 Content-Type 改为 `application/x-protobuf`，Response 通过嵌套 `ResponseMeta` 复用公共字段。

**Tech Stack:** buf CLI, protobuf-kotlin 4.x, SwiftProtobuf 1.28, @bufbuild/protobuf-es 2.x, NanoHTTPD (Android), NWListener (iOS), TypeScript (MCP)

---

## 文件变更地图

**新建：**
- `proto/buf.yaml`
- `proto/buf.gen.yaml`
- `proto/node.proto`
- `proto/modify.proto`
- `proto/page.proto`
- `proto/api.proto`

**Android 修改：**
- `clients/android/sdk/build.gradle.kts` — 添加 protobuf gradle plugin
- `clients/android/build.gradle.kts` — 添加 protobuf plugin classpath
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt` — 切换序列化
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt` — Content-Type + 删除 SSE
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/EventManager.kt` — 删除
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewQueryService.kt` — 引用生成类
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt` — 引用生成类

**Android 删除：**
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/model/` — 全目录（ClickRequest 等手写类）
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/models/` — 全目录（Node 等手写类）

**iOS 修改：**
- `clients/ios/sdk/ClientToolsSDK.podspec` — 添加 SwiftProtobuf 依赖
- `clients/ios/demo/Podfile` — 添加 SwiftProtobuf
- `clients/ios/sdk/Sources/HttpServer/HttpServer.swift` — 切换为 pb binary 解析/序列化
- `clients/ios/sdk/Sources/HttpServer/Pages/*.swift` — 各 handler 引用生成类
- `clients/ios/sdk/Sources/ViewQuery/ViewNode.swift` — 引用生成 Node 类型
- `clients/ios/sdk/Sources/ViewModify/ViewModifyService.swift` — 引用生成 ViewProps 类型

**iOS 删除：**
- `clients/ios/sdk/Sources/Model/Models.swift` — 手写 struct 全部删除
- `clients/ios/sdk/Sources/HttpServer/ApiResponse.swift` — 手写 ApiResponse 删除

**MCP 修改：**
- `mcp/package.json` — 添加 @bufbuild/protobuf-es，prebuild 脚本
- `mcp/src/sdk-client.ts` — 切换为 binary 收发
- `mcp/src/tools/view.ts` — 引用生成类型
- `mcp/src/tools/page.ts` — 引用生成类型
- `mcp/src/tools/webview.ts` — 引用生成类型
- `mcp/src/tools/inspector.ts` — 引用生成类型
- `mcp/src/tools/image.ts` — 引用生成类型，capture 接口切换

**MCP 删除：**
- `mcp/src/tools/dom.ts` — SSE 相关（无 DOM 工具对应 SSE，保留；仅删 EventManager 对应部分）

**.gitignore 修改：**
- 新增三端生成代码路径

---

### Task 1: 创建 proto schema 并验证 buf generate

**Files:**
- Create: `proto/buf.yaml`
- Create: `proto/buf.gen.yaml`
- Create: `proto/node.proto`
- Create: `proto/modify.proto`
- Create: `proto/page.proto`
- Create: `proto/api.proto`

- [ ] **Step 1: 安装 buf CLI（若未安装）**

```bash
brew install bufbuild/buf/buf
buf --version
```

预期输出：`1.x.x`

- [ ] **Step 2: 创建 proto/buf.yaml**

```yaml
version: v2
modules:
  - path: .
lint:
  use: [DEFAULT]
breaking:
  use: [FILE]
```

- [ ] **Step 3: 创建 proto/buf.gen.yaml**

```yaml
version: v2
plugins:
  - plugin: buf.build/protocolbuffers/kotlin:v4.26.1
    out: ../clients/android/sdk/src/main/kotlin
  - plugin: buf.build/apple/swift:v1.28.0
    out: ../clients/ios/sdk/Sources/Generated
  - plugin: buf.build/bufbuild/es:v2.2.3
    out: ../mcp/src/generated
    opt: target=ts
```

- [ ] **Step 4: 创建 proto/page.proto**

```protobuf
syntax = "proto3";
package clienttools;
option java_package = "com.clienttools.sdk.proto";
option java_multiple_files = true;

message DeviceInfo {
  float screen_width_dp = 1;
  float screen_height_dp = 2;
  float density = 3;
}

message PageInfo {
  string page_name = 1;
  string timestamp = 2;
}

message PageChangedEvent {
  string page_name = 1;
  int64 timestamp = 2;
}
```

- [ ] **Step 5: 创建 proto/node.proto**

```protobuf
syntax = "proto3";
package clienttools;
option java_package = "com.clienttools.sdk.proto";
option java_multiple_files = true;

enum NodeType {
  CONTAINER = 0;
  TEXT = 1;
  IMAGE = 2;
  LIST = 3;
}

message TextAttrs {
  float font_size = 1;
  string color = 2;
  string font_weight = 3;
}

message ImageAttrs {
  string scale_type = 1;
}

message ListAttrs {
  float item_spacing = 1;
  string orientation = 2;
}

message ContainerAttrs {
  float padding_top = 1;
  float padding_bottom = 2;
  float padding_left = 3;
  float padding_right = 4;
}

message NodeAttrs {
  oneof attrs {
    TextAttrs text = 1;
    ImageAttrs image = 2;
    ListAttrs list = 3;
    ContainerAttrs container = 4;
  }
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

message NodeList {
  repeated Node nodes = 1;
}
```

- [ ] **Step 6: 创建 proto/modify.proto**

```protobuf
syntax = "proto3";
package clienttools;
option java_package = "com.clienttools.sdk.proto";
option java_multiple_files = true;
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

message ModifyViewRequest {
  string id = 1;
  ViewProps props = 2;
}

message ClickRequest { string id = 1; }
message ClickResult  { string id = 1; }

message ScrollRequest {
  string id = 1;
  float dx = 2;
  float dy = 3;
}

message ScrollResult {
  string id = 1;
  float dx = 2;
  float dy = 3;
}
```

- [ ] **Step 7: 创建 proto/api.proto**

```protobuf
syntax = "proto3";
package clienttools;
option java_package = "com.clienttools.sdk.proto";
option java_multiple_files = true;
import "page.proto";
import "node.proto";
import "modify.proto";

message ResponseMeta {
  int32 code = 1;
  string message = 2;
  int32 sdk_version = 3;
  DeviceInfo device = 4;
}

message Empty {}

message PageResponse     { ResponseMeta meta = 1; PageInfo data = 2; }
message NodeResponse     { ResponseMeta meta = 1; Node data = 2; }
message NodeListResponse { ResponseMeta meta = 1; NodeList data = 2; }
message ModifyResponse   { ResponseMeta meta = 1; }
message ClickResponse    { ResponseMeta meta = 1; ClickResult data = 2; }
message ScrollResponse   { ResponseMeta meta = 1; ScrollResult data = 2; }
message SimpleResponse   { ResponseMeta meta = 1; }

message PushHtmlRequest  { string tag = 1; string timestamp = 2; bytes html = 3; }
message PushHtmlResult   { string tag = 1; string timestamp = 2; string file_path = 3; }
message PushHtmlResponse { ResponseMeta meta = 1; PushHtmlResult data = 2; }

message WebviewShowRequest   { string tag = 1; string timestamp = 2; }
message WebviewAdjustRequest { float offset_x = 1; float offset_y = 2; float opacity = 3; }

message CaptureResponse { ResponseMeta meta = 1; bytes image_png = 2; }
```

- [ ] **Step 8: 验证 buf lint**

```bash
cd proto && buf lint
```

预期：无输出（无 lint 错误）

- [ ] **Step 9: 执行 buf generate 验证三端生成**

```bash
# 先创建目录
mkdir -p ../clients/android/sdk/src/main/kotlin
mkdir -p ../clients/ios/sdk/Sources/Generated
mkdir -p ../mcp/src/generated

cd proto && buf generate
```

预期：无错误，三端目录下出现生成文件

- [ ] **Step 10: 将生成目录加入 .gitignore**

在项目根 `.gitignore` 添加：

```
# Proto generated code
clients/android/sdk/src/main/kotlin/com/clienttools/sdk/proto/
clients/ios/sdk/Sources/Generated/
mcp/src/generated/
```

- [ ] **Step 11: 提交**

```bash
git add proto/ .gitignore
git commit -m "feat(proto): add proto schema and buf config for all three platforms"
```

---

### Task 2: Android SDK 接入 protobuf-kotlin，迁移 ApiHandler

**Files:**
- Modify: `clients/android/build.gradle.kts`
- Modify: `clients/android/sdk/build.gradle.kts`
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt`
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`
- Delete: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/EventManager.kt`
- Delete: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/model/` (全目录)
- Delete: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/models/` (全目录)

- [ ] **Step 1: 更新 clients/android/build.gradle.kts 添加 protobuf plugin**

```kotlin
plugins {
    id("com.android.library") version "8.7.3" apply false
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}
```

- [ ] **Step 2: 更新 clients/android/sdk/build.gradle.kts**

完整替换为：

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

android {
    namespace = "com.clienttools.sdk"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.26.1"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                remove("java")
            }
            task.plugins {
                create("kotlin")
            }
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.protobuf:protobuf-kotlin:4.26.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    testImplementation(kotlin("test"))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
```

注意：移除 `kotlinx.serialization` 依赖，新增 `protobuf-kotlin`。

- [ ] **Step 3: 将 proto 文件链接到 Android 模块**

在 `clients/android/sdk/` 目录下创建 `src/main/proto/` 软链接或直接复制：

```bash
mkdir -p clients/android/sdk/src/main/proto
cp proto/*.proto clients/android/sdk/src/main/proto/
```

> 注意：proto 文件需要放在 `src/main/proto/` 下，Gradle protobuf plugin 才能自动检测到。每次修改 proto 需同步此目录（后续可用脚本自动化）。

- [ ] **Step 4: 验证 Android 编译生成 proto 类**

```bash
cd clients/android && ./gradlew :sdk:generateDebugProtos
```

预期：在 `clients/android/sdk/build/generated/source/proto/debug/kotlin/com/clienttools/sdk/proto/` 下生成 Kotlin 类

- [ ] **Step 5: 新建 ProtoHelper.kt 封装设备信息获取**

创建 `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ProtoHelper.kt`：

```kotlin
package com.clienttools.sdk.http

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.clienttools.sdk.proto.DeviceInfo
import com.clienttools.sdk.proto.ResponseMeta

object ProtoHelper {
    private const val SDK_VERSION = 1

    fun getDeviceInfo(context: Context): DeviceInfo {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        return DeviceInfo.newBuilder()
            .setScreenWidthDp(metrics.widthPixels / metrics.density)
            .setScreenHeightDp(metrics.heightPixels / metrics.density)
            .setDensity(metrics.density)
            .build()
    }

    fun okMeta(context: Context): ResponseMeta = ResponseMeta.newBuilder()
        .setCode(0)
        .setMessage("success")
        .setSdkVersion(SDK_VERSION)
        .setDevice(getDeviceInfo(context))
        .build()

    fun errMeta(code: Int, message: String, context: Context): ResponseMeta = ResponseMeta.newBuilder()
        .setCode(code)
        .setMessage(message)
        .setSdkVersion(SDK_VERSION)
        .setDevice(getDeviceInfo(context))
        .build()
}
```

- [ ] **Step 6: 重写 ApiHandler.kt 使用 protobuf**

完整替换 `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt`：

```kotlin
package com.clienttools.sdk.http

import android.content.Context
import android.util.Log
import com.clienttools.sdk.proto.*
import com.clienttools.sdk.runtime.ViewQueryService
import com.clienttools.sdk.runtime.ViewModifier
import com.clienttools.sdk.runtime.OverlayManager
import com.clienttools.sdk.listener.PageChangeListener
import com.google.protobuf.ByteString
import fi.iki.elonen.NanoHTTPD

object ApiHandler {
    private var pageChangeListener: PageChangeListener? = null
    private var appContext: Context? = null

    fun init(context: Context, listener: PageChangeListener) {
        appContext = context.applicationContext
        pageChangeListener = listener
    }

    private fun ctx() = appContext!!

    private fun okResponse(bytes: ByteArray): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/x-protobuf",
            bytes.inputStream(),
            bytes.size.toLong()
        )

    private fun errResponse(code: NanoHTTPD.Response.Status, message: String): NanoHTTPD.Response {
        val meta = ProtoHelper.errMeta(code.requestStatus, message, ctx())
        val resp = SimpleResponse.newBuilder().setMeta(meta).build()
        val bytes = resp.toByteArray()
        return NanoHTTPD.newFixedLengthResponse(code, "application/x-protobuf", bytes.inputStream(), bytes.size.toLong())
    }

    fun handleGetCurrentPage(): NanoHTTPD.Response {
        return try {
            val (pageName, timestamp) = pageChangeListener?.getCurrentPage() ?: Pair("", "")
            val data = PageInfo.newBuilder().setPageName(pageName).setTimestamp(timestamp).build()
            val resp = PageResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(data).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleGetCurrentPage", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleGetAllNodes(): NanoHTTPD.Response {
        return try {
            val nodes = ViewQueryService.getAllNodes()
            val nodeList = NodeList.newBuilder().addAllNodes(nodes).build()
            val resp = NodeListResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(nodeList).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleGetAllNodes", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleGetNode(id: String): NanoHTTPD.Response {
        return try {
            val node = ViewQueryService.getNode(id)
                ?: return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "View not found")
            val resp = NodeResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(node).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleGetNode $id", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleModify(bodyBytes: ByteArray): NanoHTTPD.Response {
        return try {
            val req = ModifyViewRequest.parseFrom(bodyBytes)
            ViewModifier.apply(req.id, req.props)
            val resp = ModifyResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleModify", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleClick(bodyBytes: ByteArray): NanoHTTPD.Response {
        return try {
            val req = ClickRequest.parseFrom(bodyBytes)
            val success = ViewModifier.click(req.id)
            if (!success) return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "View not found")
            val result = ClickResult.newBuilder().setId(req.id).build()
            val resp = ClickResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleClick", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleScroll(bodyBytes: ByteArray): NanoHTTPD.Response {
        return try {
            val req = ScrollRequest.parseFrom(bodyBytes)
            val success = ViewModifier.scroll(req.id, req.dx, req.dy)
            if (!success) return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "View not found")
            val result = ScrollResult.newBuilder().setId(req.id).setDx(req.dx).setDy(req.dy).build()
            val resp = ScrollResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleScroll", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleCaptureView(id: String): NanoHTTPD.Response {
        return try {
            val bytes = ViewQueryService.captureView(id)
                ?: return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "View not found or has no size")
            val resp = CaptureResponse.newBuilder()
                .setMeta(ProtoHelper.okMeta(ctx()))
                .setImagePng(ByteString.copyFrom(bytes))
                .build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleCaptureView $id", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handlePushHtml(bodyBytes: ByteArray, overlayManager: OverlayManager): NanoHTTPD.Response {
        return try {
            val req = PushHtmlRequest.parseFrom(bodyBytes)
            val html = req.html.toStringUtf8()
            val fileURL = overlayManager.fileStore.save(req.tag, req.timestamp, html)
                ?: return errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "Failed to save HTML")
            overlayManager.showFile(fileURL, 0.5f)
            val result = PushHtmlResult.newBuilder()
                .setTag(req.tag).setTimestamp(req.timestamp).setFilePath(fileURL.path).build()
            val resp = PushHtmlResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handlePushHtml", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleWebviewShow(bodyBytes: ByteArray, overlayManager: OverlayManager): NanoHTTPD.Response {
        return try {
            val req = WebviewShowRequest.parseFrom(bodyBytes)
            val file = overlayManager.fileStore.findFile(req.tag, req.timestamp)
                ?: return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "File not found")
            overlayManager.showFile(file, 0.5f)
            val resp = SimpleResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleWebviewShow", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleWebviewHide(overlayManager: OverlayManager): NanoHTTPD.Response {
        overlayManager.hide()
        val resp = SimpleResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
        return okResponse(resp.toByteArray())
    }

    fun handleWebviewAdjust(bodyBytes: ByteArray, overlayManager: OverlayManager): NanoHTTPD.Response {
        return try {
            val req = WebviewAdjustRequest.parseFrom(bodyBytes)
            overlayManager.adjust(req.offsetX, req.offsetY, req.opacity)
            val resp = SimpleResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleWebviewAdjust", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }
}
```

- [ ] **Step 7: 更新 ViewQueryService 返回 proto Node**

`ViewQueryService` 中的 `getViewInfo`/`getAllViewInfos` 改为返回 proto `Node`。修改 `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewQueryService.kt`：

将方法签名和实现中的 `ViewInfo` 改为 `com.clienttools.sdk.proto.Node`，字段映射：

```kotlin
import com.clienttools.sdk.proto.Node
import com.clienttools.sdk.proto.NodeType as ProtoNodeType

private fun buildNode(view: View, id: String): Node {
    val density = view.resources.displayMetrics.density
    val loc = IntArray(2)
    view.getLocationOnScreen(loc)
    val typeStr = when (view) {
        is TextView -> ProtoNodeType.TEXT
        is ImageView -> ProtoNodeType.IMAGE
        is RecyclerView -> ProtoNodeType.LIST
        else -> ProtoNodeType.CONTAINER
    }
    return Node.newBuilder()
        .setId(id)
        .setType(typeStr)
        .setScreenX(loc[0] / density)
        .setScreenY(loc[1] / density)
        .setWidthDp(view.width / density)
        .setHeightDp(view.height / density)
        .setVisibility(view.visibility)
        .setIsEnabled(view.isEnabled)
        .build()
}

fun getNode(viewId: String): Node? { ... }
fun getAllNodes(): List<Node> { ... }
```

- [ ] **Step 8: 更新 ViewModifier 接受 proto ViewProps**

修改 `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt`，将 `apply(id: String, props: ViewProps)` 的参数类型改为 `com.clienttools.sdk.proto.ViewProps`，字段访问从 `props.marginTopDiffDp` 改为 `if (props.hasMarginTopDiffDp()) props.marginTopDiffDp.value else null`（Wrapper 类型判空方式）。

- [ ] **Step 9: 删除手写模型目录**

```bash
rm -rf clients/android/sdk/src/main/kotlin/com/clienttools/sdk/model/
rm -rf clients/android/sdk/src/main/kotlin/com/clienttools/sdk/models/
rm clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/EventManager.kt
```

- [ ] **Step 10: 验证 Android SDK 编译**

```bash
cd clients/android && ./gradlew :sdk:assembleDebug
```

预期：`BUILD SUCCESSFUL`

- [ ] **Step 11: 验证 Android Demo 编译**

```bash
./gradlew :demo:assembleDebug
```

预期：`BUILD SUCCESSFUL`

- [ ] **Step 12: 提交**

```bash
git add clients/android/
git commit -m "feat(android): migrate ApiHandler to protobuf-kotlin, remove hand-written models"
```

---

### Task 3: MCP 切换为 protobuf binary

**Files:**
- Modify: `mcp/package.json`
- Modify: `mcp/tsconfig.json`
- Modify: `mcp/src/sdk-client.ts`
- Modify: `mcp/src/tools/view.ts`
- Modify: `mcp/src/tools/page.ts`
- Modify: `mcp/src/tools/webview.ts`
- Modify: `mcp/src/tools/inspector.ts`
- Modify: `mcp/src/tools/image.ts`

- [ ] **Step 1: 更新 mcp/package.json**

```json
{
  "name": "@client-tools/mcp",
  "version": "0.1.0",
  "description": "MCP Server for Client Tools SDK",
  "type": "module",
  "main": "./dist/index.js",
  "bin": {
    "client-tools-mcp": "./dist/index.js"
  },
  "scripts": {
    "prebuild": "buf generate",
    "build": "tsc",
    "start": "node dist/index.js"
  },
  "dependencies": {
    "@modelcontextprotocol/sdk": "^1.10.2",
    "@bufbuild/protobuf": "^2.2.3",
    "zod": "^3.23.8"
  },
  "devDependencies": {
    "@types/node": "^22.0.0",
    "typescript": "^5.5.0"
  }
}
```

- [ ] **Step 2: 安装新依赖并执行 buf generate**

```bash
cd mcp && npm install
cd ../proto && buf generate
```

预期：`mcp/src/generated/` 下出现 `*.ts` 文件

- [ ] **Step 3: 重写 mcp/src/sdk-client.ts**

```typescript
import { execSync } from "child_process";
import { fromBinary, toBinary, MessageShape, DescMessage } from "@bufbuild/protobuf";

const PORT = process.env.CLIENT_TOOLS_PORT ?? "8080";
const BASE_URL = `http://127.0.0.1:${PORT}`;
const DEFAULT_TIMEOUT_MS = 5000;
const DOM_TIMEOUT_MS = 8000;

function ensureAdbForward(): void {
  try {
    execSync(`adb forward tcp:${PORT} tcp:${PORT}`, { stdio: "ignore" });
  } catch {
    // adb not available or no device, ignore
  }
}

export class SdkUnreachableError extends Error {
  constructor(cause: unknown) {
    super(`SDK unreachable: ${cause instanceof Error ? cause.message : String(cause)}`);
  }
}

async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } catch (e) {
    if ((e as Error).name === "AbortError") throw new SdkUnreachableError("request timed out");
    throw new SdkUnreachableError(e);
  } finally {
    clearTimeout(timer);
  }
}

export async function sdkGet<T extends DescMessage>(
  path: string,
  schema: T
): Promise<MessageShape<T>> {
  ensureAdbForward();
  const timeoutMs = DEFAULT_TIMEOUT_MS;
  const res = await fetchWithTimeout(`${BASE_URL}${path}`, { method: "GET" }, timeoutMs);
  const buf = new Uint8Array(await res.arrayBuffer());
  return fromBinary(schema, buf);
}

export async function sdkPost<Req extends DescMessage, Res extends DescMessage>(
  path: string,
  reqSchema: Req,
  reqMsg: MessageShape<Req>,
  resSchema: Res
): Promise<MessageShape<Res>> {
  ensureAdbForward();
  const body = toBinary(reqSchema, reqMsg);
  const res = await fetchWithTimeout(
    `${BASE_URL}${path}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/x-protobuf",
        "X-CT-Proto-Version": "1",
      },
      body,
    },
    DEFAULT_TIMEOUT_MS
  );
  const buf = new Uint8Array(await res.arrayBuffer());
  return fromBinary(resSchema, buf);
}

export async function sdkGetRaw(path: string): Promise<Uint8Array> {
  ensureAdbForward();
  const res = await fetchWithTimeout(`${BASE_URL}${path}`, { method: "GET" }, DOM_TIMEOUT_MS);
  return new Uint8Array(await res.arrayBuffer());
}
```

- [ ] **Step 4: 更新 mcp/src/tools/page.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost, SdkUnreachableError } from "../sdk-client.js";
import {
  PageResponseSchema,
  ClickResponseSchema,
  ScrollResponseSchema,
  ClickRequestSchema,
  ScrollRequestSchema,
} from "../generated/api_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerPageTools(server: McpServer): void {
  server.tool("get_current_page", "查询当前 Android 页面名称", {}, async () => {
    try {
      const res = await sdkGet("/api/page/current", PageResponseSchema);
      return { content: [{ type: "text" as const, text: JSON.stringify({ pageName: res.data?.pageName, timestamp: res.data?.timestamp }) }] };
    } catch (e) { return errResult(e); }
  });

  server.tool("click_view", "点击指定 id 的 Android View", { id: z.string() }, async ({ id }) => {
    try {
      const req = create(ClickRequestSchema, { id });
      const res = await sdkPost("/api/click", ClickRequestSchema, req, ClickResponseSchema);
      return { content: [{ type: "text" as const, text: JSON.stringify({ id: res.data?.id }) }] };
    } catch (e) { return errResult(e); }
  });

  server.tool(
    "scroll_view",
    "滚动指定 id 的 Android View，单位 dp",
    {
      id: z.string(),
      dx: z.number().describe("横向滚动量 dp，正值向左"),
      dy: z.number().describe("竖向滚动量 dp，正值向上"),
    },
    async ({ id, dx, dy }) => {
      try {
        const req = create(ScrollRequestSchema, { id, dx, dy });
        const res = await sdkPost("/api/scroll", ScrollRequestSchema, req, ScrollResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ id: res.data?.id, dx: res.data?.dx, dy: res.data?.dy }) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

- [ ] **Step 5: 更新 mcp/src/tools/view.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost, sdkGetRaw, SdkUnreachableError } from "../sdk-client.js";
import {
  NodeListResponseSchema,
  NodeResponseSchema,
  ModifyResponseSchema,
  ModifyViewRequestSchema,
  CaptureResponseSchema,
} from "../generated/api_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

const DpValue = z.union([z.number(), z.literal("wrap_content")]);

const ViewPropsSchema = z.object({
  marginTopDiffDp: z.number().optional(),
  marginBottomDiffDp: z.number().optional(),
  marginLeftDiffDp: z.number().optional(),
  marginRightDiffDp: z.number().optional(),
  paddingTopDiffDp: z.number().optional(),
  paddingBottomDiffDp: z.number().optional(),
  paddingLeftDiffDp: z.number().optional(),
  paddingRightDiffDp: z.number().optional(),
  widthDp: DpValue.optional(),
  heightDp: DpValue.optional(),
  letterSpacingEm: z.number().optional(),
  lineSpacingExtraDp: z.number().optional(),
  includeFontPadding: z.boolean().optional(),
});

export function registerViewTools(server: McpServer): void {
  server.tool("capture_view", "截取指定 Android View 的截图，返回 PNG 图片", { id: z.string() }, async ({ id }) => {
    try {
      const res = await sdkGet(`/api/capture/${encodeURIComponent(id)}`, CaptureResponseSchema);
      const base64 = Buffer.from(res.imagePng).toString("base64");
      return { content: [{ type: "image" as const, data: base64, mimeType: "image/png" }] };
    } catch (e) { return errResult(e); }
  });

  server.tool("get_node", "查询 Android View 节点的屏幕位置和尺寸", { id: z.string() }, async ({ id }) => {
    try {
      const res = await sdkGet(`/api/nodes/${encodeURIComponent(id)}`, NodeResponseSchema);
      return { content: [{ type: "text" as const, text: JSON.stringify(res.data) }] };
    } catch (e) { return errResult(e); }
  });

  server.tool("get_all_nodes", "获取当前页面所有 Android View 节点的屏幕坐标和尺寸快照", {}, async () => {
    try {
      const res = await sdkGet("/api/nodes/all", NodeListResponseSchema);
      return { content: [{ type: "text" as const, text: JSON.stringify(res.data?.nodes) }] };
    } catch (e) { return errResult(e); }
  });

  server.tool(
    "modify_view",
    "修改 Android View 的布局属性（margin/padding/size），单位 dp",
    { id: z.string(), props: ViewPropsSchema },
    async ({ id, props }) => {
      try {
        const viewProps = {
          ...(props.marginTopDiffDp !== undefined && { marginTopDiffDp: { value: props.marginTopDiffDp } }),
          ...(props.marginBottomDiffDp !== undefined && { marginBottomDiffDp: { value: props.marginBottomDiffDp } }),
          ...(props.marginLeftDiffDp !== undefined && { marginLeftDiffDp: { value: props.marginLeftDiffDp } }),
          ...(props.marginRightDiffDp !== undefined && { marginRightDiffDp: { value: props.marginRightDiffDp } }),
          ...(props.paddingTopDiffDp !== undefined && { paddingTopDiffDp: { value: props.paddingTopDiffDp } }),
          ...(props.paddingBottomDiffDp !== undefined && { paddingBottomDiffDp: { value: props.paddingBottomDiffDp } }),
          ...(props.paddingLeftDiffDp !== undefined && { paddingLeftDiffDp: { value: props.paddingLeftDiffDp } }),
          ...(props.paddingRightDiffDp !== undefined && { paddingRightDiffDp: { value: props.paddingRightDiffDp } }),
          ...(props.widthDp !== undefined && { widthDp: { value: String(props.widthDp) } }),
          ...(props.heightDp !== undefined && { heightDp: { value: String(props.heightDp) } }),
          ...(props.letterSpacingEm !== undefined && { letterSpacingEm: { value: props.letterSpacingEm } }),
          ...(props.lineSpacingExtraDp !== undefined && { lineSpacingExtraDp: { value: props.lineSpacingExtraDp } }),
          ...(props.includeFontPadding !== undefined && { includeFontPadding: { value: props.includeFontPadding } }),
        };
        const req = create(ModifyViewRequestSchema, { id, props: viewProps });
        await sdkPost("/api/modify", ModifyViewRequestSchema, req, ModifyResponseSchema);
        return { content: [{ type: "text" as const, text: "ok" }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

- [ ] **Step 6: 更新 mcp/src/tools/webview.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost } from "../sdk-client.js";
import {
  PushHtmlRequestSchema,
  PushHtmlResponseSchema,
  WebviewShowRequestSchema,
  WebviewAdjustRequestSchema,
  SimpleResponseSchema,
} from "../generated/api_pb.js";
import { readFileSync } from "fs";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerWebviewTools(server: McpServer): void {
  server.tool(
    "push_html",
    "推送 HTML 设计稿叠加层到 Android 设备",
    {
      tag: z.string().describe("标识符"),
      file: z.string().describe("本地 HTML 文件绝对路径"),
      timestamp: z.string().optional().describe("时间戳，默认当前时间"),
    },
    async ({ tag, file, timestamp }) => {
      try {
        const ts = timestamp ?? new Date().toISOString().slice(0, 16).replace(/[-:T]/g, "").slice(0, 12);
        const html = readFileSync(file, "utf-8");
        const req = create(PushHtmlRequestSchema, {
          tag,
          timestamp: ts,
          html: new TextEncoder().encode(html),
        });
        const res = await sdkPost("/webview/push-html", PushHtmlRequestSchema, req, PushHtmlResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ tag: res.data?.tag, filePath: res.data?.filePath }) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "show_webview",
    "显示已推送的 HTML 叠加层",
    { tag: z.string(), timestamp: z.string() },
    async ({ tag, timestamp }) => {
      try {
        const req = create(WebviewShowRequestSchema, { tag, timestamp });
        await sdkPost("/webview/show", WebviewShowRequestSchema, req, SimpleResponseSchema);
        return { content: [{ type: "text" as const, text: "ok" }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool("hide_overlay", "隐藏叠加层", {}, async () => {
    try {
      await sdkGet("/webview/hide", SimpleResponseSchema);
      return { content: [{ type: "text" as const, text: "ok" }] };
    } catch (e) { return errResult(e); }
  });

  server.tool(
    "adjust_overlay",
    "调整叠加层偏移和透明度",
    {
      offsetX: z.number().optional(),
      offsetY: z.number().optional(),
      opacity: z.number().optional(),
    },
    async ({ offsetX, offsetY, opacity }) => {
      try {
        const req = create(WebviewAdjustRequestSchema, {
          offsetX: offsetX ?? 0,
          offsetY: offsetY ?? 0,
          opacity: opacity ?? 0.5,
        });
        await sdkPost("/webview/adjust", WebviewAdjustRequestSchema, req, SimpleResponseSchema);
        return { content: [{ type: "text" as const, text: "ok" }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

- [ ] **Step 7: 编译 MCP**

```bash
cd mcp && npm run build
```

预期：`tsc` 无错误，`dist/` 下生成文件

- [ ] **Step 8: 启动 Android App，联调验证**

```bash
# 确认 App 已运行，执行 MCP 工具联调
curl -s http://localhost:8080/api/page/current | xxd | head -3
```

预期：返回 binary（非 JSON 文本），说明 Android 已切换 protobuf 响应。

- [ ] **Step 9: 提交**

```bash
git add mcp/
git commit -m "feat(mcp): migrate sdk-client and all tools to protobuf binary"
```

---

### Task 4: iOS SDK 接入 SwiftProtobuf，迁移 HttpServer

**Files:**
- Modify: `clients/ios/sdk/ClientToolsSDK.podspec`
- Modify: `clients/ios/demo/Podfile`
- Modify: `clients/ios/sdk/Sources/HttpServer/HttpServer.swift`
- Modify: `clients/ios/sdk/Sources/HttpServer/Pages/*.swift`
- Modify: `clients/ios/sdk/Sources/ViewQuery/ViewNode.swift`
- Modify: `clients/ios/sdk/Sources/ViewModify/ViewModifyService.swift`
- Delete: `clients/ios/sdk/Sources/Model/Models.swift`
- Delete: `clients/ios/sdk/Sources/HttpServer/ApiResponse.swift`

- [ ] **Step 1: 更新 podspec 添加 SwiftProtobuf 依赖**

修改 `clients/ios/sdk/ClientToolsSDK.podspec`：

```ruby
s.dependency 'SwiftProtobuf', '~> 1.28'
```

在 `s.frameworks` 那行下面添加。

- [ ] **Step 2: 更新 Podfile 并 pod install**

在 `clients/ios/demo/Podfile` 中添加：

```ruby
pod 'SwiftProtobuf', '~> 1.28'
```

然后：

```bash
cd clients/ios/demo && pod install
```

预期：`Pod installation complete!`

- [ ] **Step 3: 将生成的 Swift pb 文件加入 podspec source_files**

`buf generate` 已生成文件到 `clients/ios/sdk/Sources/Generated/`。确认 podspec 的 `source_files` 包含此目录（`'Sources/**/*.swift'` 已覆盖，无需额外修改）。

- [ ] **Step 4: 重写 HttpServer.swift 切换 pb binary**

修改 `clients/ios/sdk/Sources/HttpServer/HttpServer.swift`，将 `handleConnection` 中的响应构造改为 pb binary：

```swift
private func sendProtoResponse(_ data: Data, to connection: NWConnection) {
    let header = "HTTP/1.1 200 OK\r\nContent-Type: application/x-protobuf\r\nContent-Length: \(data.count)\r\n\r\n"
    var full = header.data(using: .utf8)!
    full.append(data)
    connection.send(content: full, completion: .contentProcessed { _ in connection.cancel() })
}

private func sendErrorProto(code: Int, message: String, to connection: NWConnection) {
    let meta = Clienttools_ResponseMeta.with {
        $0.code = Int32(code)
        $0.message = message
        $0.sdkVersion = 1
    }
    let resp = Clienttools_SimpleResponse.with { $0.meta = meta }
    if let data = try? resp.serializedData() {
        sendProtoResponse(data, to: connection)
    } else {
        connection.cancel()
    }
}
```

将 `processRequest(_ request: String) -> String` 改为 `processRequest(_ requestData: Data, to connection: NWConnection)`，直接发送 pb binary 而非返回 JSON 字符串。

- [ ] **Step 5: 迁移各 Handler 引用生成类型**

各 `Pages/` 下 handler 文件（`PageCurrentHandler.swift`、`NodesHandler.swift`、`ClickHandler.swift`、`ScrollHandler.swift`）：
- 删除 `Codable` 解析，改为 `try Clienttools_XxxRequest(serializedBytes: bodyData)`
- 响应改为 `try Clienttools_XxxResponse.with { ... }.serializedData()`

- [ ] **Step 6: 更新 ViewNode.swift 引用 proto Node**

将 `ViewNode` 结构体的使用改为直接构建 `Clienttools_Node`：

```swift
import Foundation

func buildProtoNode(from view: UIView, id: String, density: CGFloat) -> Clienttools_Node {
    var node = Clienttools_Node()
    node.id = id
    node.type = mapViewType(view)
    let frame = view.convert(view.bounds, to: nil)
    node.screenX = Float(frame.origin.x)
    node.screenY = Float(frame.origin.y)
    node.widthDp = Float(view.bounds.width)
    node.heightDp = Float(view.bounds.height)
    node.visibility = view.isHidden ? 8 : 0
    node.isEnabled = view.isUserInteractionEnabled
    return node
}
```

- [ ] **Step 7: 删除手写 Models.swift 和 ApiResponse.swift**

```bash
rm clients/ios/sdk/Sources/Model/Models.swift
rm clients/ios/sdk/Sources/HttpServer/ApiResponse.swift
```

- [ ] **Step 8: 验证 iOS 编译**

```bash
cd clients/ios/demo && pod install && xcodebuild \
  -workspace ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build 2>&1 | tail -5
```

预期：`** BUILD SUCCEEDED **`

- [ ] **Step 9: 提交**

```bash
git add clients/ios/
git commit -m "feat(ios): migrate HttpServer to SwiftProtobuf, remove hand-written models"
```

---

### Task 5: 端到端验证 + 清理

**Files:**
- Modify: `CLAUDE.md` — 更新构建命令说明

- [ ] **Step 1: 启动 Android App，运行完整 inspect 流程验证**

用 MCP 依次调用：

```
get_current_page → 返回页面名
get_all_nodes → 返回节点列表
get_node(id) → 返回单节点
modify_view(id, props) → 修改成功
push_html(tag, file) → 推送叠加层
```

每个工具确认返回正确数据（无报错）。

- [ ] **Step 2: 验证 buf breaking change 检测**

```bash
cd proto && buf breaking --against '.git#branch=main'
```

预期：无错误（schema 未发生 breaking change）。

- [ ] **Step 3: 更新 CLAUDE.md 添加 buf generate 说明**

在"运行测试"章节添加：

```markdown
# Proto 代码生成（修改 .proto 文件后执行）
cd proto && buf generate
```

- [ ] **Step 4: 最终提交**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md with buf generate instructions"
```

---

## 自检

**Spec 覆盖：**
- ✅ proto schema（Task 1）
- ✅ buf + 三端生成（Task 1）
- ✅ Android protobuf-kotlin（Task 2）
- ✅ MCP @bufbuild/protobuf-es（Task 3）
- ✅ iOS SwiftProtobuf（Task 4）
- ✅ 全量接口覆盖包含 capture（Task 2/3/4）
- ✅ SSE EventManager 删除（Task 2 Step 9）
- ✅ ResponseMeta 组合复用（proto/api.proto）
- ✅ buf breaking change 检测（Task 5）
- ✅ .gitignore 生成代码（Task 1 Step 10）

**类型一致性：**
- `ModifyViewRequest`、`ClickRequest` 等在 Task 2/3/4 中均从同一 proto 生成，类型名一致
- `ResponseMeta` 在 Android 侧为 `ProtoHelper.okMeta()`，iOS 侧为 `Clienttools_ResponseMeta.with { ... }`，MCP 侧读取 `res.meta`

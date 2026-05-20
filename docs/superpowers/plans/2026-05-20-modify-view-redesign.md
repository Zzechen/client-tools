# Modify View 统一重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 两端统一使用 translation/scale 实现视觉调整，废弃 margin/padding/size via LayoutParams，MCP 暴露语义化 `modify_view` 工具（移动/宽高/文案）。

**Architecture:** Proto 层新增统一 `ModifyViewRequest`（move/size/text），删除 Android/iOS 专属消息；SDK 内部用 translationX/Y + scaleX/Y 实现，pivot 固定左上角；Snapshot 返回 post-transform 视觉尺寸；MCP 合并为单个 `modify_view` 工具。

**Tech Stack:** Protocol Buffers (buf), Kotlin/Android, Swift/iOS, TypeScript/MCP.

---

## 文件变更清单

| 文件 | 操作 |
|------|------|
| `proto/modify.proto` | 改写：删除旧消息，新增 MoveProps/SizeProps/TextProps/ModifyViewRequest |
| `proto/node.proto` | 修改：删除 translate_x/y/scale_x/y 字段 |
| `clients/android/sdk/src/main/proto/modify.proto` | 与 proto/ 保持一致（手动同步） |
| `clients/android/sdk/src/main/proto/node.proto` | 与 proto/ 保持一致（手动同步） |
| `mcp/src/generated/` | buf generate 自动生成（TypeScript） |
| `clients/ios/sdk/Sources/Generated/` | buf generate 自动生成（Swift） |
| `mcp/src/tools/view.ts` | 删除 modify_view_android/ios，新增 modify_view |
| `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt` | 新增 modify() 函数 |
| `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/AndroidViewModifier.kt` | 删除 |
| `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt` | 替换 handleModifyAndroid → handleModify |
| `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt` | 路由 /api/modify/android → /api/modify |
| `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewQueryService.kt` | 修复 widthDp/heightDp 为视觉尺寸 |
| `clients/ios/sdk/Sources/ViewModify/ViewModifyService.swift` | 改写：对齐新 proto |
| `clients/ios/sdk/Sources/ViewQuery/ViewNode.swift` | 删除 translateX/Y/scaleX/Y 字段 |
| `clients/ios/sdk/Sources/ViewQuery/ViewTraverser.swift` | 修复 widthDp/heightDp 为视觉尺寸，删除 translate/scale 赋值 |
| `clients/ios/sdk/Sources/HttpServer/HttpServer.swift` | 路由 /api/modify/ios → /api/modify |
| `docs/mcp-tools.md` | 更新 modify_view 工具说明 |
| `docs/sdk-http-api.md` | 更新接口路径 |

---

### Task 1: 更新共享 proto 文件

**Files:**
- Modify: `proto/modify.proto`
- Modify: `proto/node.proto`

- [ ] **Step 1: 将 proto/modify.proto 改写为以下内容**

```protobuf
syntax = "proto3";
package clienttools;
option java_package = "com.clienttools.sdk.proto";
option java_multiple_files = true;
import "google/protobuf/wrappers.proto";

message MoveProps {
  google.protobuf.FloatValue dx = 1;  // 增量横向偏移（dp），正右
  google.protobuf.FloatValue dy = 2;  // 增量纵向偏移（dp），正下
}

message SizeProps {
  google.protobuf.FloatValue width  = 1;  // 目标宽度（dp），绝对值
  google.protobuf.FloatValue height = 2;  // 目标高度（dp），绝对值
}

message TextProps {
  string content = 1;  // 替换文案内容
}

message ModifyViewRequest {
  string    id   = 1;
  MoveProps move = 2;
  SizeProps size = 3;
  TextProps text = 4;
}

message ClickRequest {
  string                     id              = 1;
  google.protobuf.FloatValue center_offset_x = 2;
  google.protobuf.FloatValue center_offset_y = 3;
}
message ClickResult { string id = 1; }

message ScrollRequest {
  string id = 1;
  float  dx = 2;
  float  dy = 3;
}

message ScrollResult {
  string id = 1;
  float  dx = 2;
  float  dy = 3;
}
```

- [ ] **Step 2: 将 proto/node.proto 改写为以下内容（删除 translate_x/y/scale_x/y 字段 11-14）**

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

- [ ] **Step 3: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add proto/modify.proto proto/node.proto
git commit -m "proto: replace Android/iOS specific modify messages with unified ModifyViewRequest, remove translate/scale from Node"
```

---

### Task 2: 同步 Android proto 文件

**Files:**
- Modify: `clients/android/sdk/src/main/proto/modify.proto`
- Modify: `clients/android/sdk/src/main/proto/node.proto`

- [ ] **Step 1: 将 clients/android/sdk/src/main/proto/modify.proto 改为与 proto/modify.proto 完全一致的内容（同 Task 1 Step 1 的内容）**

- [ ] **Step 2: 将 clients/android/sdk/src/main/proto/node.proto 改为与 proto/node.proto 完全一致的内容（同 Task 1 Step 2 的内容）**

- [ ] **Step 3: 提交**

```bash
git add clients/android/sdk/src/main/proto/
git commit -m "android: sync proto files with proto/"
```

---

### Task 3: 运行 buf generate

**Files:**
- Modify: `mcp/src/generated/` (自动)
- Modify: `clients/ios/sdk/Sources/Generated/` (自动)

- [ ] **Step 1: 运行 buf generate**

```bash
cd /Users/zzc/Desktop/works/client-tools/proto && buf generate
```

Expected: 无报错，生成成功。若报错先检查 buf 是否安装：`buf --version`

- [ ] **Step 2: 确认生成的 TypeScript 中包含新类型**

```bash
grep -l "ModifyViewRequest\|MoveProps\|SizeProps" /Users/zzc/Desktop/works/client-tools/mcp/src/generated/
```

Expected: 至少列出 `modify_pb.ts`

- [ ] **Step 3: 确认生成的 Swift 中包含新类型**

```bash
grep -l "ModifyViewRequest\|MoveProps\|SizeProps" /Users/zzc/Desktop/works/client-tools/clients/ios/sdk/Sources/Generated/
```

Expected: 至少列出 `modify.pb.swift`

- [ ] **Step 4: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add mcp/src/generated/ clients/ios/sdk/Sources/Generated/
git commit -m "generated: regenerate proto bindings for unified ModifyViewRequest"
```

---

### Task 4: 更新 MCP view.ts

**Files:**
- Modify: `mcp/src/tools/view.ts`

- [ ] **Step 1: 将 mcp/src/tools/view.ts 中的 import 替换**

找到：
```typescript
import { ModifyViewAndroidRequestSchema, ModifyViewIosRequestSchema } from "../generated/modify_pb.js";
```

替换为：
```typescript
import { ModifyViewRequestSchema } from "../generated/modify_pb.js";
```

- [ ] **Step 2: 删除 modify_view_android 工具注册（含 AndroidMarginPropsZod、AndroidPaddingPropsZod、AndroidSizePropsZod、AndroidTextPropsZod 及 server.tool("modify_view_android", ...) 整段）**

删除从 `const AndroidMarginPropsZod` 到 `modify_view_android` 的 `server.tool(...)` 结尾 `);` 的所有代码（view.ts 第 69-136 行）。

- [ ] **Step 3: 删除 modify_view_ios 工具注册（含 IosTextPropsZod、IosViewPropsZod 及 server.tool("modify_view_ios", ...) 整段）**

删除从 `const IosTextPropsZod` 到 `modify_view_ios` 的 `server.tool(...)` 结尾 `);` 的所有代码（view.ts 第 139-179 行）。

- [ ] **Step 4: 在 registerViewTools 函数末尾追加 modify_view 工具**

在 `}` 的最后一个 `server.tool(...)` 后，`}` 闭合 `registerViewTools` 之前，追加：

```typescript
  server.tool(
    "modify_view",
    "修改 View 的位置、尺寸或文案（Android/iOS 通用）。move_dx/move_dy 为增量偏移（dp），width/height 为目标尺寸绝对值（dp），text 替换文案",
    {
      id:      z.string().describe("View 的 id（Android resource id 不含包名前缀，iOS 为 accessibilityIdentifier）"),
      move_dx: z.number().optional().describe("横向偏移增量（dp），正右"),
      move_dy: z.number().optional().describe("纵向偏移增量（dp），正下"),
      width:   z.number().optional().describe("目标宽度（dp），绝对值"),
      height:  z.number().optional().describe("目标高度（dp），绝对值"),
      text:    z.string().optional().describe("替换文案内容（要求 view 为 TextView/UILabel/UITextField）"),
    },
    async ({ id, move_dx, move_dy, width, height, text }) => {
      try {
        const req = create(ModifyViewRequestSchema, {
          id,
          ...(move_dx !== undefined || move_dy !== undefined) && { move: {
            ...(move_dx !== undefined && { dx: { value: move_dx } }),
            ...(move_dy !== undefined && { dy: { value: move_dy } }),
          }},
          ...(width !== undefined || height !== undefined) && { size: {
            ...(width  !== undefined && { width:  { value: width  } }),
            ...(height !== undefined && { height: { value: height } }),
          }},
          ...(text !== undefined && { text: { content: text } }),
        });
        const res = await sdkPost("/api/modify", ModifyViewRequestSchema, req, ModifyResponseSchema);
        const msg = res.message ? res.message : "ok";
        return { content: [{ type: "text" as const, text: msg }] };
      } catch (e) { return errResult(e); }
    }
  );
```

- [ ] **Step 5: 验证 TypeScript 编译**

```bash
cd /Users/zzc/Desktop/works/client-tools/mcp && npx tsc --noEmit
```

Expected: 无报错

- [ ] **Step 6: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add mcp/src/tools/view.ts
git commit -m "mcp: replace modify_view_android/ios with unified modify_view tool"
```

---

### Task 5: Android SDK — 新增 ViewModifier.modify()

**Files:**
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt`

- [ ] **Step 1: 在 ViewModifier.kt 中追加 modify() 函数**

在文件顶部的 import 区域追加：
```kotlin
import android.widget.TextView
import com.clienttools.sdk.proto.ModifyViewRequest
import java.util.concurrent.CountDownLatch
```

在 `scroll()` 函数之后、文件末尾的 `}` 之前，追加：

```kotlin
    fun modify(viewId: String, req: ModifyViewRequest): Pair<Boolean, String> {
        val activity = ClientToolsSDK.getCurrentActivity()
            ?: return Pair(false, "No activity available")

        var result: Pair<Boolean, String> = Pair(false, "unknown error")
        val latch = CountDownLatch(1)
        activity.runOnUiThread {
            result = applyModify(viewId, req)
            latch.countDown()
        }
        latch.await()
        return result
    }

    private fun applyModify(viewId: String, req: ModifyViewRequest): Pair<Boolean, String> {
        val views = ViewTreeTraversal.findViewById(viewId)
        if (views.isEmpty()) return Pair(false, "View not found: $viewId")
        val view = views[0]

        if (req.hasText() && view !is TextView) {
            return Pair(false, "text requires TextView, but '$viewId' is ${view.javaClass.simpleName}")
        }

        val density = view.resources.displayMetrics.density
        return try {
            if (req.hasMove() || req.hasSize()) {
                view.pivotX = 0f
                view.pivotY = 0f
            }
            if (req.hasMove()) {
                val m = req.move
                if (m.hasDx()) view.translationX += m.dx.value * density
                if (m.hasDy()) view.translationY += m.dy.value * density
            }
            if (req.hasSize()) {
                val s = req.size
                val originalW = view.width.toFloat()
                val originalH = view.height.toFloat()
                if (s.hasWidth() && originalW > 0f) view.scaleX = s.width.value * density / originalW
                if (s.hasHeight() && originalH > 0f) view.scaleY = s.height.value * density / originalH
            }
            if (req.hasText() && view is TextView) {
                view.text = req.text.content
            }
            Pair(true, "")
        } catch (e: Exception) {
            Pair(false, e.message ?: "error")
        }
    }
```

- [ ] **Step 2: 提交**

```bash
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt
git commit -m "android: add ViewModifier.modify() using translation/scale with top-left pivot"
```

---

### Task 6: Android SDK — 更新 ApiHandler 和 HttpServer，删除 AndroidViewModifier

**Files:**
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt`
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`
- Delete: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/AndroidViewModifier.kt`

- [ ] **Step 1: 在 ApiHandler.kt 中删除 AndroidViewModifier 的 import**

找到并删除：
```kotlin
import com.clienttools.sdk.runtime.AndroidViewModifier
```

- [ ] **Step 2: 在 ApiHandler.kt 中替换 handleModifyAndroid 为 handleModify**

找到：
```kotlin
    fun handleModifyAndroid(bodyBytes: ByteArray): NanoHTTPD.Response {
        return try {
            val req = ModifyViewAndroidRequest.parseFrom(bodyBytes)
            val (ok, msg) = AndroidViewModifier.apply(req.id, req.props)
            if (ok) {
                val resp = ModifyResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
                okResponse(resp.toByteArray())
            } else {
                errResponse(NanoHTTPD.Response.Status.NOT_FOUND, msg)
            }
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleModifyAndroid", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }
```

替换为：
```kotlin
    fun handleModify(bodyBytes: ByteArray): NanoHTTPD.Response {
        return try {
            val req = ModifyViewRequest.parseFrom(bodyBytes)
            val (ok, msg) = ViewModifier.modify(req.id, req)
            if (ok) {
                val resp = ModifyResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
                okResponse(resp.toByteArray())
            } else {
                errResponse(NanoHTTPD.Response.Status.NOT_FOUND, msg)
            }
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleModify", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }
```

- [ ] **Step 3: 在 HttpServer.kt 中替换路由**

找到：
```kotlin
                method == Method.POST && uri == "/api/modify/android" ->
                    ApiHandler.handleModifyAndroid(readBodyBytes(session))
```

替换为：
```kotlin
                method == Method.POST && uri == "/api/modify" ->
                    ApiHandler.handleModify(readBodyBytes(session))
```

- [ ] **Step 4: 删除 AndroidViewModifier.kt**

```bash
rm clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/AndroidViewModifier.kt
```

- [ ] **Step 5: 构建验证**

```bash
cd /Users/zzc/Desktop/works/client-tools/clients/android && ./gradlew :sdk:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add clients/android/sdk/src/main/kotlin/
git commit -m "android: replace handleModifyAndroid with handleModify, delete AndroidViewModifier"
```

---

### Task 7: Android SDK — 修复 Snapshot 视觉尺寸

**Files:**
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewQueryService.kt`

- [ ] **Step 1: 在 ViewQueryService.kt 的 buildNode 中修复 widthDp/heightDp**

找到：
```kotlin
        return Node.newBuilder()
            .setId(id)
            .setType(type)
            .setScreenX(loc[0] / density)
            .setScreenY(loc[1] / density)
            .setWidthDp(view.width / density)
            .setHeightDp(view.height / density)
            .setVisibility(view.visibility)
            .setIsEnabled(view.isEnabled)
            .build()
```

替换为：
```kotlin
        return Node.newBuilder()
            .setId(id)
            .setType(type)
            .setScreenX(loc[0] / density)
            .setScreenY(loc[1] / density)
            .setWidthDp(view.width * view.scaleX / density)
            .setHeightDp(view.height * view.scaleY / density)
            .setVisibility(view.visibility)
            .setIsEnabled(view.isEnabled)
            .build()
```

> 说明：`view.width` 是 layout 原始宽（px），`view.scaleX` 是视觉缩放因子，乘积为视觉宽度（px），再除以 density 转为 dp。`getLocationOnScreen` 已包含 translationX/Y，screenX/Y 无需修改。

- [ ] **Step 2: 构建验证**

```bash
cd /Users/zzc/Desktop/works/client-tools/clients/android && ./gradlew :sdk:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewQueryService.kt
git commit -m "android: snapshot widthDp/heightDp now reflects post-transform visual size"
```

---

### Task 8: iOS SDK — 改写 ViewModifyService.swift

**Files:**
- Modify: `clients/ios/sdk/Sources/ViewModify/ViewModifyService.swift`

- [ ] **Step 1: 将 ViewModifyService.swift 改写为以下完整内容**

```swift
import UIKit

class ViewModifyService {

    private let viewQueryService = ViewQueryService()

    func modify(id: String, req: Clienttools_ModifyViewRequest) -> (Bool, String) {
        guard let view = viewQueryService.findView(byId: id) else {
            return (false, "View not found: \(id)")
        }

        if req.hasText {
            guard view is UILabel || view is UITextField else {
                return (false, "text requires UILabel or UITextField, but '\(id)' is \(type(of: view))")
            }
        }

        let sema = DispatchSemaphore(value: 0)
        DispatchQueue.main.async {
            if req.hasMove || req.hasSize {
                Self.ensureTopLeftAnchor(view)
            }
            if req.hasMove {
                Self.applyMove(to: view, move: req.move)
            }
            if req.hasSize {
                Self.applySize(to: view, size: req.size)
            }
            if req.hasText {
                if let label = view as? UILabel {
                    label.text = req.text.content
                } else if let tf = view as? UITextField {
                    tf.text = req.text.content
                }
            }
            view.setNeedsLayout()
            view.layoutIfNeeded()
            sema.signal()
        }
        sema.wait()
        return (true, "")
    }

    // pivot 设到左上角（幂等），补偿 position 保持视觉位置不变
    private static func ensureTopLeftAnchor(_ view: UIView) {
        guard view.layer.anchorPoint != CGPoint(x: 0, y: 0) else { return }
        let old = view.layer.anchorPoint
        let size = view.bounds.size
        view.layer.position = CGPoint(
            x: view.layer.position.x - old.x * size.width,
            y: view.layer.position.y - old.y * size.height
        )
        view.layer.anchorPoint = CGPoint(x: 0, y: 0)
    }

    // 增量平移：在当前 translation 基础上叠加 dx/dy，保持 scale 不变
    private static func applyMove(to view: UIView, move: Clienttools_MoveProps) {
        let t = view.transform
        let currentTx = t.tx
        let currentTy = t.ty
        let sx = sqrt(t.a * t.a + t.c * t.c)
        let sy = sqrt(t.b * t.b + t.d * t.d)
        let newTx = currentTx + (move.hasDx ? CGFloat(move.dx.value) : 0)
        let newTy = currentTy + (move.hasDy ? CGFloat(move.dy.value) : 0)
        // scale first, then translate（屏幕空间：translate 不受 scale 影响）
        view.transform = CGAffineTransform(scaleX: sx == 0 ? 1 : sx, y: sy == 0 ? 1 : sy)
            .concatenating(CGAffineTransform(translationX: newTx, y: newTy))
    }

    // 绝对尺寸：根据目标 dp 算出 scaleX/Y，保持 translation 不变
    private static func applySize(to view: UIView, size: Clienttools_SizeProps) {
        let t = view.transform
        let currentTx = t.tx
        let currentTy = t.ty
        let currentSx = sqrt(t.a * t.a + t.c * t.c)
        let currentSy = sqrt(t.b * t.b + t.d * t.d)
        let originalW = view.bounds.width   // layout 原始宽，不受 transform 影响
        let originalH = view.bounds.height
        let newSx = (size.hasWidth && originalW > 0)
            ? CGFloat(size.width.value) / originalW
            : (currentSx == 0 ? 1 : currentSx)
        let newSy = (size.hasHeight && originalH > 0)
            ? CGFloat(size.height.value) / originalH
            : (currentSy == 0 ? 1 : currentSy)
        view.transform = CGAffineTransform(scaleX: newSx, y: newSy)
            .concatenating(CGAffineTransform(translationX: currentTx, y: currentTy))
    }
}
```

- [ ] **Step 2: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add clients/ios/sdk/Sources/ViewModify/ViewModifyService.swift
git commit -m "ios: rewrite ViewModifyService for unified ModifyViewRequest (move/size/text)"
```

---

### Task 9: iOS SDK — 更新 ViewNode.swift 和 ViewTraverser.swift

**Files:**
- Modify: `clients/ios/sdk/Sources/ViewQuery/ViewNode.swift`
- Modify: `clients/ios/sdk/Sources/ViewQuery/ViewTraverser.swift`

- [ ] **Step 1: 将 ViewNode.swift 的 struct 定义改写（删除 translateX/Y/scaleX/Y 字段和 init 参数）**

找到：
```swift
public struct ViewNode: Codable {
    public let id: String
    public let type: String
    public let screenX: Float
    public let screenY: Float
    public let widthDp: Float
    public let heightDp: Float
    public let visibility: Int
    public let isEnabled: Bool
    public let attrs: NodeAttrs?
    public let translateX: Float
    public let translateY: Float
    public let scaleX: Float
    public let scaleY: Float

    public init(id: String, type: String, screenX: Float, screenY: Float,
                widthDp: Float, heightDp: Float, visibility: Int, isEnabled: Bool,
                attrs: NodeAttrs?,
                translateX: Float = 0, translateY: Float = 0,
                scaleX: Float = 1, scaleY: Float = 1) {
        self.id = id
        self.type = type
        self.screenX = screenX
        self.screenY = screenY
        self.widthDp = widthDp
        self.heightDp = heightDp
        self.visibility = visibility
        self.isEnabled = isEnabled
        self.attrs = attrs
        self.translateX = translateX
        self.translateY = translateY
        self.scaleX = scaleX
        self.scaleY = scaleY
    }
}
```

替换为：
```swift
public struct ViewNode: Codable {
    public let id: String
    public let type: String
    public let screenX: Float
    public let screenY: Float
    public let widthDp: Float
    public let heightDp: Float
    public let visibility: Int
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

- [ ] **Step 2: 将 ViewTraverser.swift 改写为以下完整内容（移除 translate/scale 字段，widthDp/heightDp 改为视觉尺寸）**

```swift
import UIKit

class ViewTraverser {

    static func traverse(_ view: UIView, path: String = "") -> [ViewNode] {
        var nodes: [ViewNode] = []

        for (index, subview) in view.subviews.enumerated() {
            if subview.tag == OverlayManager.overlayTag { continue }

            let childPath = path.isEmpty ? "\(index)" : "\(path).\(index)"
            let viewId = ViewHashGenerator.generateId(for: subview, path: childPath)

            let origin = subview.convert(CGPoint.zero, to: nil)
            let visibilityCode: Int = subview.isHidden ? 8 : (subview.alpha == 0 ? 4 : 0)

            // 计算视觉尺寸（layout 原始尺寸 × scale）
            let t = subview.transform
            let sx = Float(sqrt(t.a * t.a + t.c * t.c))
            let sy = Float(sqrt(t.b * t.b + t.d * t.d))
            let visualSx = sx == 0 ? 1 : sx
            let visualSy = sy == 0 ? 1 : sy

            let node = ViewNode(
                id: viewId,
                type: ViewTypeMapper.map(subview),
                screenX: Float(origin.x),
                screenY: Float(origin.y),
                widthDp: Float(subview.bounds.width) * visualSx,
                heightDp: Float(subview.bounds.height) * visualSy,
                visibility: visibilityCode,
                isEnabled: subview.isUserInteractionEnabled,
                attrs: StyleQuerier.query(subview)
            )

            nodes.append(node)
            nodes.append(contentsOf: traverse(subview, path: childPath))
        }

        return nodes
    }

    static func traverseFromWindow() -> [ViewNode] {
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .filter({ $0.tag != OverlayManager.overlayTag && !$0.isHidden })
            .min(by: { $0.windowLevel < $1.windowLevel }) else {
            return []
        }
        return traverse(window)
    }
}
```

- [ ] **Step 3: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add clients/ios/sdk/Sources/ViewQuery/ViewNode.swift clients/ios/sdk/Sources/ViewQuery/ViewTraverser.swift
git commit -m "ios: remove translate/scale from ViewNode, snapshot widthDp/heightDp now visual size"
```

---

### Task 10: iOS SDK — 更新 HttpServer.swift 路由和 handler

**Files:**
- Modify: `clients/ios/sdk/Sources/HttpServer/HttpServer.swift`

- [ ] **Step 1: 在 processRequest 的 switch 中替换路由**

找到：
```swift
        case ("POST", "/api/modify/ios"):
            handleModifyIos(bodyData, connection: connection)
```

替换为：
```swift
        case ("POST", "/api/modify"):
            handleModify(bodyData, connection: connection)
```

- [ ] **Step 2: 将 handleModifyIos 函数重命名并改写**

找到：
```swift
    private func handleModifyIos(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_ModifyViewIosRequest(serializedBytes: body) else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        let (success, message) = viewModifyService.modifyIosProto(id: req.id, props: req.props)
        var resp = Clienttools_ModifyResponse()
        resp.meta = okMeta()
        resp.message = message
        if success {
            sendProto(resp, connection: connection)
        } else {
            sendError(code: 404, message: message, httpCode: 404, connection: connection)
        }
    }
```

替换为：
```swift
    private func handleModify(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_ModifyViewRequest(serializedBytes: body) else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        let (success, message) = viewModifyService.modify(id: req.id, req: req)
        var resp = Clienttools_ModifyResponse()
        resp.meta = okMeta()
        resp.message = message
        if success {
            sendProto(resp, connection: connection)
        } else {
            sendError(code: 404, message: message, httpCode: 404, connection: connection)
        }
    }
```

- [ ] **Step 3: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add clients/ios/sdk/Sources/HttpServer/HttpServer.swift
git commit -m "ios: update HTTP route /api/modify/ios -> /api/modify, use unified ModifyViewRequest"
```

---

### Task 11: 构建验证

- [ ] **Step 1: 验证 Android 构建**

```bash
cd /Users/zzc/Desktop/works/client-tools/clients/android && ./gradlew :sdk:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 验证 MCP TypeScript 编译**

```bash
cd /Users/zzc/Desktop/works/client-tools/mcp && npx tsc --noEmit
```

Expected: 无报错

- [ ] **Step 3: 如有编译错误，根据错误信息定位并修复**

常见问题：
- TypeScript 中旧 schema 名称未清理 → 检查 view.ts 中是否有残余 `ModifyViewAndroidRequestSchema` / `ModifyViewIosRequestSchema`
- Android 中旧类名未更新 → 检查 ApiHandler.kt 中是否有残余 `ModifyViewAndroidRequest`、`AndroidViewModifier`
- iOS 中旧 proto 类型名未更新 → 检查 ViewModifyService.swift 中是否有残余 `Clienttools_ModifyViewIosRequest`

---

### Task 12: 更新文档

**Files:**
- Modify: `docs/mcp-tools.md`
- Modify: `docs/sdk-http-api.md`

- [ ] **Step 1: 在 docs/mcp-tools.md 中删除 modify_view_android 和 modify_view_ios 章节，新增 modify_view**

在「视图修改」分组中：

**概览表**，将：
```markdown
| 视图修改 | `modify_view_android` | 修改 Android View 布局属性 |
| | `modify_view_ios` | 修改 iOS UIView transform/文字属性 |
```
替换为：
```markdown
| 视图修改 | `modify_view` | 修改 View 的位置、尺寸或文案（Android/iOS 通用） |
```

**详情章节**，删除 `### modify_view_android` 和 `### modify_view_ios` 两个章节，替换为：

```markdown
### modify_view

修改 View 的位置、尺寸或文案。内部通过 translation/scale 实现，pivot 固定左上角，操作互不干扰。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | View 的 id |
| move_dx | number | 否 | 横向偏移增量（dp），正右 |
| move_dy | number | 否 | 纵向偏移增量（dp），正下 |
| width | number | 否 | 目标宽度（dp），绝对值 |
| height | number | 否 | 目标高度（dp），绝对值 |
| text | string | 否 | 替换文案内容（要求 view 为 TextView/UILabel/UITextField） |

**返回：** `"ok"` 或错误信息字符串
```

- [ ] **Step 2: 在 docs/sdk-http-api.md 中更新接口**

**概览表**，将：
```markdown
| `/api/modify/android` | POST | ✓ | — | 修改 Android View 属性 |
| `/api/modify/ios` | POST | — | ✓ | 修改 iOS View 属性 |
```
替换为：
```markdown
| `/api/modify` | POST | ✓ | ✓ | 修改 View 位置/尺寸/文案 |
```

**详情章节**，删除 `### POST /api/modify/android` 和 `### POST /api/modify/ios`，新增：

```markdown
### POST /api/modify

修改 View 的位置（translation）、尺寸（scale）或文案。Android/iOS 通用，pivot 固定左上角。

**请求体：** `ModifyViewRequest`
```
id: string
move.dx: FloatValue（可选）  // 横向偏移增量（dp），正右
move.dy: FloatValue（可选）  // 纵向偏移增量（dp），正下
size.width:  FloatValue（可选）  // 目标宽度（dp），绝对值
size.height: FloatValue（可选）  // 目标高度（dp），绝对值
text.content: string（可选）     // 替换文案，断言 view 为 TextView/UILabel/UITextField
```

**响应：** `ModifyResponse`
```
meta: ResponseMeta
message: string    // "ok" 或错误描述
```
```

- [ ] **Step 3: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add docs/mcp-tools.md docs/sdk-http-api.md
git commit -m "docs: update mcp-tools and sdk-http-api for unified modify_view"
```

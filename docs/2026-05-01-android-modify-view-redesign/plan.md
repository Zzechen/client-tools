# Android modify_view 重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android `modify_view` 工具的参数重构为 margin/padding/size/text 四组嵌套结构，实现两阶段全量校验（全部通过再 apply），新增 `/api/modify/android` 端点，删除旧 `/api/modify` 端点，并清理 iOS SDK 的 Android 兼容路径。

**Architecture:** 新增 `AndroidViewModifier.kt` 实现两阶段逻辑（校验 + apply 全在主线程同步执行）；更新 proto 增加四组嵌套消息；删除旧 `ViewProps`/`ModifyViewRequest`；iOS SDK 删除 `/api/modify` 兼容路径；MCP 工具改为嵌套 schema。

**Tech Stack:** Kotlin / Android SDK（NanoHTTPD）、Protobuf（buf generate）、TypeScript（MCP）、Swift（iOS SDK 清理）

---

## 文件结构

| 文件 | 变更 |
|------|------|
| `proto/modify.proto` | 删除 ViewProps、ModifyViewRequest；新增 Android 分组消息 |
| `clients/android/sdk/src/main/proto/modify.proto` | 同上，并删除 duplicate ModifyResponse |
| `mcp/src/generated/modify_pb.ts` | buf generate 重新生成 |
| `clients/ios/sdk/Sources/Generated/modify.pb.swift` | buf generate 重新生成 |
| `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/AndroidViewModifier.kt` | 新建 |
| `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt` | 删除 apply/modify/resolveDimension，保留 click/scroll |
| `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt` | 删除 handleModify，新增 handleModifyAndroid |
| `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt` | 路由 /api/modify → /api/modify/android |
| `clients/ios/sdk/Sources/HttpServer/HttpServer.swift` | 删除 /api/modify 路由和 handleModify() |
| `clients/ios/sdk/Sources/ViewModify/ViewModifyService.swift` | 删除 modifyProto() |
| `mcp/src/tools/view.ts` | 更新 modify_view_android schema 和端点 |

---

### Task 1: 更新 proto 文件

**Files:**
- Modify: `proto/modify.proto`
- Modify: `clients/android/sdk/src/main/proto/modify.proto`
- Regenerate: `mcp/src/generated/modify_pb.ts`
- Regenerate: `clients/ios/sdk/Sources/Generated/modify.pb.swift`

- [ ] **Step 1: 替换 proto/modify.proto（根目录）**

完整新内容（删除 ViewProps/ModifyViewRequest，新增 Android 分组消息）：

```protobuf
syntax = "proto3";
package clienttools;
option java_package = "com.clienttools.sdk.proto";
option java_multiple_files = true;
import "google/protobuf/wrappers.proto";

message AndroidMarginProps {
  google.protobuf.FloatValue top_diff_dp    = 1;
  google.protobuf.FloatValue bottom_diff_dp = 2;
  google.protobuf.FloatValue left_diff_dp   = 3;
  google.protobuf.FloatValue right_diff_dp  = 4;
}

message AndroidPaddingProps {
  google.protobuf.FloatValue top_diff_dp    = 1;
  google.protobuf.FloatValue bottom_diff_dp = 2;
  google.protobuf.FloatValue left_diff_dp   = 3;
  google.protobuf.FloatValue right_diff_dp  = 4;
}

message AndroidSizeProps {
  oneof width {
    float width_dp           = 1;
    bool  width_wrap_content = 2;
  }
  oneof height {
    float height_dp           = 3;
    bool  height_wrap_content = 4;
  }
}

message AndroidTextProps {
  google.protobuf.FloatValue letter_spacing_em     = 1;
  google.protobuf.FloatValue line_spacing_extra_dp = 2;
  google.protobuf.BoolValue  include_font_padding  = 3;
}

message AndroidViewProps {
  AndroidMarginProps  margin  = 1;
  AndroidPaddingProps padding = 2;
  AndroidSizeProps    size    = 3;
  AndroidTextProps    text    = 4;
}

message ModifyViewAndroidRequest {
  string           id    = 1;
  AndroidViewProps props = 2;
}

message IosTextProps {
  google.protobuf.StringValue content          = 1;
  google.protobuf.FloatValue  letter_spacing_em     = 2;
  google.protobuf.FloatValue  line_spacing_extra_dp = 3;
}

message IosViewProps {
  google.protobuf.FloatValue translate_x_dp = 1;
  google.protobuf.FloatValue translate_y_dp = 2;
  google.protobuf.FloatValue scale_x        = 3;
  google.protobuf.FloatValue scale_y        = 4;
  optional IosTextProps      text           = 11;
}

message ModifyViewIosRequest {
  string       id    = 1;
  IosViewProps props = 2;
}

message ClickRequest  { string id = 1; }
message ClickResult   { string id = 1; }

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

- [ ] **Step 2: 替换 clients/android/sdk/src/main/proto/modify.proto**

完整新内容（同根目录版本，但保留 Android 本地的 IosViewProps 完整字段；删除 duplicate ModifyResponse）：

```protobuf
syntax = "proto3";
package clienttools;
option java_package = "com.clienttools.sdk.proto";
option java_multiple_files = true;
import "google/protobuf/wrappers.proto";

message AndroidMarginProps {
  google.protobuf.FloatValue top_diff_dp    = 1;
  google.protobuf.FloatValue bottom_diff_dp = 2;
  google.protobuf.FloatValue left_diff_dp   = 3;
  google.protobuf.FloatValue right_diff_dp  = 4;
}

message AndroidPaddingProps {
  google.protobuf.FloatValue top_diff_dp    = 1;
  google.protobuf.FloatValue bottom_diff_dp = 2;
  google.protobuf.FloatValue left_diff_dp   = 3;
  google.protobuf.FloatValue right_diff_dp  = 4;
}

message AndroidSizeProps {
  oneof width {
    float width_dp           = 1;
    bool  width_wrap_content = 2;
  }
  oneof height {
    float height_dp           = 3;
    bool  height_wrap_content = 4;
  }
}

message AndroidTextProps {
  google.protobuf.FloatValue letter_spacing_em     = 1;
  google.protobuf.FloatValue line_spacing_extra_dp = 2;
  google.protobuf.BoolValue  include_font_padding  = 3;
}

message AndroidViewProps {
  AndroidMarginProps  margin  = 1;
  AndroidPaddingProps padding = 2;
  AndroidSizeProps    size    = 3;
  AndroidTextProps    text    = 4;
}

message ModifyViewAndroidRequest {
  string           id    = 1;
  AndroidViewProps props = 2;
}

message IosTextProps {
  google.protobuf.StringValue content               = 1;
  google.protobuf.FloatValue  letter_spacing_em     = 2;
  google.protobuf.FloatValue  line_spacing_extra_dp = 3;
}

message IosViewProps {
  google.protobuf.FloatValue translate_x_dp         = 1;
  google.protobuf.FloatValue translate_y_dp         = 2;
  google.protobuf.FloatValue scale_x                = 3;
  google.protobuf.FloatValue scale_y                = 4;
  google.protobuf.FloatValue width_dp               = 5;
  google.protobuf.FloatValue height_dp              = 6;
  google.protobuf.FloatValue padding_top_diff_dp    = 7;
  google.protobuf.FloatValue padding_bottom_diff_dp = 8;
  google.protobuf.FloatValue padding_left_diff_dp   = 9;
  google.protobuf.FloatValue padding_right_diff_dp  = 10;
  IosTextProps               text                   = 11;
}

message ModifyViewIosRequest {
  string       id    = 1;
  IosViewProps props = 2;
}

message ClickRequest  { string id = 1; }
message ClickResult   { string id = 1; }

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

- [ ] **Step 3: 运行 buf generate**

```bash
cd /path/to/client-tools/proto && buf generate
```

预期输出：无报错，以下文件被更新：
- `mcp/src/generated/modify_pb.ts`（含新 `ModifyViewAndroidRequestSchema`，不含 `ViewPropsSchema`）
- `clients/ios/sdk/Sources/Generated/modify.pb.swift`（含新 Android message 类型）

- [ ] **Step 4: 提交 proto 变更**

```bash
git add proto/modify.proto \
        clients/android/sdk/src/main/proto/modify.proto \
        mcp/src/generated/modify_pb.ts \
        clients/ios/sdk/Sources/Generated/modify.pb.swift
git commit -m "feat: replace ViewProps with Android grouped proto messages"
```

---

### Task 2: 实现 AndroidViewModifier.kt

**Files:**
- Create: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/AndroidViewModifier.kt`

- [ ] **Step 1: 创建 AndroidViewModifier.kt**

```kotlin
package com.clienttools.sdk.runtime

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.proto.AndroidSizeProps
import com.clienttools.sdk.proto.AndroidViewProps
import java.util.concurrent.CountDownLatch

object AndroidViewModifier {

    fun apply(viewId: String, props: AndroidViewProps): Pair<Boolean, String> {
        val activity = ClientToolsSDK.getCurrentActivity()
            ?: return Pair(false, "No activity available")

        var result: Pair<Boolean, String> = Pair(false, "unknown error")
        val latch = CountDownLatch(1)

        activity.runOnUiThread {
            result = runOnMain(viewId, props)
            latch.countDown()
        }

        latch.await()
        return result
    }

    private fun runOnMain(viewId: String, props: AndroidViewProps): Pair<Boolean, String> {
        // Phase 1: find view
        val views = ViewTreeTraversal.findViewById(viewId)
        if (views.isEmpty()) return Pair(false, "View not found: $viewId")
        val view = views[0]

        // Phase 1: validate margin
        if (props.hasMargin()) {
            val lp = view.layoutParams
            if (lp !is ViewGroup.MarginLayoutParams) {
                return Pair(false, "margin requires MarginLayoutParams, but '$viewId' has ${lp?.javaClass?.simpleName ?: "null"}")
            }
        }

        // Phase 1: validate text
        if (props.hasText()) {
            if (view !is TextView) {
                return Pair(false, "text requires TextView, but '$viewId' is ${view.javaClass.simpleName}")
            }
        }

        // Phase 2: apply
        return try {
            applyProps(view, props)
            Pair(true, "")
        } catch (e: Exception) {
            Pair(false, e.message ?: "error")
        }
    }

    private fun applyProps(view: View, props: AndroidViewProps) {
        val density = view.resources.displayMetrics.density
        val dpToPx = { dp: Float -> (dp * density).toInt() }

        // margin + size share the same layoutParams object — set once
        val lp = view.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (props.hasMargin()) {
            val m = props.margin
            val mlp = lp as ViewGroup.MarginLayoutParams
            mlp.setMargins(
                if (m.hasLeftDiffDp()) mlp.leftMargin + dpToPx(m.leftDiffDp.value) else mlp.leftMargin,
                if (m.hasTopDiffDp()) mlp.topMargin + dpToPx(m.topDiffDp.value) else mlp.topMargin,
                if (m.hasRightDiffDp()) mlp.rightMargin + dpToPx(m.rightDiffDp.value) else mlp.rightMargin,
                if (m.hasBottomDiffDp()) mlp.bottomMargin + dpToPx(m.bottomDiffDp.value) else mlp.bottomMargin
            )
        }

        if (props.hasSize()) {
            val s = props.size
            when (s.widthCase) {
                AndroidSizeProps.WidthCase.WIDTH_DP -> lp.width = dpToPx(s.widthDp)
                AndroidSizeProps.WidthCase.WIDTH_WRAP_CONTENT -> lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                else -> {}
            }
            when (s.heightCase) {
                AndroidSizeProps.HeightCase.HEIGHT_DP -> lp.height = dpToPx(s.heightDp)
                AndroidSizeProps.HeightCase.HEIGHT_WRAP_CONTENT -> lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                else -> {}
            }
        }

        if (props.hasMargin() || props.hasSize()) {
            view.layoutParams = lp
        }

        if (props.hasPadding()) {
            val p = props.padding
            val top    = if (p.hasTopDiffDp())    view.paddingTop    + dpToPx(p.topDiffDp.value)    else view.paddingTop
            val bottom = if (p.hasBottomDiffDp()) view.paddingBottom + dpToPx(p.bottomDiffDp.value) else view.paddingBottom
            val left   = if (p.hasLeftDiffDp())   view.paddingLeft   + dpToPx(p.leftDiffDp.value)   else view.paddingLeft
            val right  = if (p.hasRightDiffDp())  view.paddingRight  + dpToPx(p.rightDiffDp.value)  else view.paddingRight
            view.setPadding(left, top, right, bottom)
        }

        if (props.hasText() && view is TextView) {
            val t = props.text
            if (t.hasLetterSpacingEm()) view.letterSpacing = t.letterSpacingEm.value
            if (t.hasLineSpacingExtraDp()) {
                view.setLineSpacing(t.lineSpacingExtraDp.value * density, view.lineSpacingMultiplier)
            }
            if (t.hasIncludeFontPadding()) view.includeFontPadding = t.includeFontPadding.value
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/AndroidViewModifier.kt
git commit -m "feat: add AndroidViewModifier with two-phase validation"
```

---

### Task 3: 精简 ViewModifier.kt

**Files:**
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt`

`apply()`、`modify()`、`resolveDimension()` 已由 `AndroidViewModifier` 接管，只保留 `click()` 和 `scroll()`。

- [ ] **Step 1: 替换 ViewModifier.kt 内容**

```kotlin
package com.clienttools.sdk.runtime

import android.os.Looper
import com.clienttools.sdk.ClientToolsSDK

object ViewModifier {

    fun click(viewId: String): Boolean {
        val views = ViewTreeTraversal.findViewById(viewId)
        return if (views.isEmpty()) false else {
            views.forEach { view ->
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    view.performClick()
                } else {
                    val activity = ClientToolsSDK.getCurrentActivity()
                    activity?.runOnUiThread { view.performClick() }
                }
            }
            true
        }
    }

    fun scroll(viewId: String, dxDp: Float, dyDp: Float): Boolean {
        val views = ViewTreeTraversal.findViewById(viewId)
        return if (views.isEmpty()) false else {
            views.forEach { view ->
                val density = view.context.resources.displayMetrics.density
                val dxPx = (dxDp * density).toInt()
                val dyPx = (dyDp * density).toInt()
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    view.scrollBy(dxPx, dyPx)
                } else {
                    val activity = ClientToolsSDK.getCurrentActivity()
                    activity?.runOnUiThread { view.scrollBy(dxPx, dyPx) }
                }
            }
            true
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt
git commit -m "refactor: remove modify logic from ViewModifier, keep click/scroll"
```

---

### Task 4: 更新 ApiHandler.kt

**Files:**
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt`

- [ ] **Step 1: 更新 import 并替换 handleModify → handleModifyAndroid**

在文件头部 import 区**新增**（`ViewModifier` 仍被 handleClick/handleScroll 使用，不删除）：
```kotlin
import com.clienttools.sdk.runtime.AndroidViewModifier
```

将 `handleModify()` 方法（第 87-97 行）替换为：

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

同时在 import 区新增：
```kotlin
import com.clienttools.sdk.runtime.AndroidViewModifier
```

- [ ] **Step 2: 提交**

```bash
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt
git commit -m "feat: add handleModifyAndroid, remove handleModify"
```

---

### Task 5: 更新 Android HttpServer.kt 路由

**Files:**
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`

- [ ] **Step 1: 将路由 /api/modify 改为 /api/modify/android**

将第 46-47 行：
```kotlin
method == Method.POST && uri == "/api/modify" ->
    ApiHandler.handleModify(readBodyBytes(session))
```

替换为：
```kotlin
method == Method.POST && uri == "/api/modify/android" ->
    ApiHandler.handleModifyAndroid(readBodyBytes(session))
```

- [ ] **Step 2: 提交**

```bash
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt
git commit -m "feat: route /api/modify/android, remove /api/modify"
```

---

### Task 6: 验证 Android 编译

**Files:** 无新增，验证步骤

- [ ] **Step 1: 编译 Android SDK**

```bash
cd clients/android && ./gradlew :sdk:assembleDebug
```

预期输出：
```
BUILD SUCCESSFUL in Xs
```

如果出现编译错误，常见原因及处理：
- `Unresolved reference: ModifyViewRequest` → 检查 ApiHandler.kt 是否还有残留引用
- `Unresolved reference: ViewProps` → 检查是否有其他文件 import 了旧类型
- `Unresolved reference: AndroidSizeProps.WidthCase` → 确认 proto 文件已正确更新并 Gradle 重新生成了 Java 类

---

### Task 7: 清理 iOS SDK

**Files:**
- Modify: `clients/ios/sdk/Sources/HttpServer/HttpServer.swift`
- Modify: `clients/ios/sdk/Sources/ViewModify/ViewModifyService.swift`

- [ ] **Step 1: 删除 HttpServer.swift 中的 /api/modify 路由和 handleModify() 方法**

在 `HttpServer.swift` 的路由 switch 中，删除这两行（约第 172-173 行）：
```swift
case ("POST", "/api/modify"):
    handleModify(bodyData, connection: connection)
```

同时删除整个 `handleModify()` 方法（约第 286-298 行）：
```swift
private func handleModify(_ body: Data, connection: NWConnection) {
    guard let req = try? Clienttools_ModifyViewRequest(serializedBytes: body) else {
        sendError(code: 400, message: "Invalid request", connection: connection); return
    }
    let (success, message) = viewModifyService.modifyProto(id: req.id, props: req.props)
    if success {
        var resp = Clienttools_ModifyResponse()
        resp.meta = okMeta()
        sendProto(resp, connection: connection)
    } else {
        sendError(code: 404, message: message, httpCode: 404, connection: connection)
    }
}
```

- [ ] **Step 2: 删除 ViewModifyService.swift 中的 modifyProto() 方法**

删除 `ViewModifyService.swift` 中第 7-57 行的 `modifyProto()` 方法（从 `// Android 路径：保留现有实现` 注释到方法结束的 `}`）。

删除后文件从以下内容开始：
```swift
import UIKit

class ViewModifyService {

    private let viewQueryService = ViewQueryService()

    // iOS 路径：transform + 尺寸约束 + padding + 文字属性
    func modifyIosProto(id: String, props: Clienttools_IosViewProps) -> (Bool, String) {
        // ... 保持不变
```

- [ ] **Step 3: 提交**

```bash
git add clients/ios/sdk/Sources/HttpServer/HttpServer.swift \
        clients/ios/sdk/Sources/ViewModify/ViewModifyService.swift
git commit -m "feat: remove iOS /api/modify Android-compat path"
```

---

### Task 8: 更新 MCP view.ts

**Files:**
- Modify: `mcp/src/tools/view.ts`

- [ ] **Step 1: 更新 import**

将文件第 13 行：
```typescript
import { ModifyViewRequestSchema, ModifyViewIosRequestSchema } from "../generated/modify_pb.js";
```

替换为：
```typescript
import { ModifyViewAndroidRequestSchema, ModifyViewIosRequestSchema } from "../generated/modify_pb.js";
```

- [ ] **Step 2: 替换 modify_view_android 工具定义**

将 `// ===== modify_view_android =====` 起始到 `// ===== modify_view_ios =====` 之前的全部内容（第 68-111 行）替换为：

```typescript
  // ===== modify_view_android =====
  const AndroidMarginPropsZod = z.object({
    topDiffDp:    z.number().optional(),
    bottomDiffDp: z.number().optional(),
    leftDiffDp:   z.number().optional(),
    rightDiffDp:  z.number().optional(),
  }).describe("margin 增量调整（dp）");

  const AndroidPaddingPropsZod = z.object({
    topDiffDp:    z.number().optional(),
    bottomDiffDp: z.number().optional(),
    leftDiffDp:   z.number().optional(),
    rightDiffDp:  z.number().optional(),
  }).describe("padding 增量调整（dp）");

  const AndroidSizePropsZod = z.object({
    width:  z.union([z.number(), z.literal("wrap_content")]).optional(),
    height: z.union([z.number(), z.literal("wrap_content")]).optional(),
  }).describe("尺寸设置（dp 数值或 wrap_content）");

  const AndroidTextPropsZod = z.object({
    letterSpacingEm:    z.number().optional(),
    lineSpacingExtraDp: z.number().optional(),
    includeFontPadding: z.boolean().optional(),
  }).describe("文字属性（传此对象则断言 view 为 TextView 或其子类，否则整个请求失败）");

  server.tool(
    "modify_view_android",
    "修改 Android View 的布局属性。参数按功能分组：margin/padding 为增量（dp），size 为绝对值；传 text 组则断言 view 为 TextView 子类，否则整体拒绝",
    {
      id:      z.string().describe("Android View 的 resource id（不含 @id/ 前缀）"),
      margin:  AndroidMarginPropsZod.optional(),
      padding: AndroidPaddingPropsZod.optional(),
      size:    AndroidSizePropsZod.optional(),
      text:    AndroidTextPropsZod.optional(),
    },
    async ({ id, margin, padding, size, text }) => {
      try {
        const androidProps = {
          ...(margin && { margin: {
            ...(margin.topDiffDp    !== undefined && { topDiffDp:    margin.topDiffDp }),
            ...(margin.bottomDiffDp !== undefined && { bottomDiffDp: margin.bottomDiffDp }),
            ...(margin.leftDiffDp   !== undefined && { leftDiffDp:   margin.leftDiffDp }),
            ...(margin.rightDiffDp  !== undefined && { rightDiffDp:  margin.rightDiffDp }),
          }}),
          ...(padding && { padding: {
            ...(padding.topDiffDp    !== undefined && { topDiffDp:    padding.topDiffDp }),
            ...(padding.bottomDiffDp !== undefined && { bottomDiffDp: padding.bottomDiffDp }),
            ...(padding.leftDiffDp   !== undefined && { leftDiffDp:   padding.leftDiffDp }),
            ...(padding.rightDiffDp  !== undefined && { rightDiffDp:  padding.rightDiffDp }),
          }}),
          ...(size && { size: {
            ...(size.width  !== undefined && (typeof size.width  === "number"
              ? { widthDp:  size.width  } : { widthWrapContent:  true })),
            ...(size.height !== undefined && (typeof size.height === "number"
              ? { heightDp: size.height } : { heightWrapContent: true })),
          }}),
          ...(text && { text: {
            ...(text.letterSpacingEm    !== undefined && { letterSpacingEm:    text.letterSpacingEm }),
            ...(text.lineSpacingExtraDp !== undefined && { lineSpacingExtraDp: text.lineSpacingExtraDp }),
            ...(text.includeFontPadding !== undefined && { includeFontPadding: text.includeFontPadding }),
          }}),
        };
        const req = create(ModifyViewAndroidRequestSchema, { id, props: androidProps });
        const res = await sdkPost("/api/modify/android", ModifyViewAndroidRequestSchema, req, ModifyResponseSchema);
        const msg = res.message ? res.message : "ok";
        return { content: [{ type: "text" as const, text: msg }] };
      } catch (e) { return errResult(e); }
    }
  );

```

- [ ] **Step 3: 提交**

```bash
git add mcp/src/tools/view.ts
git commit -m "feat: update modify_view_android to grouped schema and /api/modify/android"
```

---

### Task 9: 验证 MCP 编译并最终提交

**Files:** 无新增，验证步骤

- [ ] **Step 1: 编译 MCP**

```bash
cd mcp && npm run build
```

预期输出：
```
# 无报错，dist/ 目录更新
```

常见错误处理：
- `Module '"../generated/modify_pb.js"' has no exported member 'ModifyViewAndroidRequestSchema'` → 检查 Task 1 Step 3 的 buf generate 是否成功运行
- `Property 'message' does not exist on type 'ModifyResponse'` → 确认 ModifyResponseSchema 来自 api_pb.js（有 message 字段），不是 modify_pb.js

- [ ] **Step 2: 整体验证 checklist**

确认以下均为 true：
- [ ] `POST /api/modify` 在 Android HttpServer.kt 中不存在
- [ ] `POST /api/modify` 在 iOS HttpServer.swift 中不存在
- [ ] `POST /api/modify/android` 在 Android HttpServer.kt 中存在
- [ ] `POST /api/modify/ios` 在 iOS HttpServer.swift 中存在（未动）
- [ ] `ViewProps`、`ModifyViewRequest` 在两个 modify.proto 中均已删除
- [ ] `AndroidViewModifier.kt` 存在且 `apply()` 签名为 `(String, AndroidViewProps) -> Pair<Boolean, String>`
- [ ] `ViewModifier.kt` 只剩 `click()` 和 `scroll()`
- [ ] MCP `modify_view_android` 工具入参为 `{ id, margin?, padding?, size?, text? }`，调用 `/api/modify/android`
- [ ] `modifyProto()` 在 `ViewModifyService.swift` 中已删除

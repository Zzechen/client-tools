# Android modify_view 重设计

## 背景

当前 Android `modify_view` 工具使用平铺的 `ViewProps`（13 个字段），存在两个核心问题：

1. **非原子化**：SDK 按顺序逐项 apply，前几项成功后若某项失败，已产生部分修改
2. **错误信息不明确**：失败时只抛异常 message，不说明是哪个字段/组导致失败

目标：参照 iOS 的 `modify_view_ios` 设计思路，将参数按功能分组、做前置全量校验、错误明确指向失败原因。

## 端点变更

| 变更 | 说明 |
|------|------|
| 新增 `POST /api/modify/android` | Android 专用新端点 |
| 删除 `POST /api/modify` | Android SDK 和 iOS SDK 均移除 |
| `POST /api/modify/ios` | 不动 |

最终两个端点：`/api/modify/android`（Android SDK）、`/api/modify/ios`（iOS SDK）。

## Proto 结构

新增以下 message（`modify.proto` 及 `clients/android/sdk/src/main/proto/modify.proto` 同步更新）：

```protobuf
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
```

**删除**：`ViewProps`、`ModifyViewRequest`（旧 Android request）。

`ModifyResponse` 复用现有结构，不新增 response 类型。

## Android SDK：AndroidViewModifier

新建 `AndroidViewModifier.kt` 替代 `ViewModifier.kt`。

两阶段全部在主线程执行，通过 `CountDownLatch` 同步等待：

```
调用线程
  └─ runOnUiThread {
       // 阶段 1：全量校验
       1. findView(id) → 找不到 return (false, "View not found: $id")
       2. hasMargin → layoutParams !is MarginLayoutParams
          → return (false, "margin requires MarginLayoutParams, but '$id' has ${lp::class.simpleName}")
       3. hasText → view !is TextView
          → return (false, "text requires TextView, but '$id' is ${view::class.simpleName}")

       // 阶段 2：Apply（校验全部通过后执行）
       4. margin  → 读当前值 + diff，一次 setMargins() 调用
       5. padding → 读当前值 + diff，一次 setPadding() 调用
       6. size    → 解析 oneof，一次 setLayoutParams() 调用
       7. text    → setLetterSpacing / setLineSpacing / setIncludeFontPadding

       latch.countDown()
     }
  └─ latch.await()
  └─ 返回 (true, "") 或 (false, e.message)
```

### 类型校验规则

| 组 | 校验条件 | 错误 message 格式 |
|----|----------|-------------------|
| margin | `layoutParams is MarginLayoutParams` | `"margin requires MarginLayoutParams, but '$id' has <type>"` |
| text | `view is TextView`（含子类 EditText、Button 等） | `"text requires TextView, but '$id' is <type>"` |
| padding | 无前置类型限制 | — |
| size | 无前置类型限制 | — |

### ApiHandler 变更

新增 `handleModifyAndroid()`，旧 `handleModify()` 删除：

```kotlin
fun handleModifyAndroid(bodyBytes: ByteArray): Response {
    return try {
        val req = ModifyViewAndroidRequest.parseFrom(bodyBytes)
        val (ok, msg) = AndroidViewModifier.apply(req.id, req.props)
        if (ok) {
            okResponse(ModifyResponse.newBuilder().setMeta(okMeta()).build().toByteArray())
        } else {
            errResponse(Status.NOT_FOUND, msg)
        }
    } catch (e: Exception) {
        errResponse(Status.INTERNAL_ERROR, e.message ?: "error")
    }
}
```

## iOS SDK 清理

| 变更 | 文件 |
|------|------|
| 删除 `POST /api/modify` 路由 case | `HttpServer.swift` |
| 删除 `handleModify()` 方法 | `HttpServer.swift` |
| 删除 `modifyProto()` 方法 | `ViewModifyService.swift` |
| `/api/modify/ios`、`modifyIosProto()` 不动 | — |

## MCP 工具

### modify_view_android（替换现有）

```typescript
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

// 工具入参
z.object({
  id:      z.string().describe("目标 View 的 android:id（不含 @id/ 前缀）"),
  margin:  AndroidMarginPropsZod.optional(),
  padding: AndroidPaddingPropsZod.optional(),
  size:    AndroidSizePropsZod.optional(),
  text:    AndroidTextPropsZod.optional(),
})
```

调用路径：`POST /api/modify/android`，使用 `ModifyViewAndroidRequest` proto。

错误返回：SDK HTTP 404 + message，MCP 将 message 作为文本内容返回给 AI。

### modify_view_ios

不动。

## 影响范围汇总

| 文件 | 变更类型 |
|------|----------|
| `proto/modify.proto` | 新增 Android 分组 message，删除 `ViewProps`、`ModifyViewRequest` |
| `clients/android/sdk/src/main/proto/modify.proto` | 同上（与 `proto/` 同步） |
| `clients/android/sdk/.../AndroidViewModifier.kt` | 新建 |
| `clients/android/sdk/.../ViewModifier.kt` | 删除 |
| `clients/android/sdk/.../ApiHandler.kt` | 新增 `handleModifyAndroid`，删除 `handleModify`，更新路由 |
| `clients/ios/sdk/.../HttpServer.swift` | 删除 `/api/modify` 路由和 `handleModify()` |
| `clients/ios/sdk/.../ViewModifyService.swift` | 删除 `modifyProto()` |
| `mcp/src/tools/view.ts` | 更新 `modify_view_android` schema 和调用路径 |
| `mcp/src/generated/modify_pb.ts` | 重新生成（`buf generate`） |
| `clients/ios/sdk/Sources/Generated/modify.pb.swift` | 重新生成（`buf generate`） |

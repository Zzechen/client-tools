# Design: Modify View 统一重设计

**日期：** 2026-05-20  
**状态：** 已批准

## 背景

当前 `modify_view_android` 通过 `LayoutParams` 改 margin/padding/size，`modify_view_ios` 通过 `CGAffineTransform` 改 translate/scale。iOS 的布局体系（Auto Layout）没有类似 Android 的 `LayoutParams`，无法可靠地应用 margin/padding，导致两端能力不对称、行为不一致。

目标：两端统一使用 translation/scale 实现视觉调整，MCP 层暴露语义化能力（移动/宽高/文案），对 AI 屏蔽底层实现细节。

## 设计原则

- **MCP 语义化**：MCP 工具使用"移动/宽高/文案"语义，不暴露 translation/scale
- **SDK 内部换算**：move → translationX/Y 增量；size → scaleX/Y（由 SDK 用 targetSize/originalSize 计算）
- **Pivot 左上角**：两端均将 pivot 固定到视图左上角（0,0），确保 scale 和 translation 语义独立、行为一致
- **Snapshot 视觉值**：`screenX/Y/widthDp/heightDp` 反映 post-transform 视觉位置和尺寸，不单独暴露 translateX/Y/scaleX/Y

## Proto 变更

### 删除

- `AndroidMarginProps`
- `AndroidPaddingProps`
- `AndroidSizeProps`
- `AndroidTextProps`
- `AndroidViewProps`
- `ModifyViewAndroidRequest`
- `IosTextProps`
- `IosViewProps`
- `ModifyViewIosRequest`
- `Node.translate_x`、`Node.translate_y`、`Node.scale_x`、`Node.scale_y`

### 新增

```protobuf
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
```

HTTP 路由：`POST /api/modify`（废弃 `/api/modify/android` 和 `/api/modify/ios`）

## Android SDK 变更

### 删除
- `AndroidViewModifier.kt`

### 新增：`ViewModifier.kt`

职责：处理 `ModifyViewRequest`，在 UI 线程执行。

**move 处理：**
```
view.pivotX = 0f
view.pivotY = 0f
view.translationX += dx * density
view.translationY += dy * density
```

**size 处理：**
```
view.pivotX = 0f
view.pivotY = 0f
// originalWidth = view.width（layout 原始宽，未乘 scale）
// 需用 view.width / view.scaleX 还原原始尺寸
val originalW = view.width / view.scaleX
val originalH = view.height / view.scaleY
if (hasWidth)  view.scaleX = targetW / originalW
if (hasHeight) view.scaleY = targetH / originalH
```

**text 处理：**
断言 view 为 TextView，设置 `view.text = content`。

### Snapshot 变更：`ViewQueryService.kt`

`buildNode` 中 `widthDp/heightDp` 改为视觉尺寸：
```kotlin
.setWidthDp(view.width / density)    // view.width 已包含 scale 效果（像素）
.setHeightDp(view.height / density)
```

> Android 的 `view.width` 返回的是 layout 原始宽（px），乘 scaleX 后的视觉宽需通过 `view.width * view.scaleX / density` 计算。

`getLocationOnScreen` 已自动包含 translationX/Y，screenX/Y 无需修改。

删除 Node proto 中 `translateX/Y/scaleX/Y` 字段的赋值。

## iOS SDK 变更

### 修改：`ViewModifyService.swift`

对齐新 proto `ModifyViewRequest`：

**move 处理：**
```swift
// anchorPoint 设到 (0,0)（幂等）
view.layer.anchorPoint = CGPoint(x: 0, y: 0)
// 补偿 position
view.translationX += dx
view.translationY += dy
// 通过 transform 叠加
let current = view.transform
view.transform = current.translatedBy(x: CGFloat(dx), y: CGFloat(dy))
```

**size 处理：**
```swift
// 还原原始尺寸
let originalW = view.bounds.width / currentSx
let originalH = view.bounds.height / currentSy
let newSx = hasWidth  ? CGFloat(targetW) / originalW : currentSx
let newSy = hasHeight ? CGFloat(targetH) / originalH : currentSy
view.transform = CGAffineTransform(scaleX: newSx, y: newSy)
    .concatenating(CGAffineTransform(translationX: currentTx, y: currentTy))
```

**text 处理：**
断言 view 为 UILabel 或 UITextField，设置 `label.text = content`。

### 修改：`ViewTraverser.swift`

删除 `translateX/Y/scaleX/Y` 字段赋值。

`widthDp/heightDp` 改为视觉尺寸：
```swift
widthDp:  Float(subview.bounds.width * currentSx),
heightDp: Float(subview.bounds.height * currentSy),
```

`screenX/Y` 无需改动（`convert(.zero, to: nil)` 已包含 transform 偏移）。

### 修改：`ViewNode.swift`

删除 `translateX`、`translateY`、`scaleX`、`scaleY` 字段。

## MCP 变更

### 删除工具
- `modify_view_android`（`mcp/src/tools/view.ts`）
- `modify_view_ios`（`mcp/src/tools/view.ts`）

### 新增工具：`modify_view`

```typescript
server.tool(
  "modify_view",
  "修改 View 的位置、尺寸或文案（Android/iOS 通用）",
  {
    id:     z.string().describe("View 的 id"),
    move_dx: z.number().optional().describe("横向偏移增量（dp），正右"),
    move_dy: z.number().optional().describe("纵向偏移增量（dp），正下"),
    width:   z.number().optional().describe("目标宽度（dp），绝对值"),
    height:  z.number().optional().describe("目标高度（dp），绝对值"),
    text:    z.string().optional().describe("替换文案内容"),
  },
  async ({ id, move_dx, move_dy, width, height, text }) => { ... }
)
```

路由调用 `POST /api/modify`，传 `ModifyViewRequest` proto。

## 文档变更

- `docs/mcp-tools.md`：删除 `modify_view_android/ios`，新增 `modify_view`
- `docs/sdk-http-api.md`：删除 `/api/modify/android` 和 `/api/modify/ios`，新增 `/api/modify`

## 不在范围内

- 文字排版属性（字间距、行间距）：暂不支持
- Android margin/padding：完全废弃，不保留

## 成功标准

- 调用 `modify_view` 在 Android/iOS 上都能移动 View 和改变视觉尺寸
- `get_node` / `get_all_nodes` 返回的 `screenX/Y/widthDp/heightDp` 反映 post-transform 视觉值
- MCP 工具层无需感知 Android/iOS 差异
- 两端 pivot 固定到左上角，scale 和 move 操作互不干扰

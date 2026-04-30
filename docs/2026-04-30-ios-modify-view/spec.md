# iOS modify_view 重构 Spec

## 背景

现有 iOS `modify_view` 通过修改 NSLayoutConstraint constant 实现位移，存在以下问题：

- `ConstraintModifier` 改约束 constant 时，双锚点视图（top + bottom 同时存在）会被拉伸而非平移，且无法可靠撤销
- `FrameModifier` 停用旧约束加新约束，在 SnapKit 生成的约束上匹配不稳定
- 多次调用累加，无归零机制，导致不可逆变形

## 目标

用 `transform`（translation + scale）替代约束修改实现位移和缩放，行为稳定、幂等、可逆。宽高保留约束修改但简化逻辑。文字属性保留现有实现并独立为类型断言结构。

---

## Proto 变更

### 新增 `ModifyViewIosRequest`

```proto
message ModifyViewIosRequest {
  string id = 1;

  // 位移（屏幕空间 dp，叠加到当前值）
  optional float translate_x_dp = 2;
  optional float translate_y_dp = 3;

  // 缩放（绝对值，1.0 为原始大小）
  optional float scale_x = 4;
  optional float scale_y = 5;

  // 尺寸（绝对值 dp，停用现有尺寸约束后设固定约束）
  optional float width_dp = 6;
  optional float height_dp = 7;

  // padding 差值（dp）
  optional float padding_top_diff_dp = 8;
  optional float padding_bottom_diff_dp = 9;
  optional float padding_left_diff_dp = 10;
  optional float padding_right_diff_dp = 11;

  // 文字属性（类型断言：传此字段则要求 view 必须是 UILabel，否则整个请求失败）
  optional IosTextProps text = 12;
}

message IosTextProps {
  optional float letter_spacing_em = 1;
  optional float line_spacing_extra_dp = 2;
}
```

### 现有 `ModifyViewRequest` 不变

保持现有字段，仅供 Android 使用。

### `Node` 新增 transform 分量

```proto
message Node {
  // ... 现有字段 ...

  // transform 分量（仅 iOS 有意义，Android 始终为默认值）
  float translate_x = 20;
  float translate_y = 21;
  float scale_x = 22;  // 默认 1.0
  float scale_y = 23;  // 默认 1.0
}
```

### `ModifyResponse` 新增 message 字段

```proto
message ModifyResponse {
  ResponseMeta meta = 1;
  string message = 2;  // 失败时说明原因，成功时为空
}
```

---

## iOS SDK 变更

### HTTP 路由

新增 `/view/modify/ios`，handler 解析 `ModifyViewIosRequest`。
现有 `/view/modify` 保持不变供 Android 使用（或映射为 `/view/modify/android`）。

### ViewModifyService 重构

**执行顺序（原子语义）：**

1. 若 request 包含 `text` 字段，先断言 view 是 UILabel；若不是，立即返回失败，不执行任何其他修改
2. 依次执行：transform → 尺寸 → padding → 文字属性

**transform 处理（每次调用都执行，幂等）：**

```swift
// 1. 将 anchorPoint 设为 (0,0) 并补偿 position，保持视觉位置不变
let oldAnchor = view.layer.anchorPoint
let newAnchor = CGPoint(x: 0, y: 0)
let size = view.bounds.size
view.layer.position = CGPoint(
    x: view.layer.position.x + (newAnchor.x - oldAnchor.x) * size.width,
    y: view.layer.position.y + (newAnchor.y - oldAnchor.y) * size.height
)
view.layer.anchorPoint = newAnchor

// 2. 从当前 transform 反解分量
let current = view.transform
let currentTx = current.tx
let currentTy = current.ty
let currentSx = sqrt(current.a * current.a + current.c * current.c)
let currentSy = sqrt(current.b * current.b + current.d * current.d)

// 3. 合并新值（未传则保留当前值）
let newTx = request.hasTranslateXDp ? CGFloat(request.translateXDp) : currentTx
let newTy = request.hasTranslateYDp ? CGFloat(request.translateYDp) : currentTy
let newSx = request.hasScaleX ? CGFloat(request.scaleX) : currentSx
let newSy = request.hasScaleY ? CGFloat(request.scaleY) : currentSy

// 4. 重建 transform：先 scale，再 translate（屏幕空间语义）
view.transform = CGAffineTransform(scaleX: newSx, y: newSy)
    .concatenating(CGAffineTransform(translationX: newTx, y: newTy))
```

**宽高处理（简化，删除 wrap_content）：**

```swift
// 停用所有 width/height 固定约束，添加新的固定约束
// 不再尝试匹配 currentSize，直接替换
let allConstraints = view.constraints + (view.superview?.constraints ?? [])
for c in allConstraints where c.firstAttribute == .width && c.secondItem == nil {
    c.isActive = false
}
NSLayoutConstraint.activate([
    NSLayoutConstraint(item: view, attribute: .width, relatedBy: .equal,
                       toItem: nil, attribute: .notAnAttribute,
                       multiplier: 1, constant: value)
])
// height 同理
```

**文字属性：** 保留现有 `letterSpacingEm` / `lineSpacingExtraDp` 逻辑，移入 `IosTextModifier`。

**删除：**
- `ConstraintModifier`（margin 系列全部废弃，不再暴露给 iOS）
- `FrameModifier` 中 `wrap_content` 分支及 `currentSize` 匹配逻辑

### ViewTraverser / ViewNode 扩展

`get_all_nodes` / `get_node` 从 `view.transform` 反解 tx/ty/sx/sy 并填入 Node：

```swift
let t = subview.transform
node.translateX = Float(t.tx)
node.translateY = Float(t.ty)
node.scaleX = Float(sqrt(t.a * t.a + t.c * t.c))
node.scaleY = Float(sqrt(t.b * t.b + t.d * t.d))
```

---

## MCP 变更

### 拆分为两个工具

**`modify_view_android`**（现有字段，不变）：
- `marginTopDiffDp`, `marginBottomDiffDp`, `marginLeftDiffDp`, `marginRightDiffDp`
- `paddingTopDiffDp`, `paddingBottomDiffDp`, `paddingLeftDiffDp`, `paddingRightDiffDp`
- `widthDp`, `heightDp`
- `letterSpacingEm`, `lineSpacingExtraDp`, `includeFontPadding`

**`modify_view_ios`**（新字段）：
- `translateXDp`, `translateYDp`（位移，屏幕空间 dp）
- `scaleX`, `scaleY`（缩放绝对值，1.0 原始大小）
- `widthDp`, `heightDp`
- `paddingTopDiffDp`, `paddingBottomDiffDp`, `paddingLeftDiffDp`, `paddingRightDiffDp`
- `text`: `{ letterSpacingEm?, lineSpacingExtraDp? }`（传此字段则断言 view 为 UILabel）

### endpoint 映射

- `modify_view_android` → `POST /view/modify`（现有，不变）
- `modify_view_ios` → `POST /view/modify/ios`（新增）

---

## 行为约定

| 场景 | 行为 |
|------|------|
| 传 text 但 view 不是 UILabel | 返回 400，message 说明实际类型，不执行任何修改 |
| 只传 scaleX，不传 translateX | translateX 保留当前值 |
| 多次调用 translateX=10 | 每次都设为 10（绝对值语义），不累加 |
| 宽高修改后约束系统 layout | 固定约束优先级高于原有相对约束，视觉稳定 |
| anchorPoint 已是 (0,0) | 补偿计算结果为零，幂等无副作用 |

---

## 不在范围内

- Android modify_view 不变
- iOS margin（已废弃，用 translateX/Y 替代）
- wrap_content 支持（删除）
- rotation（不支持，transform 反解假设无 rotation）

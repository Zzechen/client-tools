# iOS modify_view 重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 iOS modify_view，用 transform（translation + scale）替代约束修改，行为幂等可逆；新增文字属性类型断言；MCP 拆分为 modify_view_ios 和 modify_view_android 两个独立工具。

**Architecture:** Proto 新增 `ModifyViewIosRequest` + `IosTextProps`，`Node` 新增 transform 分量；iOS SDK 用 anchorPoint=(0,0) + transform 重建替代 ConstraintModifier，FrameModifier 简化删除 wrap_content；MCP 拆两个工具各自调不同 endpoint。

**Tech Stack:** Swift/UIKit/NSLayoutConstraint, Protocol Buffers (buf v2), TypeScript/Zod (MCP)

---

## 文件结构

**修改：**
- `proto/modify.proto` — 新增 `ModifyViewIosRequest`、`IosTextProps`
- `proto/node.proto` — `Node` 新增 `translateX/Y`、`scaleX/Y` 字段
- `clients/ios/sdk/Sources/Generated/` — buf generate 重新生成（不手动改）
- `clients/ios/sdk/Sources/ViewModify/ViewModifyService.swift` — 重构为 iOS 新逻辑
- `clients/ios/sdk/Sources/ViewModify/FrameModifier.swift` — 简化，删除 wrap_content
- `clients/ios/sdk/Sources/ViewQuery/ViewTraverser.swift` — 新增 transform 分量采集
- `clients/ios/sdk/Sources/ViewQuery/ViewQueryService.swift` — `toProtoNode` 填 transform 字段
- `clients/ios/sdk/Sources/HttpServer/HttpServer.swift` — 新增 `/api/modify/ios` 路由
- `mcp/src/tools/view.ts` — 拆分 modify_view 为 modify_view_android + modify_view_ios

**删除：**
- `clients/ios/sdk/Sources/ViewModify/ConstraintModifier.swift`

**保留不变：**
- `clients/ios/sdk/Sources/ViewModify/PaddingModifier.swift`
- Android SDK 所有文件
- `proto/api.proto`、`proto/inspector.proto`、`proto/page.proto`

---

## Task 1: Proto — 新增 iOS modify 消息 + Node transform 字段

**Files:**
- Modify: `proto/modify.proto`
- Modify: `proto/node.proto`
- Modify: `clients/android/sdk/src/main/proto/modify.proto`（与 proto/ 保持同步）
- Modify: `clients/android/sdk/src/main/proto/node.proto`

- [ ] **Step 1: 修改 `proto/modify.proto`，新增 `IosTextProps` 和 `ModifyViewIosRequest`**

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

message IosTextProps {
  google.protobuf.StringValue content = 1;
  google.protobuf.FloatValue letter_spacing_em = 2;
  google.protobuf.FloatValue line_spacing_extra_dp = 3;
}

message IosViewProps {
  google.protobuf.FloatValue translate_x_dp = 1;
  google.protobuf.FloatValue translate_y_dp = 2;
  google.protobuf.FloatValue scale_x = 3;
  google.protobuf.FloatValue scale_y = 4;
  google.protobuf.FloatValue width_dp = 5;
  google.protobuf.FloatValue height_dp = 6;
  google.protobuf.FloatValue padding_top_diff_dp = 7;
  google.protobuf.FloatValue padding_bottom_diff_dp = 8;
  google.protobuf.FloatValue padding_left_diff_dp = 9;
  google.protobuf.FloatValue padding_right_diff_dp = 10;
  IosTextProps text = 11;
}

message ModifyViewIosRequest {
  string id = 1;
  IosViewProps props = 2;
}

message ModifyResponse {
  string message = 1;
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

- [ ] **Step 2: 修改 `proto/node.proto`，Node 新增 transform 分量**

在 `Node` message 末尾追加（字段编号 11~14）：

```protobuf
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
  float translate_x = 11;
  float translate_y = 12;
  float scale_x = 13;
  float scale_y = 14;
}
```

- [ ] **Step 3: 同步到 Android proto 目录**

```bash
cp proto/modify.proto clients/android/sdk/src/main/proto/modify.proto
cp proto/node.proto clients/android/sdk/src/main/proto/node.proto
```

- [ ] **Step 4: 生成所有端代码**

```bash
cd proto && buf generate
```

期望输出：无错误，以下文件被更新：
- `clients/ios/sdk/Sources/Generated/modify.pb.swift`
- `clients/ios/sdk/Sources/Generated/node.pb.swift`
- `mcp/src/generated/modify_pb.ts`
- `mcp/src/generated/node_pb.ts`

- [ ] **Step 5: Commit**

```bash
git add proto/ clients/android/sdk/src/main/proto/ clients/ios/sdk/Sources/Generated/ mcp/src/generated/
git commit -m "feat(proto): add ModifyViewIosRequest, IosViewProps, IosTextProps; add transform fields to Node"
```

---

## Task 2: iOS SDK — ViewTraverser 新增 transform 分量采集

**Files:**
- Modify: `clients/ios/sdk/Sources/ViewQuery/ViewTraverser.swift`
- Modify: `clients/ios/sdk/Sources/ViewQuery/ViewQueryService.swift`
- Modify: `clients/ios/sdk/Sources/ViewQuery/ViewNode.swift`

- [ ] **Step 1: ViewNode 新增 transform 字段**

在 `clients/ios/sdk/Sources/ViewQuery/ViewNode.swift` 中，`ViewNode` struct 新增 4 个字段并更新 init：

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

- [ ] **Step 2: ViewTraverser 采集 transform 分量**

在 `clients/ios/sdk/Sources/ViewQuery/ViewTraverser.swift` 的 `traverse` 方法中，在 `let origin = subview.convert(CGPoint.zero, to: nil)` 之后，`let node = ViewNode(...)` 之前，加入 transform 反解：

```swift
let t = subview.transform
let tx = Float(t.tx)
let ty = Float(t.ty)
let sx = Float(sqrt(t.a * t.a + t.c * t.c))
let sy = Float(sqrt(t.b * t.b + t.d * t.d))
```

并更新 `ViewNode(...)` 构造，末尾加：

```swift
let node = ViewNode(
    id: viewId,
    type: ViewTypeMapper.map(subview),
    screenX: Float(origin.x),
    screenY: Float(origin.y),
    widthDp: Float(subview.bounds.width),
    heightDp: Float(subview.bounds.height),
    visibility: visibilityCode,
    isEnabled: subview.isUserInteractionEnabled,
    attrs: StyleQuerier.query(subview),
    translateX: tx,
    translateY: ty,
    scaleX: sx,
    scaleY: sy
)
```

- [ ] **Step 3: ViewQueryService.toProtoNode 填 transform 字段**

在 `clients/ios/sdk/Sources/ViewQuery/ViewQueryService.swift` 的 `toProtoNode` 方法中，在 `node.heightDp = vn.heightDp` 之后追加：

```swift
node.translateX = vn.translateX
node.translateY = vn.translateY
node.scaleX = vn.scaleX
node.scaleY = vn.scaleY
```

- [ ] **Step 4: 编译验证**

用 Xcode 或命令行编译 iOS SDK，确认无报错：
```bash
cd clients/ios && xcodebuild -scheme ClientToolsSDK -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -5
```
期望：`BUILD SUCCEEDED`

- [ ] **Step 5: Commit**

```bash
git add clients/ios/sdk/Sources/ViewQuery/
git commit -m "feat(ios): add transform fields to ViewNode and Node proto response"
```

---

## Task 3: iOS SDK — ViewModifyService 重构

**Files:**
- Modify: `clients/ios/sdk/Sources/ViewModify/ViewModifyService.swift`
- Modify: `clients/ios/sdk/Sources/ViewModify/FrameModifier.swift`
- Delete: `clients/ios/sdk/Sources/ViewModify/ConstraintModifier.swift`

- [ ] **Step 1: 新增 `modifyIosProto` 方法到 ViewModifyService**

完整替换 `clients/ios/sdk/Sources/ViewModify/ViewModifyService.swift`：

```swift
import UIKit

class ViewModifyService {

    private let viewQueryService = ViewQueryService()

    // Android: 保留现有实现不变
    func modifyProto(id: String, props: Clienttools_ViewProps) -> (Bool, String) {
        guard let view = viewQueryService.findView(byId: id) else { return (false, "View not found: \(id)") }
        let sema = DispatchSemaphore(value: 0)
        DispatchQueue.main.async {
            if props.hasMarginTopDiffDp {
                ConstraintModifier.modifyMarginTop(view, diffDp: CGFloat(props.marginTopDiffDp.value))
            }
            if props.hasMarginBottomDiffDp {
                ConstraintModifier.modifyMarginBottom(view, diffDp: CGFloat(props.marginBottomDiffDp.value))
            }
            if props.hasMarginLeftDiffDp {
                ConstraintModifier.modifyMarginLeading(view, diffDp: CGFloat(props.marginLeftDiffDp.value))
            }
            if props.hasMarginRightDiffDp {
                ConstraintModifier.modifyMarginTrailing(view, diffDp: CGFloat(props.marginRightDiffDp.value))
            }
            let hasPadding = props.hasPaddingTopDiffDp || props.hasPaddingBottomDiffDp ||
                             props.hasPaddingLeftDiffDp || props.hasPaddingRightDiffDp
            if hasPadding {
                let insets = UIEdgeInsets(
                    top: CGFloat(props.paddingTopDiffDp.value),
                    left: CGFloat(props.paddingLeftDiffDp.value),
                    bottom: CGFloat(props.paddingBottomDiffDp.value),
                    right: CGFloat(props.paddingRightDiffDp.value)
                )
                PaddingModifier.modifyPadding(view, insets: insets)
            }
            let widthStr = props.hasWidthDp ? props.widthDp.value : nil
            let heightStr = props.hasHeightDp ? props.heightDp.value : nil
            FrameModifier.modifyFrame(view, widthDp: widthStr, heightDp: heightStr)
            if let label = view as? UILabel {
                if props.hasLetterSpacingEm {
                    let em = CGFloat(props.letterSpacingEm.value)
                    let fontSize = label.font.pointSize
                    if var attrs = label.attributedText?.mutableCopy() as? NSMutableAttributedString {
                        attrs.addAttribute(.kern, value: em * fontSize, range: NSRange(location: 0, length: attrs.length))
                        label.attributedText = attrs
                    } else {
                        label.attributedText = NSAttributedString(string: label.text ?? "", attributes: [
                            .font: label.font as Any,
                            .foregroundColor: label.textColor as Any,
                            .kern: em * fontSize
                        ])
                    }
                }
                if props.hasLineSpacingExtraDp {
                    let extra = CGFloat(props.lineSpacingExtraDp.value)
                    let style = NSMutableParagraphStyle()
                    style.lineSpacing = extra
                    let base = label.attributedText?.mutableCopy() as? NSMutableAttributedString
                        ?? NSMutableAttributedString(string: label.text ?? "", attributes: [.font: label.font as Any, .foregroundColor: label.textColor as Any])
                    base.addAttribute(.paragraphStyle, value: style, range: NSRange(location: 0, length: base.length))
                    label.attributedText = base
                }
            }
            view.setNeedsLayout()
            view.layoutIfNeeded()
            sema.signal()
        }
        sema.wait()
        return (true, "")
    }

    // iOS: 新实现
    func modifyIosProto(id: String, props: Clienttools_IosViewProps) -> (Bool, String) {
        guard let view = viewQueryService.findView(byId: id) else { return (false, "View not found: \(id)") }

        // 类型断言：有 text 字段则要求必须是 UILabel
        if props.hasText {
            guard view is UILabel else {
                let typeName = type(of: view)
                return (false, "text props requires UILabel, but view '\(id)' is \(typeName)")
            }
        }

        let sema = DispatchSemaphore(value: 0)
        DispatchQueue.main.async {
            // 1. transform：anchorPoint=(0,0) + 重建 transform
            Self.applyTransform(to: view, props: props)

            // 2. 宽高
            if props.hasWidthDp {
                FrameModifier.setFixedDimension(view, attribute: .width, value: CGFloat(props.widthDp.value))
            }
            if props.hasHeightDp {
                FrameModifier.setFixedDimension(view, attribute: .height, value: CGFloat(props.heightDp.value))
            }

            // 3. padding
            let hasPadding = props.hasPaddingTopDiffDp || props.hasPaddingBottomDiffDp ||
                             props.hasPaddingLeftDiffDp || props.hasPaddingRightDiffDp
            if hasPadding {
                let insets = UIEdgeInsets(
                    top: CGFloat(props.paddingTopDiffDp.value),
                    left: CGFloat(props.paddingLeftDiffDp.value),
                    bottom: CGFloat(props.paddingBottomDiffDp.value),
                    right: CGFloat(props.paddingRightDiffDp.value)
                )
                PaddingModifier.modifyPadding(view, insets: insets)
            }

            // 4. 文字属性（已断言是 UILabel）
            if props.hasText, let label = view as? UILabel {
                Self.applyTextProps(to: label, text: props.text)
            }

            view.setNeedsLayout()
            view.layoutIfNeeded()
            sema.signal()
        }
        sema.wait()
        return (true, "")
    }

    private static func applyTransform(to view: UIView, props: Clienttools_IosViewProps) {
        // 每次都将 anchorPoint 设为 (0,0)，补偿 position 保持视觉位置不变
        let oldAnchor = view.layer.anchorPoint
        if oldAnchor != CGPoint(x: 0, y: 0) {
            let size = view.bounds.size
            view.layer.position = CGPoint(
                x: view.layer.position.x - oldAnchor.x * size.width,
                y: view.layer.position.y - oldAnchor.y * size.height
            )
            view.layer.anchorPoint = CGPoint(x: 0, y: 0)
        }

        // 从当前 transform 反解分量（假设无 rotation）
        let t = view.transform
        let currentTx = t.tx
        let currentTy = t.ty
        let currentSx = sqrt(t.a * t.a + t.c * t.c)
        let currentSy = sqrt(t.b * t.b + t.d * t.d)

        let newTx = props.hasTranslateXDp ? CGFloat(props.translateXDp.value) : currentTx
        let newTy = props.hasTranslateYDp ? CGFloat(props.translateYDp.value) : currentTy
        let newSx = props.hasScaleX ? CGFloat(props.scaleX.value) : (currentSx == 0 ? 1 : currentSx)
        let newSy = props.hasScaleY ? CGFloat(props.scaleY.value) : (currentSy == 0 ? 1 : currentSy)

        // 先 scale，再 translate：屏幕空间语义，translate 不受 scale 影响
        view.transform = CGAffineTransform(scaleX: newSx, y: newSy)
            .concatenating(CGAffineTransform(translationX: newTx, y: newTy))
    }

    private static func applyTextProps(to label: UILabel, text: Clienttools_IosTextProps) {
        // 替换文案
        if text.hasContent {
            label.text = text.content.value
        }

        // letterSpacing
        if text.hasLetterSpacingEm {
            let em = CGFloat(text.letterSpacingEm.value)
            let fontSize = label.font.pointSize
            if var attrs = label.attributedText?.mutableCopy() as? NSMutableAttributedString {
                attrs.addAttribute(.kern, value: em * fontSize, range: NSRange(location: 0, length: attrs.length))
                label.attributedText = attrs
            } else {
                label.attributedText = NSAttributedString(string: label.text ?? "", attributes: [
                    .font: label.font as Any,
                    .foregroundColor: label.textColor as Any,
                    .kern: em * fontSize
                ])
            }
        }

        // lineSpacing
        if text.hasLineSpacingExtraDp {
            let extra = CGFloat(text.lineSpacingExtraDp.value)
            let style = NSMutableParagraphStyle()
            style.lineSpacing = extra
            let base = label.attributedText?.mutableCopy() as? NSMutableAttributedString
                ?? NSMutableAttributedString(string: label.text ?? "", attributes: [
                    .font: label.font as Any,
                    .foregroundColor: label.textColor as Any
                ])
            base.addAttribute(.paragraphStyle, value: style, range: NSRange(location: 0, length: base.length))
            label.attributedText = base
        }
    }
}
```

- [ ] **Step 2: 简化 FrameModifier，暴露 `setFixedDimension`，删除 wrap_content**

完整替换 `clients/ios/sdk/Sources/ViewModify/FrameModifier.swift`：

```swift
import UIKit

class FrameModifier {

    // 供 Android modify 路径使用（保留数值解析）
    static func modifyFrame(_ view: UIView, widthDp: String?, heightDp: String?) {
        guard widthDp != nil || heightDp != nil else { return }
        if let widthStr = widthDp, let value = parseDp(widthStr) {
            setFixedDimension(view, attribute: .width, value: value)
        }
        if let heightStr = heightDp, let value = parseDp(heightStr) {
            setFixedDimension(view, attribute: .height, value: value)
        }
    }

    // iOS modify 路径直接调用，传 CGFloat
    static func setFixedDimension(_ view: UIView, attribute: NSLayoutConstraint.Attribute, value: CGFloat) {
        guard let superview = view.superview else { return }
        view.translatesAutoresizingMaskIntoConstraints = false
        let allConstraints = view.constraints + superview.constraints
        for c in allConstraints where c.firstAttribute == attribute && c.secondItem == nil
            && (c.firstItem as? UIView) === view {
            c.isActive = false
        }
        NSLayoutConstraint.activate([
            NSLayoutConstraint(item: view, attribute: attribute, relatedBy: .equal,
                               toItem: nil, attribute: .notAnAttribute,
                               multiplier: 1, constant: value)
        ])
    }

    private static func parseDp(_ str: String) -> CGFloat? {
        if str.hasSuffix("dp") {
            return Float(String(str.dropLast(2))).map { CGFloat($0) }
        }
        return Float(str).map { CGFloat($0) }
    }
}
```

- [ ] **Step 3: 删除 ConstraintModifier.swift**

```bash
rm clients/ios/sdk/Sources/ViewModify/ConstraintModifier.swift
```

注意：Android modify 路径（`modifyProto`）仍引用 `ConstraintModifier`，但 ConstraintModifier 只供 Android 路径的 iOS 旧逻辑使用，删除后需确认 `modifyProto` 中移除对它的调用——实际上 iOS 的 `/api/modify` 路由今后应重定向到 `/api/modify/android`（即 `modifyProto` 为 Android 专用），iOS 用新路由。Step 3 执行后，在 `ViewModifyService.swift` 的 `modifyProto` 方法中同步移除 `ConstraintModifier` 调用，改为不做 margin 操作（margin 在 iOS 新接口中已废弃）：

将 `modifyProto` 方法中以下 4 段移除：
```swift
if props.hasMarginTopDiffDp { ConstraintModifier.modifyMarginTop(...) }
if props.hasMarginBottomDiffDp { ConstraintModifier.modifyMarginBottom(...) }
if props.hasMarginLeftDiffDp { ConstraintModifier.modifyMarginLeading(...) }
if props.hasMarginRightDiffDp { ConstraintModifier.modifyMarginTrailing(...) }
```

- [ ] **Step 4: 编译验证**

```bash
cd clients/ios && xcodebuild -scheme ClientToolsSDK -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -5
```
期望：`BUILD SUCCEEDED`

- [ ] **Step 5: Commit**

```bash
git add clients/ios/sdk/Sources/ViewModify/
git commit -m "feat(ios): refactor modify_view to use transform; remove ConstraintModifier; simplify FrameModifier"
```

---

## Task 4: iOS SDK — HttpServer 新增 /api/modify/ios 路由

**Files:**
- Modify: `clients/ios/sdk/Sources/HttpServer/HttpServer.swift`

- [ ] **Step 1: 新增路由 case**

在 `HttpServer.swift` 的 switch 中，`case ("POST", "/api/modify"):` 之后追加：

```swift
case ("POST", "/api/modify/ios"):
    handleModifyIos(bodyData, connection: connection)
```

- [ ] **Step 2: 新增 handleModifyIos 方法**

在 `handleModify` 方法之后追加：

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

- [ ] **Step 3: 同步更新 handleModify（Android 路径）返回值**

`handleModify` 目前调用 `modifyProto` 返回 `Bool`，已改为返回 `(Bool, String)`，更新如下：

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

- [ ] **Step 4: 编译验证**

```bash
cd clients/ios && xcodebuild -scheme ClientToolsSDK -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -5
```
期望：`BUILD SUCCEEDED`

- [ ] **Step 5: Commit**

```bash
git add clients/ios/sdk/Sources/HttpServer/HttpServer.swift
git commit -m "feat(ios): add /api/modify/ios route"
```

---

## Task 5: MCP — 拆分 modify_view 为 modify_view_android 和 modify_view_ios

**Files:**
- Modify: `mcp/src/tools/view.ts`
- Modify: `mcp/src/generated/modify_pb.ts`（由 buf generate 生成，不手动改）

- [ ] **Step 1: 确认生成代码包含新类型**

```bash
grep -l "ModifyViewIosRequest\|IosViewProps" mcp/src/generated/modify_pb.ts
```
期望：输出文件名（说明 Task 1 的 buf generate 已生效）

- [ ] **Step 2: 替换 `mcp/src/tools/view.ts` 中的 modify_view 工具**

找到并删除现有 `modify_view` 工具注册（`server.tool("modify_view", ...)`），替换为以下两个工具：

```typescript
// ===== modify_view_android =====
const AndroidViewPropsZod = z.object({
  marginTopDiffDp: z.number().optional(),
  marginBottomDiffDp: z.number().optional(),
  marginLeftDiffDp: z.number().optional(),
  marginRightDiffDp: z.number().optional(),
  paddingTopDiffDp: z.number().optional(),
  paddingBottomDiffDp: z.number().optional(),
  paddingLeftDiffDp: z.number().optional(),
  paddingRightDiffDp: z.number().optional(),
  widthDp: z.union([z.number(), z.literal("wrap_content")]).optional(),
  heightDp: z.union([z.number(), z.literal("wrap_content")]).optional(),
  letterSpacingEm: z.number().optional(),
  lineSpacingExtraDp: z.number().optional(),
  includeFontPadding: z.boolean().optional(),
}).describe("Android View 布局属性，margin/padding 为差值（dp），width/height 为绝对值（dp）或 \"wrap_content\"");

server.tool(
  "modify_view_android",
  "修改 Android View 的布局属性（margin/padding/size），单位 dp；TextView 额外支持 letterSpacingEm、lineSpacingExtraDp、includeFontPadding",
  { id: z.string().describe("Android View 的 resource id"), props: AndroidViewPropsZod },
  async ({ id, props }) => {
    try {
      const viewProps = {
        ...(props.marginTopDiffDp !== undefined && { marginTopDiffDp: props.marginTopDiffDp }),
        ...(props.marginBottomDiffDp !== undefined && { marginBottomDiffDp: props.marginBottomDiffDp }),
        ...(props.marginLeftDiffDp !== undefined && { marginLeftDiffDp: props.marginLeftDiffDp }),
        ...(props.marginRightDiffDp !== undefined && { marginRightDiffDp: props.marginRightDiffDp }),
        ...(props.paddingTopDiffDp !== undefined && { paddingTopDiffDp: props.paddingTopDiffDp }),
        ...(props.paddingBottomDiffDp !== undefined && { paddingBottomDiffDp: props.paddingBottomDiffDp }),
        ...(props.paddingLeftDiffDp !== undefined && { paddingLeftDiffDp: props.paddingLeftDiffDp }),
        ...(props.paddingRightDiffDp !== undefined && { paddingRightDiffDp: props.paddingRightDiffDp }),
        ...(props.widthDp !== undefined && { widthDp: String(props.widthDp) }),
        ...(props.heightDp !== undefined && { heightDp: String(props.heightDp) }),
        ...(props.letterSpacingEm !== undefined && { letterSpacingEm: props.letterSpacingEm }),
        ...(props.lineSpacingExtraDp !== undefined && { lineSpacingExtraDp: props.lineSpacingExtraDp }),
        ...(props.includeFontPadding !== undefined && { includeFontPadding: props.includeFontPadding }),
      };
      const req = create(ModifyViewRequestSchema, { id, props: viewProps });
      await sdkPost("/api/modify", ModifyViewRequestSchema, req, ModifyResponseSchema);
      return { content: [{ type: "text" as const, text: "ok" }] };
    } catch (e) { return errResult(e); }
  }
);

// ===== modify_view_ios =====
const IosTextPropsZod = z.object({
  content: z.string().optional().describe("替换 UILabel 文案内容"),
  letterSpacingEm: z.number().optional().describe("字间距，单位 em"),
  lineSpacingExtraDp: z.number().optional().describe("额外行间距，单位 dp"),
}).describe("文字属性（传此对象则断言 view 为 UILabel，否则整个请求失败）");

const IosViewPropsZod = z.object({
  translateXDp: z.number().optional().describe("X 轴位移绝对值（dp），屏幕空间，不受 scale 影响"),
  translateYDp: z.number().optional().describe("Y 轴位移绝对值（dp），屏幕空间，不受 scale 影响"),
  scaleX: z.number().optional().describe("X 轴缩放绝对值，1.0 为原始大小"),
  scaleY: z.number().optional().describe("Y 轴缩放绝对值，1.0 为原始大小"),
  widthDp: z.number().optional().describe("宽度绝对值（dp）"),
  heightDp: z.number().optional().describe("高度绝对值（dp）"),
  paddingTopDiffDp: z.number().optional(),
  paddingBottomDiffDp: z.number().optional(),
  paddingLeftDiffDp: z.number().optional(),
  paddingRightDiffDp: z.number().optional(),
  text: IosTextPropsZod.optional(),
}).describe("iOS View 属性");

server.tool(
  "modify_view_ios",
  "修改 iOS UIView 的 transform（位移/缩放）、尺寸、padding；传 text 字段则断言为 UILabel 并修改文字属性",
  { id: z.string().describe("iOS View 的 accessibilityIdentifier"), props: IosViewPropsZod },
  async ({ id, props }) => {
    try {
      const textProps = props.text ? {
        ...(props.text.content !== undefined && { content: props.text.content }),
        ...(props.text.letterSpacingEm !== undefined && { letterSpacingEm: props.text.letterSpacingEm }),
        ...(props.text.lineSpacingExtraDp !== undefined && { lineSpacingExtraDp: props.text.lineSpacingExtraDp }),
      } : undefined;

      const iosProps = {
        ...(props.translateXDp !== undefined && { translateXDp: props.translateXDp }),
        ...(props.translateYDp !== undefined && { translateYDp: props.translateYDp }),
        ...(props.scaleX !== undefined && { scaleX: props.scaleX }),
        ...(props.scaleY !== undefined && { scaleY: props.scaleY }),
        ...(props.widthDp !== undefined && { widthDp: props.widthDp }),
        ...(props.heightDp !== undefined && { heightDp: props.heightDp }),
        ...(props.paddingTopDiffDp !== undefined && { paddingTopDiffDp: props.paddingTopDiffDp }),
        ...(props.paddingBottomDiffDp !== undefined && { paddingBottomDiffDp: props.paddingBottomDiffDp }),
        ...(props.paddingLeftDiffDp !== undefined && { paddingLeftDiffDp: props.paddingLeftDiffDp }),
        ...(props.paddingRightDiffDp !== undefined && { paddingRightDiffDp: props.paddingRightDiffDp }),
        ...(textProps && { text: textProps }),
      };
      const req = create(ModifyViewIosRequestSchema, { id, props: iosProps });
      const res = await sdkPost("/api/modify/ios", ModifyViewIosRequestSchema, req, ModifyResponseSchema);
      const msg = res.message ? res.message : "ok";
      return { content: [{ type: "text" as const, text: msg }] };
    } catch (e) { return errResult(e); }
  }
);
```

- [ ] **Step 3: 更新 import，加入新生成的 Schema**

在 `view.ts` 顶部 import 中，追加：

```typescript
import { ModifyViewIosRequestSchema } from "../generated/modify_pb.js";
```

并删除 `DpValue` 和旧的 `ViewPropsZod` 常量（已被 `AndroidViewPropsZod` 替代）。

- [ ] **Step 4: 编译 MCP**

```bash
cd mcp && npm run build 2>&1 | tail -10
```
期望：无 TypeScript 错误

- [ ] **Step 5: Commit**

```bash
git add mcp/src/tools/view.ts
git commit -m "feat(mcp): split modify_view into modify_view_android and modify_view_ios"
```

---

## Task 6: 端到端验证

- [ ] **Step 1: 重启 iOS App 到 login 页面，重启 MCP**

- [ ] **Step 2: 测试 get_all_nodes 返回 transform 字段**

调用 `get_all_nodes`，检查返回的节点中包含 `translateX`、`translateY`、`scaleX`、`scaleY` 字段，初始值应为 `translateX=0, translateY=0, scaleX=1, scaleY=1`。

- [ ] **Step 3: 测试 translateY（位移不破坏约束）**

```
modify_view_ios(id="login_btn_submit", props={ translateYDp: -20 })
get_all_nodes → login_btn_submit.screenY 应减少约 20
modify_view_ios(id="login_btn_submit", props={ translateYDp: 0 })
get_all_nodes → login_btn_submit.screenY 恢复原值
```

- [ ] **Step 4: 测试 scaleX/scaleY**

```
modify_view_ios(id="login_btn_submit", props={ scaleX: 0.8, scaleY: 0.8 })
视觉上按钮缩小，get_all_nodes 中 scaleX=0.8, scaleY=0.8
modify_view_ios(id="login_btn_submit", props={ scaleX: 1.0, scaleY: 1.0 })
恢复原始大小
```

- [ ] **Step 5: 测试 translate + scale 独立性**

```
modify_view_ios(id="login_btn_submit", props={ translateYDp: -20, scaleX: 0.8 })
再调用 modify_view_ios(id="login_btn_submit", props={ scaleX: 1.0 })
→ translateY 应保持 -20，scaleX 恢复 1.0
```

- [ ] **Step 6: 测试 text 类型断言**

```
modify_view_ios(id="login_btn_submit", props={ text: { content: "测试" } })
→ 应返回错误 message，包含"UILabel"字样，视图无任何变化

modify_view_ios(id="login_text_title", props={ text: { content: "新标题" } })
→ 应成功，UILabel 文案变为"新标题"
```

- [ ] **Step 7: 测试 bottom-anchored 视图位移**

```
modify_view_ios(id="login_social_container", props={ translateYDp: -94 })
→ 视图整体上移 94dp，约束不被破坏，高度不变（区别于之前 margin 实现会拉伸高度）
get_all_nodes → login_social_container.heightDp 与修改前相同
modify_view_ios(id="login_social_container", props={ translateYDp: 0 })
→ 恢复原位，高度仍不变
```

- [ ] **Step 8: 测试 widthDp/heightDp**

```
记录初始值：get_all_nodes → login_input_phone_container.widthDp（应为 327）
modify_view_ios(id="login_input_phone_container", props={ widthDp: 200 })
get_all_nodes → login_input_phone_container.widthDp 应为 200
modify_view_ios(id="login_input_phone_container", props={ widthDp: 327 })
get_all_nodes → 恢复 327
```

- [ ] **Step 9: 测试 widthDp 与 translateX 组合（互不干扰）**

```
modify_view_ios(id="login_input_phone_container", props={ widthDp: 200, translateXDp: 24 })
get_all_nodes → widthDp=200，screenX 相对原值增加 24
modify_view_ios(id="login_input_phone_container", props={ widthDp: 327, translateXDp: 0 })
→ 恢复
```

- [ ] **Step 10: 测试 text.content + letterSpacingEm 组合**

```
modify_view_ios(id="login_text_title", props={ text: { content: "欢迎回来，测试文案较长看省略", letterSpacingEm: 0.05 } })
→ 文案替换，字间距增大，视觉可见
modify_view_ios(id="login_text_title", props={ text: { content: "欢迎回来" } })
→ 文案恢复
```

- [ ] **Step 11: 测试 text 断言失败时其他属性也不生效**

```
记录初始值：get_all_nodes → login_btn_submit.screenY
modify_view_ios(id="login_btn_submit", props={ translateYDp: -30, text: { content: "测试" } })
→ 应返回错误，message 包含"UILabel"
get_all_nodes → login_btn_submit.screenY 与初始值相同（translateYDp 也没有生效）
```

- [ ] **Step 12: 测试幂等性（多次相同调用结果一致）**

```
modify_view_ios(id="login_btn_submit", props={ translateYDp: -20 })
modify_view_ios(id="login_btn_submit", props={ translateYDp: -20 })
modify_view_ios(id="login_btn_submit", props={ translateYDp: -20 })
get_all_nodes → login_btn_submit.screenY 减少约 20（不是 60）
```

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "test(ios): verify modify_view_ios end-to-end"
```

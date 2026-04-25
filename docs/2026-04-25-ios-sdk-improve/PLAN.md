# iOS SDK View 修改能力改进 - Implementation Plan

**日期**：2026-04-25
**基于**：SPEC.md
**目标**：提升 iOS SDK 的 view 修改能力，对齐 Android

---

## 技术背景

### iOS Auto Layout 约束系统
- **约束优先级高于 frame**：设置 frame 后，约束会在下次布局时覆盖
- **约束查找**：约束可能加在 view 上（内部），也可能加在 superview 上（外部）
- **isActive**：约束可以动态激活/禁用

### Lookin 参考实现
- 修改 `constraint.constant`
- 找不到约束时动态添加
- 需要修改 frame 时禁用约束系统

---

## 项目结构

```
packages/ios/sdk/Sources/
├── ViewModify/
│   ├── ConstraintModifier.swift   # [改进] 支持添加约束
│   ├── FrameModifier.swift        # [改进] 支持禁用/恢复约束
│   └── ViewModifyService.swift   # [改进] 智能判断处理方式
```

---

## Phase 1：ConstraintModifier 改进

### Task 1.1：添加约束查找增强

**文件**：`ConstraintModifier.swift`

**改进内容**：
```swift
// 新增方法
static func modifyMarginTop(_ view: UIView, diffDp: CGFloat) {
    let scale = UIScreen.main.scale
    let diff = diffDp * scale
    
    // 1. 优先查找 superview 上的外部约束
    if let superview = view.superview {
        if let constraint = superview.constraints.first(where: {
            $0.firstItem === view && $0.firstAttribute == .top
        }) {
            constraint.constant += diff
            return
        }
    }
    
    // 2. 查找内部约束（宽高）
    if let constraint = view.constraints.first(where: { $0.firstAttribute == .top }) {
        constraint.constant += diff
        return
    }
    
    // 3. 找不到则添加新约束
    addTopConstraint(to: view, constant: diff)
}

// 新增方法：添加 top 约束
private static func addTopConstraint(to view: UIView, constant: CGFloat) {
    guard let superview = view.superview else { return }
    
    let constraint = NSLayoutConstraint(
        item: view,
        attribute: .top,
        relatedBy: .equal,
        toItem: superview,
        attribute: .top,
        multiplier: 1,
        constant: constant
    )
    constraint.isActive = true
    superview.addConstraint(constraint)
}
```

### Task 1.2：添加其他 margin 方向

**方向**：top, bottom, leading, trailing

**类比 Task 1.1**，为每个方向实现：
- `modifyMarginBottom`
- `modifyMarginLeading`
- `modifyMarginTrailing`

---

## Phase 2：FrameModifier 改进

### Task 2.1：约束状态管理

**文件**：`FrameModifier.swift`

**新增属性**：
```swift
// view id -> 保存的约束列表
private static var savedConstraints: [String: [NSLayoutConstraint]] = [:]

// view id -> 是否处于 frame 模式
private static var frameModeViews: Set<String> = []
```

### Task 2.2：启用 Frame 模式

```swift
/// 启用 frame 模式（禁用约束）
static func enableFrameMode(_ view: UIView) -> Bool {
    let viewId = ViewHashGenerator.generateId(for: view)
    
    // 已经在 frame 模式
    if frameModeViews.contains(viewId) {
        return true
    }
    
    // 保存所有相关约束
    var allConstraints: [NSLayoutConstraint] = []
    if let superview = view.superview {
        allConstraints.append(contentsOf: superview.constraints.filter { $0.firstItem === view })
    }
    allConstraints.append(contentsOf: view.constraints)
    savedConstraints[viewId] = allConstraints
    
    // 禁用约束
    NSLayoutConstraint.deactivate(allConstraints)
    frameModeViews.insert(viewId)
    
    // 启用 autoresizing mask
    view.translatesAutoresizingMaskIntoConstraints = true
    
    return true
}
```

### Task 2.3：禁用 Frame 模式

```swift
/// 禁用 frame 模式（恢复约束）
static func disableFrameMode(_ view: UIView) -> Bool {
    let viewId = ViewHashGenerator.generateId(for: view)
    
    guard frameModeViews.contains(viewId) else {
        return true  // 本来就不在 frame 模式
    }
    
    // 恢复约束
    if let constraints = savedConstraints[viewId] {
        NSLayoutConstraint.activate(constraints)
        savedConstraints.removeValue(forKey: viewId)
    }
    
    frameModeViews.remove(viewId)
    view.translatesAutoresizingMaskIntoConstraints = false
    
    return true
}
```

### Task 2.4：Frame 修改逻辑

```swift
static func modifyFrame(_ view: UIView, widthDp: String?, heightDp: String?) {
    let scale = CGFloat(UIScreen.main.scale)
    
    // 判断是否需要启用 frame 模式
    let hasConstraints = !view.constraints.isEmpty || 
        (view.superview?.constraints.contains(where: { $0.firstItem === view }) ?? false)
    
    if hasConstraints && !isInFrameMode(view) {
        enableFrameMode(view)
    }
    
    // 直接修改 frame
    if let widthStr = widthDp {
        if widthStr == "wrap_content" {
            view.sizeToFit()
        } else if let width = parseDp(widthStr) {
            view.frame.size.width = width * scale
        }
    }
    
    if let heightStr = heightDp {
        if heightStr == "wrap_content" {
            view.sizeToFit()
        } else if let height = parseDp(heightStr) {
            view.frame.size.height = height * scale
        }
    }
}
```

---

## Phase 3：ViewModifyService 集成

### Task 3.1：统一处理入口

**文件**：`ViewModifyService.swift`

**改进内容**：
```swift
func modify(id: String, props: ModifyProps) -> Bool {
    guard let view = viewQueryService.findView(byId: id) else {
        return false
    }
    
    let scale = CGFloat(UIScreen.main.scale)
    
    // margin 修改
    if let top = props.marginTopDiffDp {
        ConstraintModifier.modifyMarginTop(view, diffDp: top)
    }
    if let bottom = props.marginBottomDiffDp {
        ConstraintModifier.modifyMarginBottom(view, diffDp: bottom)
    }
    if let leading = props.marginLeftDiffDp {
        ConstraintModifier.modifyMarginLeading(view, diffDp: leading)
    }
    if let trailing = props.marginRightDiffDp {
        ConstraintModifier.modifyMarginTrailing(view, diffDp: trailing)
    }
    
    // padding 修改
    let hasPadding = props.paddingTopDiffDp != nil || props.paddingBottomDiffDp != nil ||
                     props.paddingLeftDiffDp != nil || props.paddingRightDiffDp != nil
    if hasPadding {
        let insets = UIEdgeInsets(
            top: (props.paddingTopDiffDp ?? 0) * scale,
            left: (props.paddingLeftDiffDp ?? 0) * scale,
            bottom: (props.paddingBottomDiffDp ?? 0) * scale,
            right: (props.paddingRightDiffDp ?? 0) * scale
        )
        PaddingModifier.modifyPadding(view, insets: insets)
    }
    
    // frame 修改
    let hasFrame = props.widthDp != nil || props.heightDp != nil
    if hasFrame {
        FrameModifier.modifyFrame(view, widthDp: props.widthDp, heightDp: props.heightDp)
    }
    
    // 触发布局更新
    view.setNeedsLayout()
    view.layoutIfNeeded()
    
    return true
}
```

---

## Phase 4：验证测试

### Task 4.1：编译验证

```bash
cd ~/Desktop/works/client-tools/packages/ios/demo
xcodebuild -workspace ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  build
```

### Task 4.2：功能测试

**测试用例**：

| 用例 | 操作 | 预期结果 |
|------|------|----------|
| 修改有约束的 view margin | 调用 modify marginTopDiffDp=10 | 找到约束，修改 constant |
| 修改无约束的 view margin | 调用 modify marginTopDiffDp=10 | 自动添加新约束 |
| 修改有约束的 view frame | 调用 modify widthDp=200dp | 自动禁用约束，修改 frame |
| 连续修改 frame | 多次调用 modify widthDp | 保持在 frame 模式 |

---

## 执行顺序

```
Phase 1：ConstraintModifier 改进
  Task 1.1 → 1.2
        ↓
Phase 2：FrameModifier 改进
  Task 2.1 → 2.2 → 2.3 → 2.4
        ↓
Phase 3：ViewModifyService 集成
  Task 3.1
        ↓
Phase 4：验证测试
  Task 4.1 → 4.2
```

---

## 预计工作量

| Phase | 任务数 | 复杂度 | 时间 |
|-------|--------|--------|------|
| ConstraintModifier | 2 | ⭐ | 2小时 |
| FrameModifier | 4 | ⭐⭐ | 3小时 |
| ViewModifyService | 1 | ⭐ | 1小时 |
| 验证测试 | 2 | ⭐ | 1小时 |
| **总计** | 9 | | **7小时** |

---

## 风险与注意事项

1. **约束冲突**：添加新约束时可能与现有约束冲突
2. **约束泄漏**：约束保存后需确保能正确恢复
3. **内存管理**：savedConstraints 字典需要考虑清理
4. **第三方控件**：可能有特殊的约束处理逻辑

---

## 验证方式

```bash
# 编译成功
xcodebuild ... build  # BUILD SUCCEEDED

# API 测试（需启动 App 后）
curl -X POST http://localhost:8080/api/modify \
  -H "Content-Type: application/json" \
  -d '{"id":"view_id","props":{"marginTopDiffDp":10}}'
```

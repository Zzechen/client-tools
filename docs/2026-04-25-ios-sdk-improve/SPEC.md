# iOS SDK View 修改能力改进方案

**日期**：2026-04-25
**目标**：对齐 Android 的 view 修改能力，减少 iOS/Auto Layout 限制

---

## 背景

当前 iOS SDK 的 View 修改能力受限于 Auto Layout 约束系统：

| 能力 | Android | iOS (当前) |
|------|---------|------------|
| margin | ✅ 直接改 | ⚠️ 需约束存在 |
| padding | ✅ 直接改 | ⚠️ 仅部分控件 |
| frame | ✅ 直接改 | ⚠️ 被约束覆盖 |

**问题**：iOS 的约束系统比 Android 复杂，修改 view 需要考虑约束的存在与否。

---

## 改进策略

参考 Lookin 的实现方式，采用**渐进式修改**策略：

### 策略一：约束优先

当需要修改 margin 时：
1. 查找现有约束 → 修改 `constant`
2. 找不到约束 → **动态添加新约束**

### 策略二：约束绕过（frame 专用）

当需要修改 frame 时：
1. 如果约束存在 → **禁用约束**
2. 设置 `translatesAutoresizingMaskIntoConstraints = true`
3. 直接修改 frame
4. 恢复约束系统

---

## API 设计

**沿用现有 `/api/modify` API**，内部自动处理约束逻辑：

```json
POST /api/modify
{
  "id": "view_id",
  "props": {
    "marginTopDiffDp": 10,
    "widthDp": "200dp",
    "heightDp": "100dp"
  }
}
```

### SDK 内部自动处理逻辑

#### margin 修改
```
1. 查找 view.superview 上 attribute = .top 的约束
2. 如果找到 → 修改 constraint.constant += diff
3. 如果找不到 → 动态添加新约束
```

#### frame 修改
```
1. 判断 view 是否使用约束系统
2. 如果有约束 → 保存约束 → 禁用 → 修改 frame
3. 如果无约束 → 直接修改 frame
```

**注意**：约束**不自动恢复**，由用户下次操作时决定如何处理。

---

## 技术实现

### ConstraintModifier 改进

```swift
class ConstraintModifier {
    
    /// 修改 margin，如果约束不存在则添加
    static func modifyMargin(_ view: UIView, attribute: NSLayoutConstraint.Attribute, diff: CGFloat) {
        let scale = UIScreen.main.scale
        
        // 1. 查找现有约束
        if let existingConstraint = findConstraint(view: view, attribute: attribute) {
            existingConstraint.constant += diff * scale
            return
        }
        
        // 2. 找不到则添加新约束
        addConstraint(view: view, attribute: attribute, constant: diff * scale)
    }
    
    /// 查找约束
    private static func findConstraint(view: UIView, attribute: NSLayoutConstraint.Attribute) -> NSLayoutConstraint? {
        // 优先查找 superview 上的外部约束
        if let superview = view.superview {
            if let c = superview.constraints.first(where: {
                $0.firstItem === view && $0.firstAttribute == attribute
            }) {
                return c
            }
        }
        // 其次查找内部约束（宽高）
        return view.constraints.first { $0.firstAttribute == attribute }
    }
    
    /// 添加新约束
    private static func addConstraint(view: UIView, attribute: NSLayoutConstraint.Attribute, constant: CGFloat) {
        guard let superview = view.superview else { return }
        
        var relatedAttribute: NSLayoutConstraint.Attribute = .notAnAttribute
        var toItem: Any? = superview
        
        switch attribute {
        case .top, .bottom, .leading, .trailing:
            relatedAttribute = attribute
        case .width, .height:
            relatedAttribute = attribute
            toItem = nil  // 内部约束不需要 toItem
        default:
            return
        }
        
        let constraint = NSLayoutConstraint(
            item: view,
            attribute: attribute,
            relatedBy: .equal,
            toItem: toItem,
            attribute: relatedAttribute == .notAnAttribute ? attribute : relatedAttribute,
            multiplier: 1,
            constant: constant
        )
        constraint.isActive = true
        superview.addConstraint(constraint)
    }
}
```

### FrameModifier 改进

```swift
class FrameModifier {
    
    private static var savedConstraints: [String: [NSLayoutConstraint]] = [:]  // view id -> constraints
    
    /// 启用 frame 模式（禁用约束）
    static func enableFrameMode(_ view: UIView) -> Bool {
        let viewId = ViewHashGenerator.generateId(for: view)
        
        // 保存现有约束
        var allConstraints: [NSLayoutConstraint] = []
        if let superview = view.superview {
            allConstraints.append(contentsOf: superview.constraints.filter { $0.firstItem === view })
        }
        allConstraints.append(contentsOf: view.constraints)
        savedConstraints[viewId] = allConstraints
        
        // 禁用约束
        NSLayoutConstraint.deactivate(allConstraints)
        
        // 启用 autoresizing mask
        view.translatesAutoresizingMaskIntoConstraints = true
        
        return true
    }
    
    /// 禁用 frame 模式（恢复约束）
    static func disableFrameMode(_ view: UIView) -> Bool {
        let viewId = ViewHashGenerator.generateId(for: view)
        
        // 恢复约束
        if let constraints = savedConstraints[viewId] {
            NSLayoutConstraint.activate(constraints)
            savedConstraints.removeValue(forKey: viewId)
        }
        
        view.translatesAutoresizingMaskIntoConstraints = false
        
        return true
    }
    
    /// 修改 frame
    static func modifyFrame(_ view: UIView, widthDp: String?, heightDp: String?) {
        let scale = CGFloat(UIScreen.main.scale)
        
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
    
    private static func parseDp(_ str: String) -> CGFloat? {
        // 支持 "100dp" 格式
        if str.hasSuffix("dp") {
            let numStr = String(str.dropLast(2))
            return CGFloat(numStr)
        }
        return CGFloat(str)
    }
}
```

---

## 风险与限制

| 风险 | 描述 | 缓解措施 |
|------|------|----------|
| 约束冲突 | 添加新约束可能与现有约束冲突 | 添加前检查，优先修改现有约束 |
| 约束泄漏 | 删除约束后可能无法完全恢复 | 使用 deep copy 保存约束 |
| 性能 | 频繁禁用/启用约束可能影响性能 | 建议批量操作后统一恢复 |
| 第三方控件 | 可能有自定义约束逻辑 | 添加 try-catch，失败时返回错误 |

---

## 测试用例

### 1. margin 修改测试
- ✅ 现有约束存在 → 修改 constant
- ✅ 约束不存在 → 添加新约束
- ✅ 多个约束同时修改

### 2. frame 模式测试
- ✅ enable → 修改 frame → disable
- ✅ 连续多次 enable（应该报错或自动处理）
- ✅ 约束恢复后 view 是否正确布局

### 3. 边界测试
- ✅ view 没有 superview
- ✅ 已经是 autoresizing mask 模式
- ✅ 约束修改后 layoutIfNeeded

---

## 工作量估算

| 功能 | 复杂度 | 预估 |
|------|--------|------|
| ConstraintModifier 改进（添加约束） | ⭐ | 2小时 |
| FrameModifier 改进（禁用/恢复约束） | ⭐⭐ | 3小时 |
| 测试验证 | ⭐⭐ | 2小时 |
| **总计** | | **1天** |

---

## 优先级

1. **P0**：ConstraintModifier 支持添加约束（核心能力）
2. **P1**：FrameModifier 支持禁用/恢复约束
3. **P2**：完善测试和错误处理

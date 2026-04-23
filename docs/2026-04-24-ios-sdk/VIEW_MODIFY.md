# iOS SDK — View 修改设计

**日期**：2026-04-24

---

## 一、修改类型

| 修改项 | 实现方式 | 代码示例 |
|--------|---------|---------|
| frame | 直接赋值 | `view.frame = newFrame` |
| bounds | 直接赋值 | `view.bounds = newBounds` |
| center | 直接赋值 | `view.center = newCenter` |
| alpha | 直接赋值 | `view.alpha = 0.5` |
| isHidden | 直接赋值 | `view.isHidden = true` |
| margin | constraint.constant | 遍历找到约束，改 constant |
| padding | contentEdgeInsets | 仅 UITextField/UITextView/UButton |

---

## 二、margin 修改

iOS 的 margin 通过 Auto Layout 约束实现。SDK 通过遍历 `superview.constraints` 找到相关约束：

```swift
func modifyMargin(view: UIView, attribute: NSLayoutConstraint.Attribute, constant: CGFloat) {
    guard let superview = view.superview else { return }

    // 找到约束：superview.attr == view.attr * multiplier + constant
    if let constraint = superview.constraints.first(where: {
        $0.firstItem as? UIView === view && $0.firstAttribute == attribute
    }) {
        constraint.constant = constant
    }
}
```

### 2.1 约束属性映射

| margin 方向 | NSLayoutConstraint.Attribute |
|------------|---------------------------|
| marginTop | `.top` |
| marginBottom | `.bottom` |
| marginLeft | `.leading` / `.left` |
| marginRight | `.trailing` / `.right` |

---

## 三、padding 修改

仅部分控件支持：

| 控件 | 属性 | 说明 |
|------|------|------|
| UITextField | `contentEdgeInsets` | |
| UITextView | `textContainerInset` | |
| UIButton | `contentEdgeInsets` | |
| UILabel | ❌ | 无内置 padding |

```swift
func modifyPadding(view: UIView, insets: UIEdgeInsets) {
    switch view {
    case let textField as UITextField:
        textField.contentEdgeInsets = insets
    case let textView as UITextView:
        textView.textContainerInset = insets
    case let button as UIButton:
        button.contentEdgeInsets = insets
    default:
        break
    }
}
```

---

## 四、修改请求体

与 Android 一致：

```json
POST /api/modify
{
  "id": "login_title",
  "props": {
    "marginTopDiffDp": 8,
    "paddingLeftDiffDp": 16,
    "widthDp": 240
  }
}
```

---

## 五、与其他平台的对比

| 修改类型 | Android | iOS UIKit | 说明 |
|----------|---------|-----------|------|
| x/y | ✅ | ✅ | frame.origin |
| width/height | ✅ | ✅ | frame.size |
| margin | ✅ LayoutParams | ✅ constraint.constant | 效果相同 |
| padding | ✅ setPadding() | ⚠️ contentEdgeInsets | 仅部分控件 |

iOS margin 修改的实现路径与 Android 不同，但最终视觉效果完全一致。

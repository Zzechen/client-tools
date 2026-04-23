# iOS SDK — View 查询设计

**日期**：2026-04-24

---

## 一、ID 生成策略

**优先级**：
1. `accessibilityIdentifier`（宿主设置，语义化）
2. Runtime Hash（自动生成兜底）

```swift
func generateViewId(_ view: UIView, path: String = "") -> String {
    // 优先使用 accessibilityIdentifier
    if let identifier = view.accessibilityIdentifier, !identifier.isEmpty {
        return identifier
    }
    // 降级：Runtime Hash
    let className = String(describing: type(of: view))
    let address = String(format: "%p", view)
    let hash = "\(className)_\(address)"
    if path.isEmpty {
        return hash
    }
    return "\(hash)_\(path)"
}
```

---

## 二、遍历方式

从 `UIWindow` 的 rootViewController 开始，递归遍历所有 subviews：

```swift
func traverseView(_ view: UIView, path: String = "") -> [ViewNode] {
    var nodes: [ViewNode] = []

    for (index, subview) in view.subviews.enumerated() {
        let childPath = path.isEmpty ? "\(index)" : "\(path).\(index)"
        let viewId = generateViewId(subview, path: childPath)
        let node = ViewNode(
            id: viewId,
            type: mapViewType(subview),
            screenX: subview.frame.origin.x,
            screenY: subview.frame.origin.y,
            widthDp: subview.frame.width,
            heightDp: subview.frame.height,
            attrs: queryStyleAttributes(subview)
        )
        nodes.append(node)
        nodes.append(contentsOf: traverseView(subview, path: childPath))
    }
    return nodes
}
```

---

## 三、View 类型映射

| UIKit 控件 | type | 说明 |
|------------|------|------|
| UILabel | TEXT | |
| UITextField | TEXT | |
| UITextView | TEXT | |
| UIButton | TEXT | |
| UIImageView | IMAGE | |
| UITableView | LIST | |
| UICollectionView | LIST | |
| UIView | CONTAINER | |
| 其他 | CONTAINER | |

---

## 四、样式属性查询

### 4.1 支持的属性

| 属性 | 控件 | 获取方式 |
|------|------|---------|
| font | UILabel, UITextField | `label.font.pointSize` |
| textColor | UILabel, UITextField | `label.textColor` (转 hex) |
| text | UILabel, UITextField | `label.text` |
| backgroundColor | 所有 UIView | `view.backgroundColor` |
| alpha | 所有 UIView | `view.alpha` |
| image | UIImageView | `imageView.image` |

### 4.2 数据转换

```swift
func queryStyleAttributes(_ view: UIView) -> NodeAttrs? {
    switch view {
    case let label as UILabel:
        let font = label.font
        let color = label.textColor
        return TextAttrs(
            fontSize: font?.pointSize ?? 0,
            color: color.toHex(),
            fontWeight: font.fontDescriptor.fontAttributes[.traits]
        )
    case let imageView as UIImageView:
        return ImageAttrs(scaleType: "\(imageView.contentMode)")
    default:
        return nil
    }
}
```

---

## 五、数据结构（KMP 共享）

与 Android 共用同一套数据结构：

```swift
// packages/shared
data class Node(
    val id: String,
    val type: NodeType,
    val screenX: Float,
    val screenY: Float,
    val widthDp: Float,
    val heightDp: Float,
    val attrs: NodeAttrs?
)
```

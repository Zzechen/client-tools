# Click Touch Event Design

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将双端 `click_view` 的实现从 listener 触发改为注入完整触摸事件，使点击行为与真实手指触屏等价，不依赖业务是否设置了 click listener。

**Architecture:** Proto 层 `ClickRequest` 新增可选 `center_offset_x` / `center_offset_y`（相对 view 中心的 dp 偏移，空 = 中心），MCP tool 透传新参数；Android 改为向 decorView 注入 `MotionEvent(ACTION_DOWN + ACTION_UP)`，iOS 改为 hitTest 定位实际接收事件的 view 后按类型分发。

**Tech Stack:** Protobuf (google.protobuf.FloatValue)、Kotlin MotionEvent、Swift UIKit hitTest + UIGestureRecognizer state KVO hack

---

## 1. Proto 变更

文件：`proto/modify.proto`

```protobuf
message ClickRequest {
  string id                    = 1;
  google.protobuf.FloatValue center_offset_x = 2;  // dp，相对 view 中心，正右/正下，空 = 0
  google.protobuf.FloatValue center_offset_y = 3;
}
```

`ClickResult` 不变。需在 `proto/modify.proto` 顶部补充 import：
```protobuf
import "google/protobuf/wrappers.proto";
```

生成命令：`cd proto && buf generate`（Android proto 文件同步更新 `clients/android/sdk/src/main/proto/`）。

---

## 2. MCP tool 变更

文件：`mcp/src/tools/page.ts`

新增两个可选参数：
```ts
{
  id: z.string(),
  centerOffsetX: z.number().optional().describe("相对 view 中心的横向偏移 dp，正右，默认 0"),
  centerOffsetY: z.number().optional().describe("相对 view 中心的纵向偏移 dp，正下，默认 0"),
}
```

构造 `ClickRequest` 时，若 centerOffsetX/centerOffsetY 存在则填入 `center_offset_x` / `center_offset_y` wrapper 字段。

---

## 3. Android 实现

文件：`clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt`

**触点坐标计算：**
```
val loc = IntArray(2)
view.getLocationOnScreen(loc)
val density = view.resources.displayMetrics.density
val cx = loc[0] + view.width / 2f  + (centerOffsetXDp ?: 0f) * density
val cy = loc[1] + view.height / 2f + (centerOffsetYDp ?: 0f) * density
```

**注入事件：**
```kotlin
val downTime = SystemClock.uptimeMillis()
val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, cx, cy, 0)
val up   = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, cx, cy, 0)
val decorView = activity.window.decorView
decorView.dispatchTouchEvent(down)
decorView.dispatchTouchEvent(up)
down.recycle()
up.recycle()
```

`ApiHandler.handleClick` 从 proto 里读 `center_offset_x.value` / `center_offset_y.value` 传入。

---

## 4. iOS 实现

文件：`clients/ios/sdk/Sources/HttpServer/HttpServer.swift`（`handleClick` 函数）

**触点坐标计算（window 坐标系）：**
```swift
guard let window = view.window else { /* error */ }
let viewCenter = view.convert(
    CGPoint(x: view.bounds.midX + CGFloat(req.hasCenterOffsetX ? req.centerOffsetX.value : 0),
            y: view.bounds.midY + CGFloat(req.hasCenterOffsetY ? req.centerOffsetY.value : 0)),
    to: window
)
```

注意：proto `center_offset_x` 单位 dp = pt（iOS 上 1dp = 1pt），无需 density 换算。

**hitTest 定位实际 view：**
```swift
let hitView = window.hitTest(viewCenter, with: nil) ?? view
```

**类型分发（按优先级）：**

1. **UIControl**
   ```swift
   if let control = hitView as? UIControl {
       control.sendActions(for: .touchUpInside)
       return
   }
   ```

2. **UITableViewCell**
   ```swift
   if let cell = sequentialSuperview(of: hitView, type: UITableViewCell.self),
      let tableView = sequentialSuperview(of: cell, type: UITableView.self),
      let indexPath = tableView.indexPath(for: cell) {
       tableView.selectRow(at: indexPath, animated: false, scrollPosition: .none)
       tableView.delegate?.tableView?(tableView, didSelectRowAt: indexPath)
       return
   }
   ```

3. **UICollectionViewCell**
   ```swift
   if let cell = sequentialSuperview(of: hitView, type: UICollectionViewCell.self),
      let cv = sequentialSuperview(of: cell, type: UICollectionView.self),
      let indexPath = cv.indexPath(for: cell) {
       cv.delegate?.collectionView?(cv, didSelectItemAt: indexPath)
       return
   }
   ```

4. **UITapGestureRecognizer（hitView 及其祖先链）**
   ```swift
   var current: UIView? = hitView
   while let v = current {
       if let tap = v.gestureRecognizers?.first(where: { $0 is UITapGestureRecognizer }) {
           tap.setValue(UIGestureRecognizer.State.ended.rawValue, forKey: "state")
           return
       }
       current = v.superview
   }
   ```

5. **都不匹配** → 返回 400 `"No interactive handler found at point"`

**辅助函数：**
```swift
private func sequentialSuperview<T: UIView>(of view: UIView, type: T.Type) -> T? {
    var v: UIView? = view
    while let current = v {
        if let typed = current as? T { return typed }
        v = current.superview
    }
    return nil
}
```

---

## 5. 边界情况

| 情况 | 处理 |
|------|------|
| offset 超出 view bounds | 允许（可点到相邻 view，由 hitTest 决定接收者） |
| view.window == nil（未在窗口树中） | 返回 404 error |
| hitTest 返回 nil | fallback 到目标 view 本身走分发 |
| view 被遮挡（另一个 view 覆盖触点） | hitTest 会找到覆盖层，符合预期（模拟真实手指） |

---

## 6. 不在范围内

- 长按、双击、滑动手势 — 后续有需要再扩展
- HarmonyOS — 待定

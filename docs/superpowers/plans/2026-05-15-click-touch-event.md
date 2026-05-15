# Click Touch Event Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将双端 `click_view` 从触发 listener 改为注入完整触摸事件，支持可选 `centerOffsetX/Y` 参数偏移触点，覆盖手势识别器和自定义 touch 处理，不依赖业务是否设置了 click listener。

**Architecture:** Proto 层 `ClickRequest` 新增两个 FloatValue offset 字段；Android 改为向 decorView 注入 `MotionEvent(DOWN+UP)`；iOS 改为 `window.hitTest` 定位实际接收事件的 view，再按 UIControl → TableViewCell → CollectionViewCell → UITapGestureRecognizer 优先级分发；MCP tool 新增两个可选参数透传。

**Tech Stack:** Protobuf / buf generate、Kotlin MotionEvent、Swift UIKit hitTest + UIGestureRecognizer state KVO、TypeScript MCP SDK

---

## File Map

| 文件 | 操作 | 说明 |
|------|------|------|
| `proto/modify.proto` | 修改 | `ClickRequest` 加 `center_offset_x/y` |
| `clients/android/sdk/src/main/proto/modify.proto` | 同步修改 | Android 本地 proto 副本，与 `proto/` 保持一致 |
| `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt` | 修改 | `click()` 改为注入 MotionEvent |
| `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt` | 修改 | `handleClick` 读取 offset 并传入 |
| `clients/ios/sdk/Sources/HttpServer/HttpServer.swift` | 修改 | `handleClick` 改为 hitTest + 类型分发 |
| `mcp/src/tools/page.ts` | 修改 | `click_view` 新增 `centerOffsetX/Y` 可选参数 |

---

## Task 1: Proto — 扩展 ClickRequest

**Files:**
- Modify: `proto/modify.proto:69`
- Modify: `clients/android/sdk/src/main/proto/modify.proto:69`

- [ ] **Step 1: 修改 `proto/modify.proto`**

将第 69 行：
```protobuf
message ClickRequest  { string id = 1; }
```
替换为：
```protobuf
message ClickRequest {
  string                     id               = 1;
  google.protobuf.FloatValue center_offset_x  = 2;  // dp，相对 view 中心，正右/正下，空 = 0
  google.protobuf.FloatValue center_offset_y  = 3;
}
```

注意：`import "google/protobuf/wrappers.proto";` 已在文件顶部，无需重复添加。

- [ ] **Step 2: 同步修改 Android 本地 proto 副本**

`clients/android/sdk/src/main/proto/modify.proto` 第 69 行做完全相同的修改（内容与 Step 1 一致）。

- [ ] **Step 3: 运行 buf generate 生成代码**

```bash
cd proto && buf generate
```

预期输出：无报错，生成文件更新：
- `clients/ios/sdk/Sources/Generated/api.pb.swift`（或 `modify.pb.swift`）
- `mcp/src/generated/modify_pb.ts`

- [ ] **Step 4: 验证生成的 TypeScript 包含新字段**

```bash
grep -n "centerOffsetX\|center_offset_x" mcp/src/generated/modify_pb.ts
```

预期：找到 `centerOffsetX` 字段定义。

- [ ] **Step 5: 验证生成的 Swift 包含新字段**

```bash
grep -n "centerOffsetX\|center_offset_x" clients/ios/sdk/Sources/Generated/*.swift
```

预期：找到 `centerOffsetX` 属性。

- [ ] **Step 6: Commit**

```bash
git add proto/modify.proto clients/android/sdk/src/main/proto/modify.proto \
        clients/ios/sdk/Sources/Generated/ mcp/src/generated/
git commit -m "feat(proto): add center_offset_x/y to ClickRequest"
```

---

## Task 2: Android — ViewModifier.click() 改为注入 MotionEvent

**Files:**
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt`
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt`

- [ ] **Step 1: 修改 ViewModifier.kt**

将整个 `click()` 函数替换为：

```kotlin
fun click(viewId: String, centerOffsetXDp: Float? = null, centerOffsetYDp: Float? = null): Boolean {
    val views = ViewTreeTraversal.findViewById(viewId)
    if (views.isEmpty()) return false
    views.forEach { view ->
        val activity = ClientToolsSDK.getCurrentActivity() ?: return@forEach
        val density = view.resources.displayMetrics.density
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val cx = loc[0] + view.width / 2f + (centerOffsetXDp ?: 0f) * density
        val cy = loc[1] + view.height / 2f + (centerOffsetYDp ?: 0f) * density
        val decorView = activity.window.decorView
        if (Looper.myLooper() == Looper.getMainLooper()) {
            injectTap(decorView, cx, cy)
        } else {
            activity.runOnUiThread { injectTap(decorView, cx, cy) }
        }
    }
    return true
}

private fun injectTap(decorView: android.view.View, x: Float, y: Float) {
    val downTime = android.os.SystemClock.uptimeMillis()
    val down = android.view.MotionEvent.obtain(downTime, downTime,
        android.view.MotionEvent.ACTION_DOWN, x, y, 0)
    val up = android.view.MotionEvent.obtain(downTime, downTime + 50,
        android.view.MotionEvent.ACTION_UP, x, y, 0)
    decorView.dispatchTouchEvent(down)
    decorView.dispatchTouchEvent(up)
    down.recycle()
    up.recycle()
}
```

完整文件结果：
```kotlin
package com.clienttools.sdk.runtime

import android.os.Looper
import com.clienttools.sdk.ClientToolsSDK

object ViewModifier {

    fun click(viewId: String, centerOffsetXDp: Float? = null, centerOffsetYDp: Float? = null): Boolean {
        val views = ViewTreeTraversal.findViewById(viewId)
        if (views.isEmpty()) return false
        views.forEach { view ->
            val activity = ClientToolsSDK.getCurrentActivity() ?: return@forEach
            val density = view.resources.displayMetrics.density
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val cx = loc[0] + view.width / 2f + (centerOffsetXDp ?: 0f) * density
            val cy = loc[1] + view.height / 2f + (centerOffsetYDp ?: 0f) * density
            val decorView = activity.window.decorView
            if (Looper.myLooper() == Looper.getMainLooper()) {
                injectTap(decorView, cx, cy)
            } else {
                activity.runOnUiThread { injectTap(decorView, cx, cy) }
            }
        }
        return true
    }

    private fun injectTap(decorView: android.view.View, x: Float, y: Float) {
        val downTime = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(downTime, downTime,
            android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        val up = android.view.MotionEvent.obtain(downTime, downTime + 50,
            android.view.MotionEvent.ACTION_UP, x, y, 0)
        decorView.dispatchTouchEvent(down)
        decorView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
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

- [ ] **Step 2: 修改 ApiHandler.kt 的 handleClick，读取 offset 参数**

将 `handleClick` 函数（约第 107-119 行）替换为：

```kotlin
fun handleClick(bodyBytes: ByteArray): NanoHTTPD.Response {
    return try {
        val req = ClickRequest.parseFrom(bodyBytes)
        val offsetX = if (req.hasCenterOffsetX()) req.centerOffsetX.value else null
        val offsetY = if (req.hasCenterOffsetY()) req.centerOffsetY.value else null
        val success = ViewModifier.click(req.id, offsetX, offsetY)
        if (!success) return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "View not found")
        val result = ClickResult.newBuilder().setId(req.id).build()
        val resp = ClickResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
        okResponse(resp.toByteArray())
    } catch (e: Exception) {
        Log.e("ApiHandler", "handleClick", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd clients/android && ./gradlew :sdk:assembleDebug
```

预期：BUILD SUCCESSFUL，无编译错误。

- [ ] **Step 4: Commit**

```bash
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt \
        clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt
git commit -m "feat(android): inject MotionEvent for click_view with center offset support"
```

---

## Task 3: iOS — handleClick 改为 hitTest + 类型分发

**Files:**
- Modify: `clients/ios/sdk/Sources/HttpServer/HttpServer.swift`（`handleClick` 函数 + 新增辅助函数 `findSuperview`）

- [ ] **Step 1: 替换 handleClick 函数**

找到 `private func handleClick(_ body: Data, connection: NWConnection)` 整个函数（约第 250-273 行），替换为：

```swift
private func handleClick(_ body: Data, connection: NWConnection) {
    guard let req = try? Clienttools_ClickRequest(serializedBytes: body) else {
        sendError(code: 400, message: "Invalid request", connection: connection); return
    }
    guard let view = viewQueryService.findView(byId: req.id) else {
        sendError(code: 404, message: "View not found", httpCode: 404, connection: connection); return
    }

    var clickError: String? = nil
    let sema = DispatchSemaphore(value: 0)
    DispatchQueue.main.async {
        guard let window = view.window else {
            clickError = "View has no window"
            sema.signal(); return
        }
        // 触点坐标（window 坐标系），iOS 1pt = 1dp，无需 density 换算
        let offsetX = req.hasCenterOffsetX ? CGFloat(req.centerOffsetX.value) : 0
        let offsetY = req.hasCenterOffsetY ? CGFloat(req.centerOffsetY.value) : 0
        let localPoint = CGPoint(x: view.bounds.midX + offsetX, y: view.bounds.midY + offsetY)
        let pointInWindow = view.convert(localPoint, to: window)

        let hitView = window.hitTest(pointInWindow, with: nil) ?? view

        // 1. UIControl
        if let control = hitView as? UIControl {
            control.sendActions(for: .touchUpInside)
            sema.signal(); return
        }
        // 2. UITableViewCell
        if let cell = self.findSuperview(of: hitView, type: UITableViewCell.self),
           let tableView = self.findSuperview(of: cell, type: UITableView.self),
           let indexPath = tableView.indexPath(for: cell) {
            tableView.selectRow(at: indexPath, animated: false, scrollPosition: .none)
            tableView.delegate?.tableView?(tableView, didSelectRowAt: indexPath)
            sema.signal(); return
        }
        // 3. UICollectionViewCell
        if let cell = self.findSuperview(of: hitView, type: UICollectionViewCell.self),
           let cv = self.findSuperview(of: cell, type: UICollectionView.self),
           let indexPath = cv.indexPath(for: cell) {
            cv.delegate?.collectionView?(cv, didSelectItemAt: indexPath)
            sema.signal(); return
        }
        // 4. UITapGestureRecognizer（hitView 及其祖先链）
        var current: UIView? = hitView
        while let v = current {
            if let tap = v.gestureRecognizers?.first(where: { $0 is UITapGestureRecognizer }) {
                tap.setValue(UIGestureRecognizer.State.ended.rawValue, forKey: "state")
                sema.signal(); return
            }
            current = v.superview
        }
        clickError = "No interactive handler found at point"
        sema.signal()
    }
    sema.wait()

    if let err = clickError {
        sendError(code: 400, message: err, connection: connection); return
    }
    var result = Clienttools_ClickResult()
    result.id = req.id
    var resp = Clienttools_ClickResponse()
    resp.meta = okMeta()
    resp.data = result
    sendProto(resp, connection: connection)
}
```

- [ ] **Step 2: 在 HttpServer 类中新增辅助函数 `findSuperview`**

在 `handleClick` 函数之后（`handleScroll` 之前）插入：

```swift
private func findSuperview<T: UIView>(of view: UIView, type: T.Type) -> T? {
    var v: UIView? = view
    while let current = v {
        if let typed = current as? T { return typed }
        v = current.superview
    }
    return nil
}
```

- [ ] **Step 3: 编译验证**

```bash
cd clients/ios && xcodebuild -workspace demo/ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo -destination 'platform=iOS Simulator,name=iPhone 17' \
  build 2>&1 | tail -5
```

预期：`** BUILD SUCCEEDED **`

- [ ] **Step 4: Commit**

```bash
git add clients/ios/sdk/Sources/HttpServer/HttpServer.swift
git commit -m "feat(ios): inject hitTest touch dispatch for click_view with center offset support"
```

---

## Task 4: MCP — click_view 新增 centerOffsetX/Y 参数

**Files:**
- Modify: `mcp/src/tools/page.ts:27-33`

- [ ] **Step 1: 修改 page.ts 的 click_view tool**

将 `click_view` tool（第 27-33 行）替换为：

```typescript
server.tool(
  "click_view",
  "点击指定 id 的 View（Android/iOS 通用）。默认点击 view 中心，可通过 centerOffsetX/Y 偏移触点",
  {
    id: z.string().describe("View 的 id（Android resource id 不含包名前缀，iOS 为 accessibilityIdentifier）"),
    centerOffsetX: z.number().optional().describe("触点相对 view 中心的横向偏移 dp，正右，默认 0"),
    centerOffsetY: z.number().optional().describe("触点相对 view 中心的纵向偏移 dp，正下，默认 0"),
  },
  async ({ id, centerOffsetX, centerOffsetY }) => {
    try {
      const req = create(ClickRequestSchema, {
        id,
        ...(centerOffsetX !== undefined && { centerOffsetX }),
        ...(centerOffsetY !== undefined && { centerOffsetY }),
      });
      const res = await sdkPost("/api/click", ClickRequestSchema, req, ClickResponseSchema);
      return { content: [{ type: "text" as const, text: JSON.stringify({ id: res.data?.id }) }] };
    } catch (e) { return errResult(e); }
  }
);
```

- [ ] **Step 2: 编译验证**

```bash
cd mcp && npm run build 2>&1 | tail -5
```

预期：无 TypeScript 编译错误。

- [ ] **Step 3: Commit**

```bash
git add mcp/src/tools/page.ts
git commit -m "feat(mcp): add centerOffsetX/Y params to click_view tool"
```

---

## Task 5: 端到端验证

通过 MCP 工具验证双端新实现，确保：
1. 默认不传 offset 时正常点击
2. 传 offset 时触点偏移正确

- [ ] **Step 1: 验证 Android click（默认中心）**

用 MCP `click_view` 点击 Demo 首页的第一个列表项（id: `home_cell_0`），确认页面导航到 Login 页。

- [ ] **Step 2: 验证 iOS click（默认中心）**

用 MCP `click_view` 点击 iOS Demo 首页 `home_cell_0`，确认页面导航到 Login 页。

- [ ] **Step 3: 验证 iOS click 覆盖无 listener 场景**

iOS Demo 中 `login_btn_submit` 是 UIButton（UIControl），但此次改为 hitTest 分发后行为不变；确认点击后触发 submit 动作（进入 loading 态）。

- [ ] **Step 4: 验证 centerOffset 参数生效**

调用 `click_view` 时传入 `centerOffsetX: 100, centerOffsetY: 0`，此时触点偏移到 view 右侧 100dp，hitTest 会命中相邻区域，确认不报错（即使没有可点击 view 也返回 400 而不是 crash）。

- [ ] **Step 5: Push**

```bash
git push
```

# Android SDK TODO — Implementation Plan

**日期**：2026-04-23  
**范围**：Android SDK 新增 API + MCP 适配 + Skill 适配

---

## Phase 1：Android SDK

### Task 1.1：新增 PageInfo 数据类

**文件**：`packages/android/sdk/src/main/kotlin/com/clienttools/sdk/model/PageInfo.kt`

```kotlin
package com.clienttools.sdk.model

data class PageInfo(
    val pageName: String,
    val timestamp: String
)
```

**验证**：`./gradlew :android:sdk:compileDebugKotlin`

---

### Task 1.2：新增 ClickResult / ScrollResult 数据类

**文件**：`packages/android/sdk/src/main/kotlin/com/clienttools/sdk/model/ClickResult.kt`

```kotlin
package com.clienttools.sdk.model

data class ClickResult(
    val id: String
)
```

**文件**：`packages/android/sdk/src/main/kotlin/com/clienttools/sdk/model/ScrollResult.kt`

```kotlin
package com.clienttools.sdk.model

data class ScrollResult(
    val id: String,
    val dx: Float,
    val dy: Float
)
```

**验证**：`./gradlew :android:sdk:compileDebugKotlin`

---

### Task 1.3：ViewModifier 新增 click() 和 scroll()

**文件**：`packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt`

**在 ViewModifier 类中新增两个方法**：

```kotlin
fun click(view: View) {
    view.performClick()
}

fun scroll(view: View, dxDp: Float, dyDp: Float) {
    val density = view.context.resources.displayMetrics.density
    val dxPx = (dxDp * density).toInt()
    val dyPx = (dyDp * density).toInt()
    view.scrollBy(dxPx, dyPx)
}
```

**验证**：`./gradlew :android:sdk:compileDebugKotlin`

---

### Task 1.4：PageChangeListener 移除 SSE 推送，保留页面记录

**文件**：`packages/android/sdk/src/main/kotlin/com/clienttools/sdk/listener/PageChangeListener.kt`

**改动**：
- 删除 `EventManager` 引用
- 保留 `currentPageName` 和 `lastChangeTime` 字段
- 新增 `getCurrentPage()` 方法供外部查询

```kotlin
class PageChangeListener(private val context: Context) {
    private var currentPageName: String = ""
    private var lastChangeTime: String = ""

    fun getCurrentPage(): Pair<String, String> = Pair(currentPageName, lastChangeTime)

    fun onActivityResumed(activity: Activity) {
        currentPageName = activity.javaClass.name
        lastChangeTime = SimpleDateFormat("MMdd-HHmm", Locale.US).format(Date())
    }
}
```

**验证**：`./gradlew :android:sdk:compileDebugKotlin`

---

### Task 1.5：ApiHandler 新增 3 个路由

**文件**：`packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt`

**在 `handleGet()` 中新增**：

```kotlin
"/api/page/current" -> {
    val (pageName, timestamp) = pageChangeListener.getCurrentPage()
    respondJson(ApiResponse(code = 0, message = "success", data = PageInfo(pageName, timestamp)))
}
```

**在 `handlePost()` 中新增**：

```kotlin
"/api/click" -> {
    val body = parseBody<ClickRequest>(data)
    val view = findViewById(body.id)
    if (view != null) {
        viewModifier.click(view)
        respondJson(ApiResponse(code = 0, message = "success", data = ClickResult(body.id)))
    } else {
        respondJson(ApiResponse(code = 404, message = "View not found"), 404)
    }
}

"/api/scroll" -> {
    val body = parseBody<ScrollRequest>(data)
    val view = findViewById(body.id)
    if (view != null) {
        viewModifier.scroll(view, body.dx, body.dy)
        respondJson(ApiResponse(code = 0, message = "success", data = ScrollResult(body.id, body.dx, body.dy)))
    } else {
        respondJson(ApiResponse(code = 404, message = "View not found"), 404)
    }
}
```

**新增 Request 数据类**（可放在 `model/` 下）：

```kotlin
data class ClickRequest(val id: String)
data class ScrollRequest(val id: String, val dx: Float, val dy: Float)
```

**验证**：`./gradlew :android:sdk:compileDebugKotlin`

---

### Task 1.6：删除 SSE 相关代码

**文件**：`packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/EventManager.kt` — 删除

**验证**：`./gradlew :android:sdk:compileDebugKotlin`

---

### Task 1.7：编译 & APK 构建验证

**命令**：
```bash
cd packages && ./gradlew :android:sdk:assemble
```

**验证**：生成 `android-sdk/build/outputs/apk/debug/android-sdk-debug.apk`

---

## Phase 2：MCP

### Task 2.1：删除 event-monitor.ts

**文件**：`packages/client-tools/mcp/src/event-monitor.ts` — 删除

**验证**：`npm run build`

---

### Task 2.2：新建 page.ts 工具文件

**文件**：`packages/client-tools/mcp/src/tools/page.ts`

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkGet, sdkPost } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerPageTools(server: McpServer): void {
  server.tool(
    "get_current_page",
    "查询当前 Android 页面名称",
    {},
    async () => {
      try {
        const result = await sdkGet("/api/page/current");
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "click_view",
    "点击指定 id 的 Android View",
    { id: z.string() },
    async ({ id }) => {
      try {
        const result = await sdkPost("/api/click", { id });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "scroll_view",
    "滚动指定 id 的 Android View，单位 dp",
    {
      id: z.string(),
      dx: z.number().describe("横向滚动量，dp，正值向左滚"),
      dy: z.number().describe("竖向滚动量，dp，正值向上滚"),
    },
    async ({ id, dx, dy }) => {
      try {
        const result = await sdkPost("/api/scroll", { id, dx, dy });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

**验证**：`npm run build`

---

### Task 2.3：更新 index.ts

**文件**：`packages/client-tools/mcp/src/index.ts`

**改动**：
- 删除 `eventMonitor` 引用和启动调用
- 删除 `import { eventMonitor }`
- 删除 `eventMonitor.start()`
- 新增 `import { registerPageTools }`
- 新增 `registerPageTools(server)`

**验证**：`npm run build && npm start`

---

## Phase 3：Skill

### Task 3.1：更新 client-tools-inspect skill

**文件**：`packages/client-tools/skill/client-tools-inspect/SKILL.md`

**改动点**：
1. 删除 SSE 订阅相关描述
2. 新增 inspect 开始前 `get_current_page()` 调用说明
3. click/scroll 作为独立功能说明（不在流程内）

**验证**：阅读 skill 文档确认改动

---

## Phase 4：集成验证

### Task 4.1：手动 API 测试

**前提**：设备连接，`adb forward tcp:8080 tcp:8080`

```bash
# 1. 测试 get_current_page
curl http://localhost:8080/api/page/current

# 2. 测试 click
curl -X POST http://localhost:8080/api/click \
  -H "Content-Type: application/json" \
  -d '{"id":"btn_login"}'

# 3. 测试 scroll
curl -X POST http://localhost:8080/api/scroll \
  -H "Content-Type: application/json" \
  -d '{"id":"content_list","dx":0,"dy":-100}'
```

**预期**：均返回 `{"code":0,"message":"success","data":{...}}`

---

## 执行顺序

```
Phase 1（Android SDK）
  Task 1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.6 → 1.7
        ↓
Phase 2（MCP）
  Task 2.1 → 2.2 → 2.3
        ↓
Phase 3（Skill）
  Task 3.1
        ↓
Phase 4（集成验证）
  Task 4.1
```

---

## 风险与注意事项

| 风险 | 缓解 |
|------|------|
| scroll 方向判断错误 | 以 Android scrollBy 行为为准，dx>0 向左，dy>0 向上 |
| click 找不到 View | 返回 404，AI 自行处理 |
| SSE 删除影响其他依赖方 | 确认无其他依赖再删除 |

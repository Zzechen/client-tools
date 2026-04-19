# DOM 树查询接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Inspector WebView 叠加层基础上，实现 `/dom/all` 和 `/dom/:id` 两个 HTTP 接口，通过 JS 注入获取 DOM 节点数据并换算为屏幕绝对坐标。

**Architecture:** 新增 `DomNodeInfo` 数据类和 `DomQueryService` 类（独立于 `InspectorApiHandler`），`DomQueryService` 通过 `WebView.evaluateJavascript` + `suspendCoroutine` + `withTimeout` 实现异步 JS 桥接，坐标换算在 Kotlin 层完成（WebView 屏幕位置 + 内容滚动 + Inspector 偏移量）。`InspectorApiHandler` 和 `HttpServer` 各扩展两个路由。

**Tech Stack:** Kotlin coroutines (`suspendCoroutine`, `withTimeout`)、`WebView.evaluateJavascript`、NanoHTTPD、AndroidX Test (instrumented tests)

---

## 文件结构

| 操作 | 路径 | 职责 |
|------|------|------|
| 新增 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/DomNodeInfo.kt` | DOM 节点数据类 |
| 新增 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/DomQueryService.kt` | JS 注入 + 坐标换算 + 超时控制 |
| 修改 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt` | 新增 `handleDomAll` 和 `handleDomById` |
| 修改 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt` | 注册 `/dom/all` 和 `/dom/:id` 路由 |
| 新增 | `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/DomQueryServiceTest.kt` | instrumented tests |

---

### Task 1: DomNodeInfo 数据类

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/DomNodeInfo.kt`

- [ ] **Step 1: 创建 DomNodeInfo.kt**

```kotlin
package com.clienttools.sdk.inspector

data class DomNodeInfo(
    val id: String,
    val tagName: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val text: String
)
```

- [ ] **Step 2: 编译验证**

```bash
cd packages && ./gradlew :android:sdk:compileDebugKotlin --quiet
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/DomNodeInfo.kt
git commit -m "feat(dom): add DomNodeInfo data class"
```

---

### Task 2: DomQueryService（JS 注入 + 坐标换算）

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/DomQueryService.kt`
- Test: `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/DomQueryServiceTest.kt`

- [ ] **Step 1: 写测试（先写失败用例）**

创建 `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/DomQueryServiceTest.kt`：

```kotlin
package com.clienttools.sdk.inspector

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.clienttools.sdk.inspector.DomQueryService
import com.clienttools.sdk.inspector.DomNodeInfo
import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import android.content.Context

@RunWith(AndroidJUnit4::class)
class DomQueryServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun parseNodes_validJson_returnsList() {
        // 直接测试 JSON 解析逻辑，无需真实 WebView
        val service = DomQueryService()
        val json = """[{"id":"btn","tagName":"button","x":10,"y":20,"width":100,"height":48,"text":"OK"}]"""
        val nodes = service.parseNodesJson(json, webViewLeft = 0, webViewTop = 0, webViewScrollX = 0, webViewScrollY = 0, offsetXPx = 0, offsetYPx = 0)
        assertEquals(1, nodes.size)
        assertEquals("btn", nodes[0].id)
        assertEquals("button", nodes[0].tagName)
        assertEquals(10, nodes[0].x)
        assertEquals(20, nodes[0].y)
        assertEquals(100, nodes[0].width)
        assertEquals(48, nodes[0].height)
        assertEquals("OK", nodes[0].text)
    }

    @Test
    fun parseNodes_withOffset_appliesCoordinateConversion() {
        val service = DomQueryService()
        val json = """[{"id":"","tagName":"div","x":50,"y":100,"width":200,"height":80,"text":""}]"""
        val nodes = service.parseNodesJson(json, webViewLeft = 10, webViewTop = 20, webViewScrollX = 5, webViewScrollY = 15, offsetXPx = 3, offsetYPx = 7)
        assertEquals(1, nodes.size)
        // screenX = webViewLeft(10) + webViewScrollX(5) + elementX(50) + offsetXPx(3) = 68
        assertEquals(68, nodes[0].x)
        // screenY = webViewTop(20) + webViewScrollY(15) + elementY(100) + offsetYPx(7) = 142
        assertEquals(142, nodes[0].y)
    }

    @Test
    fun parseNodes_invalidJson_returnsEmptyList() {
        val service = DomQueryService()
        val nodes = service.parseNodesJson("not-json", 0, 0, 0, 0, 0, 0)
        assertEquals(0, nodes.size)
    }

    @Test
    fun parseNodeById_validJson_returnsNode() {
        val service = DomQueryService()
        val json = """{"id":"title","tagName":"h1","x":0,"y":0,"width":300,"height":40,"text":"Hello"}"""
        val node = service.parseNodeJson(json, webViewLeft = 0, webViewTop = 0, webViewScrollX = 0, webViewScrollY = 0, offsetXPx = 0, offsetYPx = 0)
        assertNotNull(node)
        assertEquals("title", node!!.id)
        assertEquals("h1", node.tagName)
    }

    @Test
    fun parseNodeById_nullJson_returnsNull() {
        val service = DomQueryService()
        val node = service.parseNodeJson(null, 0, 0, 0, 0, 0, 0)
        assertNull(node)
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd packages && ./gradlew :android:sdk:connectedDebugAndroidTest --tests "*.DomQueryServiceTest" 2>&1 | tail -20
```

Expected: FAILED（DomQueryService 不存在）

- [ ] **Step 3: 实现 DomQueryService**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/DomQueryService.kt`：

```kotlin
package com.clienttools.sdk.inspector

import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

class DomQueryService(
    private val timeoutMs: Long = 3000L
) {

    private val TAG = "DomQueryService"

    private val JS_ALL = """
        (function() {
          var nodes = [];
          var all = document.querySelectorAll('*');
          for (var i = 0; i < all.length; i++) {
            var el = all[i];
            var r = el.getBoundingClientRect();
            nodes.push({
              id: el.id || '',
              tagName: el.tagName.toLowerCase(),
              x: Math.round(r.left),
              y: Math.round(r.top),
              width: Math.round(r.width),
              height: Math.round(r.height),
              text: (el.innerText || '').substring(0, 200)
            });
          }
          return JSON.stringify(nodes);
        })()
    """.trimIndent()

    private fun jsById(id: String) = """
        (function() {
          var el = document.getElementById('${id.replace("'", "\\'")}');
          if (!el) return null;
          var r = el.getBoundingClientRect();
          return JSON.stringify({
            id: el.id || '',
            tagName: el.tagName.toLowerCase(),
            x: Math.round(r.left),
            y: Math.round(r.top),
            width: Math.round(r.width),
            height: Math.round(r.height),
            text: (el.innerText || '').substring(0, 200)
          });
        })()
    """.trimIndent()

    suspend fun queryAll(
        webView: WebView,
        webViewOffsetXDp: Int,
        webViewOffsetYDp: Int
    ): List<DomNodeInfo> {
        val density = webView.context.resources.displayMetrics.density
        val offsetXPx = (webViewOffsetXDp * density).toInt()
        val offsetYPx = (webViewOffsetYDp * density).toInt()

        val loc = IntArray(2)
        webView.getLocationOnScreen(loc)
        val webViewLeft = loc[0]
        val webViewTop = loc[1]
        val scrollX = webView.scrollX
        val scrollY = webView.scrollY

        return try {
            val rawJson = withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    webView.post {
                        webView.evaluateJavascript(JS_ALL) { result ->
                            cont.resume(result)
                        }
                    }
                }
            }
            parseNodesJson(rawJson, webViewLeft, webViewTop, scrollX, scrollY, offsetXPx, offsetYPx)
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "queryAll timeout")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "queryAll error", e)
            emptyList()
        }
    }

    suspend fun queryById(
        webView: WebView,
        id: String,
        webViewOffsetXDp: Int,
        webViewOffsetYDp: Int
    ): DomNodeInfo? {
        val density = webView.context.resources.displayMetrics.density
        val offsetXPx = (webViewOffsetXDp * density).toInt()
        val offsetYPx = (webViewOffsetYDp * density).toInt()

        val loc = IntArray(2)
        webView.getLocationOnScreen(loc)
        val webViewLeft = loc[0]
        val webViewTop = loc[1]
        val scrollX = webView.scrollX
        val scrollY = webView.scrollY

        return try {
            val rawJson = withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    webView.post {
                        webView.evaluateJavascript(jsById(id)) { result ->
                            cont.resume(result)
                        }
                    }
                }
            }
            parseNodeJson(rawJson, webViewLeft, webViewTop, scrollX, scrollY, offsetXPx, offsetYPx)
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "queryById timeout")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "queryById error", e)
            null
        }
    }

    // 以下两个方法 internal 以便测试直接调用

    internal fun parseNodesJson(
        raw: String?,
        webViewLeft: Int, webViewTop: Int,
        webViewScrollX: Int, webViewScrollY: Int,
        offsetXPx: Int, offsetYPx: Int
    ): List<DomNodeInfo> {
        if (raw == null || raw == "null") return emptyList()
        return try {
            val unescaped = unescapeJsString(raw)
            val arr = JSONArray(unescaped)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                parseNode(obj, webViewLeft, webViewTop, webViewScrollX, webViewScrollY, offsetXPx, offsetYPx)
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseNodesJson failed: ${e.message}")
            emptyList()
        }
    }

    internal fun parseNodeJson(
        raw: String?,
        webViewLeft: Int, webViewTop: Int,
        webViewScrollX: Int, webViewScrollY: Int,
        offsetXPx: Int, offsetYPx: Int
    ): DomNodeInfo? {
        if (raw == null || raw == "null") return null
        return try {
            val unescaped = unescapeJsString(raw)
            val obj = JSONObject(unescaped)
            parseNode(obj, webViewLeft, webViewTop, webViewScrollX, webViewScrollY, offsetXPx, offsetYPx)
        } catch (e: Exception) {
            Log.w(TAG, "parseNodeJson failed: ${e.message}")
            null
        }
    }

    private fun parseNode(
        obj: JSONObject,
        webViewLeft: Int, webViewTop: Int,
        webViewScrollX: Int, webViewScrollY: Int,
        offsetXPx: Int, offsetYPx: Int
    ): DomNodeInfo {
        val elemX = obj.optInt("x", 0)
        val elemY = obj.optInt("y", 0)
        return DomNodeInfo(
            id = obj.optString("id", ""),
            tagName = obj.optString("tagName", ""),
            x = webViewLeft + webViewScrollX + elemX + offsetXPx,
            y = webViewTop + webViewScrollY + elemY + offsetYPx,
            width = obj.optInt("width", 0),
            height = obj.optInt("height", 0),
            text = obj.optString("text", "")
        )
    }

    // evaluateJavascript 返回的字符串是 JSON 字符串字面量（带引号且转义），需要去掉外层引号并反转义
    private fun unescapeJsString(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed.substring(1, trimmed.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        } else {
            trimmed
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd packages && ./gradlew :android:sdk:connectedDebugAndroidTest --tests "*.DomQueryServiceTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 5 tests passed

- [ ] **Step 5: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/DomQueryService.kt \
        packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/DomQueryServiceTest.kt
git commit -m "feat(dom): add DomQueryService with JS injection and coordinate conversion"
```

---

### Task 3: InspectorApiHandler 扩展 DOM 路由处理

**Files:**
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt`
- Test: `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorApiHandlerTest.kt`

注意：`InspectorApiHandler` 目前是普通类，`handleDomAll` 和 `handleDomById` 是 `suspend` 函数，调用方（`HttpServer`）需要在协程中调用。

- [ ] **Step 1: 在 InspectorApiHandlerTest 末尾追加 DOM 相关测试**

打开 `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorApiHandlerTest.kt`，在最后一个 `}` 前追加：

```kotlin
    @Test
    fun domAll_noWebView_returnsCode3() {
        // getTopViewModel 返回 null，WebView 不存在
        val response = kotlinx.coroutines.runBlocking {
            handler.handleDomAll(webView = null)
        }
        assert(response.status.requestStatus == 200)
        val body = response.data.let { stream ->
            stream?.bufferedReader()?.readText() ?: ""
        }
        assert(body.contains("\"code\":3")) { "Expected code 3, got: $body" }
    }

    @Test
    fun domById_noWebView_returnsCode3() {
        val response = kotlinx.coroutines.runBlocking {
            handler.handleDomById(webView = null, id = "some-id")
        }
        assert(response.status.requestStatus == 200)
        val body = response.data.let { stream ->
            stream?.bufferedReader()?.readText() ?: ""
        }
        assert(body.contains("\"code\":3")) { "Expected code 3, got: $body" }
    }
```

- [ ] **Step 2: 运行新增测试，确认失败**

```bash
cd packages && ./gradlew :android:sdk:connectedDebugAndroidTest --tests "*.InspectorApiHandlerTest.domAll*" 2>&1 | tail -20
```

Expected: FAILED（handleDomAll 不存在）

- [ ] **Step 3: 在 InspectorApiHandler 中添加 DOM 方法**

在 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt` 的 `// ── 内部工具` 注释前插入：

```kotlin
    // ── DOM 查询 ────────────────────────────────────────────────────────────────

    private val domQueryService = DomQueryService(timeoutMs = 3000L)

    suspend fun handleDomAll(webView: android.webkit.WebView?): NanoHTTPD.Response {
        if (webView == null) return domError(3, "webview not ready")
        val vm = getTopViewModel()
        val offsetX = vm?.webView?.value?.offsetX ?: 0
        val offsetY = vm?.webView?.value?.offsetY ?: 0
        return try {
            val nodes = domQueryService.queryAll(webView, offsetX, offsetY)
            val nodesJson = nodes.joinToString(",") { n ->
                """{"id":"${n.id}","tagName":"${n.tagName}","x":${n.x},"y":${n.y},"width":${n.width},"height":${n.height},"text":${org.json.JSONObject.quote(n.text)}}"""
            }
            ok("""{"code":0,"data":{"count":${nodes.size},"nodes":[$nodesJson]}}""")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            domError(2, "timeout")
        } catch (e: Exception) {
            Log.e(TAG, "domAll error", e)
            domError(1, "parse error")
        }
    }

    suspend fun handleDomById(webView: android.webkit.WebView?, id: String): NanoHTTPD.Response {
        if (webView == null) return domError(3, "webview not ready")
        val vm = getTopViewModel()
        val offsetX = vm?.webView?.value?.offsetX ?: 0
        val offsetY = vm?.webView?.value?.offsetY ?: 0
        return try {
            val node = domQueryService.queryById(webView, id, offsetX, offsetY)
                ?: return domError(1, "not found")
            ok("""{"code":0,"data":{"id":"${node.id}","tagName":"${node.tagName}","x":${node.x},"y":${node.y},"width":${node.width},"height":${node.height},"text":${org.json.JSONObject.quote(node.text)}}}""")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            domError(2, "timeout")
        } catch (e: Exception) {
            Log.e(TAG, "domById error", e)
            domError(1, "parse error")
        }
    }

    private fun domError(code: Int, message: String) = ok("""{"code":$code,"message":"$message"}""")
```

- [ ] **Step 4: 运行所有 InspectorApiHandlerTest，确认通过**

```bash
cd packages && ./gradlew :android:sdk:connectedDebugAndroidTest --tests "*.InspectorApiHandlerTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 8 tests passed

- [ ] **Step 5: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt \
        packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorApiHandlerTest.kt
git commit -m "feat(dom): add handleDomAll and handleDomById to InspectorApiHandler"
```

---

### Task 4: HttpServer 注册 DOM 路由

**Files:**
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`

`HttpServer.serve()` 是同步方法（NanoHTTPD 调用）。DOM handler 是 suspend 函数，需要用 `runBlocking` 在路由中调用。WebView 引用从 `ClientToolsSDK.getTop()?.renderer?.webView` 获取（`WebViewRenderer` 持有 `webView` 实例，需暴露为 `internal val`）。

- [ ] **Step 1: 暴露 WebViewRenderer.webView**

打开 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/WebViewRenderer.kt`，将第 16 行：

```kotlin
    private val webView: WebView = rootView.findViewById(R.id.overlay_webview)
```

改为：

```kotlin
    internal val webView: WebView = rootView.findViewById(R.id.overlay_webview)
```

- [ ] **Step 2: 在 HttpServer.serve() 中添加 DOM 路由**

打开 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`，在 `else -> newFixedLengthResponse(...)` 之前插入：

```kotlin
                method == Method.GET && uri == "/dom/all" -> {
                    val webView = ClientToolsSDK.getTop()?.renderer?.webView
                    kotlinx.coroutines.runBlocking {
                        inspectorHandler().handleDomAll(webView)
                    }
                }
                method == Method.GET && uri.startsWith("/dom/") -> {
                    val id = uri.removePrefix("/dom/")
                    val webView = ClientToolsSDK.getTop()?.renderer?.webView
                    kotlinx.coroutines.runBlocking {
                        inspectorHandler().handleDomById(webView, id)
                    }
                }
```

- [ ] **Step 3: 编译验证**

```bash
cd packages && ./gradlew :android:sdk:compileDebugKotlin --quiet
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 真机验证（需连接设备并 adb forward）**

```bash
adb forward tcp:8080 tcp:8080
# 先推送一个 HTML 文件（让 WebView 加载内容）
curl -s -X POST http://localhost:8080/webview/push-html \
  -H 'Content-Type: application/json' \
  -d '{"tag":"test","html":"<html><body><button id=\"btn\">OK</button><div id=\"title\">Hello</div></body></html>"}'
# 查询全量 DOM
curl -s http://localhost:8080/dom/all | python3 -m json.tool
# 查询指定 id
curl -s http://localhost:8080/dom/btn | python3 -m json.tool
```

Expected：`/dom/all` 返回 `{"code":0,"data":{"count":N,"nodes":[...]}}` 且包含 `btn` 和 `title` 节点，坐标为正数。`/dom/btn` 返回单个节点 `{"code":0,"data":{...}}`。

- [ ] **Step 5: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/WebViewRenderer.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt
git commit -m "feat(dom): register /dom/all and /dom/:id routes in HttpServer"
```

---

### Task 5: 运行全量测试 + 推送

**Files:** 无新增，验证整体

- [ ] **Step 1: 运行所有 instrumented tests**

```bash
cd packages && ./gradlew :android:sdk:connectedDebugAndroidTest 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL，所有测试通过（InspectorApiHandlerTest、InspectorFileStoreTest、ImageFileStoreTest、ImageApiHandlerTest、DomQueryServiceTest）

- [ ] **Step 2: 推送**

```bash
git push
```

---

## 自检清单

**Spec 覆盖：**
- ✅ `DomNodeInfo` 数据类（Task 1）
- ✅ `DomQueryService.queryAll` + `queryById`，`timeoutMs` 参数（Task 2）
- ✅ 坐标换算公式：`webViewLeft + scrollX + elemX + offsetPx`（Task 2）
- ✅ 超时 → code 2，JS null/解析失败 → code 1（Task 3）
- ✅ WebView 未就绪 → code 3（Task 3）
- ✅ `GET /dom/all` 返回 `count + nodes`（Task 3/4）
- ✅ `GET /dom/:id` 返回单节点（Task 3/4）
- ✅ `text` 字段用 `JSONObject.quote` 转义（Task 3，防注入）

**类型一致性：**
- `DomNodeInfo` 字段（id/tagName/x/y/width/height/text）在 Task 1 定义，Task 2、3 引用一致
- `DomQueryService.parseNodesJson/parseNodeJson` 签名在 Task 2 定义，Task 3 无直接调用（封装在 queryAll/queryById 内）
- `handleDomAll(webView: android.webkit.WebView?)` / `handleDomById(webView: android.webkit.WebView?, id: String)` 在 Task 3 定义，Task 4 调用一致

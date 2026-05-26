# Custom Routes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow app layer to register custom HTTP routes at SDK init time, discoverable and callable via two new MCP tools (`list_custom_routes`, `custom_call`).

**Architecture:** App registers `CustomRoute` list at SDK init; SDK HTTP Server adds `/custom/routes` (metadata) and `/custom/{path}` (dispatch) endpoints. MCP adds `sdkGetText`/`sdkPostText` helpers and two new tools. Android and iOS implemented symmetrically. Demo registers 4 routes covering all handler branches (normal / business error / timeout / crash).

**Tech Stack:** Kotlin coroutines (`withTimeout`), NanoHTTPD (Android), Swift async/await + NWListener (iOS), TypeScript + zod (MCP).

---

## File Map

**Create:**
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/CustomRoute.kt`
- `clients/android/sdk/src/test/kotlin/com/clienttools/sdk/http/CustomRouteTest.kt`
- `clients/ios/sdk/Sources/HttpServer/CustomRoute.swift`
- `mcp/src/tools/custom.ts`

**Modify:**
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt`
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt`
- `clients/ios/sdk/Sources/ClientToolsSDK.swift`
- `clients/ios/sdk/Sources/HttpServer/HttpServer.swift`
- `mcp/src/sdk-client.ts`
- `mcp/src/index.ts`
- `clients/android/demo/src/main/kotlin/com/clienttools/demo/DemoApplication.kt`
- `clients/android/demo/src/main/kotlin/com/clienttools/demo/LoginActivity.kt`
- `clients/ios/demo/Sources/ClientToolsDemo/AppDelegate.swift`
- `docs/mcp-tools.md`
- `docs/sdk-http-api.md`

---

## Task 1: Android SDK — CustomRoute 类型定义

**Files:**
- Create: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/CustomRoute.kt`
- Create: `clients/android/sdk/src/test/kotlin/com/clienttools/sdk/http/CustomRouteTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
// clients/android/sdk/src/test/kotlin/com/clienttools/sdk/http/CustomRouteTest.kt
package com.clienttools.sdk.http

import kotlin.test.*

class CustomRouteTest {

    @Test
    fun `HttpMethod GET value is GET`() {
        assertEquals("GET", HttpMethod.GET.value)
    }

    @Test
    fun `HttpMethod POST value is POST`() {
        assertEquals("POST", HttpMethod.POST.value)
    }

    @Test
    fun `CustomResult ok has code 0 and data`() {
        val r = CustomResult.ok("hello")
        assertEquals(0, r.code)
        assertEquals("ok", r.message)
        assertEquals("hello", r.data)
    }

    @Test
    fun `CustomResult ok with no arg has empty data`() {
        val r = CustomResult.ok()
        assertEquals("", r.data)
    }

    @Test
    fun `CustomResult error has code -1 and null data`() {
        val r = CustomResult.error("something failed")
        assertEquals(-1, r.code)
        assertEquals("something failed", r.message)
        assertNull(r.data)
    }

    @Test
    fun `CustomResult error allows custom code`() {
        val r = CustomResult.error("forbidden", code = 403)
        assertEquals(403, r.code)
    }

    @Test
    fun `buildCustomResultJson ok produces valid json`() {
        val r = CustomResult.ok("world")
        val json = buildCustomResultJson(r)
        assertEquals("""{"code":0,"message":"ok","data":"world"}""", json)
    }

    @Test
    fun `buildCustomResultJson error produces null data`() {
        val r = CustomResult.error("oops")
        val json = buildCustomResultJson(r)
        assertEquals("""{"code":-1,"message":"oops","data":null}""", json)
    }

    @Test
    fun `buildCustomResultJson escapes quotes in message`() {
        val r = CustomResult.error("say \"hello\"")
        val json = buildCustomResultJson(r)
        assertEquals("""{"code":-1,"message":"say \"hello\"","data":null}""", json)
    }

    @Test
    fun `buildCustomResultJson escapes quotes in data`() {
        val r = CustomResult.ok("""{"key":"val"}""")
        val json = buildCustomResultJson(r)
        assertEquals("""{"code":0,"message":"ok","data":"{\"key\":\"val\"}"}""", json)
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd clients/android && ./gradlew :sdk:test --tests "com.clienttools.sdk.http.CustomRouteTest"
```
预期：编译失败，`CustomRoute.kt` 不存在。

- [ ] **Step 3: 创建 CustomRoute.kt**

```kotlin
// clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/CustomRoute.kt
package com.clienttools.sdk.http

/**
 * HTTP 方法枚举。value 字段防止混淆后枚举名变化影响路由匹配。
 */
enum class HttpMethod(val value: String) {
    GET("GET"),
    POST("POST")
}

/**
 * 自定义路由处理结果。构造函数私有，app 只能通过工厂方法构建。
 */
class CustomResult private constructor(
    internal val code: Int,
    internal val message: String,
    internal val data: String?
) {
    companion object {
        fun ok(data: String = "") = CustomResult(0, "ok", data)
        fun error(message: String, code: Int = -1) = CustomResult(code, message, null)
    }
}

/**
 * app 注册的自定义路由。
 * @param path    相对路径，不含 /custom/ 前缀，如 "user/profile"
 * @param method  HTTP 方法
 * @param description 路由用途描述，供 AI 理解
 * @param params  参数名 → 说明（body 字段描述）
 * @param handler 异步处理器，body 为原始请求体字符串
 */
data class CustomRoute(
    val path: String,
    val method: HttpMethod,
    val description: String,
    val params: Map<String, String> = emptyMap(),
    val handler: suspend (body: String?) -> CustomResult
)

/**
 * 将 CustomResult 序列化为标准 JSON 字符串。
 * data 字段始终作为 JSON string 类型（含 null）。
 */
internal fun buildCustomResultJson(result: CustomResult): String {
    fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
    val msg = result.message.esc()
    val dataVal = if (result.data != null) "\"${result.data.esc()}\"" else "null"
    return """{"code":${result.code},"message":"$msg","data":$dataVal}"""
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd clients/android && ./gradlew :sdk:test --tests "com.clienttools.sdk.http.CustomRouteTest"
```
预期：所有测试 PASS。

- [ ] **Step 5: 提交**

```bash
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/CustomRoute.kt \
        clients/android/sdk/src/test/kotlin/com/clienttools/sdk/http/CustomRouteTest.kt
git commit -m "feat(android-sdk): add CustomRoute types (HttpMethod, CustomResult, CustomRoute)"
```

---

## Task 2: Android SDK — ApiHandler 自定义路由处理

**Files:**
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt`

- [ ] **Step 1: 在 ApiHandler.kt 末尾（`}` 前）添加两个函数**

在文件最后一个 `}` 之前，紧接 `handleMockClear` 之后，添加：

```kotlin
    fun handleCustomRoutes(routes: List<CustomRoute>): NanoHTTPD.Response {
        fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
        val items = routes.joinToString(",") { route ->
            val params = route.params.entries.joinToString(",") { (k, v) ->
                "\"${k.esc()}\":\"${v.esc()}\""
            }
            """{"path":"/custom/${route.path}","method":"${route.method.value}","description":"${route.description.esc()}","params":{$params}}"""
        }
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "application/json", "[$items]"
        )
    }

    suspend fun handleCustomCall(
        route: CustomRoute,
        body: String?,
        timeoutMs: Long
    ): NanoHTTPD.Response {
        val result = try {
            kotlinx.coroutines.withTimeout(timeoutMs) { route.handler(body) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            CustomResult.error("handler timeout")
        } catch (e: Exception) {
            CustomResult.error("handler error: ${e.message}")
        }
        val json = buildCustomResultJson(result)
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", json)
    }
```

- [ ] **Step 2: 验证编译通过**

```bash
cd clients/android && ./gradlew :sdk:assembleDebug
```
预期：BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt
git commit -m "feat(android-sdk): add handleCustomRoutes and handleCustomCall to ApiHandler"
```

---

## Task 3: Android SDK — HttpServer 路由 + ClientToolsSDK init

**Files:**
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt`

- [ ] **Step 1: 修改 HttpServer 构造函数，增加 customRoutes 和 customHandlerTimeoutMs**

将 `HttpServer.kt` 第 9 行的类声明改为：

```kotlin
class HttpServer(
    private val context: Context,
    private val pageChangeListener: PageChangeListener,
    private val customRoutes: List<CustomRoute> = emptyList(),
    private val customHandlerTimeoutMs: Long = 4500L
) : NanoHTTPD(8080) {
```

- [ ] **Step 2: 在 HttpServer.serve() 的 when 块末尾（else Not found 之前）插入自定义路由分支**

找到 `serve()` 方法中的：
```kotlin
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
```

在其之前插入：

```kotlin
                method == Method.GET && uri == "/custom/routes" ->
                    ApiHandler.handleCustomRoutes(customRoutes)

                uri.startsWith("/custom/") -> {
                    val path = uri.removePrefix("/custom/")
                    val route = customRoutes.find {
                        it.path == path && it.method.value == method.name
                    }
                    if (route != null)
                        kotlinx.coroutines.runBlocking {
                            ApiHandler.handleCustomCall(route, readBody(session), customHandlerTimeoutMs)
                        }
                    else
                        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
                }

```

- [ ] **Step 3: 修改 ClientToolsSDK.init，接收并透传参数**

将 `ClientToolsSDK.kt` 第 29 行的 `fun init(context: Context)` 改为：

```kotlin
    fun init(
        context: Context,
        customRoutes: List<com.clienttools.sdk.http.CustomRoute> = emptyList(),
        customHandlerTimeoutMs: Long = 4500L
    ) {
        if (isInitialized) return
        try {
            fileStore = InspectorFileStore(context)
            imageFileStore = ImageFileStore(context)
            pageChangeListener = PageChangeListener()
            pageChangeListener!!.register(context)
            httpServer = HttpServer(context, pageChangeListener!!, customRoutes, customHandlerTimeoutMs)
            httpServer!!.startServer()
            registerInspectorLifecycle(context)
            isInitialized = true
            Log.d(TAG, "ClientToolsSDK initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
        }
    }
```

- [ ] **Step 4: 编译验证**

```bash
cd clients/android && ./gradlew :sdk:assembleDebug
```
预期：BUILD SUCCESSFUL。

- [ ] **Step 5: 运行全量 SDK 单元测试**

```bash
cd clients/android && ./gradlew :sdk:test
```
预期：所有测试 PASS，无回归。

- [ ] **Step 6: 提交**

```bash
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt \
        clients/android/sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt
git commit -m "feat(android-sdk): wire custom routes into HttpServer and ClientToolsSDK.init"
```

---

## Task 4: MCP — sdkGetText/sdkPostText + 自定义工具

**Files:**
- Modify: `mcp/src/sdk-client.ts`
- Create: `mcp/src/tools/custom.ts`
- Modify: `mcp/src/index.ts`

- [ ] **Step 1: 在 sdk-client.ts 末尾添加两个文本请求函数**

在文件最后，`sdkDelete` 函数之后追加：

```typescript
const CUSTOM_TIMEOUT_MS = parseInt(process.env.CLIENT_TOOLS_CUSTOM_TIMEOUT_MS ?? "5000", 10);

export async function sdkGetText(path: string): Promise<string> {
  ensureAdbForward();
  const res = await fetchWithTimeout(`${BASE_URL}${path}`, { method: "GET" }, CUSTOM_TIMEOUT_MS);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.text();
}

export async function sdkPostText(path: string, body: string): Promise<string> {
  ensureAdbForward();
  const res = await fetchWithTimeout(
    `${BASE_URL}${path}`,
    {
      method: "POST",
      headers: { "Content-Type": "text/plain" },
      body,
    },
    CUSTOM_TIMEOUT_MS
  );
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.text();
}
```

- [ ] **Step 2: 创建 mcp/src/tools/custom.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkGetText, sdkPostText } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerCustomTools(server: McpServer): void {
  server.tool(
    "list_custom_routes",
    "列出 app 层注册的所有自定义路由，包含路径、HTTP 方法、描述和参数说明（Android/iOS 通用）",
    {},
    async () => {
      try {
        const text = await sdkGetText("/custom/routes");
        return { content: [{ type: "text" as const, text }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "custom_call",
    "调用 app 层注册的自定义路由。响应格式：{\"code\":0,\"message\":\"ok\",\"data\":\"...\"} 或 {\"code\":-1,\"message\":\"...\",\"data\":null}（Android/iOS 通用）",
    {
      path:   z.string().describe("路由路径，如 \"user/profile\"（不含 /custom/ 前缀）"),
      method: z.enum(["GET", "POST"]).describe("HTTP 方法"),
      body:   z.string().optional().describe("请求体字符串（POST 时使用，通常为 JSON）"),
    },
    async ({ path, method, body }) => {
      try {
        const text = method === "GET"
          ? await sdkGetText(`/custom/${path}`)
          : await sdkPostText(`/custom/${path}`, body ?? "");
        return { content: [{ type: "text" as const, text }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

- [ ] **Step 3: 在 mcp/src/index.ts 注册新工具**

在 `registerMockTools(server);` 之后追加一行：

```typescript
import { registerCustomTools } from "./tools/custom.js";
// ...（其他 import 已有）

registerCustomTools(server);
```

完整 index.ts 应如下：

```typescript
#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { registerWebviewTools } from "./tools/webview.js";
import { registerImageTools } from "./tools/image.js";
import { registerDomTools } from "./tools/dom.js";
import { registerViewTools } from "./tools/view.js";
import { registerInspectorTools } from "./tools/inspector.js";
import { registerPageTools } from "./tools/page.js";
import { registerMockTools } from "./tools/mock.js";
import { registerCustomTools } from "./tools/custom.js";

const server = new McpServer({
  name: "client-tools",
  version: "0.1.0",
});

registerWebviewTools(server);
registerImageTools(server);
registerDomTools(server);
registerViewTools(server);
registerInspectorTools(server);
registerPageTools(server);
registerMockTools(server);
registerCustomTools(server);

const transport = new StdioServerTransport();
await server.connect(transport);
```

- [ ] **Step 4: 编译验证**

```bash
cd mcp && npm run build
```
预期：无编译错误，`dist/` 下生成 `tools/custom.js`。

- [ ] **Step 5: 提交**

```bash
git add mcp/src/sdk-client.ts mcp/src/tools/custom.ts mcp/src/index.ts
git commit -m "feat(mcp): add list_custom_routes and custom_call tools"
```

---

## Task 5: iOS SDK — CustomRoute 类型定义

**Files:**
- Create: `clients/ios/sdk/Sources/HttpServer/CustomRoute.swift`

- [ ] **Step 1: 创建 CustomRoute.swift**

```swift
// clients/ios/sdk/Sources/HttpServer/CustomRoute.swift
import Foundation

/// HTTP 方法枚举。value 属性防止混淆后枚举名变化影响路由匹配。
public enum HttpMethod: Int {
    case get
    case post

    var value: String {
        switch self {
        case .get:  return "GET"
        case .post: return "POST"
        }
    }
}

/// 自定义路由处理结果。构造函数私有，app 只能通过工厂方法构建。
public final class CustomResult {
    let code: Int
    let message: String
    let data: String?

    private init(code: Int, message: String, data: String?) {
        self.code = code
        self.message = message
        self.data = data
    }

    public static func ok(_ data: String = "") -> CustomResult {
        CustomResult(code: 0, message: "ok", data: data)
    }

    public static func error(_ message: String, code: Int = -1) -> CustomResult {
        CustomResult(code: code, message: message, data: nil)
    }

    /// 序列化为标准 JSON 字符串，data 始终作为 JSON string 类型（含 null）。
    func toJson() -> String {
        func esc(_ s: String) -> String {
            s.replacingOccurrences(of: "\\", with: "\\\\")
             .replacingOccurrences(of: "\"", with: "\\\"")
        }
        let msg = esc(message)
        if let d = data {
            return "{\"code\":\(code),\"message\":\"\(msg)\",\"data\":\"\(esc(d))\"}"
        } else {
            return "{\"code\":\(code),\"message\":\"\(msg)\",\"data\":null}"
        }
    }
}

/// app 注册的自定义路由。
/// - path: 相对路径，不含 /custom/ 前缀，如 "user/profile"
/// - method: HTTP 方法
/// - description: 路由用途描述，供 AI 理解
/// - params: 参数名 → 说明（body 字段描述）
/// - handler: 异步处理器，可抛异常，SDK 统一捕获并包装为 error 响应
public struct CustomRoute {
    public let path: String
    public let method: HttpMethod
    public let description: String
    public let params: [String: String]
    public let handler: (String?) async throws -> CustomResult

    public init(
        path: String,
        method: HttpMethod,
        description: String,
        params: [String: String] = [:],
        handler: @escaping (String?) async throws -> CustomResult
    ) {
        self.path = path
        self.method = method
        self.description = description
        self.params = params
        self.handler = handler
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' \
  build 2>&1 | tail -5
```
预期：`BUILD SUCCEEDED`。

- [ ] **Step 3: 提交**

```bash
git add clients/ios/sdk/Sources/HttpServer/CustomRoute.swift
git commit -m "feat(ios-sdk): add CustomRoute types (HttpMethod, CustomResult, CustomRoute)"
```

---

## Task 6: iOS SDK — HttpServer 路由 + ClientToolsSDK start()

**Files:**
- Modify: `clients/ios/sdk/Sources/HttpServer/HttpServer.swift`
- Modify: `clients/ios/sdk/Sources/ClientToolsSDK.swift`

- [ ] **Step 1: 修改 HttpServer.init，增加 customRoutes 和 customHandlerTimeoutMs**

将第 21 行的 `init(port: Int = 8080)` 改为：

```swift
    private let customRoutes: [CustomRoute]
    private let customHandlerTimeoutMs: Int

    init(port: Int = 8080, customRoutes: [CustomRoute] = [], customHandlerTimeoutMs: Int = 4500) {
        self.port = port
        self.customRoutes = customRoutes
        self.customHandlerTimeoutMs = customHandlerTimeoutMs
        self.listener = try? NWListener(using: .tcp, on: NWEndpoint.Port(integerLiteral: UInt16(port)))
    }
```

- [ ] **Step 2: 在 processRequest 的 default 分支末尾（sendError 404 之前）插入自定义路由处理**

找到 `processRequest` 中的：
```swift
            } else {
                sendError(code: 404, message: "Not found", httpCode: 404, connection: connection)
            }
```

在其之前插入：

```swift
            } else if method == "GET" && path == "/custom/routes" {
                handleCustomRoutes(connection: connection)
            } else if path.hasPrefix("/custom/") {
                let customPath = String(path.dropFirst("/custom/".count))
                if let route = customRoutes.first(where: {
                    $0.path == customPath && $0.method.value == method
                }) {
                    let bodyStr = String(data: bodyData, encoding: .utf8)
                    handleCustomCall(route, body: bodyStr, connection: connection)
                } else {
                    sendError(code: 404, message: "Not found", httpCode: 404, connection: connection)
                }
```

- [ ] **Step 3: 在 HttpServer.swift 末尾（最后一个 `}` 前）添加两个新方法**

```swift
    private func handleCustomRoutes(connection: NWConnection) {
        func esc(_ s: String) -> String {
            s.replacingOccurrences(of: "\\", with: "\\\\")
             .replacingOccurrences(of: "\"", with: "\\\"")
        }
        let items = customRoutes.map { route -> String in
            let paramsJson = route.params.map { k, v in
                "\"\(esc(k))\":\"\(esc(v))\""
            }.joined(separator: ",")
            return "{\"path\":\"/custom/\(route.path)\",\"method\":\"\(route.method.value)\",\"description\":\"\(esc(route.description))\",\"params\":{\(paramsJson)}}"
        }.joined(separator: ",")
        sendJson("[\(items)]", connection: connection)
    }

    private func handleCustomCall(_ route: CustomRoute, body: String?, connection: NWConnection) {
        let timeoutMs = customHandlerTimeoutMs
        let sema = DispatchSemaphore(value: 0)
        let lock = NSLock()
        var result = CustomResult.error("handler timeout")
        var signaled = false

        func signalOnce(_ r: CustomResult) {
            lock.lock(); defer { lock.unlock() }
            guard !signaled else { return }
            signaled = true
            result = r
            sema.signal()
        }

        Task {
            do {
                let r = try await route.handler(body)
                signalOnce(r)
            } catch {
                signalOnce(CustomResult.error("handler error: \(error.localizedDescription)"))
            }
        }

        DispatchQueue.global().asyncAfter(deadline: .now() + .milliseconds(timeoutMs)) {
            signalOnce(CustomResult.error("handler timeout"))
        }

        sema.wait()
        sendJson(result.toJson(), connection: connection)
    }
```

- [ ] **Step 4: 修改 ClientToolsSDK.swift — start() 增加参数并透传**

将 `ClientToolsSDK.swift` 中的 `private var customRoutes: [CustomRoute] = []` 和 `private var customHandlerTimeoutMs: Int = 4500` 两个属性，以及 `start()` 方法改为：

```swift
    private var customRoutes: [CustomRoute] = []
    private var customHandlerTimeoutMs: Int = 4500

    public func start(
        port: Int = 8080,
        customRoutes: [CustomRoute] = [],
        customHandlerTimeoutMs: Int = 4500
    ) {
        #if DEBUG
        guard !isRunning else { return }
        isRunning = true
        self.port = port
        self.customRoutes = customRoutes
        self.customHandlerTimeoutMs = customHandlerTimeoutMs
        startHttpServer(port: port)
        startPageTracking()
        startOverlayManager()
        print("[ClientToolsSDK] started on port \(port)")
        #endif
    }

    private func startHttpServer(port: Int) {
        httpServer = HttpServer(port: port, customRoutes: customRoutes, customHandlerTimeoutMs: customHandlerTimeoutMs)
        httpServer?.start()
    }
```

- [ ] **Step 5: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' \
  build 2>&1 | tail -5
```
预期：`BUILD SUCCEEDED`。

- [ ] **Step 6: 提交**

```bash
git add clients/ios/sdk/Sources/HttpServer/HttpServer.swift \
        clients/ios/sdk/Sources/ClientToolsSDK.swift
git commit -m "feat(ios-sdk): wire custom routes into HttpServer and ClientToolsSDK.start"
```

---

## Task 7: Android Demo 接入

**Files:**
- Modify: `clients/android/demo/src/main/kotlin/com/clienttools/demo/DemoApplication.kt`
- Modify: `clients/android/demo/src/main/kotlin/com/clienttools/demo/LoginActivity.kt`

- [ ] **Step 1: 修改 DemoApplication.kt**

```kotlin
package com.clienttools.demo

import android.app.Application
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.http.CustomResult
import com.clienttools.sdk.http.CustomRoute
import com.clienttools.sdk.http.HttpMethod
import com.clienttools.sdk.mock.MockInterceptor
import com.clienttools.demo.model.UserInfo
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import org.json.JSONObject

class DemoApplication : Application() {

    companion object {
        lateinit var httpClient: OkHttpClient
            private set

        // 跨 Activity 共享当前登录用户状态
        var currentUser: UserInfo? = null
        var currentToken: String = ""
    }

    override fun onCreate() {
        super.onCreate()
        ClientToolsSDK.init(
            context = this,
            customRoutes = listOf(
                // 分支：正常 + 业务错误（未登录）
                CustomRoute(
                    path = "demo/current-user",
                    method = HttpMethod.GET,
                    description = "返回当前登录用户信息，未登录时返回 error",
                    handler = { _ ->
                        val user = currentUser
                            ?: return@CustomRoute CustomResult.error("not logged in")
                        CustomResult.ok("""{"id":"${user.id}","name":"${user.name}","phone":"${user.phone}","email":"${user.email}","tokenPrefix":"${currentToken.take(20)}"}""")
                    }
                ),
                // 分支：正常 + 参数校验错误
                CustomRoute(
                    path = "demo/set-username",
                    method = HttpMethod.POST,
                    description = "更新当前展示的用户名，name 为空时返回 error",
                    params = mapOf("name" to "新用户名"),
                    handler = { body ->
                        val name = JSONObject(body ?: "{}").optString("name")
                        if (name.isBlank()) return@CustomRoute CustomResult.error("name is required")
                        currentUser = currentUser?.copy(name = name)
                            ?: return@CustomRoute CustomResult.error("not logged in")
                        CustomResult.ok("""{"name":"$name"}""")
                    }
                ),
                // 分支：超时（delay > customHandlerTimeoutMs = 4500ms）
                CustomRoute(
                    path = "demo/slow-query",
                    method = HttpMethod.GET,
                    description = "模拟耗时操作，固定 delay 6000ms，必然触发 handler 超时",
                    handler = { _ ->
                        delay(6000)
                        CustomResult.ok("""{"result":"should not reach here"}""")
                    }
                ),
                // 分支：未捕获异常
                CustomRoute(
                    path = "demo/crash",
                    method = HttpMethod.GET,
                    description = "直接抛出异常，验证 SDK 捕获并包装为 error 响应",
                    handler = { _ ->
                        throw RuntimeException("intentional crash for testing")
                    }
                )
            )
        )
        httpClient = OkHttpClient.Builder()
            .addInterceptor(MockInterceptor())
            .build()
    }
}
```

- [ ] **Step 2: 修改 LoginActivity.kt — navigateToUserInfo 中写入全局状态**

找到 `LoginActivity.kt` 第 200 行的 `navigateToUserInfo` 方法，在 `startActivity(...)` 之前插入两行：

```kotlin
    private fun navigateToUserInfo(user: UserInfo, token: String) {
        DemoApplication.currentUser = user      // 写入全局状态供自定义路由读取
        DemoApplication.currentToken = token
        startActivity(Intent(this, UserInfoActivity::class.java).apply {
            putExtra(UserInfoActivity.KEY_USER, user)
            putExtra(UserInfoActivity.KEY_TOKEN, token)
        })
        finish()
    }
```

- [ ] **Step 3: 编译验证**

```bash
cd clients/android && ./gradlew :demo:assembleDebug
```
预期：BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add clients/android/demo/src/main/kotlin/com/clienttools/demo/DemoApplication.kt \
        clients/android/demo/src/main/kotlin/com/clienttools/demo/LoginActivity.kt
git commit -m "feat(android-demo): register custom routes covering all handler branches"
```

---

## Task 8: iOS Demo 接入

**Files:**
- Modify: `clients/ios/demo/Sources/ClientToolsDemo/AppDelegate.swift`

- [ ] **Step 1: 修改 AppDelegate.swift**

```swift
import UIKit
import ClientToolsSDK

// 简单结构体，持有 demo 用户状态（iOS demo 无真实登录流程，启动时预填）
struct DemoUser {
    var id: String
    var name: String
    var phone: String
    var email: String
}

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    var currentUser: DemoUser? = DemoUser(
        id: "demo-001",
        name: "Demo User",
        phone: "138****8888",
        email: "demo@example.com"
    )
    var currentToken: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.demo"

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        ClientToolsSDK.shared.start(
            customRoutes: [
                // 分支：正常 + 业务错误（未登录）
                CustomRoute(
                    path: "demo/current-user",
                    method: .get,
                    description: "返回当前登录用户信息，未登录时返回 error",
                    handler: { [weak self] _ in
                        guard let user = self?.currentUser else {
                            return .error("not logged in")
                        }
                        let token = self?.currentToken.prefix(20) ?? ""
                        return .ok("{\"id\":\"\(user.id)\",\"name\":\"\(user.name)\",\"phone\":\"\(user.phone)\",\"email\":\"\(user.email)\",\"tokenPrefix\":\"\(token)\"}")
                    }
                ),
                // 分支：正常 + 参数校验错误
                CustomRoute(
                    path: "demo/set-username",
                    method: .post,
                    description: "更新当前展示的用户名，name 为空时返回 error",
                    params: ["name": "新用户名"],
                    handler: { [weak self] body in
                        guard let body = body,
                              let data = body.data(using: .utf8),
                              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                              let name = json["name"] as? String,
                              !name.isEmpty
                        else { return .error("name is required") }
                        self?.currentUser?.name = name
                        return .ok("{\"name\":\"\(name)\"}")
                    }
                ),
                // 分支：超时（sleep 6s > customHandlerTimeoutMs 4500ms）
                CustomRoute(
                    path: "demo/slow-query",
                    method: .get,
                    description: "模拟耗时操作，固定 delay 6000ms，必然触发 handler 超时",
                    handler: { _ in
                        try? await Task.sleep(nanoseconds: 6_000_000_000)
                        return .ok("{\"result\":\"should not reach here\"}")
                    }
                ),
                // 分支：未捕获异常
                CustomRoute(
                    path: "demo/crash",
                    method: .get,
                    description: "直接抛出异常，验证 SDK 捕获并包装为 error 响应",
                    handler: { _ in
                        throw NSError(
                            domain: "DemoError",
                            code: -1,
                            userInfo: [NSLocalizedDescriptionKey: "intentional crash for testing"]
                        )
                    }
                )
            ]
        )
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }

    func application(
        _ application: UIApplication,
        didDiscardSceneSessions sceneSessions: Set<UISceneSession>
    ) {}
}
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' \
  build 2>&1 | tail -5
```
预期：`BUILD SUCCEEDED`。

- [ ] **Step 3: 提交**

```bash
git add clients/ios/demo/Sources/ClientToolsDemo/AppDelegate.swift
git commit -m "feat(ios-demo): register custom routes covering all handler branches"
```

---

## Task 9: 文档更新

**Files:**
- Modify: `docs/mcp-tools.md`
- Modify: `docs/sdk-http-api.md`

- [ ] **Step 1: 在 docs/mcp-tools.md 概览表格中追加两行**

找到 Mock 行之后（`| mock_clear | ...`），追加：

```markdown
| 自定义路由 | `list_custom_routes` | 列出 app 注册的所有自定义路由 |
| | `custom_call` | 调用指定自定义路由 |
```

- [ ] **Step 2: 在 docs/mcp-tools.md 末尾追加工具详情章节**

```markdown
---

## 自定义路由

### list_custom_routes

列出 app 层在 SDK 初始化时注册的所有自定义路由。Android/iOS 通用。

**参数：** 无

**返回：**
```json
[
  {
    "path": "/custom/user/profile",
    "method": "GET",
    "description": "获取当前登录用户信息",
    "params": {}
  },
  {
    "path": "/custom/order/submit",
    "method": "POST",
    "description": "提交订单",
    "params": {
      "orderId": "订单ID",
      "quantity": "数量"
    }
  }
]
```

---

### custom_call

调用 app 层注册的自定义路由。Android/iOS 通用。

**参数：**

| 名称 | 类型 | 必填 | 说明 |
|------|------|------|------|
| path | string | 是 | 路由路径，如 `"user/profile"`（不含 `/custom/` 前缀） |
| method | string | 是 | `"GET"` 或 `"POST"` |
| body | string | 否 | 请求体字符串（POST 时使用，通常为 JSON） |

**返回：**
```json
{"code": 0, "message": "ok", "data": "业务数据字符串"}
```
> 失败时：`{"code": -1, "message": "错误描述", "data": null}`
> 超时时：`{"code": -1, "message": "handler timeout", "data": null}`
```

- [ ] **Step 3: 在 docs/sdk-http-api.md 接口概览表格末尾追加两行**

找到 `/mock/rules DELETE` 行之后，追加：

```markdown
| `/custom/routes` | GET | ✓ | ✓ | 列出自定义路由元数据 |
| `/custom/{path}` | GET/POST | ✓ | ✓ | 调用 app 自定义路由 |
```

- [ ] **Step 4: 在 docs/sdk-http-api.md 接口详情末尾追加章节**

```markdown
---

### GET /custom/routes

列出 app 层注册的所有自定义路由元数据。

**响应：** JSON 数组（`Content-Type: application/json`）
```json
[
  {
    "path": "/custom/user/profile",
    "method": "GET",
    "description": "获取当前登录用户信息",
    "params": {}
  }
]
```

---

### GET|POST /custom/{path}

调用 app 层注册的自定义路由。`path` 为注册时的相对路径。

**请求体（POST）：** 原始字符串，通常为 JSON

**响应：** JSON 字符串（`Content-Type: text/plain`）
```json
{"code": 0, "message": "ok", "data": "业务数据字符串"}
```

| code | 含义 |
|------|------|
| 0 | 成功，data 为 handler 返回的字符串 |
| -1 | 失败（业务错误 / handler 异常 / 超时），message 说明原因 |
```

- [ ] **Step 5: 提交**

```bash
git add docs/mcp-tools.md docs/sdk-http-api.md
git commit -m "docs: add list_custom_routes and custom_call to mcp-tools.md and sdk-http-api.md"
```

---

## 自检结果

经对 spec 逐节比对：

| Spec 要求 | 覆盖任务 |
|-----------|----------|
| HttpMethod 枚举带 value 字段防混淆 | Task 1, Task 5 |
| CustomResult 工厂方法，构造私有 | Task 1, Task 5 |
| CustomRoute 含 path/method/description/params/handler | Task 1, Task 5 |
| SDK init/start 接收 customRoutes + customHandlerTimeoutMs | Task 3, Task 6 |
| /custom/routes 返回 JSON 元数据 | Task 2, Task 6 |
| /custom/{path} 路由转发 + runBlocking | Task 3, Task 6 |
| handler 超时用 withTimeout/DispatchSemaphore | Task 2, Task 6 |
| SDK 捕获异常并包装 | Task 2, Task 6 |
| MCP sdkGetText/sdkPostText（CUSTOM_TIMEOUT_MS 环境变量） | Task 4 |
| MCP list_custom_routes + custom_call | Task 4 |
| Android Demo 4 条路由覆盖所有分支 | Task 7 |
| iOS Demo 4 条路由覆盖所有分支 | Task 8 |
| 文档同步 | Task 9 |

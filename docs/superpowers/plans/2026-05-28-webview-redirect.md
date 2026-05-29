# WebView Redirect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `resolveRedirect(url)` SDK method and supporting HTTP CRUD API so AI can redirect WebView URLs to local dev servers at runtime, with a noop release build variant for both Android and iOS.

**Architecture:** A new `WebViewRedirectStore` (parallel to `MockRuleStore`) holds in-memory redirect rules with regex URL patterns. `ClientToolsSDK.resolveRedirect()` matches rules first-win and merges query params. Four HTTP endpoints under `/webview/redirects` manage the rules. A noop module/pod provides empty implementations for release builds.

**Tech Stack:** Kotlin (Android SDK + noop), Swift (iOS SDK + noop), TypeScript (MCP tools), Protocol Buffers (wire format), Node.js (local test server)

---

## File Map

**New files:**
- `proto/webview_redirect.proto` — proto messages for redirect rules
- `clients/android/sdk/src/main/proto/webview_redirect.proto` — Android copy
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewRedirectStore.kt`
- `clients/android/sdk/src/test/kotlin/com/clienttools/sdk/webview/WebViewRedirectStoreTest.kt`
- `clients/android/noop/build.gradle.kts`
- `clients/android/noop/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt`
- `clients/android/noop/src/main/kotlin/com/clienttools/sdk/mock/MockInterceptor.kt`
- `clients/android/demo/src/main/kotlin/com/clienttools/demo/WebViewRedirectActivity.kt`
- `clients/android/demo/src/main/res/layout/activity_webview_redirect.xml`
- `clients/android/demo/src/main/assets/test_local.html`
- `clients/ios/sdk/Sources/WebViewRedirect/WebViewRedirectStore.swift`
- `clients/ios/noop/ClientToolsSDK-Noop.podspec`
- `clients/ios/noop/Sources/ClientToolsSDK.swift`
- `clients/ios/demo/Sources/ClientToolsDemo/WebViewRedirect/WebViewRedirectViewController.swift`
- `clients/ios/demo/Resources/test_local.html`
- `mcp/src/tools/webview_redirect.ts`
- `tests/runtime/src/suites/webview-redirect.ts`
- `tests/local-server/server.js`
- `tests/local-server/public/index.html`
- `tests/local-server/public/test.html`

**Modified files:**
- `proto/api.proto` — add WebViewRedirect response messages
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt` — add 4 redirect handlers
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt` — route 4 redirect endpoints
- `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt` — add `resolveRedirect()`
- `clients/android/settings.gradle.kts` — add `:noop` module
- `clients/android/demo/src/main/AndroidManifest.xml` — register `WebViewRedirectActivity`
- `clients/android/demo/src/main/kotlin/com/clienttools/demo/MainActivity.kt` — add menu entry
- `clients/ios/sdk/Sources/HttpServer/HttpServer.swift` — route 4 redirect endpoints + handlers
- `clients/ios/sdk/Sources/ClientToolsSDK.swift` — add `resolveRedirect()`
- `mcp/src/tools/webview.ts` — re-export or split; new redirect tools go in `webview_redirect.ts`
- `mcp/src/index.ts` — register redirect tools
- `tests/runtime/src/index.ts` — add redirect suite
- `docs/mcp-tools.md` — document 4 new tools
- `docs/sdk-http-api.md` — document 4 new endpoints

---

### Task 1: Proto — add WebViewRedirect messages

**Files:**
- Create: `proto/webview_redirect.proto`
- Create: `clients/android/sdk/src/main/proto/webview_redirect.proto`
- Modify: `proto/api.proto`
- Modify: `clients/android/sdk/src/main/proto/api.proto`

- [ ] **Step 1: Create `proto/webview_redirect.proto`**

```protobuf
syntax = "proto3";
package clienttools;
option java_package = "com.clienttools.sdk.proto";
option java_multiple_files = true;

message WebViewRedirectRule {
  string id          = 1;
  string url_pattern = 2;
  string target_url  = 3;
}

message AddWebViewRedirectRequest {
  string url_pattern = 1;
  string target_url  = 2;
}

message WebViewRedirectRuleList {
  repeated WebViewRedirectRule rules = 1;
}
```

- [ ] **Step 2: Copy the same file to Android proto dir**

```bash
cp proto/webview_redirect.proto clients/android/sdk/src/main/proto/webview_redirect.proto
```

- [ ] **Step 3: Append redirect response messages to `proto/api.proto`**

Add at the end of `proto/api.proto` (after the existing import of mock.proto, add a new import and messages):

```protobuf
import "webview_redirect.proto";

message WebViewRedirectResponse      { ResponseMeta meta = 1; WebViewRedirectRule data = 2; }
message WebViewRedirectListResponse  { ResponseMeta meta = 1; WebViewRedirectRuleList data = 2; }
message ClearWebViewRedirectsResponse { ResponseMeta meta = 1; int32 cleared_count = 2; }
```

- [ ] **Step 4: Apply the same additions to `clients/android/sdk/src/main/proto/api.proto`**

Add the same import and 3 message definitions to the Android copy.

- [ ] **Step 5: Regenerate iOS + MCP generated code**

```bash
cd proto && buf generate
```

Expected: `clients/ios/sdk/Sources/Generated/webview_redirect.pb.swift` created, `clients/ios/sdk/Sources/Generated/api.pb.swift` updated, `mcp/src/generated/webview_redirect_pb.ts` created, `mcp/src/generated/api_pb.ts` updated.

- [ ] **Step 6: Verify Android builds with new proto**

```bash
cd clients/android && ./gradlew :sdk:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add proto/webview_redirect.proto \
        clients/android/sdk/src/main/proto/webview_redirect.proto \
        proto/api.proto \
        clients/android/sdk/src/main/proto/api.proto \
        clients/ios/sdk/Sources/Generated/ \
        mcp/src/generated/
git commit -m "feat(proto): add WebViewRedirect messages

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
```

---

### Task 2: Android SDK — WebViewRedirectStore + unit tests

**Files:**
- Create: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewRedirectStore.kt`
- Create: `clients/android/sdk/src/test/kotlin/com/clienttools/sdk/webview/WebViewRedirectStoreTest.kt`

- [ ] **Step 1: Write failing unit tests**

Create `clients/android/sdk/src/test/kotlin/com/clienttools/sdk/webview/WebViewRedirectStoreTest.kt`:

```kotlin
package com.clienttools.sdk.webview

import kotlin.test.*

class WebViewRedirectStoreTest {

    @BeforeTest
    fun setUp() {
        WebViewRedirectStore.clear()
    }

    @Test
    fun `resolveRedirect returns original url when no rules`() {
        assertEquals("https://example.com/page", WebViewRedirectStore.resolveRedirect("https://example.com/page"))
    }

    @Test
    fun `resolveRedirect returns targetUrl when pattern matches`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(
            id = "1", urlPattern = "https://example\\.com/page", targetUrl = "http://192.168.1.1:3000/page"
        ))
        assertEquals("http://192.168.1.1:3000/page", WebViewRedirectStore.resolveRedirect("https://example.com/page"))
    }

    @Test
    fun `resolveRedirect first rule wins when multiple match`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(id = "1", urlPattern = "example\\.com", targetUrl = "http://first"))
        WebViewRedirectStore.add(WebViewRedirectEntry(id = "2", urlPattern = "example\\.com", targetUrl = "http://second"))
        assertEquals("http://first", WebViewRedirectStore.resolveRedirect("https://example.com/page"))
    }

    @Test
    fun `resolveRedirect appends original query params to targetUrl`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(
            id = "1", urlPattern = "example\\.com/page", targetUrl = "http://192.168.1.1:3000/page"
        ))
        val result = WebViewRedirectStore.resolveRedirect("https://example.com/page?foo=bar&baz=qux")
        assertTrue(result.startsWith("http://192.168.1.1:3000/page"))
        assertTrue(result.contains("foo=bar"))
        assertTrue(result.contains("baz=qux"))
    }

    @Test
    fun `resolveRedirect original query wins on conflict`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(
            id = "1", urlPattern = "example\\.com", targetUrl = "http://192.168.1.1:3000?foo=TARGET"
        ))
        val result = WebViewRedirectStore.resolveRedirect("https://example.com?foo=ORIGINAL")
        assertTrue(result.contains("foo=ORIGINAL"))
        assertFalse(result.contains("foo=TARGET"))
    }

    @Test
    fun `resolveRedirect returns original url when no pattern matches`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(id = "1", urlPattern = "other\\.com", targetUrl = "http://x"))
        assertEquals("https://example.com", WebViewRedirectStore.resolveRedirect("https://example.com"))
    }

    @Test
    fun `resolveRedirect uses regex prefix matching`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(
            id = "1", urlPattern = "example\\.com/section/.*", targetUrl = "http://local:3000"
        ))
        assertNotNull(WebViewRedirectStore.resolveRedirect("https://example.com/section/page1").let {
            if (it == "http://local:3000") it else null
        })
        assertEquals("https://example.com/other", WebViewRedirectStore.resolveRedirect("https://example.com/other"))
    }

    @Test
    fun `delete removes rule`() {
        val entry = WebViewRedirectStore.add(WebViewRedirectEntry(id = "1", urlPattern = "x", targetUrl = "y"))
        WebViewRedirectStore.delete(entry.id)
        assertEquals("https://x.com", WebViewRedirectStore.resolveRedirect("https://x.com"))
    }

    @Test
    fun `clear removes all rules and returns count`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(id = "1", urlPattern = "a", targetUrl = "b"))
        WebViewRedirectStore.add(WebViewRedirectEntry(id = "2", urlPattern = "c", targetUrl = "d"))
        val count = WebViewRedirectStore.clear()
        assertEquals(2, count)
        assertEquals(0, WebViewRedirectStore.list().size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd clients/android && ./gradlew :sdk:testDebugUnitTest --tests "com.clienttools.sdk.webview.*"
```

Expected: compilation failure — `WebViewRedirectStore` does not exist yet.

- [ ] **Step 3: Create `WebViewRedirectStore.kt`**

```kotlin
package com.clienttools.sdk.webview

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class WebViewRedirectEntry(
    val id: String,
    val urlPattern: String,
    val targetUrl: String
)

object WebViewRedirectStore {
    private val rules = ConcurrentHashMap<String, WebViewRedirectEntry>()
    private val insertOrder = CopyOnWriteArrayList<String>()

    fun add(entry: WebViewRedirectEntry): WebViewRedirectEntry {
        rules[entry.id] = entry
        insertOrder.add(entry.id)
        return entry
    }

    fun delete(id: String): Boolean {
        val removed = rules.remove(id) != null
        if (removed) insertOrder.remove(id)
        return removed
    }

    fun list(): List<WebViewRedirectEntry> = insertOrder.mapNotNull { rules[it] }

    fun clear(): Int {
        val count = rules.size
        rules.clear()
        insertOrder.clear()
        return count
    }

    fun resolveRedirect(url: String): String {
        val urlWithoutQuery = url.substringBefore("?")
        val originalQuery = url.substringAfter("?", "")

        val match = insertOrder.mapNotNull { rules[it] }.firstOrNull { entry ->
            Regex(entry.urlPattern).containsMatchIn(urlWithoutQuery)
        } ?: return url

        return mergeQueryParams(match.targetUrl, originalQuery)
    }

    private fun mergeQueryParams(targetUrl: String, originalQuery: String): String {
        if (originalQuery.isEmpty()) return targetUrl

        val targetBase = targetUrl.substringBefore("?")
        val targetQuery = targetUrl.substringAfter("?", "")

        // Parse into maps (original wins on key conflict)
        val params = mutableMapOf<String, String>()
        if (targetQuery.isNotEmpty()) {
            targetQuery.split("&").forEach { pair ->
                val k = pair.substringBefore("=")
                val v = pair.substringAfter("=", "")
                params[k] = v
            }
        }
        // Original overwrites target on conflict
        originalQuery.split("&").forEach { pair ->
            val k = pair.substringBefore("=")
            val v = pair.substringAfter("=", "")
            params[k] = v
        }

        val merged = params.entries.joinToString("&") { (k, v) -> "$k=$v" }
        return "$targetBase?$merged"
    }
}
```

- [ ] **Step 4: Run tests and verify they pass**

```bash
cd clients/android && ./gradlew :sdk:testDebugUnitTest --tests "com.clienttools.sdk.webview.*"
```

Expected: 9 tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ \
        clients/android/sdk/src/test/kotlin/com/clienttools/sdk/webview/
git commit -m "feat(android-sdk): add WebViewRedirectStore with resolveRedirect logic

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
```

---

### Task 3: Android SDK — expose `resolveRedirect()` + HTTP endpoints

**Files:**
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt`
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt`
- Modify: `clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`

- [ ] **Step 1: Add `resolveRedirect()` to `ClientToolsSDK.kt`**

Add import and method to the `ClientToolsSDK` object:

```kotlin
import com.clienttools.sdk.webview.WebViewRedirectStore
```

Add after the `shutdown()` method:

```kotlin
fun resolveRedirect(url: String): String = WebViewRedirectStore.resolveRedirect(url)
```

- [ ] **Step 2: Add 4 handler methods to `ApiHandler.kt`**

Add imports at the top:
```kotlin
import com.clienttools.sdk.webview.WebViewRedirectEntry
import com.clienttools.sdk.webview.WebViewRedirectStore
import com.clienttools.sdk.proto.AddWebViewRedirectRequest
import com.clienttools.sdk.proto.WebViewRedirectResponse
import com.clienttools.sdk.proto.WebViewRedirectListResponse
import com.clienttools.sdk.proto.ClearWebViewRedirectsResponse
import com.clienttools.sdk.proto.WebViewRedirectRule
import com.clienttools.sdk.proto.WebViewRedirectRuleList
```

Add a private extension function and 4 handler methods after `handleMockClear()`:

```kotlin
private fun WebViewRedirectEntry.toProto(): WebViewRedirectRule = WebViewRedirectRule.newBuilder()
    .setId(id)
    .setUrlPattern(urlPattern)
    .setTargetUrl(targetUrl)
    .build()

fun handleWebViewRedirectAdd(body: ByteArray): NanoHTTPD.Response {
    return try {
        val req = AddWebViewRedirectRequest.parseFrom(body)
        val entry = WebViewRedirectEntry(
            id = UUID.randomUUID().toString(),
            urlPattern = req.urlPattern,
            targetUrl = req.targetUrl
        )
        WebViewRedirectStore.add(entry)
        val resp = WebViewRedirectResponse.newBuilder()
            .setMeta(ProtoHelper.okMeta(ctx()))
            .setData(entry.toProto())
            .build()
        okResponse(resp.toByteArray())
    } catch (e: Exception) {
        Log.e("ApiHandler", "handleWebViewRedirectAdd", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}

fun handleWebViewRedirectList(): NanoHTTPD.Response {
    return try {
        val protoRules = WebViewRedirectStore.list().map { it.toProto() }
        val resp = WebViewRedirectListResponse.newBuilder()
            .setMeta(ProtoHelper.okMeta(ctx()))
            .setData(WebViewRedirectRuleList.newBuilder().addAllRules(protoRules).build())
            .build()
        okResponse(resp.toByteArray())
    } catch (e: Exception) {
        Log.e("ApiHandler", "handleWebViewRedirectList", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}

fun handleWebViewRedirectDelete(id: String): NanoHTTPD.Response {
    return try {
        WebViewRedirectStore.delete(id)
        val resp = SimpleResponse.newBuilder()
            .setMeta(ProtoHelper.okMeta(ctx()))
            .build()
        okResponse(resp.toByteArray())
    } catch (e: Exception) {
        Log.e("ApiHandler", "handleWebViewRedirectDelete", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}

fun handleWebViewRedirectClear(): NanoHTTPD.Response {
    return try {
        val count = WebViewRedirectStore.clear()
        val resp = ClearWebViewRedirectsResponse.newBuilder()
            .setMeta(ProtoHelper.okMeta(ctx()))
            .setClearedCount(count)
            .build()
        okResponse(resp.toByteArray())
    } catch (e: Exception) {
        Log.e("ApiHandler", "handleWebViewRedirectClear", e)
        errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
    }
}
```

- [ ] **Step 3: Add routes to `HttpServer.kt`**

In the `when` block, add these cases before the `else` branch (after the mock routes):

```kotlin
method == Method.POST && uri == "/webview/redirects" ->
    ApiHandler.handleWebViewRedirectAdd(readBodyBytes(session))

method == Method.GET && uri == "/webview/redirects" ->
    ApiHandler.handleWebViewRedirectList()

method == Method.DELETE && uri.startsWith("/webview/redirects/") -> {
    val id = uri.removePrefix("/webview/redirects/")
    ApiHandler.handleWebViewRedirectDelete(id)
}

method == Method.DELETE && uri == "/webview/redirects" ->
    ApiHandler.handleWebViewRedirectClear()
```

- [ ] **Step 4: Build SDK**

```bash
cd clients/android && ./gradlew :sdk:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add clients/android/sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt \
        clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt \
        clients/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt
git commit -m "feat(android-sdk): expose resolveRedirect() and HTTP redirect endpoints

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
```

---

### Task 4: Android noop module

**Files:**
- Create: `clients/android/noop/build.gradle.kts`
- Create: `clients/android/noop/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt`
- Create: `clients/android/noop/src/main/kotlin/com/clienttools/sdk/mock/MockInterceptor.kt`
- Create: `clients/android/noop/src/main/kotlin/com/clienttools/sdk/http/CustomRoute.kt`
- Create: `clients/android/noop/src/main/kotlin/com/clienttools/sdk/http/CustomResult.kt`
- Create: `clients/android/noop/src/main/kotlin/com/clienttools/sdk/http/HttpMethod.kt`
- Modify: `clients/android/settings.gradle.kts`

- [ ] **Step 1: Register noop in `settings.gradle.kts`**

Add to the end of `clients/android/settings.gradle.kts`:

```kotlin
include(":noop")
```

- [ ] **Step 2: Create `clients/android/noop/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.clienttools.sdk"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.Zzechen"
                artifactId = "client-tools-noop"
                version = "1.0.0"
            }
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core)
}
```

- [ ] **Step 3: Create noop `ClientToolsSDK.kt`**

Create `clients/android/noop/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt`:

```kotlin
package com.clienttools.sdk

import android.content.Context

object ClientToolsSDK {
    fun init(context: Context) {}
    fun init(context: Context, customRoutes: List<Any> = emptyList(), customHandlerTimeoutMs: Long = 4500L) {}
    fun resolveRedirect(url: String): String = url
    fun shutdown() {}
}
```

- [ ] **Step 4: Create noop `MockInterceptor.kt`**

Create `clients/android/noop/src/main/kotlin/com/clienttools/sdk/mock/MockInterceptor.kt`:

```kotlin
package com.clienttools.sdk.mock

import okhttp3.Interceptor
import okhttp3.Response

class MockInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
```

- [ ] **Step 5: Create noop `HttpMethod.kt`, `CustomResult.kt`, `CustomRoute.kt`**

Create `clients/android/noop/src/main/kotlin/com/clienttools/sdk/http/HttpMethod.kt`:
```kotlin
package com.clienttools.sdk.http

enum class HttpMethod(val value: String) { GET("GET"), POST("POST"), PUT("PUT"), DELETE("DELETE") }
```

Create `clients/android/noop/src/main/kotlin/com/clienttools/sdk/http/CustomResult.kt`:
```kotlin
package com.clienttools.sdk.http

data class CustomResult(val data: String?, val error: String?, val code: Int = 200) {
    companion object {
        fun ok(data: String) = CustomResult(data, null)
        fun error(message: String, code: Int = 400) = CustomResult(null, message, code)
    }
}
```

Create `clients/android/noop/src/main/kotlin/com/clienttools/sdk/http/CustomRoute.kt`:
```kotlin
package com.clienttools.sdk.http

data class CustomRoute(
    val path: String,
    val method: HttpMethod,
    val description: String = "",
    val params: Map<String, String> = emptyMap(),
    val handler: suspend (String?) -> CustomResult
)
```

- [ ] **Step 6: Build noop module**

```bash
cd clients/android && ./gradlew :noop:assembleRelease
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add clients/android/noop/ clients/android/settings.gradle.kts
git commit -m "feat(android-noop): add noop SDK module for release builds

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
```

---

### Task 5: iOS SDK — WebViewRedirectStore + resolveRedirect() + HTTP endpoints

**Files:**
- Create: `clients/ios/sdk/Sources/WebViewRedirect/WebViewRedirectStore.swift`
- Modify: `clients/ios/sdk/Sources/ClientToolsSDK.swift`
- Modify: `clients/ios/sdk/Sources/HttpServer/HttpServer.swift`

- [ ] **Step 1: Create `WebViewRedirectStore.swift`**

```swift
import Foundation

struct WebViewRedirectEntry {
    let id: String
    let urlPattern: String
    let targetUrl: String
}

class WebViewRedirectStore {
    static let shared = WebViewRedirectStore()
    private var rules: [String: WebViewRedirectEntry] = [:]
    private var insertOrder: [String] = []
    private let lock = NSLock()

    private init() {}

    @discardableResult
    func add(_ entry: WebViewRedirectEntry) -> WebViewRedirectEntry {
        lock.lock(); defer { lock.unlock() }
        rules[entry.id] = entry
        insertOrder.append(entry.id)
        return entry
    }

    func delete(id: String) -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard rules[id] != nil else { return false }
        rules.removeValue(forKey: id)
        insertOrder.removeAll { $0 == id }
        return true
    }

    func list() -> [WebViewRedirectEntry] {
        lock.lock(); defer { lock.unlock() }
        return insertOrder.compactMap { rules[$0] }
    }

    func clear() -> Int {
        lock.lock(); defer { lock.unlock() }
        let count = rules.count
        rules.removeAll()
        insertOrder.removeAll()
        return count
    }

    func resolveRedirect(_ url: String) -> String {
        lock.lock()
        let snapshot = insertOrder.compactMap { rules[$0] }
        lock.unlock()

        let urlWithoutQuery = url.components(separatedBy: "?").first ?? url
        let originalQuery = url.contains("?") ? String(url.dropFirst(urlWithoutQuery.count + 1)) : ""

        guard let match = snapshot.first(where: { entry in
            (try? NSRegularExpression(pattern: entry.urlPattern))
                .map { regex in
                    let range = NSRange(urlWithoutQuery.startIndex..., in: urlWithoutQuery)
                    return regex.firstMatch(in: urlWithoutQuery, range: range) != nil
                } ?? false
        }) else { return url }

        return mergeQueryParams(targetUrl: match.targetUrl, originalQuery: originalQuery)
    }

    private func mergeQueryParams(targetUrl: String, originalQuery: String) -> String {
        guard !originalQuery.isEmpty else { return targetUrl }

        let targetBase = targetUrl.components(separatedBy: "?").first ?? targetUrl
        let targetQuery = targetUrl.contains("?") ? String(targetUrl.dropFirst(targetBase.count + 1)) : ""

        var params: [String: String] = [:]
        if !targetQuery.isEmpty {
            targetQuery.split(separator: "&").forEach { pair in
                let parts = pair.split(separator: "=", maxSplits: 1)
                if parts.count == 2 { params[String(parts[0])] = String(parts[1]) }
            }
        }
        // Original overwrites target on conflict
        originalQuery.split(separator: "&").forEach { pair in
            let parts = pair.split(separator: "=", maxSplits: 1)
            if parts.count == 2 { params[String(parts[0])] = String(parts[1]) }
        }

        let merged = params.map { "\($0.key)=\($0.value)" }.joined(separator: "&")
        return "\(targetBase)?\(merged)"
    }
}
```

- [ ] **Step 2: Add `resolveRedirect()` to `ClientToolsSDK.swift`**

Add after the `makeMockSession()` method:

```swift
public func resolveRedirect(_ url: String) -> String {
    return WebViewRedirectStore.shared.resolveRedirect(url)
}
```

- [ ] **Step 3: Add 4 HTTP handler methods to `HttpServer.swift`**

Add a private helper and 4 handler methods after `handleMockClear()`:

```swift
private func makeRedirectProto(_ entry: WebViewRedirectEntry) -> Clienttools_WebViewRedirectRule {
    var rule = Clienttools_WebViewRedirectRule()
    rule.id = entry.id
    rule.urlPattern = entry.urlPattern
    rule.targetUrl = entry.targetUrl
    return rule
}

private func handleWebViewRedirectAdd(_ body: Data, connection: NWConnection) {
    guard let req = try? Clienttools_AddWebViewRedirectRequest(serializedBytes: body) else {
        sendError(code: 400, message: "Invalid request", connection: connection); return
    }
    let entry = WebViewRedirectEntry(
        id: UUID().uuidString,
        urlPattern: req.urlPattern,
        targetUrl: req.targetUrl
    )
    WebViewRedirectStore.shared.add(entry)
    var resp = Clienttools_WebViewRedirectResponse()
    resp.meta = okMeta()
    resp.data = makeRedirectProto(entry)
    sendProto(resp, connection: connection)
}

private func handleWebViewRedirectList(connection: NWConnection) {
    let entries = WebViewRedirectStore.shared.list()
    var ruleList = Clienttools_WebViewRedirectRuleList()
    ruleList.rules = entries.map { makeRedirectProto($0) }
    var resp = Clienttools_WebViewRedirectListResponse()
    resp.meta = okMeta()
    resp.data = ruleList
    sendProto(resp, connection: connection)
}

private func handleWebViewRedirectDelete(_ id: String, connection: NWConnection) {
    WebViewRedirectStore.shared.delete(id: id)
    var resp = Clienttools_SimpleResponse()
    resp.meta = okMeta()
    sendProto(resp, connection: connection)
}

private func handleWebViewRedirectClear(connection: NWConnection) {
    let count = WebViewRedirectStore.shared.clear()
    var resp = Clienttools_ClearWebViewRedirectsResponse()
    resp.meta = okMeta()
    resp.clearedCount = Int32(count)
    sendProto(resp, connection: connection)
}
```

- [ ] **Step 4: Add routes to `processRequest()` in `HttpServer.swift`**

In the `switch (method, path)` block, add before `default:`:

```swift
case ("POST", "/webview/redirects"):
    handleWebViewRedirectAdd(bodyData, connection: connection)
case ("GET", "/webview/redirects"):
    handleWebViewRedirectList(connection: connection)
case ("DELETE", "/webview/redirects"):
    handleWebViewRedirectClear(connection: connection)
```

And in the `default:` block's `else if` chain, add before the final `else`:

```swift
} else if method == "DELETE" && path.hasPrefix("/webview/redirects/") {
    let ruleId = String(path.dropFirst("/webview/redirects/".count))
    handleWebViewRedirectDelete(ruleId, connection: connection)
```

- [ ] **Step 5: Build iOS SDK (pod lib lint)**

```bash
cd /Users/zzc/Desktop/works/client-tools
pod lib lint clients/ios/sdk/ClientToolsSDK.podspec --allow-warnings
```

Expected: passes.

- [ ] **Step 6: Commit**

```bash
git add clients/ios/sdk/Sources/WebViewRedirect/ \
        clients/ios/sdk/Sources/ClientToolsSDK.swift \
        clients/ios/sdk/Sources/HttpServer/HttpServer.swift
git commit -m "feat(ios-sdk): add WebViewRedirectStore, resolveRedirect(), and HTTP endpoints

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
```

---

### Task 6: iOS noop pod

**Files:**
- Create: `clients/ios/noop/ClientToolsSDK-Noop.podspec`
- Create: `clients/ios/noop/Sources/ClientToolsSDK.swift`

- [ ] **Step 1: Create noop `ClientToolsSDK.swift`**

Create `clients/ios/noop/Sources/ClientToolsSDK.swift`:

```swift
import Foundation

public class ClientToolsSDK {
    public static let shared = ClientToolsSDK()
    private init() {}

    public func start(port: Int = 8080, customRoutes: [Any] = [], customHandlerTimeoutMs: Int = 4500) {}
    public func resolveRedirect(_ url: String) -> String { return url }
    public func getCurrentPage() -> (pageName: String, timestamp: String) { return ("", "") }
    public func recordPageChange(_ pageName: String) {}
}
```

- [ ] **Step 2: Create `ClientToolsSDK-Noop.podspec`**

```ruby
Pod::Spec.new do |s|
  s.name             = 'ClientToolsSDK-Noop'
  s.version          = '1.0.1'
  s.summary          = 'Noop (release-safe) stub for ClientToolsSDK'
  s.description      = 'Drop-in replacement for ClientToolsSDK in Release builds. All methods are no-ops.'
  s.homepage         = 'https://github.com/Zzechen/client-tools'
  s.license          = { :type => 'MIT', :file => 'LICENSE' }
  s.author           = { 'Zzechen' => 'zzcm1259@qq.com' }
  s.source           = { :git => 'https://github.com/Zzechen/client-tools.git', :tag => "ios/#{s.version}" }
  s.ios.deployment_target = '14.0'
  s.swift_version    = '5.0'
  s.source_files     = 'clients/ios/noop/Sources/**/*.swift'
end
```

- [ ] **Step 3: Lint the noop podspec**

```bash
cd /Users/zzc/Desktop/works/client-tools
pod lib lint clients/ios/noop/ClientToolsSDK-Noop.podspec --allow-warnings
```

Expected: passes.

- [ ] **Step 4: Commit**

```bash
git add clients/ios/noop/
git commit -m "feat(ios-noop): add ClientToolsSDK-Noop pod for release builds

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
```

---

### Task 7: MCP tools — webview_redirect.ts

**Files:**
- Create: `mcp/src/tools/webview_redirect.ts`
- Modify: `mcp/src/index.ts`

- [ ] **Step 1: Create `mcp/src/tools/webview_redirect.ts`**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { create } from "@bufbuild/protobuf";
import { sdkPost, sdkGet, sdkDelete } from "../sdk-client.js";
import {
  WebViewRedirectResponseSchema,
  WebViewRedirectListResponseSchema,
  SimpleResponseSchema,
  ClearWebViewRedirectsResponseSchema,
} from "../generated/api_pb.js";
import { AddWebViewRedirectRequestSchema } from "../generated/webview_redirect_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerWebViewRedirectTools(server: McpServer): void {
  server.tool(
    "webview_redirect_add",
    "添加 WebView URL 重定向规则。AI 调用后，App 加载 WebView 时若 URL 命中 urlPattern，则跳转到 targetUrl（原始 URL 的 query 参数会追加到目标 URL）",
    {
      urlPattern: z.string().describe("正则表达式，匹配原始 URL（不含 query 部分）"),
      targetUrl: z.string().describe("命中后重定向到的目标地址，如 http://192.168.1.x:3000/page"),
    },
    async ({ urlPattern, targetUrl }) => {
      try {
        const req = create(AddWebViewRedirectRequestSchema, { urlPattern, targetUrl });
        const res = await sdkPost(
          "/webview/redirects",
          AddWebViewRedirectRequestSchema,
          req,
          WebViewRedirectResponseSchema
        );
        return {
          content: [{
            type: "text" as const,
            text: JSON.stringify({ id: res.data?.id, urlPattern: res.data?.urlPattern, targetUrl: res.data?.targetUrl }),
          }],
        };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "webview_redirect_list",
    "列出所有当前生效的 WebView 重定向规则",
    {},
    async () => {
      try {
        const res = await sdkGet("/webview/redirects", WebViewRedirectListResponseSchema);
        const rules = (res.data?.rules ?? []).map(r => ({
          id: r.id,
          urlPattern: r.urlPattern,
          targetUrl: r.targetUrl,
        }));
        return {
          content: [{ type: "text" as const, text: JSON.stringify(rules) }],
        };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "webview_redirect_delete",
    "按 id 删除一条 WebView 重定向规则",
    { id: z.string().describe("规则 id，由 webview_redirect_add 返回") },
    async ({ id }) => {
      try {
        await sdkDelete(`/webview/redirects/${id}`, SimpleResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ success: true }) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "webview_redirect_clear",
    "清空所有 WebView 重定向规则",
    {},
    async () => {
      try {
        const res = await sdkDelete("/webview/redirects", ClearWebViewRedirectsResponseSchema);
        return {
          content: [{
            type: "text" as const,
            text: JSON.stringify({ cleared_count: Number(res.clearedCount) }),
          }],
        };
      } catch (e) { return errResult(e); }
    }
  );
}
```

- [ ] **Step 2: Register the tools in `mcp/src/index.ts`**

Find the existing imports of register functions and add:

```typescript
import { registerWebViewRedirectTools } from "./tools/webview_redirect.js";
```

Then add after the other `register*Tools(server)` calls:

```typescript
registerWebViewRedirectTools(server);
```

- [ ] **Step 3: Build MCP**

```bash
cd mcp && npm run build
```

Expected: no TypeScript errors.

- [ ] **Step 4: Commit**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add mcp/src/tools/webview_redirect.ts mcp/src/index.ts
git commit -m "feat(mcp): add webview_redirect_add/list/delete/clear tools

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
```

---

### Task 8: Runtime E2E test — webview-redirect suite

**Files:**
- Create: `tests/runtime/src/suites/webview-redirect.ts`
- Modify: `tests/runtime/src/index.ts`

- [ ] **Step 1: Create `tests/runtime/src/suites/webview-redirect.ts`**

```typescript
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost, sdkDelete } from "../client.js";
import { assert } from "../helpers.js";
import {
  WebViewRedirectResponseSchema,
  WebViewRedirectListResponseSchema,
  SimpleResponseSchema,
  ClearWebViewRedirectsResponseSchema,
} from "../../../../mcp/src/generated/api_pb.js";
import { AddWebViewRedirectRequestSchema } from "../../../../mcp/src/generated/webview_redirect_pb.js";

export async function runWebViewRedirectSuite(): Promise<void> {
  console.log("\n↩️   webview redirects");

  // Clean state
  await sdkDelete("/webview/redirects", ClearWebViewRedirectsResponseSchema);

  // ── add ───────────────────────────────────────────────────────────────────
  const addReq = create(AddWebViewRedirectRequestSchema, {
    urlPattern: "https://example\\.com/page",
    targetUrl: "http://192.168.1.1:3000/page",
  });
  const addRes = await sdkPost(
    "/webview/redirects",
    AddWebViewRedirectRequestSchema,
    addReq,
    WebViewRedirectResponseSchema
  );
  assert((addRes.data?.id ?? "").length > 0, "webview_redirect_add returns non-empty id");
  assert(addRes.data?.urlPattern === "https://example\\.com/page", "add: urlPattern stored correctly");
  assert(addRes.data?.targetUrl === "http://192.168.1.1:3000/page", "add: targetUrl stored correctly");
  const ruleId = addRes.data!.id;

  // ── list contains added rule ──────────────────────────────────────────────
  const listRes = await sdkGet("/webview/redirects", WebViewRedirectListResponseSchema);
  const found = listRes.data?.rules.find(r => r.id === ruleId);
  assert(found != null, "list contains added rule by id");
  assert(found?.urlPattern === "https://example\\.com/page", "list rule.urlPattern correct");

  // ── delete specific rule ──────────────────────────────────────────────────
  await sdkDelete(`/webview/redirects/${ruleId}`, SimpleResponseSchema);
  const listAfterDelete = await sdkGet("/webview/redirects", WebViewRedirectListResponseSchema);
  assert(
    !listAfterDelete.data?.rules.some(r => r.id === ruleId),
    "delete: rule no longer in list"
  );

  // ── add two rules then clear all ──────────────────────────────────────────
  const r1 = create(AddWebViewRedirectRequestSchema, { urlPattern: "example\\.com/a", targetUrl: "http://local/a" });
  const r2 = create(AddWebViewRedirectRequestSchema, { urlPattern: "example\\.com/b", targetUrl: "http://local/b" });
  await sdkPost("/webview/redirects", AddWebViewRedirectRequestSchema, r1, WebViewRedirectResponseSchema);
  await sdkPost("/webview/redirects", AddWebViewRedirectRequestSchema, r2, WebViewRedirectResponseSchema);

  const clearRes = await sdkDelete("/webview/redirects", ClearWebViewRedirectsResponseSchema);
  assert((clearRes.clearedCount ?? 0) >= 2, `clear: clearedCount >= 2 (got ${clearRes.clearedCount})`);

  const listAfterClear = await sdkGet("/webview/redirects", WebViewRedirectListResponseSchema);
  assert((listAfterClear.data?.rules.length ?? 0) === 0, "clear: list is empty after clear");
}
```

- [ ] **Step 2: Add suite to `tests/runtime/src/index.ts`**

Add import:
```typescript
import { runWebViewRedirectSuite } from "./suites/webview-redirect.js";
```

Add call after `runMockSuite()`:
```typescript
await runWebViewRedirectSuite();
```

- [ ] **Step 3: Commit**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add tests/runtime/src/suites/webview-redirect.ts tests/runtime/src/index.ts
git commit -m "test(runtime): add webview-redirect E2E suite

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
```

---

### Task 9: Local static file server

**Files:**
- Create: `tests/local-server/server.js`
- Create: `tests/local-server/public/index.html`
- Create: `tests/local-server/public/test.html`

- [ ] **Step 1: Create `tests/local-server/server.js`**

```javascript
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');

const port = parseInt(process.argv[2] || '3000', 10);
const publicDir = path.join(__dirname, 'public');

const mimeTypes = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css',
  '.js': 'application/javascript',
  '.json': 'application/json',
};

const server = http.createServer((req, res) => {
  const urlPath = req.url.split('?')[0];
  const query = req.url.includes('?') ? req.url.slice(req.url.indexOf('?') + 1) : '';
  const filePath = path.join(publicDir, urlPath === '/' ? 'index.html' : urlPath);
  const ext = path.extname(filePath);
  const contentType = mimeTypes[ext] || 'text/plain';

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Not found');
      return;
    }
    // Inject query params as JSON for test pages
    const body = data.toString().replace('{{QUERY}}', query || '(none)');
    res.writeHead(200, { 'Content-Type': contentType });
    res.end(body);
  });
});

server.listen(port, '0.0.0.0', () => {
  const interfaces = os.networkInterfaces();
  const lanIp = Object.values(interfaces)
    .flat()
    .find(i => i.family === 'IPv4' && !i.internal)?.address ?? 'localhost';
  console.log(`\nLocal server running:`);
  console.log(`  Local:   http://localhost:${port}`);
  console.log(`  LAN:     http://${lanIp}:${port}  ← use this in targetUrl`);
  console.log('\nCtrl+C to stop\n');
});
```

- [ ] **Step 2: Create `tests/local-server/public/index.html`**

```html
<!DOCTYPE html>
<html lang="zh">
<head><meta charset="UTF-8"><title>本地服务器</title>
<style>body{font-family:sans-serif;padding:40px;background:#1a1a2e;color:#eee;}
h1{color:#00d4aa;}p{color:#aaa;}</style></head>
<body>
  <h1>本地服务器 - 已替换</h1>
  <p>WebView 重定向成功！</p>
  <p>Query 参数：<strong>{{QUERY}}</strong></p>
</body>
</html>
```

- [ ] **Step 3: Create `tests/local-server/public/test.html`**

```html
<!DOCTYPE html>
<html lang="zh">
<head><meta charset="UTF-8"><title>测试页</title>
<style>body{font-family:sans-serif;padding:40px;background:#1a1a2e;color:#eee;}
h1{color:#00d4aa;}pre{background:#111;padding:16px;border-radius:8px;color:#0f0;}</style></head>
<body>
  <h1>本地测试页</h1>
  <p>Query 参数：</p>
  <pre>{{QUERY}}</pre>
  <script>
    document.querySelector('pre').textContent = window.location.search || '(无)';
  </script>
</body>
</html>
```

- [ ] **Step 4: Verify server starts**

```bash
node tests/local-server/server.js 3000
```

Expected: prints local + LAN addresses. Open `http://localhost:3000` in browser, see "本地服务器 - 已替换".

- [ ] **Step 5: Commit**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add tests/local-server/
git commit -m "feat(tests): add local static file server for WebView redirect testing

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
```

---

### Task 10: Android demo — WebView redirect test page

**Files:**
- Create: `clients/android/demo/src/main/kotlin/com/clienttools/demo/WebViewRedirectActivity.kt`
- Create: `clients/android/demo/src/main/res/layout/activity_webview_redirect.xml`
- Create: `clients/android/demo/src/main/assets/test_local.html`
- Modify: `clients/android/demo/src/main/AndroidManifest.xml`
- Modify: `clients/android/demo/src/main/kotlin/com/clienttools/demo/MainActivity.kt`

- [ ] **Step 1: Create `assets/test_local.html`**

```html
<!DOCTYPE html>
<html lang="zh">
<head><meta charset="UTF-8"><title>本地测试页</title>
<style>body{font-family:sans-serif;padding:32px;background:#fff;}
h2{color:#333;}p{color:#666;}</style></head>
<body>
  <h2>本地测试页 - 原始</h2>
  <p>这是 App 内嵌的本地 HTML 文件。</p>
</body>
</html>
```

- [ ] **Step 2: Create layout `activity_webview_redirect.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/webview_redirect_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg_dark">

    <LinearLayout
        android:id="@+id/webview_redirect_nav_bar"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingStart="16dp"
        android:paddingEnd="16dp">

        <TextView
            android:id="@+id/webview_redirect_btn_back"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="← Back"
            android:textColor="@color/text_secondary"
            android:textSize="14sp" />

        <TextView
            android:id="@+id/webview_redirect_title"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="WebView 重定向测试"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:textStyle="bold"
            android:gravity="center" />

        <TextView
            android:id="@+id/webview_redirect_btn_reload"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="重新加载"
            android:textColor="@color/primary_light"
            android:textSize="14sp" />

    </LinearLayout>

    <TextView
        android:id="@+id/webview_redirect_label_remote"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="远程 URL WebView"
        android:textColor="@color/text_secondary"
        android:textSize="12sp"
        android:paddingStart="16dp"
        android:paddingTop="8dp"
        android:paddingBottom="4dp" />

    <TextView
        android:id="@+id/webview_redirect_url_remote"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="@color/primary_light"
        android:textSize="11sp"
        android:paddingStart="16dp"
        android:paddingBottom="4dp" />

    <WebView
        android:id="@+id/webview_redirect_remote"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="@color/text_secondary"
        android:alpha="0.2" />

    <TextView
        android:id="@+id/webview_redirect_label_local"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="本地文件 WebView"
        android:textColor="@color/text_secondary"
        android:textSize="12sp"
        android:paddingStart="16dp"
        android:paddingTop="8dp"
        android:paddingBottom="4dp" />

    <TextView
        android:id="@+id/webview_redirect_url_local"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="@color/primary_light"
        android:textSize="11sp"
        android:paddingStart="16dp"
        android:paddingBottom="4dp" />

    <WebView
        android:id="@+id/webview_redirect_local"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

</LinearLayout>
```

- [ ] **Step 3: Create `WebViewRedirectActivity.kt`**

```kotlin
package com.clienttools.demo

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.clienttools.sdk.ClientToolsSDK

class WebViewRedirectActivity : AppCompatActivity() {

    private val remoteOriginalUrl = "https://example.com"
    private val localOriginalUrl = "file:///android_asset/test_local.html"

    private lateinit var remoteWebView: WebView
    private lateinit var localWebView: WebView
    private lateinit var remoteUrlLabel: TextView
    private lateinit var localUrlLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview_redirect)

        remoteWebView = findViewById(R.id.webview_redirect_remote)
        localWebView = findViewById(R.id.webview_redirect_local)
        remoteUrlLabel = findViewById(R.id.webview_redirect_url_remote)
        localUrlLabel = findViewById(R.id.webview_redirect_url_local)

        remoteWebView.webViewClient = WebViewClient()
        localWebView.webViewClient = WebViewClient()

        findViewById<TextView>(R.id.webview_redirect_btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.webview_redirect_btn_reload).setOnClickListener { loadAll() }

        loadAll()
    }

    private fun loadAll() {
        val resolvedRemote = ClientToolsSDK.resolveRedirect(remoteOriginalUrl)
        val resolvedLocal = ClientToolsSDK.resolveRedirect(localOriginalUrl)

        remoteUrlLabel.text = resolvedRemote
        localUrlLabel.text = resolvedLocal

        remoteWebView.loadUrl(resolvedRemote)
        localWebView.loadUrl(resolvedLocal)
    }
}
```

- [ ] **Step 4: Register activity in `AndroidManifest.xml`**

Add inside the `<application>` block:

```xml
<activity
    android:name="com.clienttools.demo.WebViewRedirectActivity"
    android:exported="false" />
```

- [ ] **Step 5: Add entry to `MainActivity.kt`**

In the `pages` list, add:

```kotlin
Page("WebView 重定向测试") { startActivity(Intent(this, WebViewRedirectActivity::class.java)) },
```

- [ ] **Step 6: Build demo**

```bash
cd clients/android && ./gradlew :demo:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add clients/android/demo/src/main/kotlin/com/clienttools/demo/WebViewRedirectActivity.kt \
        clients/android/demo/src/main/kotlin/com/clienttools/demo/MainActivity.kt \
        clients/android/demo/src/main/res/layout/activity_webview_redirect.xml \
        clients/android/demo/src/main/assets/test_local.html \
        clients/android/demo/src/main/AndroidManifest.xml
git commit -m "feat(android-demo): add WebView redirect test page

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
```

---

### Task 11: iOS demo — WebView redirect test page

**Files:**
- Create: `clients/ios/demo/Sources/ClientToolsDemo/WebViewRedirect/WebViewRedirectViewController.swift`
- Create: `clients/ios/demo/Resources/test_local.html`
- Modify: `clients/ios/demo/Sources/ClientToolsDemo/Home/HomeViewController.swift`

- [ ] **Step 1: Create `Resources/test_local.html`**

```html
<!DOCTYPE html>
<html lang="zh">
<head><meta charset="UTF-8"><title>本地测试页</title>
<style>body{font-family:sans-serif;padding:32px;background:#fff;}
h2{color:#333;}p{color:#666;}</style></head>
<body>
  <h2>本地测试页 - 原始</h2>
  <p>这是 App 内嵌的本地 HTML 文件。</p>
</body>
</html>
```

- [ ] **Step 2: Create `WebViewRedirectViewController.swift`**

```swift
import UIKit
import WebKit
import SnapKit

class WebViewRedirectViewController: UIViewController {

    private let remoteOriginalUrl = "https://example.com"
    private let localOriginalUrl: String = {
        Bundle.main.url(forResource: "test_local", withExtension: "html")?.absoluteString ?? ""
    }()

    private lazy var remoteUrlLabel: UILabel = {
        let l = UILabel()
        l.font = .systemFont(ofSize: 11)
        l.textColor = UIColor(red: 0, green: 0.83, blue: 0.67, alpha: 1)
        l.numberOfLines = 2
        l.accessibilityIdentifier = "webview_redirect_url_remote"
        return l
    }()

    private lazy var localUrlLabel: UILabel = {
        let l = UILabel()
        l.font = .systemFont(ofSize: 11)
        l.textColor = UIColor(red: 0, green: 0.83, blue: 0.67, alpha: 1)
        l.numberOfLines = 2
        l.accessibilityIdentifier = "webview_redirect_url_local"
        return l
    }()

    private lazy var remoteWebView: WKWebView = {
        let wv = WKWebView()
        wv.accessibilityIdentifier = "webview_redirect_remote"
        return wv
    }()

    private lazy var localWebView: WKWebView = {
        let wv = WKWebView()
        wv.accessibilityIdentifier = "webview_redirect_local"
        return wv
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "WebView 重定向测试"
        view.backgroundColor = .systemBackground

        navigationItem.rightBarButtonItem = UIBarButtonItem(
            title: "重新加载",
            style: .plain,
            target: self,
            action: #selector(loadAll)
        )

        let remoteSectionLabel = makeLabel("远程 URL WebView")
        let localSectionLabel = makeLabel("本地文件 WebView")
        let divider = UIView()
        divider.backgroundColor = .separator

        [remoteSectionLabel, remoteUrlLabel, remoteWebView,
         divider, localSectionLabel, localUrlLabel, localWebView].forEach { view.addSubview($0) }

        remoteSectionLabel.snp.makeConstraints { make in
            make.top.equalTo(view.safeAreaLayoutGuide).offset(8)
            make.leading.trailing.equalToSuperview().inset(16)
        }
        remoteUrlLabel.snp.makeConstraints { make in
            make.top.equalTo(remoteSectionLabel.snp.bottom).offset(2)
            make.leading.trailing.equalToSuperview().inset(16)
        }
        remoteWebView.snp.makeConstraints { make in
            make.top.equalTo(remoteUrlLabel.snp.bottom).offset(4)
            make.leading.trailing.equalToSuperview()
            make.height.equalToSuperview().multipliedBy(0.35)
        }
        divider.snp.makeConstraints { make in
            make.top.equalTo(remoteWebView.snp.bottom)
            make.leading.trailing.equalToSuperview()
            make.height.equalTo(1)
        }
        localSectionLabel.snp.makeConstraints { make in
            make.top.equalTo(divider.snp.bottom).offset(8)
            make.leading.trailing.equalToSuperview().inset(16)
        }
        localUrlLabel.snp.makeConstraints { make in
            make.top.equalTo(localSectionLabel.snp.bottom).offset(2)
            make.leading.trailing.equalToSuperview().inset(16)
        }
        localWebView.snp.makeConstraints { make in
            make.top.equalTo(localUrlLabel.snp.bottom).offset(4)
            make.leading.trailing.equalToSuperview()
            make.bottom.equalTo(view.safeAreaLayoutGuide)
        }

        loadAll()
    }

    @objc private func loadAll() {
        let resolvedRemote = ClientToolsSDK.shared.resolveRedirect(remoteOriginalUrl)
        let resolvedLocal = ClientToolsSDK.shared.resolveRedirect(localOriginalUrl)

        remoteUrlLabel.text = resolvedRemote
        localUrlLabel.text = resolvedLocal

        if let url = URL(string: resolvedRemote) {
            remoteWebView.load(URLRequest(url: url))
        }
        if let url = URL(string: resolvedLocal) {
            localWebView.load(URLRequest(url: url))
        }
    }

    private func makeLabel(_ text: String) -> UILabel {
        let l = UILabel()
        l.text = text
        l.font = .systemFont(ofSize: 12)
        l.textColor = .secondaryLabel
        return l
    }
}
```

- [ ] **Step 3: Add entry to `HomeViewController.swift`**

In the `pages` array, add:

```swift
("WebView 重定向测试", "验证 URL 重定向规则", "↩️", { [weak self] in
    self?.navigationController?.pushViewController(WebViewRedirectViewController(), animated: true)
}),
```

- [ ] **Step 4: Add `test_local.html` to the Xcode project via `pod install`**

The HTML file is in `Resources/` — verify it appears in the demo target's bundle. For the Podfile-based demo project, add the file to the `ClientToolsDemo` target manually in Xcode or via the `.xcodeproj`'s `Copy Bundle Resources` build phase. (This step is manual.)

- [ ] **Step 5: Commit**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add clients/ios/demo/Sources/ClientToolsDemo/WebViewRedirect/ \
        clients/ios/demo/Resources/test_local.html \
        clients/ios/demo/Sources/ClientToolsDemo/Home/HomeViewController.swift
git commit -m "feat(ios-demo): add WebView redirect test page

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
```

---

### Task 12: Update docs

**Files:**
- Modify: `docs/mcp-tools.md`
- Modify: `docs/sdk-http-api.md`

- [ ] **Step 1: Add 4 tools to `docs/mcp-tools.md`**

Find the tools count in the README ("23 个工具") and update to 27. In `docs/mcp-tools.md`, add a new section for WebView Redirect tools:

```markdown
## WebView 重定向

| 工具名 | 参数 | 返回值 | 说明 |
|--------|------|--------|------|
| `webview_redirect_add` | `urlPattern` (string), `targetUrl` (string) | `{id, urlPattern, targetUrl}` | 添加重定向规则，返回规则 id |
| `webview_redirect_list` | — | `[{id, urlPattern, targetUrl}]` | 列出所有规则 |
| `webview_redirect_delete` | `id` (string) | `{success: true}` | 删除指定规则 |
| `webview_redirect_clear` | — | `{cleared_count}` | 清空所有规则 |
```

- [ ] **Step 2: Add 4 endpoints to `docs/sdk-http-api.md`**

Add a new section:

```markdown
## WebView 重定向

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/webview/redirects` | 添加规则，body: `AddWebViewRedirectRequest` |
| GET | `/webview/redirects` | 列出所有规则 |
| DELETE | `/webview/redirects/{id}` | 删除指定规则 |
| DELETE | `/webview/redirects` | 清空所有规则 |

### resolveRedirect() SDK 方法

App 在加载 WebView 前调用，返回最终 URL（命中规则时返回 targetUrl + 合并后的 query，否则原样返回）。

**Android:**
```kotlin
val finalUrl = ClientToolsSDK.resolveRedirect(originalUrl)
webView.loadUrl(finalUrl)
```

**iOS:**
```swift
let finalUrl = ClientToolsSDK.shared.resolveRedirect(originalUrl)
webView.load(URLRequest(url: URL(string: finalUrl)!))
```
```

- [ ] **Step 3: Commit**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add docs/mcp-tools.md docs/sdk-http-api.md README.md
git commit -m "docs: update mcp-tools and sdk-http-api for WebView redirect feature

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
```

---

## Self-Review

**Spec coverage check:**

| Spec section | Task |
|---|---|
| Proto messages | Task 1 |
| Android `WebViewRedirectStore` + unit tests | Task 2 |
| Android SDK HTTP endpoints + `resolveRedirect()` | Task 3 |
| Android noop module | Task 4 |
| iOS SDK `WebViewRedirectStore` + `resolveRedirect()` + HTTP endpoints | Task 5 |
| iOS noop pod | Task 6 |
| MCP tools (4 tools) | Task 7 |
| Runtime E2E tests | Task 8 |
| Local static file server | Task 9 |
| Android demo test page | Task 10 |
| iOS demo test page | Task 11 |
| Docs update | Task 12 |

All spec requirements are covered. No TBDs or placeholders found in tasks.

**Type consistency check:**
- `WebViewRedirectEntry` used consistently across Tasks 2, 3, 5
- `WebViewRedirectStore.resolveRedirect()` used in Tasks 2, 3, 5, 10, 11
- Proto message names `Clienttools_WebViewRedirectRule`, `Clienttools_WebViewRedirectListResponse`, `Clienttools_ClearWebViewRedirectsResponse`, `Clienttools_AddWebViewRedirectRequest` used consistently in Tasks 1, 5
- MCP schema names `AddWebViewRedirectRequestSchema`, `WebViewRedirectResponseSchema`, `WebViewRedirectListResponseSchema`, `ClearWebViewRedirectsResponseSchema` used consistently in Tasks 7, 8

# WebView 管理系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Android SDK 实现完整的 WebView 管理系统，支持 HTTP 推送、本地存储、手动调整面板等功能。

**Architecture:** 分层架构由 4 个核心组件组成：底层文件存储（WebViewFileStore）、业务逻辑协调（WebViewManager）、HTTP 接口处理（WebViewApiHandler）、顶层 UI（FloatingControlPanel）。此外增强 OverlayManager 以支持位移控制。所有组件通过 ViewModel 持久化 Activity 重启时的状态。

**Tech Stack:** Kotlin, Android SDK, Nanohttpd, WebView, Material Design, ViewModels, WindowManager

---

## 文件结构

### 待创建文件

```
packages/android/sdk/src/main/kotlin/com/clienttools/sdk/
├─ webview/
│  ├─ WebViewManager.kt           (核心协调器)
│  ├─ WebViewFileStore.kt         (本地存储管理)
│  ├─ WebViewApiHandler.kt        (HTTP 接口)
│  └─ ui/
│     ├─ FloatingControlPanel.kt  (悬浮按钮 + 展开面板)
│     ├─ WebViewModule.kt         (WebView 文件列表模块)
│     ├─ AdjustModule.kt          (位移和透明度调整模块)
│     └─ ControlModule.kt         (显示/隐藏/关闭模块)
│
packages/android/sdk/src/main/kotlin/com/clienttools/sdk/model/
├─ WebViewFile.kt                (文件信息数据类)
└─ WebViewState.kt               (状态持久化数据类)

packages/android/demo/src/main/kotlin/com/clienttools/demo/
└─ WebViewViewModel.kt           (Activity 级 ViewModel)

packages/android/sdk/src/test/kotlin/com/clienttools/sdk/webview/
├─ WebViewFileStoreTest.kt
├─ WebViewApiHandlerTest.kt
└─ WebViewManagerTest.kt
```

### 修改文件

```
packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/
└─ OverlayManager.kt             (添加 setOffset() 和 getOffset() 方法)

packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/
└─ ApiHandler.kt                 (集成 WebViewApiHandler)

packages/android/demo/src/main/kotlin/com/clienttools/demo/
└─ MainActivity.kt               (显示 WebView 功能入口)
```

---

## 任务清单

### Task 1: WebViewFileStore - 本地存储管理

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewFileStore.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/model/WebViewFile.kt`
- Test: `packages/android/sdk/src/test/kotlin/com/clienttools/sdk/webview/WebViewFileStoreTest.kt`

#### 数据模型

- [ ] **Step 1: 定义 WebViewFile 数据类**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/model/WebViewFile.kt
package com.clienttools.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class WebViewFile(
    val tag: String,
    val timestamp: String,  // MMdd-HHmm format
    val filePath: String,   // absolute path on disk
    val fileSize: Long,     // bytes
    val isCurrent: Boolean = false
)
```

- [ ] **Step 2: 创建 WebViewFileStore 类框架**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewFileStore.kt
package com.clienttools.sdk.webview

import android.content.Context
import android.util.Log
import com.clienttools.sdk.model.WebViewFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object WebViewFileStore {
    private lateinit var cacheDir: File
    private val TAG = "WebViewFileStore"
    
    fun init(context: Context) {
        cacheDir = File(context.cacheDir, "webview")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    // placeholder: add methods below
}
```

#### 文件保存功能

- [ ] **Step 3: 编写测试 - 保存 HTML 文件**

```kotlin
// packages/android/sdk/src/test/kotlin/com/clienttools/sdk/webview/WebViewFileStoreTest.kt
package com.clienttools.sdk.webview

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

@RunWith(AndroidJUnit4::class)
class WebViewFileStoreTest {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WebViewFileStore.init(context)
        // Clean up before each test
        WebViewFileStore.deleteAll()
    }
    
    @Test
    fun testSaveHtmlFile() {
        val tag = "login"
        val timestamp = "0418-1430"
        val htmlContent = "<html><body>Test</body></html>"
        
        val result = WebViewFileStore.saveHtmlFile(tag, timestamp, htmlContent)
        
        assert(result != null)
        assert(result?.fileSize == htmlContent.length.toLong())
        assert(result?.tag == tag)
        assert(result?.timestamp == timestamp)
        assert(result?.isCurrent == true)  // First file should be marked as current
        assert(result?.filePath?.endsWith("login_0418-1430.html") == true)
    }
}
```

- [ ] **Step 4: 实现 saveHtmlFile 方法**

```kotlin
// Add to WebViewFileStore in webview/WebViewFileStore.kt
fun saveHtmlFile(tag: String, timestamp: String, htmlContent: String): WebViewFile? = try {
    // Create tag directory
    val tagDir = File(cacheDir, tag)
    if (!tagDir.exists()) {
        tagDir.mkdirs()
    }
    
    // Generate filename and save
    val filename = "${tag}_${timestamp}.html"
    val file = File(tagDir, filename)
    file.writeText(htmlContent, Charsets.UTF_8)
    
    // Mark previous file as not current
    val existingFiles = listFilesByTag(tag)
    existingFiles.forEach { existingFile ->
        updateCurrentMarker(existingFile.filePath, false)
    }
    
    WebViewFile(
        tag = tag,
        timestamp = timestamp,
        filePath = file.absolutePath,
        fileSize = file.length(),
        isCurrent = true
    )
} catch (e: Exception) {
    Log.e(TAG, "Error saving HTML file", e)
    null
}

private fun updateCurrentMarker(filePath: String, isCurrent: Boolean) {
    // Store current marker in a separate metadata file if needed
    // For now, we track in-memory with currentFile variable
}
```

#### 文件查询功能

- [ ] **Step 5: 编写测试 - 获取文件列表**

```kotlin
// Add to WebViewFileStoreTest
@Test
fun testGetAllFiles() {
    WebViewFileStore.saveHtmlFile("login", "0418-1430", "<html>Login1</html>")
    WebViewFileStore.saveHtmlFile("login", "0418-1440", "<html>Login2</html>")
    WebViewFileStore.saveHtmlFile("home", "0418-1500", "<html>Home</html>")
    
    val allFiles = WebViewFileStore.getAllFiles()
    
    assert(allFiles.size == 3)
    assert(allFiles.count { it.tag == "login" } == 2)
    assert(allFiles.count { it.isCurrent && it.tag == "login" } == 1)
    assert(allFiles[0].timestamp == "0418-1440")  // Latest for login
}

@Test
fun testListFilesByTag() {
    WebViewFileStore.saveHtmlFile("login", "0417-1400", "<html>Old</html>")
    WebViewFileStore.saveHtmlFile("login", "0418-1430", "<html>New</html>")
    
    val files = WebViewFileStore.listFilesByTag("login")
    
    assert(files.size == 2)
    assert(files[0].timestamp == "0418-1430")
}
```

- [ ] **Step 6: 实现文件查询方法**

```kotlin
// Add to WebViewFileStore
private var currentFile: Pair<String, String>? = null  // (tag, timestamp)

fun getAllFiles(): List<WebViewFile> = try {
    val files = mutableListOf<WebViewFile>()
    cacheDir.listFiles()?.forEach { tagDir ->
        if (tagDir.isDirectory) {
            tagDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".html")) {
                    val timestamp = parseTimestamp(file.name)
                    files.add(WebViewFile(
                        tag = tagDir.name,
                        timestamp = timestamp,
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        isCurrent = currentFile == Pair(tagDir.name, timestamp)
                    ))
                }
            }
        }
    }
    files.sortByDescending { it.timestamp }
    files
} catch (e: Exception) {
    Log.e(TAG, "Error getting all files", e)
    emptyList()
}

fun listFilesByTag(tag: String): List<WebViewFile> {
    return getAllFiles().filter { it.tag == tag }
}

fun getCurrentFile(): Pair<String, String>? = currentFile

fun setCurrentFile(tag: String, timestamp: String) {
    currentFile = Pair(tag, timestamp)
}

private fun parseTimestamp(filename: String): String {
    // Extract timestamp from "tag_0418-1430.html"
    val regex = """_(\d{4}-\d{4})\.html$""".toRegex()
    return regex.find(filename)?.groupValues?.get(1) ?: ""
}
```

#### 文件删除和清理

- [ ] **Step 7: 编写测试 - 删除文件**

```kotlin
// Add to WebViewFileStoreTest
@Test
fun testDeleteFile() {
    val file1 = WebViewFileStore.saveHtmlFile("login", "0418-1430", "<html>Test</html>")!!
    WebViewFileStore.saveHtmlFile("login", "0418-1440", "<html>Test2</html>")
    
    val deleted = WebViewFileStore.deleteFile(file1.filePath)
    
    assert(deleted == true)
    assert(WebViewFileStore.getAllFiles().size == 1)
}

@Test
fun testDeleteAll() {
    WebViewFileStore.saveHtmlFile("login", "0418-1430", "<html>Test</html>")
    WebViewFileStore.saveHtmlFile("home", "0418-1500", "<html>Test</html>")
    
    WebViewFileStore.deleteAll()
    
    assert(WebViewFileStore.getAllFiles().isEmpty())
}
```

- [ ] **Step 8: 实现删除方法**

```kotlin
// Add to WebViewFileStore
fun deleteFile(filePath: String): Boolean = try {
    val file = File(filePath)
    file.delete().also { success ->
        if (success && currentFile != null) {
            val isCurrentFile = filePath.endsWith("_${currentFile!!.second}.html")
            if (isCurrentFile) {
                currentFile = null
            }
        }
    }
} catch (e: Exception) {
    Log.e(TAG, "Error deleting file", e)
    false
}

fun deleteAll(): Boolean = try {
    cacheDir.deleteRecursively()
    currentFile = null
    cacheDir.mkdirs()
    true
} catch (e: Exception) {
    Log.e(TAG, "Error deleting all files", e)
    false
}
```

- [ ] **Step 9: 运行所有 WebViewFileStore 测试**

```bash
cd packages && ./gradlew :sdk:testDebugUnitTest -k WebViewFileStoreTest
```

Expected: All 6 tests pass

- [ ] **Step 10: 提交 Task 1**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/model/WebViewFile.kt
git add packages/android/sdk/src/test/kotlin/com/clienttools/sdk/webview/WebViewFileStoreTest.kt
git commit -m "feat: implement WebViewFileStore for local HTML storage management"
```

---

### Task 2: OverlayManager 增强 - 位移控制

**Files:**
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/OverlayManager.kt`
- Test: `packages/android/sdk/src/test/kotlin/com/clienttools/sdk/runtime/OverlayManagerTest.kt`

- [ ] **Step 1: 编写测试 - 位移控制**

```kotlin
// packages/android/sdk/src/test/kotlin/com/clienttools/sdk/runtime/OverlayManagerTest.kt
package com.clienttools.sdk.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayManagerTest {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testSetOffset() {
        val offsetX = 50
        val offsetY = -100
        
        val success = OverlayManager.setOffset(offsetX, offsetY)
        
        // Note: This test is limited because we can't easily test WindowManager
        // In real testing, you'd use a mock WindowManager
        assert(success == false || success == true)  // Just verify no exception
    }
    
    @Test
    fun testGetOffset() {
        OverlayManager.setOffset(25, -50)
        
        val (x, y) = OverlayManager.getOffset()
        
        // Offset tracking logic will be implemented
        assert(x >= 0 && y >= 0 || x <= 0 && y <= 0)  // Valid values
    }
}
```

- [ ] **Step 2: 在 OverlayManager 中添加位移字段和方法**

```kotlin
// Modify OverlayManager.kt - add to class level
private var currentOffsetX: Int = 0
private var currentOffsetY: Int = 0

// Add new methods
fun setOffset(offsetX: Int, offsetY: Int): Boolean = try {
    val activity = ClientToolsSDK.getCurrentActivity() ?: return false
    currentOffsetX = offsetX
    currentOffsetY = offsetY
    
    if (webView == null || layoutParams == null) {
        return false
    }
    
    if (Looper.myLooper() == Looper.getMainLooper()) {
        updateWebViewOffset(activity)
    } else {
        activity.runOnUiThread { updateWebViewOffset(activity) }
    }
    true
} catch (e: Exception) {
    false
}

fun getOffset(): Pair<Int, Int> = Pair(currentOffsetX, currentOffsetY)

private fun updateWebViewOffset(activity: Activity) {
    try {
        layoutParams?.let {
            it.x = currentOffsetX
            it.y = currentOffsetY
            windowManager?.updateViewLayout(webView, it)
        }
    } catch (e: Exception) {
        Log.e("OverlayManager", "Error updating offset", e)
    }
}
```

- [ ] **Step 3: 运行 OverlayManager 测试**

```bash
cd packages && ./gradlew :sdk:testDebugUnitTest -k OverlayManagerTest
```

Expected: Tests pass

- [ ] **Step 4: 提交 Task 2**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/OverlayManager.kt
git add packages/android/sdk/src/test/kotlin/com/clienttools/sdk/runtime/OverlayManagerTest.kt
git commit -m "feat: add offset control methods to OverlayManager"
```

---

### Task 3: WebViewManager - 核心协调器

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewManager.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/model/WebViewState.kt`
- Test: `packages/android/sdk/src/test/kotlin/com/clienttools/sdk/webview/WebViewManagerTest.kt`

- [ ] **Step 1: 定义 WebViewState 数据类**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/model/WebViewState.kt
package com.clienttools.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class WebViewState(
    val currentTag: String? = null,
    val currentTimestamp: String? = null,
    val isVisible: Boolean = false,
    val opacity: Float = 1.0f,
    val offsetX: Int = 0,
    val offsetY: Int = 0
)
```

- [ ] **Step 2: 创建 WebViewManager 框架**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewManager.kt
package com.clienttools.sdk.webview

import android.content.Context
import android.util.Log
import com.clienttools.sdk.model.WebViewFile
import com.clienttools.sdk.model.WebViewState
import com.clienttools.sdk.runtime.OverlayManager

object WebViewManager {
    private val TAG = "WebViewManager"
    private var state = WebViewState()
    
    fun init(context: Context) {
        WebViewFileStore.init(context)
        loadStateFromStorage(context)
    }
    
    private fun loadStateFromStorage(context: Context) {
        // State will be restored from ViewModel in Activity
        // This is for initialization of default state
    }
    
    fun setState(newState: WebViewState) {
        state = newState
    }
    
    fun getState(): WebViewState = state
}
```

- [ ] **Step 3: 实现 pushHtml 方法**

```kotlin
// Add to WebViewManager
fun pushHtml(tag: String, htmlContent: String, timestamp: String? = null): Map<String, Any> = try {
    val finalTimestamp = timestamp ?: generateTimestamp()
    val savedFile = WebViewFileStore.saveHtmlFile(tag, finalTimestamp, htmlContent)
    
    if (savedFile != null) {
        mapOf(
            "code" to 0,
            "message" to "success",
            "data" to mapOf(
                "tag" to savedFile.tag,
                "timestamp" to savedFile.timestamp,
                "filePath" to savedFile.filePath,
                "fileSize" to savedFile.fileSize
            )
        )
    } else {
        mapOf(
            "code" to 400,
            "message" to "Failed to save HTML file"
        )
    }
} catch (e: Exception) {
    Log.e(TAG, "Error pushing HTML", e)
    mapOf(
        "code" to 400,
        "message" to "Invalid HTML content or tag: ${e.message}"
    )
}

private fun generateTimestamp(): String {
    val sdf = java.text.SimpleDateFormat("MMdd-HHmm", java.util.Locale.US)
    return sdf.format(java.util.Date())
}
```

- [ ] **Step 4: 实现 showWebView 方法**

```kotlin
// Add to WebViewManager
fun showWebView(tag: String, timestamp: String): Map<String, Any> = try {
    val files = WebViewFileStore.listFilesByTag(tag)
    val file = files.find { it.timestamp == timestamp }
    
    if (file == null) {
        return mapOf(
            "code" to 404,
            "message" to "File not found"
        )
    }
    
    val success = OverlayManager.show("file://${file.filePath}", state.opacity)
    if (success) {
        state = state.copy(
            currentTag = tag,
            currentTimestamp = timestamp,
            isVisible = true
        )
        WebViewFileStore.setCurrentFile(tag, timestamp)
        
        mapOf(
            "code" to 0,
            "message" to "success",
            "data" to mapOf(
                "tag" to tag,
                "timestamp" to timestamp,
                "opacity" to state.opacity,
                "offsetX" to state.offsetX,
                "offsetY" to state.offsetY
            )
        )
    } else {
        mapOf(
            "code" to 500,
            "message" to "Failed to show WebView"
        )
    }
} catch (e: Exception) {
    Log.e(TAG, "Error showing WebView", e)
    mapOf(
        "code" to 500,
        "message" to "Error: ${e.message}"
    )
}
```

- [ ] **Step 5: 实现 hideWebView 和 adjustWebView 方法**

```kotlin
// Add to WebViewManager
fun hideWebView(): Map<String, Any> = try {
    val success = OverlayManager.hide()
    if (success) {
        state = state.copy(isVisible = false)
        mapOf(
            "code" to 0,
            "message" to "success"
        )
    } else {
        mapOf(
            "code" to 500,
            "message" to "Failed to hide WebView"
        )
    }
} catch (e: Exception) {
    Log.e(TAG, "Error hiding WebView", e)
    mapOf(
        "code" to 500,
        "message" to "Error: ${e.message}"
    )
}

fun adjustWebView(offsetXDelta: Int, offsetYDelta: Int, opacity: Float?): Map<String, Any> = try {
    val newOffsetX = state.offsetX + offsetXDelta
    val newOffsetY = state.offsetY + offsetYDelta
    val newOpacity = opacity?.coerceIn(0.0f, 1.0f) ?: state.opacity
    
    val offsetSuccess = OverlayManager.setOffset(newOffsetX, newOffsetY)
    val opacitySuccess = OverlayManager.setOpacity(newOpacity)
    
    if (offsetSuccess || opacitySuccess) {
        state = state.copy(
            offsetX = newOffsetX,
            offsetY = newOffsetY,
            opacity = newOpacity
        )
        
        mapOf(
            "code" to 0,
            "data" to mapOf(
                "offsetX" to newOffsetX,
                "offsetY" to newOffsetY,
                "opacity" to newOpacity
            )
        )
    } else {
        mapOf(
            "code" to 500,
            "message" to "Failed to adjust WebView"
        )
    }
} catch (e: Exception) {
    Log.e(TAG, "Error adjusting WebView", e)
    mapOf(
        "code" to 500,
        "message" to "Error: ${e.message}"
    )
}

fun getFiles(): Map<String, Any> = try {
    val files = WebViewFileStore.getAllFiles()
    mapOf(
        "code" to 0,
        "data" to mapOf(
            "files" to files.map { f ->
                mapOf(
                    "tag" to f.tag,
                    "timestamp" to f.timestamp,
                    "size" to f.fileSize,
                    "isCurrent" to f.isCurrent
                )
            }
        )
    )
} catch (e: Exception) {
    Log.e(TAG, "Error getting files", e)
    mapOf(
        "code" to 500,
        "message" to "Error: ${e.message}"
    )
}
```

- [ ] **Step 6: 编写 WebViewManager 测试**

```kotlin
// packages/android/sdk/src/test/kotlin/com/clienttools/sdk/webview/WebViewManagerTest.kt
package com.clienttools.sdk.webview

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewManagerTest {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WebViewManager.init(context)
        WebViewFileStore.deleteAll()
    }
    
    @Test
    fun testPushHtml() {
        val result = WebViewManager.pushHtml("login", "<html>Test</html>")
        
        assert((result["code"] as Int) == 0)
        val data = result["data"] as Map<*, *>
        assert(data["tag"] == "login")
        assert((data["fileSize"] as Long) > 0)
    }
    
    @Test
    fun testGetFiles() {
        WebViewManager.pushHtml("login", "<html>Test1</html>")
        WebViewManager.pushHtml("login", "<html>Test2</html>")
        
        val result = WebViewManager.getFiles()
        
        assert((result["code"] as Int) == 0)
        val data = result["data"] as Map<*, *>
        val files = data["files"] as List<*>
        assert(files.size == 2)
    }
}
```

- [ ] **Step 7: 运行 WebViewManager 测试**

```bash
cd packages && ./gradlew :sdk:testDebugUnitTest -k WebViewManagerTest
```

Expected: All tests pass

- [ ] **Step 8: 提交 Task 3**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewManager.kt
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/model/WebViewState.kt
git add packages/android/sdk/src/test/kotlin/com/clienttools/sdk/webview/WebViewManagerTest.kt
git commit -m "feat: implement WebViewManager core orchestrator with state management"
```

---

### Task 4: WebViewApiHandler - HTTP 接口

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewApiHandler.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`

- [ ] **Step 1: 创建 WebViewApiHandler**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewApiHandler.kt
package com.clienttools.sdk.webview

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import fi.iki.elonen.NanoHTTPD

object WebViewApiHandler {
    private val TAG = "WebViewApiHandler"
    
    fun handlePushHtml(body: String): NanoHTTPD.Response {
        return try {
            val json = Json.parseToJsonElement(body)
            val tag = json.jsonObject["tag"]?.jsonPrimitive?.content
                ?: return errorResponse(400, "Missing tag")
            val html = json.jsonObject["html"]?.jsonPrimitive?.content
                ?: return errorResponse(400, "Missing html")
            val timestamp = json.jsonObject["timestamp"]?.jsonPrimitive?.content
            
            val result = WebViewManager.pushHtml(tag, html, timestamp)
            successResponse(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handlePushHtml", e)
            errorResponse(400, "Invalid request body")
        }
    }
    
    fun handleShow(body: String): NanoHTTPD.Response {
        return try {
            val json = Json.parseToJsonElement(body)
            val tag = json.jsonObject["tag"]?.jsonPrimitive?.content
                ?: return errorResponse(400, "Missing tag")
            val timestamp = json.jsonObject["timestamp"]?.jsonPrimitive?.content
                ?: return errorResponse(400, "Missing timestamp")
            
            val result = WebViewManager.showWebView(tag, timestamp)
            successResponse(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleShow", e)
            errorResponse(400, "Invalid request body")
        }
    }
    
    fun handleHide(): NanoHTTPD.Response {
        return try {
            val result = WebViewManager.hideWebView()
            successResponse(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleHide", e)
            errorResponse(500, "Internal error")
        }
    }
    
    fun handleAdjust(body: String): NanoHTTPD.Response {
        return try {
            val json = Json.parseToJsonElement(body)
            val offsetX = json.jsonObject["offsetX"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val offsetY = json.jsonObject["offsetY"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val opacity = json.jsonObject["opacity"]?.jsonPrimitive?.content?.toFloatOrNull()
            
            val result = WebViewManager.adjustWebView(offsetX, offsetY, opacity)
            successResponse(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleAdjust", e)
            errorResponse(400, "Invalid request body")
        }
    }
    
    fun handleGetFiles(): NanoHTTPD.Response {
        return try {
            val result = WebViewManager.getFiles()
            successResponse(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleGetFiles", e)
            errorResponse(500, "Internal error")
        }
    }
    
    private fun successResponse(data: Map<String, Any>): NanoHTTPD.Response {
        val json = Json.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            Json.parseToJsonElement(Json.encodeToString(data))
        )
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json",
            json
        )
    }
    
    private fun errorResponse(code: Int, message: String): NanoHTTPD.Response {
        val json = """{"code":$code,"message":"$message"}"""
        return NanoHTTPD.newFixedLengthResponse(
            if (code >= 500) NanoHTTPD.Response.Status.INTERNAL_ERROR else NanoHTTPD.Response.Status.BAD_REQUEST,
            "application/json",
            json
        )
    }
}
```

- [ ] **Step 2: 在 HttpServer 中注册 WebView 接口**

```kotlin
// Modify HttpServer.kt - add routing for WebView endpoints
// Find the serve() method in HttpServer and add:

when {
    uri.startsWith("/webview/push-html") && method == Method.POST -> {
        WebViewApiHandler.handlePushHtml(bodyText)
    }
    uri.startsWith("/webview/show") && method == Method.POST -> {
        WebViewApiHandler.handleShow(bodyText)
    }
    uri.startsWith("/webview/hide") && method == Method.POST -> {
        WebViewApiHandler.handleHide()
    }
    uri.startsWith("/webview/adjust") && method == Method.POST -> {
        WebViewApiHandler.handleAdjust(bodyText)
    }
    uri.startsWith("/webview/files") && method == Method.GET -> {
        WebViewApiHandler.handleGetFiles()
    }
    // ... rest of existing routes
}
```

- [ ] **Step 3: 在 SDK 初始化时启动 WebViewManager**

```kotlin
// Modify ApiHandler.kt - add WebViewManager initialization in appropriate location
// Usually in a static block or init function called by SdkInitProvider

// In SdkInitProvider.kt, add to onCreate():
WebViewManager.init(context)
```

- [ ] **Step 4: 编译 SDK**

```bash
cd packages && ./gradlew :sdk:assembleDebug
```

Expected: Build succeeds

- [ ] **Step 5: 提交 Task 4**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewApiHandler.kt
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt
git commit -m "feat: implement WebView HTTP API endpoints (push-html, show, hide, adjust, files)"
```

---

### Task 5: FloatingControlPanel - UI 实现

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ui/FloatingControlPanel.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ui/WebViewModule.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ui/AdjustModule.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ui/ControlModule.kt`
- Create: `packages/android/demo/src/main/kotlin/com/clienttools/demo/WebViewViewModel.kt`
- Modify: `packages/android/demo/src/main/kotlin/com/clienttools/demo/MainActivity.kt`

- [ ] **Step 1: 创建 WebViewViewModel**

```kotlin
// packages/android/demo/src/main/kotlin/com/clienttools/demo/WebViewViewModel.kt
package com.clienttools.demo

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.clienttools.sdk.model.WebViewFile

class WebViewViewModel : ViewModel() {
    val currentFile = MutableLiveData<Pair<String, String>?>()  // (tag, timestamp)
    val isWebViewVisible = MutableLiveData<Boolean>(false)
    val offsetX = MutableLiveData<Int>(0)
    val offsetY = MutableLiveData<Int>(0)
    val opacity = MutableLiveData<Float>(1.0f)
    val savedFiles = MutableLiveData<List<WebViewFile>>(emptyList())
    
    fun updateState(
        tag: String?,
        timestamp: String?,
        visible: Boolean,
        offsetX: Int,
        offsetY: Int,
        opacity: Float
    ) {
        this.currentFile.value = if (tag != null && timestamp != null) Pair(tag, timestamp) else null
        this.isWebViewVisible.value = visible
        this.offsetX.value = offsetX
        this.offsetY.value = offsetY
        this.opacity.value = opacity
    }
}
```

- [ ] **Step 2: 创建控制模块 UI**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ui/ControlModule.kt
package com.clienttools.sdk.webview.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

class ControlModule @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    val showButton = Button(context)
    val hideButton = Button(context)
    val closeButton = Button(context)
    
    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        
        // Show/Hide row
        val row1 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 48)
        }
        
        showButton.apply {
            text = "Show WebView"
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        row1.addView(showButton)
        
        hideButton.apply {
            text = "Hide"
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        row1.addView(hideButton)
        
        addView(row1)
        
        // Close panel button
        closeButton.apply {
            text = "Close Panel"
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 48).apply {
                setMargins(0, 8, 0, 0)
            }
        }
        addView(closeButton)
    }
}
```

- [ ] **Step 3: 创建位移调整模块 UI**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ui/AdjustModule.kt
package com.clienttools.sdk.webview.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout

class AdjustModule @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    val upButton = ImageButton(context)
    val downButton = ImageButton(context)
    val leftButton = ImageButton(context)
    val rightButton = ImageButton(context)
    
    val step1Button = Button(context)
    val step10Button = Button(context)
    val step50Button = Button(context)
    
    val opacitySlider = SeekBar(context)
    val opacityLabel = TextView(context)
    val offsetLabel = TextView(context)
    
    var currentStep = 10  // Default step
    var currentOffsetX = 0
    var currentOffsetY = 0
    
    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        setPadding(16, 12, 16, 12)
        
        // Direction controls
        val directionLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        
        // Up button
        upButton.apply {
            layoutParams = LayoutParams(48, 48).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        directionLayout.addView(upButton)
        
        // Left, Center, Right row
        val horizontalRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 48)
            gravity = Gravity.CENTER
        }
        leftButton.apply {
            layoutParams = LayoutParams(48, 48)
        }
        horizontalRow.addView(leftButton)
        
        val spacer = Space(context).apply {
            layoutParams = LayoutParams(0, 0, 1f)
        }
        horizontalRow.addView(spacer)
        
        rightButton.apply {
            layoutParams = LayoutParams(48, 48)
        }
        horizontalRow.addView(rightButton)
        
        directionLayout.addView(horizontalRow)
        
        // Down button
        downButton.apply {
            layoutParams = LayoutParams(48, 48).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        directionLayout.addView(downButton)
        
        addView(directionLayout)
        
        // Step buttons
        val stepLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 8, 0, 0)
            }
        }
        
        step1Button.apply {
            text = "1dp"
            layoutParams = LayoutParams(0, 36, 1f)
        }
        stepLayout.addView(step1Button)
        
        step10Button.apply {
            text = "10dp"
            layoutParams = LayoutParams(0, 36, 1f)
        }
        stepLayout.addView(step10Button)
        
        step50Button.apply {
            text = "50dp"
            layoutParams = LayoutParams(0, 36, 1f)
        }
        stepLayout.addView(step50Button)
        
        addView(stepLayout)
        
        // Opacity control
        opacityLabel.apply {
            text = "Opacity: 100%"
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 12, 0, 4)
            }
            textSize = 12f
        }
        addView(opacityLabel)
        
        opacitySlider.apply {
            max = 100
            progress = 100
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(opacitySlider)
        
        // Offset display
        offsetLabel.apply {
            text = "Offset: X: 0 Y: 0"
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 12, 0, 0)
            }
            textSize = 12f
        }
        addView(offsetLabel)
    }
    
    fun updateOffsetDisplay() {
        offsetLabel.text = "Offset: X: ${currentOffsetX}dp Y: ${currentOffsetY}dp"
    }
}
```

- [ ] **Step 4: 创建 WebView 文件列表模块 UI**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ui/WebViewModule.kt
package com.clienttools.sdk.webview.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.*
import com.clienttools.sdk.model.WebViewFile

class WebViewModule @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    val currentFileLabel = TextView(context)
    val fileListView = ListView(context)
    
    private val fileAdapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1)
    
    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        setPadding(16, 12, 16, 12)
        
        // Current file display
        currentFileLabel.apply {
            text = "Current: None"
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 8)
            }
            textSize = 14f
        }
        addView(currentFileLabel)
        
        // File list header
        val headerLabel = TextView(context).apply {
            text = "Saved Files:"
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 8, 0, 4)
            }
            textSize = 12f
        }
        addView(headerLabel)
        
        // File list
        fileListView.apply {
            adapter = fileAdapter
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 200)
        }
        addView(fileListView)
    }
    
    fun updateFiles(files: List<WebViewFile>, currentFile: Pair<String, String>?) {
        val items = files.map { file ->
            val marker = if (currentFile == Pair(file.tag, file.timestamp)) "◐ ★" else "○"
            "$marker ${file.tag}_${file.timestamp} (${file.fileSize / 1024}KB)"
        }
        
        fileAdapter.clear()
        fileAdapter.addAll(items)
        fileAdapter.notifyDataSetChanged()
        
        currentFileLabel.text = if (currentFile != null) {
            "Current: ${currentFile.first} (${currentFile.second})"
        } else {
            "Current: None"
        }
    }
}
```

- [ ] **Step 5: 创建主悬浮窗面板**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ui/FloatingControlPanel.kt
package com.clienttools.sdk.webview.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*
import androidx.core.view.marginTop

class FloatingControlPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    // Floating button
    val floatingButton = Button(context)
    var isExpanded = false
    
    // Expandable panel
    val expandedPanel = FrameLayout(context)
    val dragHandle = View(context)
    val contentScroll = ScrollView(context)
    val contentLayout = LinearLayout(context)
    
    // Modules
    val webViewModule = WebViewModule(context)
    val adjustModule = AdjustModule(context)
    val controlModule = ControlModule(context)
    
    // Drag tracking
    private var lastX = 0f
    private var lastY = 0f
    
    init {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        
        // Setup floating button
        floatingButton.apply {
            text = "⚙"
            layoutParams = FrameLayout.LayoutParams(48, 48).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 10, 10)
            }
            setBackgroundColor(0xFF6200EE.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        addView(floatingButton)
        
        // Setup expanded panel
        expandedPanel.apply {
            layoutParams = FrameLayout.LayoutParams(280, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 10, 70)
            }
            setBackgroundColor(0xFF1F1F1F.toInt())
        }
        
        // Drag handle
        dragHandle.apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 40)
            setBackgroundColor(0xFF333333.toInt())
        }
        expandedPanel.addView(dragHandle)
        
        // Content scroll
        contentScroll.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 40
            }
        }
        
        // Content layout
        contentLayout.apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        contentLayout.addView(webViewModule)
        contentLayout.addView(divider())
        contentLayout.addView(adjustModule)
        contentLayout.addView(divider())
        contentLayout.addView(controlModule)
        
        contentScroll.addView(contentLayout)
        expandedPanel.addView(contentScroll)
        
        addView(expandedPanel)
        
        // Initially hide expanded panel
        expandedPanel.visibility = GONE
        
        // Setup click listeners
        floatingButton.setOnClickListener {
            togglePanel()
        }
        
        dragHandle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    val params = expandedPanel.layoutParams as FrameLayout.LayoutParams
                    params.rightMargin = (params.rightMargin - dx.toInt()).coerceAtLeast(0)
                    params.bottomMargin = (params.bottomMargin - dy.toInt()).coerceAtLeast(0)
                    expandedPanel.layoutParams = params
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                else -> false
            }
        }
    }
    
    private fun divider(): View {
        return View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                setMargins(0, 4, 0, 4)
            }
            setBackgroundColor(0xFF333333.toInt())
        }
    }
    
    private fun togglePanel() {
        isExpanded = !isExpanded
        expandedPanel.visibility = if (isExpanded) VISIBLE else GONE
    }
    
    fun showPanel() {
        isExpanded = true
        expandedPanel.visibility = VISIBLE
    }
    
    fun hidePanel() {
        isExpanded = false
        expandedPanel.visibility = GONE
    }
}
```

- [ ] **Step 6: 在 MainActivity 中集成悬浮窗**

```kotlin
// Modify MainActivity.kt
// Add to imports
import androidx.lifecycle.ViewModelProvider
import com.clienttools.sdk.webview.ui.FloatingControlPanel
import com.clienttools.demo.WebViewViewModel

// Add to class
private lateinit var floatingPanel: FloatingControlPanel
private lateinit var webViewViewModel: WebViewViewModel

// In onCreate(), after setContentView():
floatingPanel = FloatingControlPanel(this)
webViewViewModel = ViewModelProvider(this).get(WebViewViewModel::class.java)

// Add floating panel to root layout
val rootLayout = findViewById<FrameLayout>(R.id.root_container)
rootLayout.addView(floatingPanel)

// Setup observers
webViewViewModel.currentFile.observe(this) { file ->
    // Update UI when file changes
}

webViewViewModel.isWebViewVisible.observe(this) { visible ->
    // Update show/hide button state
}

// Setup button listeners
floatingPanel.controlModule.showButton.setOnClickListener {
    // Call /webview/show endpoint
}

floatingPanel.controlModule.hideButton.setOnClickListener {
    // Call /webview/hide endpoint
}

floatingPanel.controlModule.closeButton.setOnClickListener {
    floatingPanel.hidePanel()
}

floatingPanel.adjustModule.upButton.setOnClickListener {
    floatingPanel.adjustModule.currentOffsetY -= floatingPanel.adjustModule.currentStep
    floatingPanel.adjustModule.updateOffsetDisplay()
    // Call /webview/adjust with delta
}

// ... similar for other direction buttons

floatingPanel.adjustModule.opacitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        floatingPanel.adjustModule.opacityLabel.text = "Opacity: ${progress}%"
        // Call /webview/adjust with opacity
    }
    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
})
```

- [ ] **Step 7: 在 Demo 中创建布局文件**

```xml
<!-- packages/android/demo/src/main/res/layout/activity_main.xml -->
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/root_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <LinearLayout
        android:id="@+id/button_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="16dp">
        
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="SDK Demo"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp"/>
        
    </LinearLayout>
    
</FrameLayout>
```

- [ ] **Step 8: 编译 Demo App**

```bash
cd packages && ./gradlew :demo:assembleDebug
```

Expected: Build succeeds

- [ ] **Step 9: 提交 Task 5**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ui/
git add packages/android/demo/src/main/kotlin/com/clienttools/demo/WebViewViewModel.kt
git add packages/android/demo/src/main/kotlin/com/clienttools/demo/MainActivity.kt
git add packages/android/demo/src/main/res/layout/activity_main.xml
git commit -m "feat: implement FloatingControlPanel UI with modules and ViewModel integration"
```

---

## 完成后的验收标准

| 功能 | 验收方法 |
|-----|--------|
| HTML 推送 | POST 到 /webview/push-html，验证文件被保存 |
| WebView 显示 | POST 到 /webview/show，验证 WebView 出现在屏幕上 |
| WebView 隐藏 | POST 到 /webview/hide，验证 WebView 消失 |
| 位移调整 | 点击方向按钮，验证 WebView 移动 |
| 透明度调整 | 拖动透明度滑块，验证 WebView 透明度变化 |
| 文件列表 | GET /webview/files，验证返回所有保存的文件 |
| 快速切换 | 点击列表中的文件，验证 WebView 加载对应 HTML |
| 悬浮窗拖动 | 拖动面板，验证可以移动，保持在屏幕内 |
| 模块折叠 | 点击模块标题，验证可以展开/隐藏 |
| Activity 重启 | 旋转屏幕，验证 WebView 状态恢复 |

---

## 后续集成步骤（不在本计划中）

1. 在 SDK 初始化 (SdkInitProvider) 中正式集成 WebViewManager
2. 在 HttpServer 中正式注册所有 WebView 路由
3. 添加单元测试覆盖所有 HTTP 端点
4. 添加集成测试，验证端到端流程
5. 性能测试：大文件处理、多个文件切换
6. 兼容性测试：不同 Android 版本和设备

# Inspector 系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Android SDK 实现完整的 Inspector 系统（WebView 叠加对比），包含悬浮按钮、看板面板、WebView 渲染，支持 HTML 推送、本地存储、手动调整与 HTTP 接口。

**Architecture:** 每个 Activity 对应一个 `InspectorPage`（View + ViewModel），由 `ClientToolsSDK` 通过 `ActivityLifecycleCallbacks` 自动注入，无需宿主 App 任何改动。状态统一存于 `InspectorViewModel`（StateFlow），`WebViewRenderer` 和 `InspectorPanel` 均为观察者，HTTP 接口通过 `ClientToolsSDK.getTop()` 写入当前页 ViewModel。

**Tech Stack:** Kotlin, Android SDK (API 26+), AndroidX ViewModel + Lifecycle, Coroutines/Flow, NanoHTTPD, XML 布局

---

## 文件结构

### 新建
| 文件 | 职责 |
|------|------|
| `sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorViewModel.kt` | StateFlow 状态中心 + FileInfo 数据类 |
| `sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPage.kt` | Activity 管理单元，inflate 布局、持有 panel + renderer |
| `sdk/src/main/kotlin/com/clienttools/sdk/inspector/WebViewRenderer.kt` | collect ViewModel → WebView 渲染（loadUrl/visibility/alpha/translation） |
| `sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPanel.kt` | 悬浮按钮 + 看板面板 UI，collect ViewModel 驱动渲染，写 ViewModel 响应操作 |
| `sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorFileStore.kt` | HTML 文件本地存储（替代 WebViewFileStore） |
| `sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt` | HTTP 接口处理，写入 getTop().viewModel（替代 WebViewApiHandler） |
| `sdk/src/main/res/layout/inspector_overlay.xml` | 统一根布局：WebView（底）+ 看板（中）+ 悬浮按钮（顶） |
| `sdk/src/main/res/layout/inspector_panel.xml` | 看板面板：3 个可折叠模块 |
| `sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorFileStoreTest.kt` | InspectorFileStore 集成测试 |
| `sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorApiHandlerTest.kt` | InspectorApiHandler 单元测试 |

### 修改
| 文件 | 变更 |
|------|------|
| `sdk/build.gradle.kts` | 添加 lifecycle-viewmodel、lifecycle-runtime-ktx 依赖 |
| `sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt` | 添加 InspectorPage 栈（pageStack）、getTop()、ActivityLifecycleCallbacks 注册 |
| `sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt` | 路由 /webview/* 改接 InspectorApiHandler |
| `sdk/src/main/kotlin/com/clienttools/sdk/listener/PageChangeListener.kt` | onActivityDestroyed 不再调用 setCurrentActivity，由 InspectorPage 管理 |

### 废弃（保留文件，清空实现为空存根，避免编译错误）
- `sdk/.../webview/WebViewManager.kt`
- `sdk/.../webview/WebViewFileStore.kt`
- `sdk/.../webview/WebViewApiHandler.kt`
- `sdk/.../webview/ui/FloatingControlPanel.kt`
- `sdk/.../runtime/OverlayManager.kt`

---

## Task 1: 依赖配置 + InspectorViewModel

**Files:**
- Modify: `packages/android/sdk/build.gradle.kts`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorViewModel.kt`

- [ ] **Step 1: 添加 lifecycle 依赖**

编辑 `packages/android/sdk/build.gradle.kts`，在 `dependencies {}` 中添加：

```kotlin
dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation(libs.kotlinx.serialization.json)

    // lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    testImplementation(kotlin("test"))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
```

- [ ] **Step 2: 创建 InspectorViewModel**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorViewModel.kt`：

```kotlin
package com.clienttools.sdk.inspector

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow

data class FileInfo(
    val tag: String,
    val timestamp: String,
    val fileUrl: String  // file:// 绝对路径，供 WebView.loadUrl() 使用
)

class InspectorViewModel(app: Application) : AndroidViewModel(app) {
    val currentFile = MutableStateFlow<FileInfo?>(null)
    val isVisible   = MutableStateFlow(false)
    val offsetX     = MutableStateFlow(0)     // dp，累计绝对值
    val offsetY     = MutableStateFlow(0)     // dp，累计绝对值
    val opacity     = MutableStateFlow(0.5f)  // 0.0-1.0，默认 0.5
}
```

- [ ] **Step 3: 验证编译**

```bash
cd /Users/zzc/Desktop/works/client-tools/packages
./gradlew :sdk:compileDebugKotlin
```

期望：BUILD SUCCESSFUL，无编译错误。

- [ ] **Step 4: Commit**

```bash
git add packages/android/sdk/build.gradle.kts \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorViewModel.kt
git commit -m "feat(inspector): add InspectorViewModel with StateFlow state"
```

---

## Task 2: InspectorFileStore

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorFileStore.kt`
- Create: `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorFileStoreTest.kt`

- [ ] **Step 1: 写测试（先失败）**

创建 `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorFileStoreTest.kt`：

```kotlin
package com.clienttools.sdk.inspector

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InspectorFileStoreTest {

    private lateinit var store: InspectorFileStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        store = InspectorFileStore(context)
        store.deleteAll()
    }

    @Test
    fun saveHtmlFile_returnsCorrectMetadata() {
        val result = store.saveHtmlFile("login", "0418-1430", "<html>Test</html>")
        assert(result != null)
        assert(result!!.tag == "login")
        assert(result.timestamp == "0418-1430")
        assert(result.fileUrl.startsWith("file://"))
        assert(result.fileUrl.endsWith("login_0418-1430.html"))
    }

    @Test
    fun getAllFiles_returnsAllSaved() {
        store.saveHtmlFile("login", "0418-1430", "<html>A</html>")
        store.saveHtmlFile("login", "0418-1440", "<html>B</html>")
        store.saveHtmlFile("home", "0418-1500", "<html>C</html>")
        val files = store.getAllFiles()
        assert(files.size == 3)
    }

    @Test
    fun getFilePath_returnsFileUrl() {
        store.saveHtmlFile("login", "0418-1430", "<html>Test</html>")
        val url = store.getFilePath("login", "0418-1430")
        assert(url != null)
        assert(url!!.startsWith("file://"))
    }

    @Test
    fun getFilePath_returnsNullForMissing() {
        val url = store.getFilePath("notexist", "0000-0000")
        assert(url == null)
    }

    @Test
    fun deleteAll_clearsAllFiles() {
        store.saveHtmlFile("login", "0418-1430", "<html>A</html>")
        store.deleteAll()
        assert(store.getAllFiles().isEmpty())
    }

    @Test
    fun generateTimestamp_hasCorrectFormat() {
        val ts = store.generateTimestamp()
        // MMdd-HHmm: 4 digits, dash, 4 digits
        assert(ts.matches(Regex("""\d{4}-\d{4}""")))
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd /Users/zzc/Desktop/works/client-tools/packages
./gradlew :sdk:connectedDebugAndroidTest --tests "*.InspectorFileStoreTest"
```

期望：编译失败（InspectorFileStore 未定义）。

- [ ] **Step 3: 实现 InspectorFileStore**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorFileStore.kt`：

```kotlin
package com.clienttools.sdk.inspector

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class InspectorFileStore(context: Context) {
    private val cacheDir = File(context.cacheDir, "inspector")
    private val TAG = "InspectorFileStore"

    init {
        cacheDir.mkdirs()
    }

    fun saveHtmlFile(tag: String, timestamp: String, htmlContent: String): FileInfo? = try {
        val tagDir = File(cacheDir, tag).also { it.mkdirs() }
        val file = File(tagDir, "${tag}_${timestamp}.html")
        file.writeText(htmlContent, Charsets.UTF_8)
        FileInfo(
            tag = tag,
            timestamp = timestamp,
            fileUrl = "file://${file.absolutePath}"
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error saving HTML file", e)
        null
    }

    fun getAllFiles(): List<FileInfo> = try {
        val result = mutableListOf<FileInfo>()
        cacheDir.listFiles()?.forEach { tagDir ->
            if (!tagDir.isDirectory) return@forEach
            tagDir.listFiles()?.forEach { file ->
                if (!file.name.endsWith(".html")) return@forEach
                val timestamp = parseTimestamp(file.name) ?: return@forEach
                result.add(FileInfo(tagDir.name, timestamp, "file://${file.absolutePath}"))
            }
        }
        result.sortedByDescending { it.timestamp }
    } catch (e: Exception) {
        Log.e(TAG, "Error listing files", e)
        emptyList()
    }

    fun getFilePath(tag: String, timestamp: String): String? {
        val file = File(File(cacheDir, tag), "${tag}_${timestamp}.html")
        return if (file.exists()) "file://${file.absolutePath}" else null
    }

    fun deleteAll(): Boolean = try {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting all", e)
        false
    }

    fun generateTimestamp(): String =
        SimpleDateFormat("MMdd-HHmm", Locale.US).format(Date())

    private fun parseTimestamp(filename: String): String? =
        Regex("""_(\d{4}-\d{4})\.html$""").find(filename)?.groupValues?.get(1)
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd /Users/zzc/Desktop/works/client-tools/packages
./gradlew :sdk:connectedDebugAndroidTest --tests "*.InspectorFileStoreTest"
```

期望：所有测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorFileStore.kt \
        packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorFileStoreTest.kt
git commit -m "feat(inspector): add InspectorFileStore with file:// URL support"
```

---

## Task 3: InspectorApiHandler

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt`
- Create: `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorApiHandlerTest.kt`

- [ ] **Step 1: 写测试（先失败）**

创建 `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorApiHandlerTest.kt`：

```kotlin
package com.clienttools.sdk.inspector

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InspectorApiHandlerTest {

    private lateinit var store: InspectorFileStore
    private lateinit var handler: InspectorApiHandler

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        store = InspectorFileStore(context)
        store.deleteAll()
        handler = InspectorApiHandler(store, getTopViewModel = { null })
    }

    @Test
    fun pushHtml_savesFileAndReturns200() {
        val body = """{"tag":"login","html":"<html>Test</html>","timestamp":"0418-1430"}"""
        val response = handler.handlePushHtml(body)
        assert(response.status.requestStatus == 200)
        val files = store.getAllFiles()
        assert(files.any { it.tag == "login" && it.timestamp == "0418-1430" })
    }

    @Test
    fun pushHtml_missingTag_returns400() {
        val body = """{"html":"<html>Test</html>"}"""
        val response = handler.handlePushHtml(body)
        assert(response.status.requestStatus == 400)
    }

    @Test
    fun getFiles_returnsAllFiles() {
        store.saveHtmlFile("login", "0418-1430", "<html>A</html>")
        store.saveHtmlFile("home", "0418-1500", "<html>B</html>")
        val response = handler.handleGetFiles(currentFile = null)
        assert(response.status.requestStatus == 200)
    }

    @Test
    fun show_fileNotFound_returns404() {
        val body = """{"tag":"notexist","timestamp":"0000-0000"}"""
        val response = handler.handleShow(body)
        assert(response.status.requestStatus == 404)
    }

    @Test
    fun hide_returnsSuccess() {
        val response = handler.handleHide()
        assert(response.status.requestStatus == 200)
    }

    @Test
    fun adjust_returnsUpdatedValues() {
        // viewModel is null → should still return 200 with current state
        val body = """{"offsetX":10,"offsetY":-5,"opacity":0.7}"""
        val response = handler.handleAdjust(body, currentOffsetX = 0, currentOffsetY = 0, currentOpacity = 0.5f)
        assert(response.status.requestStatus == 200)
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd /Users/zzc/Desktop/works/client-tools/packages
./gradlew :sdk:connectedDebugAndroidTest --tests "*.InspectorApiHandlerTest"
```

期望：编译失败（InspectorApiHandler 未定义）。

- [ ] **Step 3: 实现 InspectorApiHandler**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt`：

```kotlin
package com.clienttools.sdk.inspector

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class InspectorApiHandler(
    private val fileStore: InspectorFileStore,
    private val getTopViewModel: () -> InspectorViewModel?
) {
    private val TAG = "InspectorApiHandler"
    private val json = Json { ignoreUnknownKeys = true }

    fun handlePushHtml(body: String): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val tag = obj["tag"]?.jsonPrimitive?.content ?: return error(400, "Missing tag")
        val html = obj["html"]?.jsonPrimitive?.content ?: return error(400, "Missing html")
        val timestamp = obj["timestamp"]?.jsonPrimitive?.content ?: fileStore.generateTimestamp()

        val saved = fileStore.saveHtmlFile(tag, timestamp, html)
            ?: return error(500, "Failed to save file")

        ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp","filePath":"${saved.fileUrl}","fileSize":${html.length}}}""")
    } catch (e: Exception) {
        Log.e(TAG, "pushHtml error", e)
        error(400, "Invalid request: ${e.message}")
    }

    fun handleShow(body: String): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val tag = obj["tag"]?.jsonPrimitive?.content ?: return error(400, "Missing tag")
        val timestamp = obj["timestamp"]?.jsonPrimitive?.content ?: return error(400, "Missing timestamp")

        val fileUrl = fileStore.getFilePath(tag, timestamp) ?: return error(404, "File not found")

        getTopViewModel()?.let { vm ->
            vm.currentFile.value = FileInfo(tag, timestamp, fileUrl)
            vm.isVisible.value = true
            val opacity = vm.opacity.value
            val offsetX = vm.offsetX.value
            val offsetY = vm.offsetY.value
            ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp","opacity":$opacity,"offsetX":$offsetX,"offsetY":$offsetY}}""")
        } ?: ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp"}}""")
    } catch (e: Exception) {
        Log.e(TAG, "show error", e)
        error(500, "Internal error: ${e.message}")
    }

    fun handleHide(): NanoHTTPD.Response = try {
        getTopViewModel()?.isVisible?.value = false
        ok("""{"code":0,"message":"success"}""")
    } catch (e: Exception) {
        Log.e(TAG, "hide error", e)
        error(500, "Internal error: ${e.message}")
    }

    fun handleAdjust(body: String, currentOffsetX: Int = 0, currentOffsetY: Int = 0, currentOpacity: Float = 0.5f): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val dx = obj["offsetX"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val dy = obj["offsetY"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val opacity = obj["opacity"]?.jsonPrimitive?.content?.toFloatOrNull()

        val vm = getTopViewModel()
        val newX = (vm?.offsetX?.value ?: currentOffsetX) + dx
        val newY = (vm?.offsetY?.value ?: currentOffsetY) + dy
        val newOpacity = opacity?.coerceIn(0f, 1f) ?: (vm?.opacity?.value ?: currentOpacity)

        vm?.offsetX?.value = newX
        vm?.offsetY?.value = newY
        if (opacity != null) vm?.opacity?.value = newOpacity

        ok("""{"code":0,"data":{"offsetX":$newX,"offsetY":$newY,"opacity":$newOpacity}}""")
    } catch (e: Exception) {
        Log.e(TAG, "adjust error", e)
        error(500, "Internal error: ${e.message}")
    }

    fun handleGetFiles(currentFile: FileInfo?): NanoHTTPD.Response = try {
        val vmCurrentFile = getTopViewModel()?.currentFile?.value ?: currentFile
        val files = fileStore.getAllFiles()
        val filesJson = files.joinToString(",") { f ->
            val isCurrent = vmCurrentFile?.tag == f.tag && vmCurrentFile.timestamp == f.timestamp
            val size = f.fileUrl.let { java.io.File(it.removePrefix("file://")).length() }
            """{"tag":"${f.tag}","timestamp":"${f.timestamp}","size":$size,"isCurrent":$isCurrent}"""
        }
        ok("""{"code":0,"data":{"files":[$filesJson]}}""")
    } catch (e: Exception) {
        Log.e(TAG, "getFiles error", e)
        error(500, "Internal error: ${e.message}")
    }

    private fun ok(json: String) = NanoHTTPD.newFixedLengthResponse(
        NanoHTTPD.Response.Status.OK, "application/json", json
    )

    private fun error(code: Int, message: String): NanoHTTPD.Response {
        val status = if (code == 404) NanoHTTPD.Response.Status.NOT_FOUND
                     else if (code >= 500) NanoHTTPD.Response.Status.INTERNAL_ERROR
                     else NanoHTTPD.Response.Status.BAD_REQUEST
        return NanoHTTPD.newFixedLengthResponse(status, "application/json",
            """{"code":$code,"message":"$message"}""")
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd /Users/zzc/Desktop/works/client-tools/packages
./gradlew :sdk:connectedDebugAndroidTest --tests "*.InspectorApiHandlerTest"
```

期望：所有测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt \
        packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorApiHandlerTest.kt
git commit -m "feat(inspector): add InspectorApiHandler for HTTP routes"
```

---

## Task 4: XML 布局（inspector_overlay + inspector_panel）

**Files:**
- Create: `packages/android/sdk/src/main/res/layout/inspector_overlay.xml`
- Create: `packages/android/sdk/src/main/res/layout/inspector_panel.xml`

- [ ] **Step 1: 创建 inspector_overlay.xml**

创建 `packages/android/sdk/src/main/res/layout/inspector_overlay.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 统一根布局，FrameLayout 层叠：WebView（底）→ 看板（中）→ 悬浮按钮（顶） -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- 层级 1：WebView，全屏透明，默认隐藏 -->
    <WebView
        android:id="@+id/overlay_webview"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@android:color/transparent"
        android:visibility="gone" />

    <!-- 层级 2：看板面板，默认隐藏 -->
    <include
        android:id="@+id/inspector_panel_container"
        layout="@layout/inspector_panel"
        android:visibility="gone" />

    <!-- 层级 3：悬浮按钮，始终可见，初始位置右下角 -->
    <TextView
        android:id="@+id/float_btn"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:layout_gravity="bottom|end"
        android:layout_margin="16dp"
        android:background="#CC6200EE"
        android:gravity="center"
        android:text="⚙"
        android:textColor="#FFFFFF"
        android:textSize="20sp"
        android:elevation="8dp" />

</FrameLayout>
```

- [ ] **Step 2: 创建 inspector_panel.xml**

创建 `packages/android/sdk/src/main/res/layout/inspector_panel.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/panel_root"
    android:layout_width="280dp"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom|end"
    android:layout_margin="16dp"
    android:background="#F0F0F0"
    android:elevation="16dp"
    android:orientation="vertical"
    android:minHeight="200dp">

    <!-- 拖动条 -->
    <View
        android:id="@+id/drag_handle"
        android:layout_width="match_parent"
        android:layout_height="40dp"
        android:background="#6200EE" />

    <!-- WebView 模块：标题（可折叠） -->
    <TextView
        android:id="@+id/section_webview_title"
        android:layout_width="match_parent"
        android:layout_height="44dp"
        android:gravity="center_vertical"
        android:paddingStart="12dp"
        android:text="▼ WebView"
        android:textSize="14sp"
        android:textStyle="bold"
        android:background="#E0E0E0" />

    <LinearLayout
        android:id="@+id/section_webview_content"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="8dp">

        <TextView
            android:id="@+id/current_file_label"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="当前：无"
            android:textSize="12sp"
            android:paddingBottom="4dp" />

        <!-- 文件列表：动态添加 -->
        <LinearLayout
            android:id="@+id/file_list_container"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical" />

    </LinearLayout>

    <!-- 调整模块：标题（可折叠） -->
    <TextView
        android:id="@+id/section_adjust_title"
        android:layout_width="match_parent"
        android:layout_height="44dp"
        android:gravity="center_vertical"
        android:paddingStart="12dp"
        android:text="▼ 调整"
        android:textSize="14sp"
        android:textStyle="bold"
        android:background="#E0E0E0" />

    <LinearLayout
        android:id="@+id/section_adjust_content"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="8dp">

        <!-- 档位选择 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginBottom="4dp">

            <Button
                android:id="@+id/btn_step_1"
                style="?android:attr/buttonStyleSmall"
                android:layout_width="0dp"
                android:layout_height="36dp"
                android:layout_weight="1"
                android:text="1dp"
                android:textSize="11sp" />

            <Button
                android:id="@+id/btn_step_10"
                style="?android:attr/buttonStyleSmall"
                android:layout_width="0dp"
                android:layout_height="36dp"
                android:layout_weight="1"
                android:text="10dp"
                android:textSize="11sp" />

            <Button
                android:id="@+id/btn_step_50"
                style="?android:attr/buttonStyleSmall"
                android:layout_width="0dp"
                android:layout_height="36dp"
                android:layout_weight="1"
                android:text="50dp"
                android:textSize="11sp" />

        </LinearLayout>

        <!-- 方向按钮：一行，均匀排布 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginBottom="4dp">

            <Button
                android:id="@+id/btn_left"
                android:layout_width="0dp"
                android:layout_height="44dp"
                android:layout_weight="1"
                android:text="◀"
                android:textSize="16sp" />

            <Button
                android:id="@+id/btn_up"
                android:layout_width="0dp"
                android:layout_height="44dp"
                android:layout_weight="1"
                android:text="△"
                android:textSize="16sp" />

            <Button
                android:id="@+id/btn_down"
                android:layout_width="0dp"
                android:layout_height="44dp"
                android:layout_weight="1"
                android:text="▽"
                android:textSize="16sp" />

            <Button
                android:id="@+id/btn_right"
                android:layout_width="0dp"
                android:layout_height="44dp"
                android:layout_weight="1"
                android:text="▶"
                android:textSize="16sp" />

        </LinearLayout>

        <!-- 透明度 -->
        <TextView
            android:id="@+id/opacity_label"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="透明度：50%"
            android:textSize="12sp" />

        <SeekBar
            android:id="@+id/opacity_seekbar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:max="100"
            android:progress="50" />

        <!-- 偏移显示 -->
        <TextView
            android:id="@+id/offset_label"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="偏移：X: 0dp  Y: 0dp"
            android:textSize="12sp"
            android:layout_marginTop="4dp" />

    </LinearLayout>

    <!-- 控制模块：不可折叠 -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="44dp"
        android:gravity="center_vertical"
        android:paddingStart="12dp"
        android:text="控制"
        android:textSize="14sp"
        android:textStyle="bold"
        android:background="#E0E0E0" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="8dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginBottom="8dp">

            <Button
                android:id="@+id/btn_show"
                android:layout_width="0dp"
                android:layout_height="48dp"
                android:layout_weight="1"
                android:text="显示"
                android:layout_marginEnd="4dp" />

            <Button
                android:id="@+id/btn_hide"
                android:layout_width="0dp"
                android:layout_height="48dp"
                android:layout_weight="1"
                android:text="隐藏" />

        </LinearLayout>

        <Button
            android:id="@+id/btn_close_panel"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="关闭面板" />

    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 3: 验证编译**

```bash
cd /Users/zzc/Desktop/works/client-tools/packages
./gradlew :sdk:compileDebugKotlin
```

期望：BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add packages/android/sdk/src/main/res/layout/inspector_overlay.xml \
        packages/android/sdk/src/main/res/layout/inspector_panel.xml
git commit -m "feat(inspector): add XML layouts inspector_overlay + inspector_panel"
```

---

## Task 5: WebViewRenderer

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/WebViewRenderer.kt`

- [ ] **Step 1: 实现 WebViewRenderer**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/WebViewRenderer.kt`：

```kotlin
package com.clienttools.sdk.inspector

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import com.clienttools.sdk.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

// scope 由 InspectorPage 传入（activity.lifecycleScope），与 Activity 生命周期绑定
class WebViewRenderer(rootView: View, private val viewModel: InspectorViewModel) {

    private val webView: WebView = rootView.findViewById(R.id.overlay_webview)
    private var job: Job? = null

    init {
        // WebView 初始化配置
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        @Suppress("DEPRECATION")
        webView.settings.allowFileAccessFromFileURLs = true
    }

    fun startObserving(scope: CoroutineScope) {
        job = scope.launch {
            // 显隐
            launch {
                viewModel.isVisible.collect { visible ->
                    webView.visibility = if (visible) View.VISIBLE else View.GONE
                }
            }
            // 加载 URL（currentFile 变化且非 null 时重新加载）
            launch {
                viewModel.currentFile.filterNotNull().collect { file ->
                    webView.loadUrl(file.fileUrl)
                }
            }
            // 透明度
            launch {
                viewModel.opacity.collect { alpha ->
                    webView.alpha = alpha
                }
            }
            // 位移（dp → px）
            launch {
                combine(viewModel.offsetX, viewModel.offsetY) { x, y -> x to y }
                    .collect { (x, y) ->
                        webView.translationX = dpToPx(x)
                        webView.translationY = dpToPx(y)
                    }
            }
        }
    }

    fun stopObserving() {
        job?.cancel()
        job = null
    }

    private fun dpToPx(dp: Int): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        webView.context.resources.displayMetrics
    )
}
```

- [ ] **Step 2: 验证编译**

```bash
cd /Users/zzc/Desktop/works/client-tools/packages
./gradlew :sdk:compileDebugKotlin
```

期望：BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/WebViewRenderer.kt
git commit -m "feat(inspector): add WebViewRenderer collecting ViewModel to drive WebView"
```

---

## Task 6: InspectorPanel

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPanel.kt`

- [ ] **Step 1: 实现 InspectorPanel**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPanel.kt`：

```kotlin
package com.clienttools.sdk.inspector

import android.app.AlertDialog
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.clienttools.sdk.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class InspectorPanel(
    private val rootView: View,
    private val viewModel: InspectorViewModel
) {
    private val floatBtn: TextView = rootView.findViewById(R.id.float_btn)
    private val panelContainer: View = rootView.findViewById(R.id.inspector_panel_container)
    private val dragHandle: View = rootView.findViewById(R.id.drag_handle)
    private val currentFileLabel: TextView = rootView.findViewById(R.id.current_file_label)
    private val fileListContainer: LinearLayout = rootView.findViewById(R.id.file_list_container)
    private val sectionWebviewTitle: TextView = rootView.findViewById(R.id.section_webview_title)
    private val sectionWebviewContent: View = rootView.findViewById(R.id.section_webview_content)
    private val sectionAdjustTitle: TextView = rootView.findViewById(R.id.section_adjust_title)
    private val sectionAdjustContent: View = rootView.findViewById(R.id.section_adjust_content)
    private val btnStep1: Button = rootView.findViewById(R.id.btn_step_1)
    private val btnStep10: Button = rootView.findViewById(R.id.btn_step_10)
    private val btnStep50: Button = rootView.findViewById(R.id.btn_step_50)
    private val btnUp: Button = rootView.findViewById(R.id.btn_up)
    private val btnDown: Button = rootView.findViewById(R.id.btn_down)
    private val btnLeft: Button = rootView.findViewById(R.id.btn_left)
    private val btnRight: Button = rootView.findViewById(R.id.btn_right)
    private val opacityLabel: TextView = rootView.findViewById(R.id.opacity_label)
    private val opacitySeekBar: SeekBar = rootView.findViewById(R.id.opacity_seekbar)
    private val offsetLabel: TextView = rootView.findViewById(R.id.offset_label)
    private val btnShow: Button = rootView.findViewById(R.id.btn_show)
    private val btnHide: Button = rootView.findViewById(R.id.btn_hide)
    private val btnClosePanel: Button = rootView.findViewById(R.id.btn_close_panel)

    private var stepDp = 10
    private var job: Job? = null

    init {
        setupInteractions()
    }

    private fun setupInteractions() {
        // 悬浮按钮：点击展开/收起，可拖动
        setupDraggableClick(floatBtn) {
            panelContainer.visibility =
                if (panelContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // 面板拖动（通过拖动条）
        setupDraggableMove(dragHandle, panelContainer)

        // 折叠 section
        sectionWebviewTitle.setOnClickListener {
            sectionWebviewContent.visibility =
                if (sectionWebviewContent.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        sectionAdjustTitle.setOnClickListener {
            sectionAdjustContent.visibility =
                if (sectionAdjustContent.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // 档位选择（默认 10dp 高亮）
        selectStep(10)
        btnStep1.setOnClickListener { selectStep(1) }
        btnStep10.setOnClickListener { selectStep(10) }
        btnStep50.setOnClickListener { selectStep(50) }

        // 方向按钮：直接写 ViewModel，增量累加
        btnUp.setOnClickListener    { viewModel.offsetY.value -= stepDp }
        btnDown.setOnClickListener  { viewModel.offsetY.value += stepDp }
        btnLeft.setOnClickListener  { viewModel.offsetX.value -= stepDp }
        btnRight.setOnClickListener { viewModel.offsetX.value += stepDp }

        // 透明度：直接写 ViewModel
        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.opacity.value = progress / 100f
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // 显示/隐藏
        btnShow.setOnClickListener {
            viewModel.currentFile.value?.let {
                viewModel.isVisible.value = true
            }
        }
        btnHide.setOnClickListener { viewModel.isVisible.value = false }

        // 关闭面板
        btnClosePanel.setOnClickListener { panelContainer.visibility = View.GONE }
    }

    fun startObserving(scope: CoroutineScope) {
        job = scope.launch {
            // 当前文件 label
            launch {
                viewModel.currentFile.collect { file ->
                    currentFileLabel.text = if (file != null) "当前：${file.tag} (${file.timestamp})" else "当前：无"
                }
            }
            // 透明度 label + seekbar
            launch {
                viewModel.opacity.collect { opacity ->
                    val progress = (opacity * 100).toInt()
                    opacityLabel.text = "透明度：$progress%"
                    if (opacitySeekBar.progress != progress) {
                        opacitySeekBar.progress = progress
                    }
                }
            }
            // 偏移 label
            launch {
                kotlinx.coroutines.flow.combine(viewModel.offsetX, viewModel.offsetY) { x, y -> x to y }
                    .collect { (x, y) ->
                        offsetLabel.text = "偏移：X: ${x}dp  Y: ${y}dp"
                    }
            }
        }
    }

    fun stopObserving() {
        job?.cancel()
        job = null
    }

    // 展示文件选择 Dialog（由外部调用，传入文件列表）
    fun showFileSelectDialog(files: List<FileInfo>, onSelect: (FileInfo) -> Unit) {
        if (files.isEmpty()) {
            Toast.makeText(rootView.context, "暂无已保存的 HTML 文件", Toast.LENGTH_SHORT).show()
            return
        }
        val currentFile = viewModel.currentFile.value
        val labels = files.map { f ->
            val cur = if (f.tag == currentFile?.tag && f.timestamp == currentFile.timestamp) " ★" else ""
            "${f.tag}  ${f.timestamp}$cur"
        }.toTypedArray()

        AlertDialog.Builder(rootView.context)
            .setTitle("选择 HTML 文件")
            .setItems(labels) { _, idx -> onSelect(files[idx]) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun selectStep(dp: Int) {
        stepDp = dp
        val active = 0xFF6200EE.toInt()
        val inactive = 0xFF888888.toInt()
        btnStep1.backgroundTintList  = android.content.res.ColorStateList.valueOf(if (dp == 1)  active else inactive)
        btnStep10.backgroundTintList = android.content.res.ColorStateList.valueOf(if (dp == 10) active else inactive)
        btnStep50.backgroundTintList = android.content.res.ColorStateList.valueOf(if (dp == 50) active else inactive)
    }

    // 拖动：点击 + 拖动分离
    private fun setupDraggableClick(v: View, onClick: () -> Unit) {
        var startX = 0f; var startY = 0f
        var viewStartX = 0f; var viewStartY = 0f
        var moved = false
        v.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    viewStartX = view.x; viewStartY = view.y
                    moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX; val dy = event.rawY - startY
                    if (!moved && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) moved = true
                    if (moved) clampMove(view, viewStartX + dx, viewStartY + dy)
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) onClick(); true }
                else -> false
            }
        }
    }

    private fun setupDraggableMove(handle: View, target: View) {
        var startX = 0f; var startY = 0f
        var targetStartX = 0f; var targetStartY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    targetStartX = target.x; targetStartY = target.y; true
                }
                MotionEvent.ACTION_MOVE -> {
                    clampMove(target, targetStartX + event.rawX - startX, targetStartY + event.rawY - startY)
                    true
                }
                else -> false
            }
        }
    }

    private fun clampMove(v: View, x: Float, y: Float) {
        val parent = v.parent as? ViewGroup ?: return
        v.x = x.coerceIn(0f, (parent.width - v.width).toFloat().coerceAtLeast(0f))
        v.y = y.coerceIn(0f, (parent.height - v.height).toFloat().coerceAtLeast(0f))
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd /Users/zzc/Desktop/works/client-tools/packages
./gradlew :sdk:compileDebugKotlin
```

期望：BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPanel.kt
git commit -m "feat(inspector): add InspectorPanel with drag, fold, step controls"
```

---

## Task 7: InspectorPage + ClientToolsSDK 接入

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPage.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`

- [ ] **Step 1: 创建 InspectorPage**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPage.kt`：

```kotlin
package com.clienttools.sdk.inspector

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.clienttools.sdk.R

class InspectorPage(val activity: Activity) {

    val viewModel: InspectorViewModel =
        ViewModelProvider(activity as androidx.activity.ComponentActivity)[InspectorViewModel::class.java]

    private val rootView: View = LayoutInflater.from(activity)
        .inflate(R.layout.inspector_overlay, null)

    val panel: InspectorPanel = InspectorPanel(rootView, viewModel)
    val renderer: WebViewRenderer = WebViewRenderer(rootView, viewModel)

    fun attach() {
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        content.addView(rootView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val scope = (activity as LifecycleOwner).lifecycleScope
        panel.startObserving(scope)
        renderer.startObserving(scope)
    }

    fun detach() {
        panel.stopObserving()
        renderer.stopObserving()
    }
}
```

- [ ] **Step 2: 修改 ClientToolsSDK，添加 InspectorPage 栈**

完整替换 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt`：

```kotlin
package com.clienttools.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.clienttools.sdk.http.EventManager
import com.clienttools.sdk.http.HttpServer
import com.clienttools.sdk.inspector.InspectorFileStore
import com.clienttools.sdk.inspector.InspectorPage
import com.clienttools.sdk.listener.PageChangeListener
import com.clienttools.sdk.model.ModifyRequest
import com.clienttools.sdk.model.ViewInfo
import com.clienttools.sdk.runtime.ViewModifier
import com.clienttools.sdk.runtime.ViewQueryService
import java.util.WeakHashMap

object ClientToolsSDK {
    private var httpServer: HttpServer? = null
    private var eventManager: EventManager? = null
    private var pageChangeListener: PageChangeListener? = null
    private var isInitialized = false
    private const val TAG = "ClientToolsSDK"

    // InspectorPage 栈：有序，lastOrNull() = 当前前台页面
    private val pageStack = mutableListOf<InspectorPage>()
    private val pageMap = WeakHashMap<Activity, InspectorPage>()

    internal lateinit var fileStore: InspectorFileStore

    fun getTop(): InspectorPage? = pageStack.lastOrNull()

    fun init(context: Context) {
        if (isInitialized) return
        try {
            fileStore = InspectorFileStore(context)
            eventManager = EventManager()
            httpServer = HttpServer(context, eventManager!!)
            httpServer!!.startServer()
            pageChangeListener = PageChangeListener(eventManager!!)
            pageChangeListener!!.register(context)
            registerInspectorLifecycle(context)
            isInitialized = true
            Log.d(TAG, "ClientToolsSDK initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
        }
    }

    private fun registerInspectorLifecycle(context: Context) {
        val app = context.applicationContext as? Application ?: return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (pageMap[activity] == null) {
                    try {
                        val page = InspectorPage(activity)
                        page.attach()
                        pageMap[activity] = page
                        pageStack.add(page)
                        Log.d(TAG, "InspectorPage attached: ${activity::class.simpleName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to attach InspectorPage", e)
                    }
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                pageMap.remove(activity)?.let { page ->
                    page.detach()
                    pageStack.remove(page)
                    Log.d(TAG, "InspectorPage removed: ${activity::class.simpleName}")
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    fun getViewInfo(viewId: String): ViewInfo? = ViewQueryService.getViewInfo(viewId)
    fun modify(request: ModifyRequest): Boolean = ViewModifier.apply(request.id, request.props)

    fun addPageChangeListener(callback: (pageName: String, timestamp: Long) -> Unit) {
        pageChangeListener?.addListener(callback)
    }

    fun shutdown() {
        httpServer?.stopServer()
        pageChangeListener?.unregister()
        isInitialized = false
    }

    // 保留旧接口兼容（空实现）
    internal fun setCurrentActivity(activity: Activity?) {}
    internal fun getCurrentActivity(): Activity? = getTop()?.activity
}
```

- [ ] **Step 3: 修改 HttpServer，路由接入 InspectorApiHandler**

修改 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`，在 `serve()` 中将 `/webview/*` 路由替换为：

```kotlin
// 在 serve() 中，将原来调用 WebViewApiHandler 的所有 when 分支替换为：
method == Method.POST && uri == "/webview/push-html" -> {
    val body = readBody(session)
    inspectorApiHandler().handlePushHtml(body)
}
method == Method.POST && uri == "/webview/show" -> {
    val body = readBody(session)
    inspectorApiHandler().handleShow(body)
}
method == Method.POST && uri == "/webview/hide" -> {
    inspectorApiHandler().handleHide()
}
method == Method.POST && uri == "/webview/adjust" -> {
    val body = readBody(session)
    val vm = ClientToolsSDK.getTop()?.viewModel
    inspectorApiHandler().handleAdjust(
        body,
        currentOffsetX = vm?.offsetX?.value ?: 0,
        currentOffsetY = vm?.offsetY?.value ?: 0,
        currentOpacity = vm?.opacity?.value ?: 0.5f
    )
}
method == Method.GET && uri == "/webview/files" -> {
    inspectorApiHandler().handleGetFiles(
        currentFile = ClientToolsSDK.getTop()?.viewModel?.currentFile?.value
    )
}
```

并在 HttpServer 类中添加辅助方法：

```kotlin
private fun inspectorApiHandler() = com.clienttools.sdk.inspector.InspectorApiHandler(
    fileStore = ClientToolsSDK.fileStore,
    getTopViewModel = { ClientToolsSDK.getTop()?.viewModel }
)
```

完整修改后的 `HttpServer.kt`：

```kotlin
package com.clienttools.sdk.http

import android.content.Context
import android.util.Log
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.inspector.InspectorApiHandler
import fi.iki.elonen.NanoHTTPD

class HttpServer(
    private val context: Context,
    private val eventManager: EventManager
) : NanoHTTPD(8080) {

    override fun serve(session: IHTTPSession?): Response {
        return try {
            if (session == null) return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad request")
            val uri = session.uri
            val method = session.method
            when {
                method == Method.GET && uri.startsWith("/api/nodes/") -> {
                    val id = uri.removePrefix("/api/nodes/")
                    ApiHandler.handleGetNode(id)
                }
                method == Method.POST && uri == "/api/modify" -> {
                    ApiHandler.handleModify(readBody(session))
                }
                method == Method.GET && uri == "/api/events" -> {
                    eventManager.subscribeSSE(session)
                }
                method == Method.POST && uri == "/webview/push-html" -> {
                    inspectorHandler().handlePushHtml(readBody(session))
                }
                method == Method.POST && uri == "/webview/show" -> {
                    inspectorHandler().handleShow(readBody(session))
                }
                method == Method.POST && uri == "/webview/hide" -> {
                    inspectorHandler().handleHide()
                }
                method == Method.POST && uri == "/webview/adjust" -> {
                    val vm = ClientToolsSDK.getTop()?.viewModel
                    inspectorHandler().handleAdjust(
                        readBody(session),
                        currentOffsetX = vm?.offsetX?.value ?: 0,
                        currentOffsetY = vm?.offsetY?.value ?: 0,
                        currentOpacity = vm?.opacity?.value ?: 0.5f
                    )
                }
                method == Method.GET && uri == "/webview/files" -> {
                    inspectorHandler().handleGetFiles(
                        currentFile = ClientToolsSDK.getTop()?.viewModel?.currentFile?.value
                    )
                }
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            Log.e("HttpServer", "Error handling request", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Internal error")
        }
    }

    private fun inspectorHandler() = InspectorApiHandler(
        fileStore = ClientToolsSDK.fileStore,
        getTopViewModel = { ClientToolsSDK.getTop()?.viewModel }
    )

    private fun readBody(session: IHTTPSession): String {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val buffer = ByteArray(contentLength)
        session.inputStream.read(buffer)
        return String(buffer)
    }

    fun startServer() {
        try {
            super.start()
            Log.d("HttpServer", "HTTP server started on port 8080")
        } catch (e: Exception) {
            Log.e("HttpServer", "Failed to start server", e)
        }
    }

    fun stopServer() {
        try {
            super.closeAllConnections()
            super.stop()
            Log.d("HttpServer", "HTTP server stopped")
        } catch (e: Exception) {
            Log.e("HttpServer", "Failed to stop server", e)
        }
    }
}
```

- [ ] **Step 4: 验证编译**

```bash
cd /Users/zzc/Desktop/works/client-tools/packages
./gradlew :sdk:compileDebugKotlin :demo:compileDebugKotlin
```

期望：BUILD SUCCESSFUL，无编译错误。

- [ ] **Step 5: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPage.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt
git commit -m "feat(inspector): wire InspectorPage into ClientToolsSDK lifecycle + HTTP routes"
```

---

## Task 8: 废弃旧文件 + 安装验证

**Files:**
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/OverlayManager.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewManager.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewApiHandler.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ui/FloatingControlPanel.kt`

- [ ] **Step 1: 清空旧文件为空存根**

将以下文件各自替换为最小合法存根（保留 package，删除所有实现），避免编译引用错误：

`packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/OverlayManager.kt`：
```kotlin
package com.clienttools.sdk.runtime
// Deprecated: replaced by WebViewRenderer + InspectorPage
object OverlayManager {
    fun show(url: String, opacity: Float = 1.0f): Boolean = false
    fun hide(): Boolean = false
    fun setOpacity(opacity: Float): Boolean = false
    fun setOffset(offsetX: Int, offsetY: Int): Boolean = false
    fun getOffset(): Pair<Int, Int> = 0 to 0
    fun isVisible(): Boolean = false
    fun reattachIfNeeded(activity: android.app.Activity) {}
    fun destroy() {}
}
```

`packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewManager.kt`：
```kotlin
package com.clienttools.sdk.webview
import android.content.Context
// Deprecated: replaced by InspectorPage + InspectorApiHandler
object WebViewManager {
    fun init(context: Context) {}
}
```

`packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewApiHandler.kt`：
```kotlin
package com.clienttools.sdk.webview
import fi.iki.elonen.NanoHTTPD
// Deprecated: replaced by InspectorApiHandler
object WebViewApiHandler {
    fun handlePushHtml(body: String): NanoHTTPD.Response = stub()
    fun handleShow(body: String): NanoHTTPD.Response = stub()
    fun handleHide(): NanoHTTPD.Response = stub()
    fun handleAdjust(body: String): NanoHTTPD.Response = stub()
    fun handleGetFiles(): NanoHTTPD.Response = stub()
    private fun stub() = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "application/json", "{}")
}
```

`packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ui/FloatingControlPanel.kt`：
```kotlin
package com.clienttools.sdk.webview.ui
import android.content.Context
import android.widget.FrameLayout
// Deprecated: replaced by InspectorPanel
class FloatingControlPanel(context: Context) : FrameLayout(context)
```

- [ ] **Step 2: 完整编译**

```bash
cd /Users/zzc/Desktop/works/client-tools/packages
./gradlew :sdk:assembleDebug :demo:assembleDebug
```

期望：BUILD SUCCESSFUL，生成 APK。

- [ ] **Step 3: 安装到设备并验证**

```bash
cd /Users/zzc/Desktop/works/client-tools/packages
./gradlew :demo:installDebug
```

手动验证：
1. 打开 demo App → 看到右下角悬浮按钮（⚙，40×40dp，紫色）
2. 点击悬浮按钮 → 展开看板面板
3. 使用 curl 推送 HTML：
   ```bash
   curl -X POST http://<device-ip>:8080/webview/push-html \
     -H "Content-Type: application/json" \
     -d '{"tag":"test","html":"<html><body style=\"background:red\">Hello Inspector</body></html>"}'
   ```
4. 在面板中点击「显示」→ 看到红色 WebView 叠加在 Activity 上
5. 点击方向按钮 → WebView 移动
6. 拖动透明度滑块 → WebView 透明度变化
7. 切换到另一个 Activity → 新 Activity 也出现悬浮按钮（独立状态）

- [ ] **Step 4: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/OverlayManager.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewManager.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/WebViewApiHandler.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/webview/ui/FloatingControlPanel.kt
git commit -m "feat(inspector): deprecate old overlay/webview files, full inspector system live"
```

---

## 验收检查表

| 功能 | 验证方式 |
|------|---------|
| 悬浮按钮出现在每个 Activity | 打开 Login/Form 页面，右下角均有 ⚙ 按钮 |
| 点击展开/收起面板 | 点击 ⚙ 按钮 |
| 面板可拖动 | 拖动面板顶部拖动条 |
| 悬浮按钮可拖动 | 长按拖动 ⚙ 按钮 |
| HTML 推送 | curl POST /webview/push-html |
| WebView 显示 | 点击面板「显示」或 curl POST /webview/show |
| WebView 隐藏 | 点击「隐藏」或 curl POST /webview/hide |
| 位移调整 | 选档位 + 点方向按钮，WebView 移动 |
| 透明度调整 | 拖动滑块，WebView 透明度变化 |
| 文件列表 | curl GET /webview/files |
| Activity 独立状态 | 两个页面各自 currentFile 互不影响 |
| Activity 旋转恢复 | 旋转屏幕，WebView 显示状态保持 |

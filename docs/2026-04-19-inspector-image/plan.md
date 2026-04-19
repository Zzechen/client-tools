# Inspector 图片功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Inspector 系统中增加设计稿图片（png/jpg）叠加功能，与 WebView 功能并列，通过面板 Tab 切换，状态独立。

**Architecture:** 重构 `InspectorViewModel` 为聚合结构（`WebViewState` + `ImageState` + `activeTab`），新增 `ImageFileStore`、`ImageRenderer`、`FitWidthImageView`，扩展 `InspectorApiHandler` 和 `HttpServer`，更新面板 UI 加 Tab 行。

**Tech Stack:** Kotlin, Android SDK (API 26+), AndroidX ViewModel + StateFlow, NanoHTTPD, Android Instrumented Tests (JUnit4 + ApplicationProvider)

---

## 文件清单

| 操作 | 文件 |
|------|------|
| 修改 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorViewModel.kt` |
| 修改 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/WebViewRenderer.kt` |
| 修改 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPage.kt` |
| 修改 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPanel.kt` |
| 修改 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt` |
| 修改 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt` |
| 修改 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt` |
| 修改 | `packages/android/sdk/src/main/res/layout/inspector_overlay.xml` |
| 修改 | `packages/android/sdk/src/main/res/layout/inspector_panel.xml` |
| 新增 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/ImageInfo.kt` |
| 新增 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/ImageFileStore.kt` |
| 新增 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/ImageRenderer.kt` |
| 新增 | `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/FitWidthImageView.kt` |
| 新增 | `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/ImageFileStoreTest.kt` |
| 新增 | `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/ImageApiHandlerTest.kt` |

---

## Task 1: 重构 InspectorViewModel

将现有零散字段聚合为 `WebViewState` / `ImageState`，新增 `ImageInfo` 数据类和 `ActiveTab` 枚举。`WebViewRenderer` 同步适配。

**Files:**
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorViewModel.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/ImageInfo.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/WebViewRenderer.kt`

- [ ] **Step 1: 新增 ImageInfo.kt**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/ImageInfo.kt
package com.clienttools.sdk.inspector

data class ImageInfo(
    val tag: String,
    val timestamp: String,
    val filePath: String,   // 绝对路径，供 BitmapFactory 直接使用
    val ext: String         // "png" 或 "jpg"
)
```

- [ ] **Step 2: 重写 InspectorViewModel.kt**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorViewModel.kt
package com.clienttools.sdk.inspector

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow

data class FileInfo(
    val tag: String,
    val timestamp: String,
    val fileUrl: String
)

data class WebViewState(
    val currentFile: FileInfo? = null,
    val isVisible: Boolean = false,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val opacity: Float = 0.5f
)

data class ImageState(
    val currentImage: ImageInfo? = null,
    val isVisible: Boolean = false,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val opacity: Float = 0.5f
)

enum class ActiveTab { WEBVIEW, IMAGE }

class InspectorViewModel(app: Application) : AndroidViewModel(app) {
    val activeTab = MutableStateFlow(ActiveTab.WEBVIEW)
    val webView   = MutableStateFlow(WebViewState())
    val image     = MutableStateFlow(ImageState())
}
```

- [ ] **Step 3: 适配 WebViewRenderer.kt（订阅新结构）**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/WebViewRenderer.kt
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class WebViewRenderer(rootView: View, private val viewModel: InspectorViewModel) {

    private val webView: WebView = rootView.findViewById(R.id.overlay_webview)
    private var job: Job? = null

    init {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        @Suppress("DEPRECATION")
        webView.settings.allowFileAccessFromFileURLs = true
    }

    fun startObserving(scope: CoroutineScope) {
        job = scope.launch {
            launch {
                viewModel.webView.map { it.isVisible }.collect { visible ->
                    webView.visibility = if (visible) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.webView.map { it.currentFile }.filterNotNull().collect { file ->
                    webView.loadUrl(file.fileUrl)
                }
            }
            launch {
                viewModel.webView.map { it.opacity }.collect { alpha ->
                    webView.alpha = alpha
                }
            }
            launch {
                viewModel.webView.map { it.offsetX to it.offsetY }.collect { (x, y) ->
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

- [ ] **Step 4: 确认编译通过**

```bash
cd packages && ./gradlew :android:sdk:compileDebugKotlin 2>&1 | tail -20
```

期望：无错误。若有编译错误（如其他地方引用了旧的 `vm.currentFile`、`vm.offsetX` 等），逐一修复。

- [ ] **Step 5: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorViewModel.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/ImageInfo.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/WebViewRenderer.kt
git commit -m "refactor(inspector): restructure ViewModel into WebViewState/ImageState/activeTab"
```

---

## Task 2: InspectorPanel 适配新 ViewModel + 加 Tab 行

面板顶部加 WebView / 图片 Tab，调整和控制 section 的操作对象随 `activeTab` 动态切换。

**Files:**
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPanel.kt`
- Modify: `packages/android/sdk/src/main/res/layout/inspector_panel.xml`

- [ ] **Step 1: 在 inspector_panel.xml 的 drag_handle 后插入 Tab 行**

在 `drag_handle` LinearLayout 结束标签 `</LinearLayout>` 之后、第一个 `section_webview_title` TextView 之前插入：

```xml
<!-- Tab 行：WebView / 图片 -->
<LinearLayout
    android:id="@+id/tab_row"
    android:layout_width="match_parent"
    android:layout_height="36dp"
    android:orientation="horizontal"
    android:background="#0D0D1A">

    <Button
        android:id="@+id/tab_webview"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="#6200EE"
        android:text="WebView"
        android:textColor="#FFFFFF"
        android:textSize="11sp"
        android:stateListAnimator="@null" />

    <Button
        android:id="@+id/tab_image"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="#1E1E3A"
        android:text="图片"
        android:textColor="#BB86FC"
        android:textSize="11sp"
        android:stateListAnimator="@null" />

</LinearLayout>
```

同时在现有 `section_webview_content` LinearLayout 之后、`section_adjust_title` 之前加入图片文件 section：

```xml
<!-- 图片文件 section 标题（初始隐藏，activeTab=IMAGE 时显示） -->
<TextView
    android:id="@+id/section_image_file_title"
    android:layout_width="match_parent"
    android:layout_height="40dp"
    android:background="@drawable/inspector_section_bg"
    android:gravity="center_vertical"
    android:paddingStart="14dp"
    android:paddingEnd="14dp"
    android:text="▶  图片文件"
    android:textColor="#BB86FC"
    android:textSize="12sp"
    android:textStyle="bold"
    android:letterSpacing="0.05"
    android:visibility="gone" />

<LinearLayout
    android:id="@+id/section_image_file_content"
    android:visibility="gone"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="#0D0D1A"
    android:paddingStart="12dp"
    android:paddingEnd="12dp"
    android:paddingTop="8dp"
    android:paddingBottom="8dp">

    <TextView
        android:id="@+id/current_image_label"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="当前：无"
        android:textColor="#9E9E9E"
        android:textSize="11sp"
        android:paddingBottom="6dp" />

    <Button
        android:id="@+id/btn_select_image"
        android:layout_width="match_parent"
        android:layout_height="34dp"
        android:background="#6200EE"
        android:text="选择本地图片"
        android:textColor="#FFFFFF"
        android:textSize="12sp"
        android:stateListAnimator="@null" />

</LinearLayout>
```

- [ ] **Step 2: 重写 InspectorPanel.kt**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPanel.kt
package com.clienttools.sdk.inspector

import android.app.AlertDialog
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.clienttools.sdk.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class InspectorPanel(
    private val rootView: View,
    private val viewModel: InspectorViewModel,
    private val fileStore: InspectorFileStore? = null,
    private val imageFileStore: ImageFileStore? = null
) {
    private val floatBtn: TextView = rootView.findViewById(R.id.float_btn)
    private val panelContainer: View = rootView.findViewById(R.id.inspector_panel_container)
    private val dragHandle: View = rootView.findViewById(R.id.drag_handle)
    private val btnClosePanel: TextView = rootView.findViewById(R.id.btn_close_panel)

    // Tab
    private val tabWebview: Button = rootView.findViewById(R.id.tab_webview)
    private val tabImage: Button = rootView.findViewById(R.id.tab_image)

    // WebView section
    private val sectionWebviewTitle: TextView = rootView.findViewById(R.id.section_webview_title)
    private val sectionWebviewContent: View = rootView.findViewById(R.id.section_webview_content)
    private val currentFileLabel: TextView = rootView.findViewById(R.id.current_file_label)
    private val btnSelectFile: Button = rootView.findViewById(R.id.btn_select_file)
    private val fileListContainer: LinearLayout = rootView.findViewById(R.id.file_list_container)

    // Image file section
    private val sectionImageFileTitle: TextView = rootView.findViewById(R.id.section_image_file_title)
    private val sectionImageFileContent: View = rootView.findViewById(R.id.section_image_file_content)
    private val currentImageLabel: TextView = rootView.findViewById(R.id.current_image_label)
    private val btnSelectImage: Button = rootView.findViewById(R.id.btn_select_image)

    // Adjust section（共用）
    private val sectionAdjustTitle: TextView = rootView.findViewById(R.id.section_adjust_title)
    private val sectionAdjustContent: View = rootView.findViewById(R.id.section_adjust_content)
    private val sectionControlTitle: TextView = rootView.findViewById(R.id.section_control_title)
    private val sectionControlContent: View = rootView.findViewById(R.id.section_control_content)
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

    private var stepDp = 10
    private var job: Job? = null

    init {
        setupInteractions()
    }

    private fun setupInteractions() {
        setupDraggableClick(floatBtn) {
            panelContainer.visibility =
                if (panelContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        btnClosePanel.setOnClickListener { panelContainer.visibility = View.GONE }

        // Tab 切换
        tabWebview.setOnClickListener { viewModel.activeTab.value = ActiveTab.WEBVIEW }
        tabImage.setOnClickListener   { viewModel.activeTab.value = ActiveTab.IMAGE }

        // Section 折叠（WebView 文件 section 标题）
        sectionWebviewTitle.setOnClickListener { toggleSection(sectionWebviewTitle, sectionWebviewContent) }
        sectionImageFileTitle.setOnClickListener { toggleSection(sectionImageFileTitle, sectionImageFileContent) }
        sectionAdjustTitle.setOnClickListener { toggleSection(sectionAdjustTitle, sectionAdjustContent) }
        sectionControlTitle.setOnClickListener { toggleSection(sectionControlTitle, sectionControlContent) }

        selectStep(10)
        btnStep1.setOnClickListener  { selectStep(1) }
        btnStep10.setOnClickListener { selectStep(10) }
        btnStep50.setOnClickListener { selectStep(50) }

        // 方向按钮：操作当前 Tab 的 state
        btnUp.setOnClickListener    { applyOffset(0, -stepDp) }
        btnDown.setOnClickListener  { applyOffset(0, stepDp) }
        btnLeft.setOnClickListener  { applyOffset(-stepDp, 0) }
        btnRight.setOnClickListener { applyOffset(stepDp, 0) }

        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val alpha = progress / 100f
                when (viewModel.activeTab.value) {
                    ActiveTab.WEBVIEW -> viewModel.webView.value = viewModel.webView.value.copy(opacity = alpha)
                    ActiveTab.IMAGE   -> viewModel.image.value = viewModel.image.value.copy(opacity = alpha)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnSelectFile.setOnClickListener {
            val files = fileStore?.getAllFiles() ?: emptyList()
            showFileSelectDialog(files) { selected ->
                viewModel.webView.value = viewModel.webView.value.copy(currentFile = selected, isVisible = true)
            }
        }

        btnSelectImage.setOnClickListener {
            val images = imageFileStore?.getAllImages() ?: emptyList()
            showImageSelectDialog(images) { selected ->
                viewModel.image.value = viewModel.image.value.copy(currentImage = selected, isVisible = true)
            }
        }

        btnShow.setOnClickListener {
            when (viewModel.activeTab.value) {
                ActiveTab.WEBVIEW -> {
                    if (viewModel.webView.value.currentFile != null)
                        viewModel.webView.value = viewModel.webView.value.copy(isVisible = true)
                }
                ActiveTab.IMAGE -> {
                    if (viewModel.image.value.currentImage != null)
                        viewModel.image.value = viewModel.image.value.copy(isVisible = true)
                }
            }
        }
        btnHide.setOnClickListener {
            when (viewModel.activeTab.value) {
                ActiveTab.WEBVIEW -> viewModel.webView.value = viewModel.webView.value.copy(isVisible = false)
                ActiveTab.IMAGE   -> viewModel.image.value = viewModel.image.value.copy(isVisible = false)
            }
        }
    }

    private fun applyOffset(dx: Int, dy: Int) {
        when (viewModel.activeTab.value) {
            ActiveTab.WEBVIEW -> viewModel.webView.value = viewModel.webView.value.let {
                it.copy(offsetX = it.offsetX + dx, offsetY = it.offsetY + dy)
            }
            ActiveTab.IMAGE -> viewModel.image.value = viewModel.image.value.let {
                it.copy(offsetX = it.offsetX + dx, offsetY = it.offsetY + dy)
            }
        }
    }

    fun startObserving(scope: CoroutineScope) {
        job = scope.launch {
            // Tab 切换：更新 Tab 按钮颜色 + section 可见性
            launch {
                viewModel.activeTab.collect { tab ->
                    val wvActive = tab == ActiveTab.WEBVIEW
                    tabWebview.setBackgroundColor(if (wvActive) 0xFF6200EE.toInt() else 0xFF1E1E3A.toInt())
                    tabWebview.setTextColor(if (wvActive) 0xFFFFFFFF.toInt() else 0xFFBB86FC.toInt())
                    tabImage.setBackgroundColor(if (!wvActive) 0xFF6200EE.toInt() else 0xFF1E1E3A.toInt())
                    tabImage.setTextColor(if (!wvActive) 0xFFFFFFFF.toInt() else 0xFFBB86FC.toInt())

                    sectionWebviewTitle.visibility = if (wvActive) View.VISIBLE else View.GONE
                    sectionWebviewContent.visibility = View.GONE
                    sectionImageFileTitle.visibility = if (!wvActive) View.VISIBLE else View.GONE
                    sectionImageFileContent.visibility = View.GONE
                }
            }
            // WebView 文件标签
            launch {
                viewModel.webView.map { it.currentFile }.collect { file ->
                    currentFileLabel.text = if (file != null) "当前：${file.tag}  ${file.timestamp}" else "当前：无"
                }
            }
            // 图片文件标签
            launch {
                viewModel.image.map { it.currentImage }.collect { img ->
                    currentImageLabel.text = if (img != null) "当前：${img.tag}  ${img.timestamp}" else "当前：无"
                }
            }
            // 透明度（当前 Tab）
            launch {
                viewModel.activeTab.collect { /* 触发重绘 */ }
            }
            launch {
                viewModel.webView.map { it.opacity }.collect { opacity ->
                    if (viewModel.activeTab.value == ActiveTab.WEBVIEW) syncOpacityUI(opacity)
                }
            }
            launch {
                viewModel.image.map { it.opacity }.collect { opacity ->
                    if (viewModel.activeTab.value == ActiveTab.IMAGE) syncOpacityUI(opacity)
                }
            }
            // 偏移（当前 Tab）
            launch {
                viewModel.webView.map { it.offsetX to it.offsetY }.collect { (x, y) ->
                    if (viewModel.activeTab.value == ActiveTab.WEBVIEW) offsetLabel.text = "偏移：X: ${x}dp  Y: ${y}dp"
                }
            }
            launch {
                viewModel.image.map { it.offsetX to it.offsetY }.collect { (x, y) ->
                    if (viewModel.activeTab.value == ActiveTab.IMAGE) offsetLabel.text = "偏移：X: ${x}dp  Y: ${y}dp"
                }
            }
        }
    }

    private fun syncOpacityUI(opacity: Float) {
        val progress = (opacity * 100).toInt()
        opacityLabel.text = "透明度：$progress%"
        if (opacitySeekBar.progress != progress) opacitySeekBar.progress = progress
    }

    fun stopObserving() {
        job?.cancel()
        job = null
    }

    fun showFileSelectDialog(files: List<FileInfo>, onSelect: (FileInfo) -> Unit) {
        if (files.isEmpty()) {
            Toast.makeText(rootView.context, "暂无已保存的 HTML 文件", Toast.LENGTH_SHORT).show()
            return
        }
        val currentFile = viewModel.webView.value.currentFile
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

    private fun showImageSelectDialog(images: List<ImageInfo>, onSelect: (ImageInfo) -> Unit) {
        if (images.isEmpty()) {
            Toast.makeText(rootView.context, "暂无已保存的图片", Toast.LENGTH_SHORT).show()
            return
        }
        val currentImage = viewModel.image.value.currentImage
        val labels = images.map { img ->
            val cur = if (img.tag == currentImage?.tag && img.timestamp == currentImage.timestamp) " ★" else ""
            "${img.tag}  ${img.timestamp} (${img.ext})$cur"
        }.toTypedArray()
        AlertDialog.Builder(rootView.context)
            .setTitle("选择图片")
            .setItems(labels) { _, idx -> onSelect(images[idx]) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toggleSection(title: TextView, content: View) {
        val visible = content.visibility == View.VISIBLE
        content.visibility = if (visible) View.GONE else View.VISIBLE
        val arrow = if (visible) "▶" else "▼"
        title.text = title.text.toString().replaceFirst(Regex("^[▼▶]"), arrow)
    }

    private fun selectStep(dp: Int) {
        stepDp = dp
        val active = 0xFF6200EE.toInt()
        val inactive = 0xFF1E1E3A.toInt()
        btnStep1.setBackgroundColor(if (dp == 1) active else inactive)
        btnStep10.setBackgroundColor(if (dp == 10) active else inactive)
        btnStep50.setBackgroundColor(if (dp == 50) active else inactive)
    }

    private fun setupDraggableClick(v: View, onClick: () -> Unit) {
        var startX = 0f; var startY = 0f
        var viewStartX = 0f; var viewStartY = 0f
        var moved = false
        v.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    viewStartX = view.x; viewStartY = view.y
                    moved = false; true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX; val dy = event.rawY - startY
                    if (!moved && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) moved = true
                    if (moved) clampMove(view, viewStartX + dx, viewStartY + dy)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> { if (!moved) onClick(); true }
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

- [ ] **Step 3: 确认编译通过**

```bash
cd packages && ./gradlew :android:sdk:compileDebugKotlin 2>&1 | tail -20
```

期望：无错误。

- [ ] **Step 4: Commit**

```bash
git add packages/android/sdk/src/main/res/layout/inspector_panel.xml \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPanel.kt
git commit -m "feat(inspector): add WebView/Image tab row and adapt panel to new ViewModel"
```

---

## Task 3: ImageFileStore

存储图片文件（base64 解码后写入磁盘），对外提供 save / list / getPath / deleteAll 接口。

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/ImageFileStore.kt`
- Create: `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/ImageFileStoreTest.kt`

- [ ] **Step 1: 先写测试**

```kotlin
// packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/ImageFileStoreTest.kt
package com.clienttools.sdk.inspector

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageFileStoreTest {

    private lateinit var store: ImageFileStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        store = ImageFileStore(context)
        store.deleteAll()
    }

    @Test
    fun saveImage_returnsCorrectMetadata() {
        val bytes = ByteArray(100) { it.toByte() }
        val result = store.saveImage("login", "0419-1430", bytes, "png")
        assert(result != null)
        assert(result!!.tag == "login")
        assert(result.timestamp == "0419-1430")
        assert(result.ext == "png")
        assert(java.io.File(result.filePath).exists())
    }

    @Test
    fun getAllImages_returnsAllSaved() {
        val bytes = ByteArray(10)
        store.saveImage("login", "0419-1430", bytes, "png")
        store.saveImage("home",  "0419-1440", bytes, "jpg")
        val images = store.getAllImages()
        assert(images.size == 2)
    }

    @Test
    fun getFilePath_returnsPathForExisting() {
        val bytes = ByteArray(10)
        store.saveImage("login", "0419-1430", bytes, "png")
        val path = store.getFilePath("login", "0419-1430")
        assert(path != null)
        assert(java.io.File(path!!).exists())
    }

    @Test
    fun getFilePath_returnsNullForMissing() {
        val path = store.getFilePath("notexist", "0000-0000")
        assert(path == null)
    }

    @Test
    fun deleteAll_clearsAllFiles() {
        val bytes = ByteArray(10)
        store.saveImage("login", "0419-1430", bytes, "png")
        store.deleteAll()
        assert(store.getAllImages().isEmpty())
    }

    @Test
    fun generateTimestamp_hasCorrectFormat() {
        val ts = store.generateTimestamp()
        assert(ts.matches(Regex("""\d{4}-\d{4}""")))
    }
}
```

- [ ] **Step 2: 实现 ImageFileStore.kt**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/ImageFileStore.kt
package com.clienttools.sdk.inspector

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ImageFileStore(context: Context) {
    private val cacheDir = File(context.cacheDir, "inspector-images")
    private val TAG = "ImageFileStore"

    init {
        cacheDir.mkdirs()
    }

    fun saveImage(tag: String, timestamp: String, bytes: ByteArray, ext: String): ImageInfo? = try {
        val tagDir = File(cacheDir, tag).also { it.mkdirs() }
        val file = File(tagDir, "${tag}_${timestamp}.${ext}")
        file.writeBytes(bytes)
        ImageInfo(tag = tag, timestamp = timestamp, filePath = file.absolutePath, ext = ext)
    } catch (e: Exception) {
        Log.e(TAG, "Error saving image", e)
        null
    }

    fun getAllImages(): List<ImageInfo> = try {
        val result = mutableListOf<ImageInfo>()
        cacheDir.listFiles()?.forEach { tagDir ->
            if (!tagDir.isDirectory) return@forEach
            tagDir.listFiles()?.forEach { file ->
                val ext = file.extension.lowercase()
                if (ext != "png" && ext != "jpg" && ext != "jpeg") return@forEach
                val timestamp = parseTimestamp(file.name) ?: return@forEach
                result.add(ImageInfo(tagDir.name, timestamp, file.absolutePath, ext))
            }
        }
        result.sortedByDescending { it.timestamp }
    } catch (e: Exception) {
        Log.e(TAG, "Error listing images", e)
        emptyList()
    }

    fun getFilePath(tag: String, timestamp: String): String? {
        val tagDir = File(cacheDir, tag)
        return tagDir.listFiles()
            ?.firstOrNull { f ->
                val ext = f.extension.lowercase()
                (ext == "png" || ext == "jpg" || ext == "jpeg") && parseTimestamp(f.name) == timestamp
            }
            ?.absolutePath
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
        Regex("""_(\d{4}-\d{4})\.\w+$""").find(filename)?.groupValues?.get(1)
}
```

- [ ] **Step 3: 运行测试**

```bash
cd packages && ./gradlew :android:sdk:connectedDebugAndroidTest \
  --tests "com.clienttools.sdk.inspector.ImageFileStoreTest" 2>&1 | tail -30
```

期望：6 个测试全部 PASS。

- [ ] **Step 4: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/ImageFileStore.kt \
        packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/ImageFileStoreTest.kt
git commit -m "feat(inspector): add ImageFileStore with save/list/getPath/deleteAll"
```

---

## Task 4: FitWidthImageView + ImageRenderer

自定义 ImageView（宽铺满高比例缩放 + 防 OOM 采样），ImageRenderer 订阅 ViewModel 驱动渲染。

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/FitWidthImageView.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/ImageRenderer.kt`
- Modify: `packages/android/sdk/src/main/res/layout/inspector_overlay.xml`

- [ ] **Step 1: 实现 FitWidthImageView.kt**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/FitWidthImageView.kt
package com.clienttools.sdk.inspector

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class FitWidthImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private var bitmapWidth = 0
    private var bitmapHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (bitmapWidth > 0 && bitmapHeight > 0) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = (bitmapHeight.toLong() * width / bitmapWidth).toInt()
            setMeasuredDimension(width, height)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    fun setImageBitmapFitWidth(bitmap: Bitmap) {
        bitmapWidth = bitmap.width
        bitmapHeight = bitmap.height
        setImageBitmap(bitmap)
        requestLayout()
    }
}
```

- [ ] **Step 2: 在 inspector_overlay.xml 中加入 FitWidthImageView**

在 `overlay_webview` WebView 之后、`inspector_panel_container` 之前插入：

```xml
<!-- 层级 1b：设计稿图片，全屏叠加，默认隐藏 -->
<com.clienttools.sdk.inspector.FitWidthImageView
    android:id="@+id/overlay_imageview"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@android:color/transparent"
    android:visibility="gone" />
```

- [ ] **Step 3: 实现 ImageRenderer.kt**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/ImageRenderer.kt
package com.clienttools.sdk.inspector

import android.graphics.BitmapFactory
import android.util.TypedValue
import android.view.View
import com.clienttools.sdk.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageRenderer(rootView: View, private val viewModel: InspectorViewModel) {

    private val imageView: FitWidthImageView = rootView.findViewById(R.id.overlay_imageview)
    private var job: Job? = null

    fun startObserving(scope: CoroutineScope) {
        job = scope.launch {
            launch {
                viewModel.image.map { it.isVisible }.collect { visible ->
                    imageView.visibility = if (visible) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.image.map { it.currentImage }.collect { imgInfo ->
                    if (imgInfo != null) loadImage(imgInfo.filePath)
                }
            }
            launch {
                viewModel.image.map { it.opacity }.collect { alpha ->
                    imageView.alpha = alpha
                }
            }
            launch {
                viewModel.image.map { it.offsetX to it.offsetY }.collect { (x, y) ->
                    imageView.translationX = dpToPx(x)
                    imageView.translationY = dpToPx(y)
                }
            }
        }
    }

    private suspend fun loadImage(filePath: String) {
        val screenWidth = imageView.context.resources.displayMetrics.widthPixels
        val bitmap = withContext(Dispatchers.IO) {
            // 第一步：只读尺寸
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, opts)
            // 第二步：计算采样率
            val sampleSize = if (opts.outWidth > 0) {
                maxOf(1, Math.ceil(opts.outWidth.toDouble() / screenWidth).toInt())
            } else 1
            // 第三步：正式解码
            BitmapFactory.decodeFile(filePath, BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            })
        }
        if (bitmap != null) imageView.setImageBitmapFitWidth(bitmap)
    }

    fun stopObserving() {
        job?.cancel()
        job = null
    }

    private fun dpToPx(dp: Int): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        imageView.context.resources.displayMetrics
    )
}
```

- [ ] **Step 4: 确认编译通过**

```bash
cd packages && ./gradlew :android:sdk:compileDebugKotlin 2>&1 | tail -20
```

期望：无错误。

- [ ] **Step 5: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/FitWidthImageView.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/ImageRenderer.kt \
        packages/android/sdk/src/main/res/layout/inspector_overlay.xml
git commit -m "feat(inspector): add FitWidthImageView and ImageRenderer with OOM-safe sampling"
```

---

## Task 5: InspectorPage + ClientToolsSDK 接入 ImageFileStore

将 `ImageRenderer` 和 `ImageFileStore` 接入生命周期。

**Files:**
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPage.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt`

- [ ] **Step 1: 更新 InspectorPage.kt**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPage.kt
package com.clienttools.sdk.inspector

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.R

class InspectorPage(val activity: Activity) {

    val viewModel: InspectorViewModel =
        ViewModelProvider(activity as androidx.activity.ComponentActivity)[InspectorViewModel::class.java]

    private val rootView: View = LayoutInflater.from(activity)
        .inflate(R.layout.inspector_overlay, null)

    val panel: InspectorPanel = InspectorPanel(
        rootView, viewModel,
        fileStore = if (ClientToolsSDK.isInitialized) ClientToolsSDK.fileStore else null,
        imageFileStore = if (ClientToolsSDK.isInitialized) ClientToolsSDK.imageFileStore else null
    )
    val renderer: WebViewRenderer = WebViewRenderer(rootView, viewModel)
    val imageRenderer: ImageRenderer = ImageRenderer(rootView, viewModel)

    fun attach() {
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        content.addView(rootView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val scope = (activity as LifecycleOwner).lifecycleScope
        panel.startObserving(scope)
        renderer.startObserving(scope)
        imageRenderer.startObserving(scope)
    }

    fun detach() {
        panel.stopObserving()
        renderer.stopObserving()
        imageRenderer.stopObserving()
    }
}
```

- [ ] **Step 2: 更新 ClientToolsSDK.kt，新增 imageFileStore**

在 `internal lateinit var fileStore: InspectorFileStore` 这行之后加：

```kotlin
internal lateinit var imageFileStore: ImageFileStore
```

在 `init` 方法中 `fileStore = InspectorFileStore(context)` 这行之后加：

```kotlin
imageFileStore = ImageFileStore(context)
```

- [ ] **Step 3: 确认编译通过**

```bash
cd packages && ./gradlew :android:sdk:compileDebugKotlin 2>&1 | tail -20
```

期望：无错误。

- [ ] **Step 4: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorPage.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/ClientToolsSDK.kt
git commit -m "feat(inspector): wire ImageRenderer and ImageFileStore into InspectorPage lifecycle"
```

---

## Task 6: 扩展 InspectorApiHandler + HttpServer

新增 push-image、show-image、images 路由，更新 hide/adjust 支持 `type` 参数。

**Files:**
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`
- Create: `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/ImageApiHandlerTest.kt`

- [ ] **Step 1: 先写 ImageApiHandlerTest**

```kotlin
// packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/ImageApiHandlerTest.kt
package com.clienttools.sdk.inspector

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageApiHandlerTest {

    private lateinit var imageStore: ImageFileStore
    private lateinit var htmlStore: InspectorFileStore
    private lateinit var handler: InspectorApiHandler

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        imageStore = ImageFileStore(context)
        imageStore.deleteAll()
        htmlStore = InspectorFileStore(context)
        htmlStore.deleteAll()
        handler = InspectorApiHandler(htmlStore, imageStore, getTopViewModel = { null })
    }

    @Test
    fun pushImage_savesFileAndReturns200() {
        val fakeBytes = ByteArray(16) { it.toByte() }
        val base64 = Base64.encodeToString(fakeBytes, Base64.NO_WRAP)
        val body = """{"tag":"login","timestamp":"0419-1430","image":"$base64","ext":"png"}"""
        val response = handler.handlePushImage(body)
        assert(response.status.requestStatus == 200)
        val images = imageStore.getAllImages()
        assert(images.any { it.tag == "login" && it.timestamp == "0419-1430" })
    }

    @Test
    fun pushImage_missingTag_returns400() {
        val body = """{"image":"abc"}"""
        val response = handler.handlePushImage(body)
        assert(response.status.requestStatus == 400)
    }

    @Test
    fun pushImage_missingImage_returns400() {
        val body = """{"tag":"login"}"""
        val response = handler.handlePushImage(body)
        assert(response.status.requestStatus == 400)
    }

    @Test
    fun showImage_fileNotFound_returns404() {
        val body = """{"tag":"notexist","timestamp":"0000-0000"}"""
        val response = handler.handleShowImage(body)
        assert(response.status.requestStatus == 404)
    }

    @Test
    fun getImages_returnsAllImages() {
        val bytes = ByteArray(10)
        imageStore.saveImage("login", "0419-1430", bytes, "png")
        imageStore.saveImage("home",  "0419-1440", bytes, "jpg")
        val response = handler.handleGetImages(currentImage = null)
        assert(response.status.requestStatus == 200)
    }
}
```

- [ ] **Step 2: 重写 InspectorApiHandler.kt（含图片接口）**

```kotlin
// packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt
package com.clienttools.sdk.inspector

import android.util.Base64
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class InspectorApiHandler(
    private val fileStore: InspectorFileStore,
    private val imageFileStore: ImageFileStore,
    private val getTopViewModel: () -> InspectorViewModel?
) {
    private val TAG = "InspectorApiHandler"
    private val json = Json { ignoreUnknownKeys = true }

    // ── WebView HTML ──────────────────────────────────────────────────────────

    fun handlePushHtml(body: String): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val tag = obj["tag"]?.jsonPrimitive?.content ?: return error(400, "Missing tag")
        val html = obj["html"]?.jsonPrimitive?.content ?: return error(400, "Missing html")
        val timestamp = obj["timestamp"]?.jsonPrimitive?.content ?: fileStore.generateTimestamp()

        val saved = fileStore.saveHtmlFile(tag, timestamp, html)
            ?: return error(500, "Failed to save file")

        getTopViewModel()?.let { vm ->
            vm.webView.value = vm.webView.value.copy(
                currentFile = FileInfo(tag, timestamp, saved.fileUrl),
                isVisible = true
            )
        }

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
            vm.webView.value = vm.webView.value.copy(
                currentFile = FileInfo(tag, timestamp, fileUrl),
                isVisible = true
            )
            val s = vm.webView.value
            ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp","opacity":${s.opacity},"offsetX":${s.offsetX},"offsetY":${s.offsetY}}}""")
        } ?: ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp"}}""")
    } catch (e: Exception) {
        Log.e(TAG, "show error", e)
        error(500, "Internal error: ${e.message}")
    }

    fun handleGetFiles(currentFile: FileInfo?): NanoHTTPD.Response = try {
        val vmCurrentFile = getTopViewModel()?.webView?.value?.currentFile ?: currentFile
        val files = fileStore.getAllFiles()
        val filesJson = files.joinToString(",") { f ->
            val isCurrent = vmCurrentFile?.tag == f.tag && vmCurrentFile?.timestamp == f.timestamp
            val size = java.io.File(f.fileUrl.removePrefix("file://")).length()
            """{"tag":"${f.tag}","timestamp":"${f.timestamp}","size":$size,"isCurrent":$isCurrent}"""
        }
        ok("""{"code":0,"data":{"files":[$filesJson]}}""")
    } catch (e: Exception) {
        Log.e(TAG, "getFiles error", e)
        error(500, "Internal error: ${e.message}")
    }

    // ── Image ─────────────────────────────────────────────────────────────────

    fun handlePushImage(body: String): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val tag = obj["tag"]?.jsonPrimitive?.content ?: return error(400, "Missing tag")
        val imageBase64 = obj["image"]?.jsonPrimitive?.content ?: return error(400, "Missing image")
        val ext = obj["ext"]?.jsonPrimitive?.content?.lowercase() ?: "png"
        val timestamp = obj["timestamp"]?.jsonPrimitive?.content ?: imageFileStore.generateTimestamp()

        val bytes = try {
            Base64.decode(imageBase64, Base64.DEFAULT)
        } catch (e: Exception) {
            return error(400, "Invalid base64: ${e.message}")
        }

        val saved = imageFileStore.saveImage(tag, timestamp, bytes, ext)
            ?: return error(500, "Failed to save image")

        getTopViewModel()?.let { vm ->
            vm.image.value = vm.image.value.copy(currentImage = saved, isVisible = true)
        }

        ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp","filePath":"${saved.filePath}","fileSize":${bytes.size}}}""")
    } catch (e: Exception) {
        Log.e(TAG, "pushImage error", e)
        error(400, "Invalid request: ${e.message}")
    }

    fun handleShowImage(body: String): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val tag = obj["tag"]?.jsonPrimitive?.content ?: return error(400, "Missing tag")
        val timestamp = obj["timestamp"]?.jsonPrimitive?.content ?: return error(400, "Missing timestamp")

        val filePath = imageFileStore.getFilePath(tag, timestamp) ?: return error(404, "Image not found")
        val ext = java.io.File(filePath).extension.lowercase()

        getTopViewModel()?.let { vm ->
            vm.image.value = vm.image.value.copy(
                currentImage = ImageInfo(tag, timestamp, filePath, ext),
                isVisible = true
            )
            val s = vm.image.value
            ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp","opacity":${s.opacity},"offsetX":${s.offsetX},"offsetY":${s.offsetY}}}""")
        } ?: ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp"}}""")
    } catch (e: Exception) {
        Log.e(TAG, "showImage error", e)
        error(500, "Internal error: ${e.message}")
    }

    fun handleGetImages(currentImage: ImageInfo?): NanoHTTPD.Response = try {
        val vmCurrentImage = getTopViewModel()?.image?.value?.currentImage ?: currentImage
        val images = imageFileStore.getAllImages()
        val imagesJson = images.joinToString(",") { img ->
            val isCurrent = vmCurrentImage?.tag == img.tag && vmCurrentImage?.timestamp == img.timestamp
            val size = java.io.File(img.filePath).length()
            """{"tag":"${img.tag}","timestamp":"${img.timestamp}","ext":"${img.ext}","size":$size,"isCurrent":$isCurrent}"""
        }
        ok("""{"code":0,"data":{"images":[$imagesJson]}}""")
    } catch (e: Exception) {
        Log.e(TAG, "getImages error", e)
        error(500, "Internal error: ${e.message}")
    }

    // ── 共用：hide / adjust ────────────────────────────────────────────────────

    fun handleHide(body: String = "{}"): NanoHTTPD.Response = try {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val typeStr = obj?.get("type")?.jsonPrimitive?.content
        val vm = getTopViewModel()
        when {
            typeStr == "image" -> vm?.image?.value = vm?.image?.value?.copy(isVisible = false) ?: ImageState()
            typeStr == "webview" -> vm?.webView?.value = vm?.webView?.value?.copy(isVisible = false) ?: WebViewState()
            else -> {
                // 缺省：隐藏当前 activeTab
                when (vm?.activeTab?.value) {
                    ActiveTab.IMAGE   -> vm.image.value = vm.image.value.copy(isVisible = false)
                    else              -> vm?.webView?.value = vm?.webView?.value?.copy(isVisible = false) ?: WebViewState()
                }
            }
        }
        ok("""{"code":0,"message":"success"}""")
    } catch (e: Exception) {
        Log.e(TAG, "hide error", e)
        error(500, "Internal error: ${e.message}")
    }

    fun handleAdjust(body: String): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val typeStr = obj["type"]?.jsonPrimitive?.content
        val dx = obj["offsetX"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val dy = obj["offsetY"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val opacity = obj["opacity"]?.jsonPrimitive?.content?.toFloatOrNull()

        val vm = getTopViewModel()
        val isImage = typeStr == "image" || (typeStr == null && vm?.activeTab?.value == ActiveTab.IMAGE)

        if (isImage) {
            val s = vm?.image?.value ?: ImageState()
            val newState = s.copy(
                offsetX = s.offsetX + dx,
                offsetY = s.offsetY + dy,
                opacity = opacity?.coerceIn(0f, 1f) ?: s.opacity
            )
            vm?.image?.value = newState
            ok("""{"code":0,"data":{"offsetX":${newState.offsetX},"offsetY":${newState.offsetY},"opacity":${newState.opacity}}}""")
        } else {
            val s = vm?.webView?.value ?: WebViewState()
            val newState = s.copy(
                offsetX = s.offsetX + dx,
                offsetY = s.offsetY + dy,
                opacity = opacity?.coerceIn(0f, 1f) ?: s.opacity
            )
            vm?.webView?.value = newState
            ok("""{"code":0,"data":{"offsetX":${newState.offsetX},"offsetY":${newState.offsetY},"opacity":${newState.opacity}}}""")
        }
    } catch (e: Exception) {
        Log.e(TAG, "adjust error", e)
        error(500, "Internal error: ${e.message}")
    }

    // ── 内部工具 ───────────────────────────────────────────────────────────────

    private fun ok(json: String) = NanoHTTPD.newFixedLengthResponse(
        NanoHTTPD.Response.Status.OK, "application/json", json
    )

    private fun error(code: Int, message: String): NanoHTTPD.Response {
        val status = when {
            code == 404  -> NanoHTTPD.Response.Status.NOT_FOUND
            code >= 500  -> NanoHTTPD.Response.Status.INTERNAL_ERROR
            else         -> NanoHTTPD.Response.Status.BAD_REQUEST
        }
        return NanoHTTPD.newFixedLengthResponse(status, "application/json",
            """{"code":$code,"message":"$message"}""")
    }
}
```

- [ ] **Step 3: 更新 HttpServer.kt，注册图片路由**

在 `"/webview/files"` 路由分支之后、`else ->` 之前插入以下路由：

```kotlin
method == Method.POST && uri == "/inspector/push-image" -> {
    inspectorHandler().handlePushImage(readBody(session))
}
method == Method.POST && uri == "/inspector/show-image" -> {
    inspectorHandler().handleShowImage(readBody(session))
}
method == Method.GET && uri == "/inspector/images" -> {
    inspectorHandler().handleGetImages(
        currentImage = ClientToolsSDK.getTop()?.viewModel?.image?.value?.currentImage
    )
}
method == Method.POST && uri == "/inspector/hide" -> {
    inspectorHandler().handleHide(readBody(session))
}
method == Method.POST && uri == "/inspector/adjust" -> {
    inspectorHandler().handleAdjust(readBody(session))
}
```

同时更新 `inspectorHandler()` 私有方法，加入 `imageFileStore` 参数：

```kotlin
private fun inspectorHandler() = InspectorApiHandler(
    fileStore = ClientToolsSDK.fileStore,
    imageFileStore = ClientToolsSDK.imageFileStore,
    getTopViewModel = { ClientToolsSDK.getTop()?.viewModel }
)
```

- [ ] **Step 4: 运行图片 API 测试**

```bash
cd packages && ./gradlew :android:sdk:connectedDebugAndroidTest \
  --tests "com.clienttools.sdk.inspector.ImageApiHandlerTest" 2>&1 | tail -30
```

期望：5 个测试全部 PASS。

- [ ] **Step 5: 运行全量 Inspector 测试确认无回归**

```bash
cd packages && ./gradlew :android:sdk:connectedDebugAndroidTest \
  --tests "com.clienttools.sdk.inspector.*" 2>&1 | tail -30
```

期望：所有测试 PASS。

- [ ] **Step 6: Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/inspector/InspectorApiHandler.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt \
        packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/ImageApiHandlerTest.kt
git commit -m "feat(inspector): add push-image/show-image/images HTTP routes and update hide/adjust with type param"
```

---

## Task 7: 更新已有测试（适配新 API 签名）

`InspectorApiHandlerTest` 中的 `InspectorApiHandler` 构造函数签名已变，需传入 `imageFileStore`。

**Files:**
- Modify: `packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorApiHandlerTest.kt`

- [ ] **Step 1: 更新 setUp 中的 handler 构造**

将：
```kotlin
handler = InspectorApiHandler(store, getTopViewModel = { null })
```
改为：
```kotlin
val context: Context = ApplicationProvider.getApplicationContext()
val imageStore = ImageFileStore(context)
handler = InspectorApiHandler(store, imageStore, getTopViewModel = { null })
```

同时将 `handleHide()` 无参调用改为 `handleHide("{}")`:
```kotlin
val response = handler.handleHide("{}")
```

- [ ] **Step 2: 运行全量测试**

```bash
cd packages && ./gradlew :android:sdk:connectedDebugAndroidTest \
  --tests "com.clienttools.sdk.inspector.*" 2>&1 | tail -30
```

期望：全部 PASS。

- [ ] **Step 3: Commit**

```bash
git add packages/android/sdk/src/androidTest/kotlin/com/clienttools/sdk/inspector/InspectorApiHandlerTest.kt
git commit -m "test(inspector): update InspectorApiHandlerTest for new handler constructor signature"
```

---

## Self-Review

**Spec coverage check:**

| Spec 要求 | 已覆盖任务 |
|-----------|-----------|
| ViewModel 重构为 WebViewState/ImageState/activeTab | Task 1 |
| WebViewRenderer 适配新结构 | Task 1 |
| 面板 Tab 行（WebView/图片切换） | Task 2 |
| Section 联动（文件/图片分别显示） | Task 2 |
| 调整/控制 section 随 activeTab 切换 | Task 2 |
| ImageFileStore（save/list/getPath/deleteAll） | Task 3 |
| FitWidthImageView（宽铺满高比例） | Task 4 |
| 防 OOM 采样加载 | Task 4 |
| ImageRenderer 订阅 ViewModel | Task 4 |
| inspector_overlay.xml 加 ImageView | Task 4 |
| InspectorPage 接入 ImageRenderer | Task 5 |
| ClientToolsSDK 接入 imageFileStore | Task 5 |
| POST /inspector/push-image（base64，自动选中） | Task 6 |
| POST /inspector/show-image | Task 6 |
| POST /inspector/hide（type 参数） | Task 6 |
| POST /inspector/adjust（type 参数） | Task 6 |
| GET /inspector/images | Task 6 |
| 旧测试适配新签名 | Task 7 |

**Placeholder scan:** 无 TBD / TODO / 模糊描述。

**Type consistency:** `ImageInfo`、`WebViewState`、`ImageState`、`ActiveTab` 在各 Task 中名称一致；`ImageFileStore.saveImage` 返回 `ImageInfo?`，在 Task 6 中直接使用；`handleHide` 签名统一为 `handleHide(body: String)`。

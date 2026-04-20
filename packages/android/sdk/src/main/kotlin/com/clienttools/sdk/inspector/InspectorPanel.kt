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
    private val tabStatus: Button = rootView.findViewById(R.id.tab_status)

    // WebView section
    private val sectionWebviewTitle: TextView = rootView.findViewById(R.id.section_webview_title)
    private val sectionWebviewContent: View = rootView.findViewById(R.id.section_webview_content)
    private val currentFileLabel: TextView = rootView.findViewById(R.id.current_file_label)
    private val btnSelectFile: Button = rootView.findViewById(R.id.btn_select_file)
    private val fileListContainer: LinearLayout = rootView.findViewById(R.id.file_list_container)

    // Status section
    private val sectionStatusContent: View = rootView.findViewById(R.id.section_status_content)
    private val statusServerLabel: TextView = rootView.findViewById(R.id.status_server_label)
    private val statusActivityLabel: TextView = rootView.findViewById(R.id.status_activity_label)

    // Image file section
    private val sectionImageFileTitle: TextView = rootView.findViewById(R.id.section_image_file_title)
    private val sectionImageFileContent: View = rootView.findViewById(R.id.section_image_file_content)
    private val currentImageLabel: TextView = rootView.findViewById(R.id.current_image_label)
    private val btnSelectImage: Button = rootView.findViewById(R.id.btn_select_image)

    // 共用 section
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

        tabWebview.setOnClickListener { viewModel.activeTab.value = ActiveTab.WEBVIEW }
        tabImage.setOnClickListener   { viewModel.activeTab.value = ActiveTab.IMAGE }
        tabStatus.setOnClickListener  { viewModel.activeTab.value = ActiveTab.STATUS }

        sectionWebviewTitle.setOnClickListener { toggleSection(sectionWebviewTitle, sectionWebviewContent) }
        sectionImageFileTitle.setOnClickListener { toggleSection(sectionImageFileTitle, sectionImageFileContent) }
        sectionAdjustTitle.setOnClickListener { toggleSection(sectionAdjustTitle, sectionAdjustContent) }
        sectionControlTitle.setOnClickListener { toggleSection(sectionControlTitle, sectionControlContent) }

        selectStep(10)
        btnStep1.setOnClickListener  { selectStep(1) }
        btnStep10.setOnClickListener { selectStep(10) }
        btnStep50.setOnClickListener { selectStep(50) }

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
                    ActiveTab.STATUS  -> Unit
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
                ActiveTab.STATUS -> Unit
            }
        }
        btnHide.setOnClickListener {
            when (viewModel.activeTab.value) {
                ActiveTab.WEBVIEW -> viewModel.webView.value = viewModel.webView.value.copy(isVisible = false)
                ActiveTab.IMAGE   -> viewModel.image.value = viewModel.image.value.copy(isVisible = false)
                ActiveTab.STATUS  -> Unit
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
            ActiveTab.STATUS -> Unit
        }
    }

    fun startObserving(scope: CoroutineScope) {
        job = scope.launch {
            launch {
                viewModel.activeTab.collect { tab ->
                    val wvActive = tab == ActiveTab.WEBVIEW
                    val imgActive = tab == ActiveTab.IMAGE
                    val statusActive = tab == ActiveTab.STATUS

                    fun tabBg(active: Boolean) = if (active) 0xFF6200EE.toInt() else 0xFF1E1E3A.toInt()
                    fun tabFg(active: Boolean) = if (active) 0xFFFFFFFF.toInt() else 0xFFBB86FC.toInt()
                    tabWebview.setBackgroundColor(tabBg(wvActive))
                    tabWebview.setTextColor(tabFg(wvActive))
                    tabImage.setBackgroundColor(tabBg(imgActive))
                    tabImage.setTextColor(tabFg(imgActive))
                    tabStatus.setBackgroundColor(tabBg(statusActive))
                    tabStatus.setTextColor(tabFg(statusActive))

                    sectionWebviewTitle.visibility = if (wvActive) View.VISIBLE else View.GONE
                    sectionWebviewContent.visibility = View.GONE
                    sectionImageFileTitle.visibility = if (imgActive) View.VISIBLE else View.GONE
                    sectionImageFileContent.visibility = View.GONE
                    sectionStatusContent.visibility = if (statusActive) View.VISIBLE else View.GONE

                    if (statusActive) {
                        val activityName = (rootView.context as? android.app.Activity)?.localClassName ?: "—"
                        statusActivityLabel.text = "Activity: $activityName"
                    }
                }
            }
            launch {
                viewModel.webView.map { it.currentFile }.collect { file ->
                    currentFileLabel.text = if (file != null) "当前：${file.tag}  ${file.timestamp}" else "当前：无"
                }
            }
            launch {
                viewModel.image.map { it.currentImage }.collect { img ->
                    currentImageLabel.text = if (img != null) "当前：${img.tag}  ${img.timestamp}" else "当前：无"
                }
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
            launch {
                viewModel.webView.map { it.offsetX to it.offsetY }.collect { (x, y) ->
                    if (viewModel.activeTab.value == ActiveTab.WEBVIEW)
                        offsetLabel.text = "偏移：X: ${x}dp  Y: ${y}dp"
                }
            }
            launch {
                viewModel.image.map { it.offsetX to it.offsetY }.collect { (x, y) ->
                    if (viewModel.activeTab.value == ActiveTab.IMAGE)
                        offsetLabel.text = "偏移：X: ${x}dp  Y: ${y}dp"
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

    private fun clampMove(v: View, x: Float, y: Float) {
        val parent = v.parent as? ViewGroup ?: return
        v.x = x.coerceIn(0f, (parent.width - v.width).toFloat().coerceAtLeast(0f))
        v.y = y.coerceIn(0f, (parent.height - v.height).toFloat().coerceAtLeast(0f))
    }
}

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
        setupDraggableClick(floatBtn) {
            panelContainer.visibility =
                if (panelContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        setupDraggableMove(dragHandle, panelContainer)

        sectionWebviewTitle.setOnClickListener {
            sectionWebviewContent.visibility =
                if (sectionWebviewContent.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        sectionAdjustTitle.setOnClickListener {
            sectionAdjustContent.visibility =
                if (sectionAdjustContent.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        selectStep(10)
        btnStep1.setOnClickListener { selectStep(1) }
        btnStep10.setOnClickListener { selectStep(10) }
        btnStep50.setOnClickListener { selectStep(50) }

        btnUp.setOnClickListener    { viewModel.offsetY.value -= stepDp }
        btnDown.setOnClickListener  { viewModel.offsetY.value += stepDp }
        btnLeft.setOnClickListener  { viewModel.offsetX.value -= stepDp }
        btnRight.setOnClickListener { viewModel.offsetX.value += stepDp }

        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.opacity.value = progress / 100f
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnShow.setOnClickListener {
            viewModel.currentFile.value?.let {
                viewModel.isVisible.value = true
            }
        }
        btnHide.setOnClickListener { viewModel.isVisible.value = false }

        btnClosePanel.setOnClickListener { panelContainer.visibility = View.GONE }
    }

    fun startObserving(scope: CoroutineScope) {
        job = scope.launch {
            launch {
                viewModel.currentFile.collect { file ->
                    currentFileLabel.text = if (file != null) "当前：${file.tag} (${file.timestamp})" else "当前：无"
                }
            }
            launch {
                viewModel.opacity.collect { opacity ->
                    val progress = (opacity * 100).toInt()
                    opacityLabel.text = "透明度：$progress%"
                    if (opacitySeekBar.progress != progress) {
                        opacitySeekBar.progress = progress
                    }
                }
            }
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

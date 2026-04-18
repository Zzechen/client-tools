package com.clienttools.sdk.webview.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*

class FloatingControlPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // Floating button
    val floatingButton = Button(context).apply {
        text = "⚙"
        layoutParams = FrameLayout.LayoutParams(48, 48).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 10, 10)
        }
        setBackgroundColor(0xFF6200EE.toInt())
        setTextColor(0xFFFFFFFF.toInt())
    }

    // Expandable panel
    val expandedPanel = FrameLayout(context).apply {
        layoutParams = FrameLayout.LayoutParams(280, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 10, 70)
        }
        setBackgroundColor(0xFF1F1F1F.toInt())
        visibility = GONE
    }

    val dragHandle = View(context).apply {
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 40)
        setBackgroundColor(0xFF333333.toInt())
    }

    val contentLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 40
        }
        setPadding(16, 16, 16, 16)
    }

    // Control buttons
    val showButton = Button(context).apply {
        text = "Show WebView"
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48).apply {
            setMargins(0, 0, 0, 8)
        }
    }

    val hideButton = Button(context).apply {
        text = "Hide"
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48).apply {
            setMargins(0, 0, 0, 8)
        }
    }

    val closeButton = Button(context).apply {
        text = "Close Panel"
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48)
    }

    val statusText = TextView(context).apply {
        text = "Status: Ready"
        textSize = 12f
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 16, 0, 0)
        }
        setTextColor(0xFFB0B0B0.toInt())
    }

    var isExpanded = false
    private var lastX = 0f
    private var lastY = 0f

    init {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        expandedPanel.addView(dragHandle)
        contentLayout.addView(showButton)
        contentLayout.addView(hideButton)
        contentLayout.addView(statusText)
        contentLayout.addView(closeButton)

        val scrollView = ScrollView(context).apply {
            addView(contentLayout)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 40
            }
        }
        expandedPanel.addView(scrollView)

        addView(floatingButton)
        addView(expandedPanel)

        // Setup listeners
        floatingButton.setOnClickListener { togglePanel() }
        closeButton.setOnClickListener { hidePanel() }

        dragHandle.setOnTouchListener { _, event ->
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

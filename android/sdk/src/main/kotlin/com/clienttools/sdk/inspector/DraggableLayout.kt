package com.clienttools.sdk.inspector

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout
import kotlin.math.abs

class DraggableLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var startX = 0f
    private var startY = 0f
    private var parentStartX = 0f
    private var parentStartY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX; startY = ev.rawY
                parentStartX = x; parentStartY = y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = abs(ev.rawX - startX)
                    val dy = abs(ev.rawY - startY)
                    if (dx > 10 || dy > 10) dragging = true
                }
                if (dragging) return true  // 拦截，交给 onTouchEvent 处理移动
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                // 确保后续事件能传进来
                startX = ev.rawX; startY = ev.rawY
                parentStartX = x; parentStartY = y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = abs(ev.rawX - startX)
                    val dy = abs(ev.rawY - startY)
                    if (dx > 10 || dy > 10) dragging = true
                }
                if (dragging) {
                    val parent = parent as? android.view.ViewGroup ?: return true
                    val newX = parentStartX + ev.rawX - startX
                    val newY = parentStartY + ev.rawY - startY
                    x = newX.coerceIn(0f, (parent.width - width).toFloat().coerceAtLeast(0f))
                    y = newY.coerceIn(0f, (parent.height - height).toFloat().coerceAtLeast(0f))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
}

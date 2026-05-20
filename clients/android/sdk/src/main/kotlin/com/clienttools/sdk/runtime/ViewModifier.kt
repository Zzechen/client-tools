package com.clienttools.sdk.runtime

import android.os.Looper
import android.widget.TextView
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.proto.ModifyViewRequest
import java.util.concurrent.CountDownLatch

object ViewModifier {

    fun click(viewId: String, centerOffsetXDp: Float? = null, centerOffsetYDp: Float? = null): Boolean {
        val views = ViewTreeTraversal.findViewById(viewId)
        if (views.isEmpty()) return false
        views.forEach { view ->
            val activity = ClientToolsSDK.getCurrentActivity() ?: return@forEach
            val density = view.resources.displayMetrics.density
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val cx = loc[0] + view.width / 2f + (centerOffsetXDp ?: 0f) * density
            val cy = loc[1] + view.height / 2f + (centerOffsetYDp ?: 0f) * density
            val decorView = activity.window.decorView
            if (Looper.myLooper() == Looper.getMainLooper()) {
                injectTap(decorView, cx, cy)
            } else {
                activity.runOnUiThread { injectTap(decorView, cx, cy) }
            }
        }
        return true
    }

    private fun injectTap(decorView: android.view.View, x: Float, y: Float) {
        val downTime = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(downTime, downTime,
            android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        val up = android.view.MotionEvent.obtain(downTime, downTime + 50,
            android.view.MotionEvent.ACTION_UP, x, y, 0)
        decorView.dispatchTouchEvent(down)
        decorView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    fun scroll(viewId: String, dxDp: Float, dyDp: Float): Boolean {
        val views = ViewTreeTraversal.findViewById(viewId)
        return if (views.isEmpty()) false else {
            views.forEach { view ->
                val density = view.context.resources.displayMetrics.density
                val dxPx = (dxDp * density).toInt()
                val dyPx = (dyDp * density).toInt()
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    view.scrollBy(dxPx, dyPx)
                } else {
                    val activity = ClientToolsSDK.getCurrentActivity()
                    activity?.runOnUiThread { view.scrollBy(dxPx, dyPx) }
                }
            }
            true
        }
    }

    fun modify(viewId: String, req: ModifyViewRequest): Pair<Boolean, String> {
        val activity = ClientToolsSDK.getCurrentActivity()
            ?: return Pair(false, "No activity available")

        var result: Pair<Boolean, String> = Pair(false, "unknown error")
        val latch = CountDownLatch(1)
        activity.runOnUiThread {
            result = applyModify(viewId, req)
            latch.countDown()
        }
        latch.await()
        return result
    }

    private fun applyModify(viewId: String, req: ModifyViewRequest): Pair<Boolean, String> {
        val views = ViewTreeTraversal.findViewById(viewId)
        if (views.isEmpty()) return Pair(false, "View not found: $viewId")
        val view = views[0]

        if (req.hasText() && view !is TextView) {
            return Pair(false, "text requires TextView, but '$viewId' is ${view.javaClass.simpleName}")
        }

        val density = view.resources.displayMetrics.density
        return try {
            if (req.hasMove() || req.hasSize()) {
                view.pivotX = 0f
                view.pivotY = 0f
            }
            if (req.hasMove()) {
                val m = req.move
                if (m.hasDx()) view.translationX += m.dx.value * density
                if (m.hasDy()) view.translationY += m.dy.value * density
            }
            if (req.hasSize()) {
                val s = req.size
                val originalW = view.width.toFloat()
                val originalH = view.height.toFloat()
                if (s.hasWidth() && originalW > 0f) view.scaleX = s.width.value * density / originalW
                if (s.hasHeight() && originalH > 0f) view.scaleY = s.height.value * density / originalH
            }
            if (req.hasText() && view is TextView) {
                view.text = req.text.content
            }
            Pair(true, "")
        } catch (e: Exception) {
            Pair(false, e.message ?: "error")
        }
    }
}

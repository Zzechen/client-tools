package com.clienttools.sdk.runtime

import android.os.Looper
import com.clienttools.sdk.ClientToolsSDK

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
}

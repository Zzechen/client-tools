package com.clienttools.sdk.runtime

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.EditText
import android.widget.TextView
import com.clienttools.sdk.ClientToolsSDK
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object InteractionHandler {

    // ── input_text ──────────────────────────────────────────────────

    /**
     * 向指定 View 输入文本。
     * @return true=成功，false=View 不存在或不支持输入
     */
    fun inputText(viewId: String, text: String, append: Boolean): Boolean {
        val views = ViewTreeTraversal.findViewById(viewId)
        if (views.isEmpty()) return false
        val view = views[0]
        if (view !is TextView) return false

        val latch = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            view.requestFocus()
            if (!append) view.text = null
            (view as? EditText)?.append(text) ?: view.append(text)
            latch.countDown()
        }
        latch.await(3, TimeUnit.SECONDS)
        return true
    }

    // ── gesture ─────────────────────────────────────────────────────

    /**
     * 对指定 View 执行手势。
     * type: "long_press" | "double_tap" | "swipe"
     */
    fun gesture(
        viewId: String,
        type: String,
        durationMs: Int,
        direction: String,
        distanceDp: Float,
        swipeDurationMs: Int
    ): Boolean {
        val views = ViewTreeTraversal.findViewById(viewId)
        if (views.isEmpty()) return false
        val view = views[0]
        val activity = ClientToolsSDK.getCurrentActivity() ?: return false
        val density = view.resources.displayMetrics.density

        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val cx = loc[0] + view.width / 2f
        val cy = loc[1] + view.height / 2f
        val decorView = activity.window.decorView

        val latch = CountDownLatch(1)
        activity.runOnUiThread {
            when (type) {
                "long_press" -> injectLongPress(decorView, cx, cy, durationMs.toLong())
                "double_tap" -> injectDoubleTap(decorView, cx, cy)
                "swipe" -> {
                    val distancePx = distanceDp * density
                    val (ex, ey) = when (direction) {
                        "up"    -> Pair(cx, cy - distancePx)
                        "down"  -> Pair(cx, cy + distancePx)
                        "left"  -> Pair(cx - distancePx, cy)
                        "right" -> Pair(cx + distancePx, cy)
                        else    -> Pair(cx, cy - distancePx)
                    }
                    injectSwipe(decorView, cx, cy, ex, ey, swipeDurationMs.toLong())
                }
            }
            latch.countDown()
        }
        latch.await(10, TimeUnit.SECONDS)
        return true
    }

    private fun injectLongPress(decorView: android.view.View, x: Float, y: Float, durationMs: Long) {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        decorView.dispatchTouchEvent(down)
        down.recycle()
        Thread.sleep(durationMs)
        val up = MotionEvent.obtain(t, t + durationMs, MotionEvent.ACTION_UP, x, y, 0)
        decorView.dispatchTouchEvent(up)
        up.recycle()
    }

    private fun injectDoubleTap(decorView: android.view.View, x: Float, y: Float) {
        injectTap(decorView, x, y)
        Thread.sleep(100)
        injectTap(decorView, x, y)
    }

    private fun injectTap(decorView: android.view.View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0)
        decorView.dispatchTouchEvent(down)
        decorView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun injectSwipe(
        decorView: android.view.View,
        sx: Float, sy: Float,
        ex: Float, ey: Float,
        durationMs: Long
    ) {
        val t = SystemClock.uptimeMillis()
        val steps = 20
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, sx, sy, 0)
        decorView.dispatchTouchEvent(down)
        down.recycle()
        for (i in 1..steps) {
            val fraction = i.toFloat() / steps
            val mx = sx + (ex - sx) * fraction
            val my = sy + (ey - sy) * fraction
            val moveTime = t + (durationMs * fraction).toLong()
            val move = MotionEvent.obtain(t, moveTime, MotionEvent.ACTION_MOVE, mx, my, 0)
            decorView.dispatchTouchEvent(move)
            move.recycle()
            Thread.sleep(durationMs / steps)
        }
        val up = MotionEvent.obtain(t, t + durationMs, MotionEvent.ACTION_UP, ex, ey, 0)
        decorView.dispatchTouchEvent(up)
        up.recycle()
    }

    // ── wait_for ─────────────────────────────────────────────────────

    /**
     * 在主线程轮询等待 View 满足 condition。
     * condition: "visible" | "gone" | "exists" | "not_exists"
     * @return Pair(met, elapsedMs)
     */
    fun waitFor(viewId: String, condition: String, timeoutMs: Int, intervalMs: Int): Pair<Boolean, Int> {
        val latch = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())
        var met = false
        val startMs = System.currentTimeMillis()

        fun check() {
            val elapsed = (System.currentTimeMillis() - startMs).toInt()
            val views = ViewTreeTraversal.findViewById(viewId)
            val conditionMet = when (condition) {
                "exists"     -> views.isNotEmpty()
                "not_exists" -> views.isEmpty()
                "visible"    -> views.isNotEmpty() && views[0].visibility == android.view.View.VISIBLE
                "gone"       -> views.isEmpty() || views[0].visibility != android.view.View.VISIBLE
                else         -> false
            }
            if (conditionMet) {
                met = true
                latch.countDown()
                return
            }
            if (elapsed >= timeoutMs) {
                latch.countDown()
                return
            }
            handler.postDelayed(::check, intervalMs.toLong())
        }

        handler.post(::check)
        latch.await((timeoutMs + 1000).toLong(), TimeUnit.MILLISECONDS)
        val elapsed = (System.currentTimeMillis() - startMs).toInt()
        return Pair(met, elapsed)
    }
}

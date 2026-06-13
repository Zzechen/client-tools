package com.clienttools.sdk.runtime

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.clienttools.sdk.ClientToolsSDK
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object InteractionHandler {

    // ── input_text ──────────────────────────────────────────────────

    fun inputText(viewId: String, text: String, append: Boolean): Boolean {
        val views = ViewTreeTraversal.findViewById(viewId)
        if (views.isEmpty()) return false
        val view = views[0]
        if (view !is TextView) return false

        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            view.requestFocus()
            if (!append) view.text = null
            (view as? EditText)?.append(text) ?: view.append(text)
            latch.countDown()
        }
        latch.await(3, TimeUnit.SECONDS)
        return true
    }

    // ── gesture ─────────────────────────────────────────────────────

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
        val mainHandler = Handler(Looper.getMainLooper())

        when (type) {
            "long_press" -> mainHandler.post {
                injectLongPressAsync(decorView, cx, cy, durationMs.toLong().coerceAtLeast(500L)) {
                    latch.countDown()
                }
            }
            "double_tap" -> mainHandler.post {
                injectDoubleTapAsync(decorView, cx, cy) { latch.countDown() }
            }
            "swipe" -> {
                val distancePx = distanceDp * density
                val (ex, ey) = when (direction) {
                    "up"    -> Pair(cx, cy - distancePx)
                    "down"  -> Pair(cx, cy + distancePx)
                    "left"  -> Pair(cx - distancePx, cy)
                    "right" -> Pair(cx + distancePx, cy)
                    else    -> Pair(cx, cy - distancePx)
                }
                mainHandler.post {
                    injectSwipeAsync(decorView, cx, cy, ex, ey, swipeDurationMs.toLong().coerceAtLeast(300L)) {
                        latch.countDown()
                    }
                }
            }
            else -> return false
        }

        latch.await(15, TimeUnit.SECONDS)
        return true
    }

    /**
     * 异步长按：ACTION_DOWN 后让主线程空转（使 Android 长按定时器能触发），
     * durationMs 后再发 ACTION_UP。
     */
    private fun injectLongPressAsync(decorView: View, x: Float, y: Float, durationMs: Long, onDone: () -> Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        decorView.dispatchTouchEvent(down)
        down.recycle()

        mainHandler.postDelayed({
            val upTime = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)
            decorView.dispatchTouchEvent(up)
            up.recycle()
            onDone()
        }, durationMs)
    }

    /**
     * 异步双击：两次点击之间通过 postDelayed 间隔 100ms，不阻塞主线程。
     */
    private fun injectDoubleTapAsync(decorView: View, x: Float, y: Float, onDone: () -> Unit) {
        injectTap(decorView, x, y)
        Handler(Looper.getMainLooper()).postDelayed({
            injectTap(decorView, x, y)
            onDone()
        }, 100)
    }

    private fun injectTap(decorView: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0)
        decorView.dispatchTouchEvent(down)
        decorView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    /**
     * 异步滑动：将每个 MOVE 事件通过 postDelayed 均匀分布，不阻塞主线程。
     */
    private fun injectSwipeAsync(
        decorView: View,
        sx: Float, sy: Float,
        ex: Float, ey: Float,
        durationMs: Long,
        onDone: () -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        val steps = 20
        val stepDelay = durationMs / steps
        val downTime = SystemClock.uptimeMillis()

        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, sx, sy, 0)
        decorView.dispatchTouchEvent(down)
        down.recycle()

        for (i in 1..steps) {
            val fraction = i.toFloat() / steps
            val mx = sx + (ex - sx) * fraction
            val my = sy + (ey - sy) * fraction
            val delay = stepDelay * i
            val isLast = i == steps
            mainHandler.postDelayed({
                val moveTime = SystemClock.uptimeMillis()
                val move = MotionEvent.obtain(downTime, moveTime, MotionEvent.ACTION_MOVE, mx, my, 0)
                decorView.dispatchTouchEvent(move)
                move.recycle()
                if (isLast) {
                    mainHandler.postDelayed({
                        val upTime = SystemClock.uptimeMillis()
                        val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, ex, ey, 0)
                        decorView.dispatchTouchEvent(up)
                        up.recycle()
                        onDone()
                    }, 50)
                }
            }, delay)
        }
    }

    // ── wait_for ─────────────────────────────────────────────────────

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
                "visible"    -> views.isNotEmpty() && views[0].visibility == View.VISIBLE
                "gone"       -> views.isEmpty() || views[0].visibility != View.VISIBLE
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

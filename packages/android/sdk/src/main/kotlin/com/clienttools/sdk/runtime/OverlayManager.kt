package com.clienttools.sdk.runtime

import android.app.Activity
import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import com.clienttools.sdk.ClientToolsSDK

object OverlayManager {
    private var webView: WebView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentOpacity: Float = 1.0f
    private var currentOffsetX: Int = 0
    private var currentOffsetY: Int = 0

    fun show(url: String, opacity: Float = 1.0f): Boolean = try {
        val activity = ClientToolsSDK.getCurrentActivity() ?: return false
        currentOpacity = opacity.coerceIn(0.0f, 1.0f)

        if (Looper.myLooper() == Looper.getMainLooper()) {
            showOnMainThread(activity, url)
        } else {
            activity.runOnUiThread { showOnMainThread(activity, url) }
        }
        true
    } catch (e: Exception) {
        false
    }

    fun hide(): Boolean = try {
        val activity = ClientToolsSDK.getCurrentActivity() ?: return false
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideOnMainThread(activity)
        } else {
            activity.runOnUiThread { hideOnMainThread(activity) }
        }
        true
    } catch (e: Exception) {
        false
    }

    fun setOpacity(opacity: Float): Boolean = try {
        currentOpacity = opacity.coerceIn(0.0f, 1.0f)
        webView?.alpha = currentOpacity
        true
    } catch (e: Exception) {
        false
    }

    fun setOffset(offsetX: Int, offsetY: Int): Boolean {
        return try {
            val activity = ClientToolsSDK.getCurrentActivity() ?: return false
            currentOffsetX = offsetX
            currentOffsetY = offsetY

            if (webView == null || layoutParams == null) {
                return false
            }

            if (Looper.myLooper() == Looper.getMainLooper()) {
                updateWebViewOffset(activity)
            } else {
                activity.runOnUiThread { updateWebViewOffset(activity) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getOffset(): Pair<Int, Int> = Pair(currentOffsetX, currentOffsetY)

    private fun showOnMainThread(activity: Activity, url: String) {
        if (webView == null) {
            webView = WebView(activity).apply {
                setBackgroundColor(Color.TRANSPARENT)
                alpha = currentOpacity
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }
                loadUrl(url)
            }

            windowManager = activity.windowManager
            layoutParams = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                format = android.graphics.PixelFormat.RGBA_8888
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }

            windowManager?.addView(webView, layoutParams)
        } else {
            webView?.loadUrl(url)
        }
    }

    private fun hideOnMainThread(activity: Activity) {
        if (webView != null && windowManager != null) {
            try {
                windowManager?.removeView(webView)
            } catch (e: Exception) {}
            webView = null
            windowManager = null
        }
    }

    private fun updateWebViewOffset(activity: Activity) {
        try {
            layoutParams?.let {
                it.x = currentOffsetX
                it.y = currentOffsetY
                windowManager?.updateViewLayout(webView, it)
            }
        } catch (e: Exception) {
            android.util.Log.e("OverlayManager", "Error updating offset", e)
        }
    }
}

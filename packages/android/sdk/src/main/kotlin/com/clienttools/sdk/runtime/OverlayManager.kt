package com.clienttools.sdk.runtime
// Deprecated: replaced by WebViewRenderer + InspectorPage
object OverlayManager {
    fun show(url: String, opacity: Float = 1.0f): Boolean = false
    fun hide(): Boolean = false
    fun setOpacity(opacity: Float): Boolean = false
    fun setOffset(offsetX: Int, offsetY: Int): Boolean = false
    fun getOffset(): Pair<Int, Int> = 0 to 0
    fun isVisible(): Boolean = false
    fun reattachIfNeeded(activity: android.app.Activity) {}
    fun destroy() {}
}

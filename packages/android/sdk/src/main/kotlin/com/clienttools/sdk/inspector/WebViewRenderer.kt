package com.clienttools.sdk.inspector

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import com.clienttools.sdk.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class WebViewRenderer(rootView: View, private val viewModel: InspectorViewModel) {

    internal val webView: WebView = rootView.findViewById(R.id.overlay_webview)
    private var job: Job? = null

    init {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        @Suppress("DEPRECATION")
        webView.settings.allowFileAccessFromFileURLs = true
    }

    fun startObserving(scope: CoroutineScope) {
        job = scope.launch {
            launch {
                viewModel.webView.map { it.isVisible }.collect { visible ->
                    webView.visibility = if (visible) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.webView.map { it.currentFile }.filterNotNull().collect { file ->
                    webView.loadUrl(file.fileUrl)
                }
            }
            launch {
                viewModel.webView.map { it.opacity }.collect { alpha ->
                    webView.alpha = alpha
                }
            }
            launch {
                viewModel.webView.map { it.offsetX to it.offsetY }.collect { (x, y) ->
                    webView.translationX = dpToPx(x)
                    webView.translationY = dpToPx(y)
                }
            }
        }
    }

    fun stopObserving() {
        job?.cancel()
        job = null
    }

    private fun dpToPx(dp: Int): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        webView.context.resources.displayMetrics
    )
}

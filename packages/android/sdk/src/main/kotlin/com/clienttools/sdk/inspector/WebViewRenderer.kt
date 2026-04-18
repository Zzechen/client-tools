package com.clienttools.sdk.inspector

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import com.clienttools.sdk.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

// scope 由 InspectorPage 传入（activity.lifecycleScope），与 Activity 生命周期绑定
class WebViewRenderer(rootView: View, private val viewModel: InspectorViewModel) {

    private val webView: WebView = rootView.findViewById(R.id.overlay_webview)
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
                viewModel.isVisible.collect { visible ->
                    webView.visibility = if (visible) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.currentFile.filterNotNull().collect { file ->
                    webView.loadUrl(file.fileUrl)
                }
            }
            launch {
                viewModel.opacity.collect { alpha ->
                    webView.alpha = alpha
                }
            }
            launch {
                combine(viewModel.offsetX, viewModel.offsetY) { x, y -> x to y }
                    .collect { (x, y) ->
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

package com.clienttools.sdk.inspector

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow

data class FileInfo(
    val tag: String,
    val timestamp: String,
    val fileUrl: String
)

data class WebViewState(
    val currentFile: FileInfo? = null,
    val isVisible: Boolean = false,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val opacity: Float = 0.5f
)

data class ImageState(
    val currentImage: ImageInfo? = null,
    val isVisible: Boolean = false,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val opacity: Float = 0.5f
)

enum class ActiveTab { WEBVIEW, IMAGE, STATUS }

class InspectorViewModel(app: Application) : AndroidViewModel(app) {
    val activeTab = MutableStateFlow(ActiveTab.WEBVIEW)
    val webView   = MutableStateFlow(WebViewState())
    val image     = MutableStateFlow(ImageState())
}

package com.clienttools.sdk.inspector

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow

data class FileInfo(
    val tag: String,
    val timestamp: String,
    val fileUrl: String  // file:// 绝对路径，供 WebView.loadUrl() 使用
)

class InspectorViewModel(app: Application) : AndroidViewModel(app) {
    val currentFile = MutableStateFlow<FileInfo?>(null)
    val isVisible   = MutableStateFlow(false)
    val offsetX     = MutableStateFlow(0)     // dp，累计绝对值
    val offsetY     = MutableStateFlow(0)     // dp，累计绝对值
    val opacity     = MutableStateFlow(0.5f)  // 0.0-1.0，默认 0.5
}

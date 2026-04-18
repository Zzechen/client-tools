package com.clienttools.demo

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.clienttools.sdk.model.WebViewFile

class WebViewViewModel : ViewModel() {
    val currentFile = MutableLiveData<Pair<String, String>?>()  // (tag, timestamp)
    val isWebViewVisible = MutableLiveData<Boolean>(false)
    val offsetX = MutableLiveData<Int>(0)
    val offsetY = MutableLiveData<Int>(0)
    val opacity = MutableLiveData<Float>(1.0f)
    val savedFiles = MutableLiveData<List<WebViewFile>>(emptyList())

    fun updateState(
        tag: String?,
        timestamp: String?,
        visible: Boolean,
        offsetX: Int,
        offsetY: Int,
        opacity: Float
    ) {
        this.currentFile.value = if (tag != null && timestamp != null) Pair(tag, timestamp) else null
        this.isWebViewVisible.value = visible
        this.offsetX.value = offsetX
        this.offsetY.value = offsetY
        this.opacity.value = opacity
    }
}

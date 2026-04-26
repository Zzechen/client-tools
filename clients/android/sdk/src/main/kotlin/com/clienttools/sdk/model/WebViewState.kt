package com.clienttools.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class WebViewState(
    val currentTag: String? = null,
    val currentTimestamp: String? = null,
    val isVisible: Boolean = false,
    val opacity: Float = 1.0f,
    val offsetX: Int = 0,
    val offsetY: Int = 0
)

package com.clienttools.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class WebViewFile(
    val tag: String,
    val timestamp: String,  // MMdd-HHmm format
    val filePath: String,   // absolute path on disk
    val fileSize: Long,     // bytes
    val isCurrent: Boolean = false
)

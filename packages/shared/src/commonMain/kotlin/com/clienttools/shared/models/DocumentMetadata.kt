package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class DocumentMetadata(
    val name: String,
    val description: String? = null,
    val designerName: String? = null,
    val createdAt: String,
    val modifiedAt: String,
    val screenWidthDp: Float,
    val screenHeightDp: Float,
    val tags: List<String> = emptyList()
)

package com.clienttools.sdk.model

import com.clienttools.sdk.models.NodeAttrs
import kotlinx.serialization.Serializable

@Serializable
data class ViewInfo(
    val id: String,
    val type: String,
    val screenX: Float,
    val screenY: Float,
    val widthDp: Float,
    val heightDp: Float,
    val attrs: NodeAttrs? = null,
    val visibility: Int,
    val isEnabled: Boolean
)

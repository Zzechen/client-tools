package com.clienttools.sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class Node(
    val id: String,
    val type: NodeType,
    val screenX: Float,
    val screenY: Float,
    val widthDp: Float,
    val heightDp: Float,
    val attrs: NodeAttrs? = null,
    val customAttrs: Map<String, String> = emptyMap()
)

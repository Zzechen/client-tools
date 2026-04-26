package com.clienttools.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class ScrollRequest(
    val id: String,
    val dx: Float,
    val dy: Float
)

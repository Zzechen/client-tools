package com.clienttools.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class PageInfo(
    val pageName: String,
    val timestamp: String
)

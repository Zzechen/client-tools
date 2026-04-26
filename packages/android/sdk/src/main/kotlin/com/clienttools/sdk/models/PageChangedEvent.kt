package com.clienttools.sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class PageChangedEvent(
    val pageName: String,
    val timestamp: Long
)

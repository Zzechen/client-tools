package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class PageChangedEvent(
    val event: String = "page_changed",
    val activityName: String,
    val timestamp: String
)

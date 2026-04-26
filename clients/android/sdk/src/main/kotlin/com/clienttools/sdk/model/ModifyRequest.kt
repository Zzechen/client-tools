package com.clienttools.sdk.model

import com.clienttools.sdk.models.ViewProps
import kotlinx.serialization.Serializable

@Serializable
data class ModifyRequest(
    val id: String,
    val props: ViewProps
)

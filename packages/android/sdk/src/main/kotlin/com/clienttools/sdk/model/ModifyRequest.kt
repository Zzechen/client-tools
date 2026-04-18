package com.clienttools.sdk.model

import com.clienttools.shared.models.ViewProps
import kotlinx.serialization.Serializable

@Serializable
data class ModifyRequest(
    val id: String,
    val props: ViewProps
)

package com.clienttools.sdk.model

import com.clienttools.shared.ViewProps
import kotlinx.serialization.Serializable

@Serializable
data class ModifyRequest(
    val id: String,
    val props: ViewProps
)

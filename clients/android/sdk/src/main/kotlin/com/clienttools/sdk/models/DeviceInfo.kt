package com.clienttools.sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val screenWidthDp: Float,
    val screenHeightDp: Float,
    val density: Float
)

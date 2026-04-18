package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val density: Float,
    val orientation: String
)

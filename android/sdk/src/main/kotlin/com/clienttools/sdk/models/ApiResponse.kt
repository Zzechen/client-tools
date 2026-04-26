package com.clienttools.sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val sdkVersion: Int,
    val device: DeviceInfo,
    val data: T? = null
)

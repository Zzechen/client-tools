package com.clienttools.sdk.http

data class CustomRoute(
    val path: String,
    val method: HttpMethod,
    val description: String = "",
    val params: Map<String, String> = emptyMap(),
    val handler: suspend (String?) -> CustomResult
)

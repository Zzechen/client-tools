package com.clienttools.sdk

import android.content.Context
import com.clienttools.sdk.http.CustomRoute

object ClientToolsSDK {
    fun init(context: Context) {}
    fun init(context: Context, customRoutes: List<CustomRoute> = emptyList(), customHandlerTimeoutMs: Long = 4500L) {}
    fun resolveRedirect(url: String): String = url
    fun shutdown() {}
}

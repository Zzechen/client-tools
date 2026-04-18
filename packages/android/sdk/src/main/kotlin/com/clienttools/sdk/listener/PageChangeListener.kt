package com.clienttools.sdk.listener

import android.content.Context
import com.clienttools.sdk.http.EventManager

class PageChangeListener(private val eventManager: EventManager) {
    fun register(context: Context) {}
    fun unregister() {}
    fun addListener(callback: (String, Long) -> Unit) {}
}

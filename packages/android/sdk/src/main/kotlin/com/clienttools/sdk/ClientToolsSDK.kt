package com.clienttools.sdk

import android.content.Context
import android.util.Log
import com.clienttools.sdk.http.EventManager
import com.clienttools.sdk.http.HttpServer
import com.clienttools.sdk.listener.PageChangeListener
import com.clienttools.sdk.model.ModifyRequest
import com.clienttools.sdk.model.ViewInfo
import com.clienttools.sdk.runtime.ViewModifier
import com.clienttools.sdk.runtime.ViewQueryService

object ClientToolsSDK {
    private var httpServer: HttpServer? = null
    private var eventManager: EventManager? = null
    private var pageChangeListener: PageChangeListener? = null
    private var currentActivity: android.app.Activity? = null
    private var isInitialized = false
    private const val TAG = "ClientToolsSDK"

    fun init(context: Context) {
        if (isInitialized) return
        try {
            eventManager = EventManager()
            httpServer = HttpServer(context, eventManager!!)
            httpServer!!.startServer()
            pageChangeListener = PageChangeListener(eventManager!!)
            pageChangeListener!!.register(context)
            isInitialized = true
            Log.d(TAG, "ClientToolsSDK initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
        }
    }

    fun getViewInfo(viewId: String): ViewInfo? = ViewQueryService.getViewInfo(viewId)
    fun modify(request: ModifyRequest): Boolean = ViewModifier.apply(request.id, request.props)
    fun showOverlay(url: String, opacity: Float = 1.0f): Boolean = false
    fun hideOverlay(): Boolean = false
    fun addPageChangeListener(callback: (pageName: String, timestamp: Long) -> Unit) {
        pageChangeListener?.addListener(callback)
    }
    fun shutdown() {
        httpServer?.stopServer()
        pageChangeListener?.unregister()
        isInitialized = false
    }

    internal fun setCurrentActivity(activity: android.app.Activity?) {
        currentActivity = activity
    }

    internal fun getCurrentActivity(): android.app.Activity? = currentActivity
}

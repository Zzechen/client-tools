package com.clienttools.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.clienttools.sdk.http.HttpServer
import com.clienttools.sdk.inspector.ImageFileStore
import com.clienttools.sdk.inspector.InspectorFileStore
import com.clienttools.sdk.inspector.InspectorPage
import com.clienttools.sdk.listener.PageChangeListener
import com.clienttools.sdk.webview.WebViewRedirectStore
import java.util.WeakHashMap

object ClientToolsSDK {
    private var httpServer: HttpServer? = null
    private var pageChangeListener: PageChangeListener? = null
    internal var isInitialized = false
    internal var appContext: android.content.Context? = null
    private const val TAG = "ClientToolsSDK"

    private val pageStack = mutableListOf<InspectorPage>()
    private val pageMap = WeakHashMap<Activity, InspectorPage>()

    internal lateinit var fileStore: InspectorFileStore
    internal lateinit var imageFileStore: ImageFileStore

    fun getTop(): InspectorPage? = pageStack.lastOrNull()

    fun init(
        context: Context,
        customRoutes: List<com.clienttools.sdk.http.CustomRoute> = emptyList(),
        customHandlerTimeoutMs: Long = 4500L
    ) {
        if (isInitialized) return
        try {
            appContext = context.applicationContext
            fileStore = InspectorFileStore(context)
            imageFileStore = ImageFileStore(context)
            pageChangeListener = PageChangeListener()
            pageChangeListener!!.register(context)
            httpServer = HttpServer(context, pageChangeListener!!, customRoutes, customHandlerTimeoutMs)
            httpServer!!.startServer()
            registerInspectorLifecycle(context)
            isInitialized = true
            Log.d(TAG, "ClientToolsSDK initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
        }
    }

    private fun registerInspectorLifecycle(context: Context) {
        val app = context.applicationContext as? Application ?: return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (pageMap[activity] == null) {
                    try {
                        val page = InspectorPage(activity)
                        page.attach()
                        pageMap[activity] = page
                        pageStack.add(page)
                        Log.d(TAG, "InspectorPage attached: ${activity::class.simpleName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to attach InspectorPage", e)
                    }
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                pageMap.remove(activity)?.let { page ->
                    page.detach()
                    pageStack.remove(page)
                    Log.d(TAG, "InspectorPage removed: ${activity::class.simpleName}")
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    fun addPageChangeListener(callback: (pageName: String, timestamp: Long) -> Unit) {
        pageChangeListener?.addListener(callback)
    }

    fun resolveRedirect(url: String): String = WebViewRedirectStore.resolveRedirect(url)

    fun shutdown() {
        httpServer?.stopServer()
        pageChangeListener?.unregister()
        isInitialized = false
    }

    internal fun getCurrentActivity(): Activity? = getTop()?.activity
}

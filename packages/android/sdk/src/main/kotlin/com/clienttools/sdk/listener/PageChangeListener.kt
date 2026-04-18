package com.clienttools.sdk.listener

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.http.EventManager
import java.util.concurrent.CopyOnWriteArrayList

class PageChangeListener(private val eventManager: EventManager) : Application.ActivityLifecycleCallbacks {
    private val callbacks = CopyOnWriteArrayList<(String, Long) -> Unit>()

    fun register(context: Context) {
        val app = context.applicationContext as? Application
        app?.registerActivityLifecycleCallbacks(this)
    }

    fun unregister() {
        callbacks.clear()
    }

    fun addListener(callback: (String, Long) -> Unit) {
        callbacks.add(callback)
    }

    override fun onActivityResumed(activity: Activity) {
        ClientToolsSDK.setCurrentActivity(activity)
        val pageName = activity::class.qualifiedName ?: activity::class.simpleName ?: "Unknown"
        val timestamp = System.currentTimeMillis()
        eventManager.publishPageChange(pageName, timestamp)
        callbacks.forEach { it(pageName, timestamp) }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (ClientToolsSDK.getCurrentActivity() == activity) {
            ClientToolsSDK.setCurrentActivity(null)
        }
    }
}

package com.clienttools.sdk.listener

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class PageChangeListener : Application.ActivityLifecycleCallbacks {
    private val callbacks = CopyOnWriteArrayList<(String, Long) -> Unit>()

    private var currentPageName: String = ""
    private var lastChangeTime: String = ""

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

    fun getCurrentPage(): Pair<String, String> = Pair(currentPageName, lastChangeTime)

    override fun onActivityResumed(activity: Activity) {
        val pageName = activity::class.qualifiedName ?: activity::class.simpleName ?: "Unknown"
        val timestamp = System.currentTimeMillis()
        lastChangeTime = SimpleDateFormat("MMdd-HHmm", Locale.US).format(Date(timestamp))
        currentPageName = pageName
        callbacks.forEach { it(pageName, timestamp) }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

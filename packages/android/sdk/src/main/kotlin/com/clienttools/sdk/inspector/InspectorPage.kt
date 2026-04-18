package com.clienttools.sdk.inspector

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.clienttools.sdk.R

class InspectorPage(val activity: Activity) {

    val viewModel: InspectorViewModel =
        ViewModelProvider(activity as androidx.activity.ComponentActivity)[InspectorViewModel::class.java]

    private val rootView: View = LayoutInflater.from(activity)
        .inflate(R.layout.inspector_overlay, null)

    val panel: InspectorPanel = InspectorPanel(rootView, viewModel)
    val renderer: WebViewRenderer = WebViewRenderer(rootView, viewModel)

    fun attach() {
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        content.addView(rootView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val scope = (activity as LifecycleOwner).lifecycleScope
        panel.startObserving(scope)
        renderer.startObserving(scope)
    }

    fun detach() {
        panel.stopObserving()
        renderer.stopObserving()
    }
}

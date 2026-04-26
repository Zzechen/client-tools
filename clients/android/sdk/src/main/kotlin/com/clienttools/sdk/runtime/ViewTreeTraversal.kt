package com.clienttools.sdk.runtime

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.clienttools.sdk.ClientToolsSDK

object ViewTreeTraversal {

    fun findViewById(viewId: String): List<View> {
        val results = mutableListOf<View>()
        val activity = ClientToolsSDK.getCurrentActivity() ?: return results

        val decorView = activity.window.decorView as? ViewGroup ?: return results
        traverseTree(decorView, viewId, results)
        return results
    }

    private fun traverseTree(view: View, targetId: String, results: MutableList<View>) {
        val id = if (view.id != View.NO_ID) {
            try {
                view.resources.getResourceName(view.id).substringAfterLast("/")
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        if (id == targetId) {
            results.add(view)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                traverseTree(view.getChildAt(i), targetId, results)
            }
        }
    }

    fun traverseAll(callback: (View) -> Unit) {
        val activity = ClientToolsSDK.getCurrentActivity() ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return
        traverseAllViews(decorView, callback)
    }

    private fun traverseAllViews(view: View, callback: (View) -> Unit) {
        callback(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                traverseAllViews(view.getChildAt(i), callback)
            }
        }
    }
}

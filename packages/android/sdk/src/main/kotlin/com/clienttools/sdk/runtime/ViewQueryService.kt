package com.clienttools.sdk.runtime

import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.widget.Button
import com.clienttools.shared.models.NodeType
import com.clienttools.sdk.model.ViewInfo

object ViewQueryService {
    fun getViewInfo(viewId: String): ViewInfo? {
        val views = ViewTreeTraversal.findViewById(viewId)
        if (views.isEmpty()) return null

        val view = views.first()
        return buildViewInfo(view, viewId)
    }

    fun getAllViewInfos(): List<ViewInfo> {
        val results = mutableListOf<ViewInfo>()
        ViewTreeTraversal.traverseAll { view ->
            if (view.id == View.NO_ID) return@traverseAll
            val id = try {
                view.resources.getResourceName(view.id).substringAfterLast("/")
            } catch (e: Exception) {
                return@traverseAll
            }
            results.add(buildViewInfo(view, id))
        }
        return results
    }

    private fun buildViewInfo(view: View, viewId: String): ViewInfo {
        val displayMetrics = view.resources.displayMetrics
        val pxToDp = { px: Int -> px / displayMetrics.density }

        val location = IntArray(2)
        view.getLocationOnScreen(location)

        val typeStr = when (view) {
            is Button -> "BUTTON"
            is TextView -> "TEXT"
            is ImageView -> "IMAGE"
            is android.widget.ListView -> "LIST"
            else -> "CONTAINER"
        }

        return ViewInfo(
            id = viewId,
            type = typeStr,
            screenX = pxToDp(location[0]).toFloat(),
            screenY = pxToDp(location[1]).toFloat(),
            widthDp = pxToDp(view.width).toFloat(),
            heightDp = pxToDp(view.height).toFloat(),
            attrs = null,
            visibility = view.visibility,
            isEnabled = view.isEnabled
        )
    }
}

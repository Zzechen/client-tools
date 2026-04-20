package com.clienttools.sdk.runtime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.widget.Button
import com.clienttools.shared.models.NodeType
import com.clienttools.sdk.model.ViewInfo
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    fun captureView(viewId: String): ByteArray? {
        val views = ViewTreeTraversal.findViewById(viewId)
        if (views.isEmpty()) return null
        val view = views.first()
        if (view.width == 0 || view.height == 0) return null

        var result: ByteArray? = null
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                view.draw(canvas)
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                result = out.toByteArray()
            } finally {
                latch.countDown()
            }
        }
        latch.await(3, TimeUnit.SECONDS)
        return result
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

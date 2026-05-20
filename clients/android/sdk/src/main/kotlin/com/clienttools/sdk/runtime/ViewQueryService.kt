package com.clienttools.sdk.runtime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.clienttools.sdk.proto.Node
import com.clienttools.sdk.proto.NodeType
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ViewQueryService {
    fun getNode(viewId: String): Node? {
        val views = ViewTreeTraversal.findViewById(viewId)
        if (views.isEmpty()) return null
        return buildNode(views.first(), viewId)
    }

    fun getAllNodes(): List<Node> {
        val results = mutableListOf<Node>()
        ViewTreeTraversal.traverseAll { view ->
            if (view.id == View.NO_ID) return@traverseAll
            val id = try {
                view.resources.getResourceName(view.id).substringAfterLast("/")
            } catch (e: Exception) {
                return@traverseAll
            }
            results.add(buildNode(view, id))
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

    private fun buildNode(view: View, id: String): Node {
        val density = view.resources.displayMetrics.density
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val type = when (view) {
            is TextView -> NodeType.TEXT
            is ImageView -> NodeType.IMAGE
            is RecyclerView -> NodeType.LIST
            else -> NodeType.CONTAINER
        }
        return Node.newBuilder()
            .setId(id)
            .setType(type)
            .setScreenX(loc[0] / density)
            .setScreenY(loc[1] / density)
            .setWidthDp(view.width * view.scaleX / density)
            .setHeightDp(view.height * view.scaleY / density)
            .setVisibility(view.visibility)
            .setIsEnabled(view.isEnabled)
            .build()
    }
}

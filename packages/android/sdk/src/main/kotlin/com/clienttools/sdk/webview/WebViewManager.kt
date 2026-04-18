package com.clienttools.sdk.webview

import android.content.Context
import android.util.Log
import com.clienttools.sdk.model.WebViewFile
import com.clienttools.sdk.model.WebViewState
import com.clienttools.sdk.runtime.OverlayManager
import java.text.SimpleDateFormat
import java.util.*

object WebViewManager {
    private val TAG = "WebViewManager"
    private var state = WebViewState()

    fun init(context: Context) {
        WebViewFileStore.init(context)
    }

    fun setState(newState: WebViewState) {
        state = newState
    }

    fun getState(): WebViewState = state

    fun pushHtml(tag: String, htmlContent: String, timestamp: String? = null): Map<String, Any> {
        return try {
            val finalTimestamp = timestamp ?: generateTimestamp()
            val savedFile = WebViewFileStore.saveHtmlFile(tag, finalTimestamp, htmlContent)

            if (savedFile != null) {
                mapOf(
                    "code" to 0,
                    "message" to "success",
                    "data" to mapOf(
                        "tag" to savedFile.tag,
                        "timestamp" to savedFile.timestamp,
                        "filePath" to savedFile.filePath,
                        "fileSize" to savedFile.fileSize
                    )
                )
            } else {
                mapOf(
                    "code" to 400,
                    "message" to "Failed to save HTML file"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing HTML", e)
            mapOf(
                "code" to 400,
                "message" to "Invalid HTML content or tag: ${e.message}"
            )
        }
    }

    fun showWebView(tag: String, timestamp: String): Map<String, Any> {
        return try {
            val files = WebViewFileStore.listFilesByTag(tag)
            val file = files.find { it.timestamp == timestamp }

            if (file == null) {
                mapOf(
                    "code" to 404,
                    "message" to "File not found"
                )
            } else {
                val success = OverlayManager.show("file://${file.filePath}", state.opacity)
                if (success) {
                    state = state.copy(
                        currentTag = tag,
                        currentTimestamp = timestamp,
                        isVisible = true
                    )
                    WebViewFileStore.setCurrentFile(tag, timestamp)

                    mapOf(
                        "code" to 0,
                        "message" to "success",
                        "data" to mapOf(
                            "tag" to tag,
                            "timestamp" to timestamp,
                            "opacity" to state.opacity,
                            "offsetX" to state.offsetX,
                            "offsetY" to state.offsetY
                        )
                    )
                } else {
                    mapOf(
                        "code" to 500,
                        "message" to "Failed to show WebView"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing WebView", e)
            mapOf(
                "code" to 500,
                "message" to "Error: ${e.message}"
            )
        }
    }

    fun hideWebView(): Map<String, Any> {
        return try {
            val success = OverlayManager.hide()
            if (success) {
                state = state.copy(isVisible = false)
                mapOf(
                    "code" to 0,
                    "message" to "success"
                )
            } else {
                mapOf(
                    "code" to 500,
                    "message" to "Failed to hide WebView"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding WebView", e)
            mapOf(
                "code" to 500,
                "message" to "Error: ${e.message}"
            )
        }
    }

    fun adjustWebView(offsetXDelta: Int, offsetYDelta: Int, opacity: Float?): Map<String, Any> {
        return try {
            val newOffsetX = state.offsetX + offsetXDelta
            val newOffsetY = state.offsetY + offsetYDelta
            val newOpacity = opacity?.coerceIn(0.0f, 1.0f) ?: state.opacity

            val offsetSuccess = OverlayManager.setOffset(newOffsetX, newOffsetY)
            val opacitySuccess = OverlayManager.setOpacity(newOpacity)

            if (offsetSuccess || opacitySuccess) {
                state = state.copy(
                    offsetX = newOffsetX,
                    offsetY = newOffsetY,
                    opacity = newOpacity
                )

                mapOf(
                    "code" to 0,
                    "data" to mapOf(
                        "offsetX" to newOffsetX,
                        "offsetY" to newOffsetY,
                        "opacity" to newOpacity
                    )
                )
            } else {
                mapOf(
                    "code" to 500,
                    "message" to "Failed to adjust WebView"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting WebView", e)
            mapOf(
                "code" to 500,
                "message" to "Error: ${e.message}"
            )
        }
    }

    fun getFiles(): Map<String, Any> {
        return try {
            val files = WebViewFileStore.getAllFiles()
            mapOf(
                "code" to 0,
                "data" to mapOf(
                    "files" to files.map { f ->
                        mapOf(
                            "tag" to f.tag,
                            "timestamp" to f.timestamp,
                            "size" to f.fileSize,
                            "isCurrent" to f.isCurrent
                        )
                    }
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting files", e)
            mapOf(
                "code" to 500,
                "message" to "Error: ${e.message}"
            )
        }
    }

    private fun generateTimestamp(): String {
        val sdf = SimpleDateFormat("MMdd-HHmm", Locale.US)
        return sdf.format(Date())
    }
}

package com.clienttools.sdk.http

import android.content.Context
import android.util.Log
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.inspector.InspectorApiHandler
import fi.iki.elonen.NanoHTTPD

class HttpServer(
    private val context: Context,
    private val eventManager: EventManager
) : NanoHTTPD(8080) {

    override fun serve(session: IHTTPSession?): Response {
        return try {
            if (session == null) return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad request")
            val uri = session.uri
            val method = session.method
            when {
                method == Method.GET && uri == "/api/nodes/all" -> {
                    ApiHandler.handleGetAllNodes()
                }
                method == Method.GET && uri.startsWith("/api/capture/") -> {
                    val id = uri.removePrefix("/api/capture/")
                    ApiHandler.handleCaptureView(id)
                }
                method == Method.GET && uri.startsWith("/api/nodes/") -> {
                    val id = uri.removePrefix("/api/nodes/")
                    ApiHandler.handleGetNode(id)
                }
                method == Method.POST && uri == "/api/modify" -> {
                    ApiHandler.handleModify(readBody(session))
                }
                method == Method.GET && uri == "/api/events" -> {
                    eventManager.subscribeSSE(session)
                }
                method == Method.POST && uri == "/webview/push-html" -> {
                    inspectorHandler().handlePushHtml(readBody(session))
                }
                method == Method.POST && uri == "/webview/show" -> {
                    inspectorHandler().handleShow(readBody(session))
                }
                method == Method.POST && uri == "/webview/hide" -> {
                    inspectorHandler().handleHide("{}")
                }
                method == Method.POST && uri == "/webview/adjust" -> {
                    inspectorHandler().handleAdjust(readBody(session))
                }
                method == Method.GET && uri == "/webview/files" -> {
                    inspectorHandler().handleGetFiles(
                        currentFile = ClientToolsSDK.getTop()?.viewModel?.webView?.value?.currentFile
                    )
                }
                method == Method.POST && uri == "/inspector/push-image" -> {
                    inspectorHandler().handlePushImage(readBody(session))
                }
                method == Method.POST && uri == "/inspector/show-image" -> {
                    inspectorHandler().handleShowImage(readBody(session))
                }
                method == Method.GET && uri == "/inspector/images" -> {
                    inspectorHandler().handleGetImages(
                        currentImage = ClientToolsSDK.getTop()?.viewModel?.image?.value?.currentImage
                    )
                }
                method == Method.POST && uri == "/inspector/hide" -> {
                    inspectorHandler().handleHide(readBody(session))
                }
                method == Method.POST && uri == "/inspector/adjust" -> {
                    inspectorHandler().handleAdjust(readBody(session))
                }
                method == Method.GET && uri == "/dom/all" -> {
                    val webView = ClientToolsSDK.getTop()?.renderer?.webView
                    kotlinx.coroutines.runBlocking {
                        inspectorHandler().handleDomAll(webView)
                    }
                }
                method == Method.GET && uri.startsWith("/dom/") -> {
                    val id = uri.removePrefix("/dom/")
                    val webView = ClientToolsSDK.getTop()?.renderer?.webView
                    kotlinx.coroutines.runBlocking {
                        inspectorHandler().handleDomById(webView, id)
                    }
                }
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            Log.e("HttpServer", "Error handling request", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Internal error")
        }
    }

    private fun inspectorHandler() = InspectorApiHandler(
        fileStore = ClientToolsSDK.fileStore,
        imageFileStore = ClientToolsSDK.imageFileStore,
        getTopViewModel = { ClientToolsSDK.getTop()?.viewModel }
    )

    private fun readBody(session: IHTTPSession): String {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val buffer = ByteArray(contentLength)
        session.inputStream.read(buffer)
        return String(buffer)
    }

    fun startServer() {
        try {
            super.start()
            Log.d("HttpServer", "HTTP server started on port 8080")
        } catch (e: Exception) {
            Log.e("HttpServer", "Failed to start server", e)
        }
    }

    fun stopServer() {
        try {
            super.closeAllConnections()
            super.stop()
            Log.d("HttpServer", "HTTP server stopped")
        } catch (e: Exception) {
            Log.e("HttpServer", "Failed to stop server", e)
        }
    }
}

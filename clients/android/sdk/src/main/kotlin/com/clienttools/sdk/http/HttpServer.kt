package com.clienttools.sdk.http

import android.content.Context
import android.util.Log
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.listener.PageChangeListener
import fi.iki.elonen.NanoHTTPD

class HttpServer(
    private val context: Context,
    private val pageChangeListener: PageChangeListener,
    private val customRoutes: List<CustomRoute> = emptyList(),
    private val customHandlerTimeoutMs: Long = 4500L
) : NanoHTTPD(8081) {

    init {
        ApiHandler.init(context, pageChangeListener)
    }

    override fun serve(session: IHTTPSession?): Response {
        return try {
            if (session == null) return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad request")
            val uri = session.uri
            val method = session.method
            when {
                method == Method.GET && uri == "/api/info" ->
                    ApiHandler.handleGetInfo()

                method == Method.POST && uri == "/api/screen/wake" ->
                    ApiHandler.handleScreenWake()

                method == Method.GET && uri == "/api/page/current" ->
                    ApiHandler.handleGetCurrentPage()

                method == Method.POST && uri == "/api/click" ->
                    ApiHandler.handleClick(readBodyBytes(session))

                method == Method.POST && uri == "/api/scroll" ->
                    ApiHandler.handleScroll(readBodyBytes(session))

                method == Method.POST && uri == "/api/input" ->
                    ApiHandler.handleInputText(readBodyBytes(session))

                method == Method.POST && uri == "/api/gesture" ->
                    ApiHandler.handleGesture(readBodyBytes(session))

                method == Method.POST && uri == "/api/wait" ->
                    ApiHandler.handleWaitFor(readBodyBytes(session))

                method == Method.GET && uri == "/api/nodes/all" ->
                    ApiHandler.handleGetAllNodes()

                method == Method.GET && uri.startsWith("/api/capture/") -> {
                    val id = uri.removePrefix("/api/capture/")
                    ApiHandler.handleCaptureView(id)
                }

                method == Method.GET && uri.startsWith("/api/nodes/") -> {
                    val id = uri.removePrefix("/api/nodes/")
                    ApiHandler.handleGetNode(id)
                }

                method == Method.POST && uri == "/api/modify" ->
                    ApiHandler.handleModify(readBodyBytes(session))

                method == Method.GET && uri == "/webview/files" ->
                    ApiHandler.handleWebviewFiles(ClientToolsSDK.fileStore)

                method == Method.POST && uri == "/webview/push-html" ->
                    ApiHandler.handlePushHtml(readBodyBytes(session), ClientToolsSDK.fileStore)

                method == Method.POST && uri == "/webview/show" ->
                    ApiHandler.handleWebviewShow(readBodyBytes(session), ClientToolsSDK.fileStore)

                method == Method.POST && uri == "/webview/hide" ->
                    ApiHandler.handleWebviewHide()

                method == Method.POST && uri == "/webview/adjust" ->
                    ApiHandler.handleWebviewAdjust(readBodyBytes(session))

                method == Method.POST && uri == "/inspector/push-image" ->
                    ApiHandler.handlePushImage(readBodyBytes(session), ClientToolsSDK.imageFileStore)

                method == Method.POST && uri == "/inspector/show-image" ->
                    ApiHandler.handleShowImage(readBodyBytes(session), ClientToolsSDK.imageFileStore)

                method == Method.GET && uri == "/inspector/images" ->
                    ApiHandler.handleGetImages(ClientToolsSDK.imageFileStore)

                method == Method.POST && uri == "/inspector/hide" ->
                    ApiHandler.handleInspectorHide(readBodyBytes(session))

                method == Method.POST && uri == "/inspector/adjust" ->
                    ApiHandler.handleInspectorAdjust(readBodyBytes(session))

                method == Method.GET && uri == "/dom/all" -> {
                    val webView = ClientToolsSDK.getTop()?.renderer?.webView
                    kotlinx.coroutines.runBlocking {
                        ApiHandler.handleDomAll(webView)
                    }
                }

                method == Method.GET && uri.startsWith("/dom/") -> {
                    val id = uri.removePrefix("/dom/")
                    val webView = ClientToolsSDK.getTop()?.renderer?.webView
                    kotlinx.coroutines.runBlocking {
                        ApiHandler.handleDomById(webView, id)
                    }
                }

                method == Method.POST && uri == "/mock/rules" ->
                    ApiHandler.handleMockAdd(readBodyBytes(session))

                method == Method.GET && uri == "/mock/rules" ->
                    ApiHandler.handleMockList()

                method == Method.DELETE && uri.startsWith("/mock/rules/") -> {
                    val id = uri.removePrefix("/mock/rules/")
                    ApiHandler.handleMockDelete(id)
                }

                method == Method.DELETE && uri == "/mock/rules" ->
                    ApiHandler.handleMockClear()

                method == Method.POST && uri == "/webview/redirects" ->
                    ApiHandler.handleWebViewRedirectAdd(readBodyBytes(session))

                method == Method.GET && uri == "/webview/redirects" ->
                    ApiHandler.handleWebViewRedirectList()

                method == Method.DELETE && uri.startsWith("/webview/redirects/") -> {
                    val id = uri.removePrefix("/webview/redirects/")
                    ApiHandler.handleWebViewRedirectDelete(id)
                }

                method == Method.DELETE && uri == "/webview/redirects" ->
                    ApiHandler.handleWebViewRedirectClear()

                method == Method.GET && uri == "/custom/routes" ->
                    ApiHandler.handleCustomRoutes(customRoutes)

                uri.startsWith("/custom/") -> {
                    val path = uri.removePrefix("/custom/")
                    val route = customRoutes.find {
                        it.path == path && it.method.value == method.name
                    }
                    if (route != null)
                        kotlinx.coroutines.runBlocking {
                            ApiHandler.handleCustomCall(route, readBody(session), customHandlerTimeoutMs)
                        }
                    else
                        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
                }

                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            Log.e("HttpServer", "Error handling request", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Internal error")
        }
    }

    private fun readBodyBytes(session: IHTTPSession): ByteArray {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val buffer = ByteArray(contentLength)
        session.inputStream.read(buffer)
        return buffer
    }

    private fun readBody(session: IHTTPSession): String {
        return readBodyBytes(session).toString(Charsets.UTF_8)
    }

    fun startServer() {
        try {
            super.start()
            Log.d("HttpServer", "HTTP server started on port 8081")
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

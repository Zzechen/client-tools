package com.clienttools.sdk.http

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.json.Json

class HttpServer(
    private val context: Context,
    private val eventManager: EventManager
) : NanoHTTPD(8080) {

    private var server: NanoHTTPD? = null

    override fun serve(session: IHTTPSession?): Response {
        return try {
            if (session == null) return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad request")

            val uri = session.uri
            val method = session.method

            when {
                method == Method.GET && uri.startsWith("/api/nodes/") -> {
                    val id = uri.removePrefix("/api/nodes/")
                    ApiHandler.handleGetNode(id)
                }
                method == Method.POST && uri == "/api/modify" -> {
                    val body = readBody(session)
                    ApiHandler.handleModify(body)
                }
                method == Method.POST && uri == "/api/overlay/show" -> {
                    val body = readBody(session)
                    ApiHandler.handleOverlayShow(body)
                }
                method == Method.POST && uri == "/api/overlay/hide" -> {
                    ApiHandler.handleOverlayHide()
                }
                method == Method.POST && uri == "/api/overlay/opacity" -> {
                    val body = readBody(session)
                    ApiHandler.handleOverlayOpacity(body)
                }
                method == Method.GET && uri == "/api/events" -> {
                    eventManager.subscribeSSE(session)
                }
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            Log.e("HttpServer", "Error handling request", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Internal error")
        }
    }

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

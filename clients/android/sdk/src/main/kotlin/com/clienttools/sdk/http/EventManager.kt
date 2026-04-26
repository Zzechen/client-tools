package com.clienttools.sdk.http

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
data class PageChangedEvent(
    val event: String = "page_changed",
    val pageName: String,
    val timestamp: Long
)

class EventManager {
    private val pageChangeCallbacks = CopyOnWriteArrayList<(String, Long) -> Unit>()
    private val sseSessions = CopyOnWriteArrayList<SSESession>()

    fun subscribeSSE(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val sseSession = SSESession()
        sseSessions.add(sseSession)

        val inputStream = PipedInputStream()
        val outputStream = PipedOutputStream(inputStream)
        sseSession.outputStream = outputStream

        val response = NanoHTTPD.newChunkedResponse(
            NanoHTTPD.Response.Status.OK,
            "text/event-stream",
            inputStream
        )
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")
        response.addHeader("Access-Control-Allow-Origin", "*")

        Thread {
            try {
                outputStream.write("data: {\"type\":\"connected\"}\n\n".toByteArray())
                outputStream.flush()
                while (sseSession.isActive) {
                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                Log.e("EventManager", "SSE session error", e)
            } finally {
                sseSessions.remove(sseSession)
                try {
                    outputStream.close()
                    inputStream.close()
                } catch (e: Exception) {}
            }
        }.start()

        return response
    }

    fun publishPageChange(pageName: String, timestamp: Long = System.currentTimeMillis()) {
        val event = PageChangedEvent(pageName = pageName, timestamp = timestamp)
        val jsonStr = Json.encodeToString(event)

        // Send to SSE clients
        sseSessions.forEach { session ->
            try {
                session.outputStream?.write("data: $jsonStr\n\n".toByteArray())
                session.outputStream?.flush()
            } catch (e: Exception) {
                Log.e("EventManager", "Error sending SSE event", e)
                session.isActive = false
            }
        }

        // Send to callback listeners
        pageChangeCallbacks.forEach { it(pageName, timestamp) }
    }

    fun addPageChangeCallback(callback: (String, Long) -> Unit) {
        pageChangeCallbacks.add(callback)
    }
}

private class SSESession {
    var outputStream: PipedOutputStream? = null
    var isActive = true
}

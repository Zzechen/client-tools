package com.clienttools.sdk.http

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
data class PageChangedEvent(
    val event: String = "page_changed",
    val pageName: String,
    val timestamp: Long
)

class EventManager {
    private val listeners = CopyOnWriteArrayList<SSEConnection>()
    private val pageChangeCallbacks = CopyOnWriteArrayList<(String, Long) -> Unit>()
    
    fun subscribe(connection: SSEConnection) { listeners.add(connection) }
    fun unsubscribe(connection: SSEConnection) { listeners.remove(connection) }
    fun publishPageChange(pageName: String, timestamp: Long = System.currentTimeMillis()) {
        val event = PageChangedEvent(pageName = pageName, timestamp = timestamp)
        val jsonStr = Json.encodeToString(event)
        listeners.forEach { try { it.send("data: $jsonStr\n\n") } catch (e: Exception) { unsubscribe(it) } }
        pageChangeCallbacks.forEach { it(pageName, timestamp) }
    }
    fun addPageChangeCallback(callback: (String, Long) -> Unit) { pageChangeCallbacks.add(callback) }
}

interface SSEConnection { fun send(data: String) }

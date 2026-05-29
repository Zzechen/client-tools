package com.clienttools.sdk.webview

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class WebViewRedirectEntry(
    val id: String,
    val urlPattern: String,
    val targetUrl: String
)

object WebViewRedirectStore {
    private val rules = ConcurrentHashMap<String, WebViewRedirectEntry>()
    private val insertOrder = CopyOnWriteArrayList<String>()

    fun add(entry: WebViewRedirectEntry): WebViewRedirectEntry {
        rules[entry.id] = entry
        insertOrder.add(entry.id)
        return entry
    }

    fun delete(id: String): Boolean {
        val removed = rules.remove(id) != null
        if (removed) insertOrder.remove(id)
        return removed
    }

    fun list(): List<WebViewRedirectEntry> = insertOrder.mapNotNull { rules[it] }

    fun clear(): Int {
        val count = rules.size
        rules.clear()
        insertOrder.clear()
        return count
    }

    fun resolveRedirect(url: String): String {
        val urlWithoutQuery = url.substringBefore("?")
        val originalQuery = url.substringAfter("?", "")

        val match = insertOrder.mapNotNull { rules[it] }.firstOrNull { entry ->
            Regex(entry.urlPattern).containsMatchIn(urlWithoutQuery)
        } ?: return url

        return mergeQueryParams(match.targetUrl, originalQuery)
    }

    private fun mergeQueryParams(targetUrl: String, originalQuery: String): String {
        if (originalQuery.isEmpty()) return targetUrl

        val targetBase = targetUrl.substringBefore("?")
        val targetQuery = targetUrl.substringAfter("?", "")

        val params = mutableMapOf<String, String>()
        if (targetQuery.isNotEmpty()) {
            targetQuery.split("&").forEach { pair ->
                val k = pair.substringBefore("=")
                val v = pair.substringAfter("=", "")
                params[k] = v
            }
        }
        // Original overwrites target on conflict
        originalQuery.split("&").forEach { pair ->
            val k = pair.substringBefore("=")
            val v = pair.substringAfter("=", "")
            params[k] = v
        }

        val merged = params.entries.joinToString("&") { (k, v) -> "$k=$v" }
        return "$targetBase?$merged"
    }
}

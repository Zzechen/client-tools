package com.clienttools.sdk.inspector

import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

class DomQueryService(
    private val timeoutMs: Long = 3000L
) {

    private val TAG = "DomQueryService"

    private val JS_ALL = """
        (function() {
          var nodes = [];
          var all = document.querySelectorAll('*');
          for (var i = 0; i < all.length; i++) {
            var el = all[i];
            var r = el.getBoundingClientRect();
            nodes.push({
              id: el.id || '',
              tagName: el.tagName.toLowerCase(),
              x: Math.round(r.left),
              y: Math.round(r.top),
              width: Math.round(r.width),
              height: Math.round(r.height),
              text: (el.innerText || '').substring(0, 200)
            });
          }
          return JSON.stringify(nodes);
        })()
    """.trimIndent()

    private fun jsById(id: String) = """
        (function() {
          var el = document.getElementById('${id.replace("'", "\\'")}');
          if (!el) return null;
          var r = el.getBoundingClientRect();
          return JSON.stringify({
            id: el.id || '',
            tagName: el.tagName.toLowerCase(),
            x: Math.round(r.left),
            y: Math.round(r.top),
            width: Math.round(r.width),
            height: Math.round(r.height),
            text: (el.innerText || '').substring(0, 200)
          });
        })()
    """.trimIndent()

    suspend fun queryAll(
        webView: WebView,
        webViewOffsetXDp: Int,
        webViewOffsetYDp: Int
    ): List<DomNodeInfo> {
        val density = webView.context.resources.displayMetrics.density
        val offsetXPx = (webViewOffsetXDp * density).toInt()
        val offsetYPx = (webViewOffsetYDp * density).toInt()

        val loc = IntArray(2)
        webView.getLocationOnScreen(loc)
        val webViewLeft = loc[0]
        val webViewTop = loc[1]
        val scrollX = webView.scrollX
        val scrollY = webView.scrollY

        return try {
            val rawJson = withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    webView.post {
                        webView.evaluateJavascript(JS_ALL) { result ->
                            cont.resume(result)
                        }
                    }
                }
            }
            parseNodesJson(rawJson, webViewLeft, webViewTop, scrollX, scrollY, offsetXPx, offsetYPx)
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "queryAll timeout")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "queryAll error", e)
            emptyList()
        }
    }

    suspend fun queryById(
        webView: WebView,
        id: String,
        webViewOffsetXDp: Int,
        webViewOffsetYDp: Int
    ): DomNodeInfo? {
        val density = webView.context.resources.displayMetrics.density
        val offsetXPx = (webViewOffsetXDp * density).toInt()
        val offsetYPx = (webViewOffsetYDp * density).toInt()

        val loc = IntArray(2)
        webView.getLocationOnScreen(loc)
        val webViewLeft = loc[0]
        val webViewTop = loc[1]
        val scrollX = webView.scrollX
        val scrollY = webView.scrollY

        return try {
            val rawJson = withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    webView.post {
                        webView.evaluateJavascript(jsById(id)) { result ->
                            cont.resume(result)
                        }
                    }
                }
            }
            parseNodeJson(rawJson, webViewLeft, webViewTop, scrollX, scrollY, offsetXPx, offsetYPx)
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "queryById timeout")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "queryById error", e)
            null
        }
    }

    internal fun parseNodesJson(
        raw: String?,
        webViewLeft: Int, webViewTop: Int,
        webViewScrollX: Int, webViewScrollY: Int,
        offsetXPx: Int, offsetYPx: Int
    ): List<DomNodeInfo> {
        if (raw == null || raw == "null") return emptyList()
        return try {
            val unescaped = unescapeJsString(raw)
            val arr = JSONArray(unescaped)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                parseNode(obj, webViewLeft, webViewTop, webViewScrollX, webViewScrollY, offsetXPx, offsetYPx)
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseNodesJson failed: ${e.message}")
            emptyList()
        }
    }

    internal fun parseNodeJson(
        raw: String?,
        webViewLeft: Int, webViewTop: Int,
        webViewScrollX: Int, webViewScrollY: Int,
        offsetXPx: Int, offsetYPx: Int
    ): DomNodeInfo? {
        if (raw == null || raw == "null") return null
        return try {
            val unescaped = unescapeJsString(raw)
            val obj = JSONObject(unescaped)
            parseNode(obj, webViewLeft, webViewTop, webViewScrollX, webViewScrollY, offsetXPx, offsetYPx)
        } catch (e: Exception) {
            Log.w(TAG, "parseNodeJson failed: ${e.message}")
            null
        }
    }

    private fun parseNode(
        obj: JSONObject,
        webViewLeft: Int, webViewTop: Int,
        webViewScrollX: Int, webViewScrollY: Int,
        offsetXPx: Int, offsetYPx: Int
    ): DomNodeInfo {
        val elemX = obj.optInt("x", 0)
        val elemY = obj.optInt("y", 0)
        return DomNodeInfo(
            id = obj.optString("id", ""),
            tagName = obj.optString("tagName", ""),
            x = webViewLeft + webViewScrollX + elemX + offsetXPx,
            y = webViewTop + webViewScrollY + elemY + offsetYPx,
            width = obj.optInt("width", 0),
            height = obj.optInt("height", 0),
            text = obj.optString("text", "")
        )
    }

    private fun unescapeJsString(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed.substring(1, trimmed.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        } else {
            trimmed
        }
    }
}

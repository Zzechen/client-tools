package com.clienttools.sdk.webview

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import fi.iki.elonen.NanoHTTPD

object WebViewApiHandler {
    private val TAG = "WebViewApiHandler"

    fun handlePushHtml(body: String): NanoHTTPD.Response {
        return try {
            val json = Json.parseToJsonElement(body)
            val tag = json.jsonObject["tag"]?.jsonPrimitive?.content
                ?: return errorResponse(400, "Missing tag")
            val html = json.jsonObject["html"]?.jsonPrimitive?.content
                ?: return errorResponse(400, "Missing html")
            val timestamp = json.jsonObject["timestamp"]?.jsonPrimitive?.content

            val result = WebViewManager.pushHtml(tag, html, timestamp)
            successResponse(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handlePushHtml", e)
            errorResponse(400, "Invalid request body")
        }
    }

    fun handleShow(body: String): NanoHTTPD.Response {
        return try {
            val json = Json.parseToJsonElement(body)
            val tag = json.jsonObject["tag"]?.jsonPrimitive?.content
                ?: return errorResponse(400, "Missing tag")
            val timestamp = json.jsonObject["timestamp"]?.jsonPrimitive?.content
                ?: return errorResponse(400, "Missing timestamp")

            val result = WebViewManager.showWebView(tag, timestamp)
            successResponse(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleShow", e)
            errorResponse(400, "Invalid request body")
        }
    }

    fun handleHide(): NanoHTTPD.Response {
        return try {
            val result = WebViewManager.hideWebView()
            successResponse(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleHide", e)
            errorResponse(500, "Internal error")
        }
    }

    fun handleAdjust(body: String): NanoHTTPD.Response {
        return try {
            val json = Json.parseToJsonElement(body)
            val offsetX = json.jsonObject["offsetX"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val offsetY = json.jsonObject["offsetY"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val opacity = json.jsonObject["opacity"]?.jsonPrimitive?.content?.toFloatOrNull()

            val result = WebViewManager.adjustWebView(offsetX, offsetY, opacity)
            successResponse(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleAdjust", e)
            errorResponse(400, "Invalid request body")
        }
    }

    fun handleGetFiles(): NanoHTTPD.Response {
        return try {
            val result = WebViewManager.getFiles()
            successResponse(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleGetFiles", e)
            errorResponse(500, "Internal error")
        }
    }

    private fun successResponse(data: Map<String, Any>): NanoHTTPD.Response {
        val jsonStr = mapToJsonString(data)
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json",
            jsonStr
        )
    }

    private fun errorResponse(code: Int, message: String): NanoHTTPD.Response {
        val json = """{"code":$code,"message":"$message"}"""
        return NanoHTTPD.newFixedLengthResponse(
            if (code >= 500) NanoHTTPD.Response.Status.INTERNAL_ERROR else NanoHTTPD.Response.Status.BAD_REQUEST,
            "application/json",
            json
        )
    }

    private fun mapToJsonString(map: Map<String, Any>): String {
        val sb = StringBuilder("{")
        map.entries.forEachIndexed { index, (key, value) ->
            sb.append("\"$key\":")
            sb.append(valueToJsonString(value))
            if (index < map.size - 1) sb.append(",")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun valueToJsonString(value: Any): String {
        return when (value) {
            is String -> "\"$value\""
            is Number -> value.toString()
            is Boolean -> value.toString()
            is Map<*, *> -> {
                val sb = StringBuilder("{")
                value.entries.forEachIndexed { index, (key, v) ->
                    sb.append("\"$key\":")
                    sb.append(valueToJsonString(v ?: "null"))
                    if (index < value.size - 1) sb.append(",")
                }
                sb.append("}")
                sb.toString()
            }
            is List<*> -> {
                val sb = StringBuilder("[")
                value.forEachIndexed { index, item ->
                    sb.append(valueToJsonString(item ?: "null"))
                    if (index < value.size - 1) sb.append(",")
                }
                sb.append("]")
                sb.toString()
            }
            else -> "\"$value\""
        }
    }
}

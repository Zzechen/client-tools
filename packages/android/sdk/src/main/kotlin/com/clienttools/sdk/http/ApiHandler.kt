package com.clienttools.sdk.http

import android.util.Log
import com.clienttools.sdk.model.ViewInfo
import com.clienttools.shared.models.ViewProps
import com.clienttools.sdk.runtime.ViewQueryService
import com.clienttools.sdk.runtime.ViewModifier
import com.clienttools.sdk.runtime.OverlayManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import fi.iki.elonen.NanoHTTPD

object ApiHandler {

    fun handleGetNode(id: String): NanoHTTPD.Response {
        return try {
            val viewInfo = ViewQueryService.getViewInfo(id)
            if (viewInfo != null) {
                val json = Json.encodeToString(ViewInfo.serializer(), viewInfo)
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "application/json",
                    json
                )
            } else {
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND,
                    "application/json",
                    """{"error":"View not found"}"""
                )
            }
        } catch (e: Exception) {
            Log.e("ApiHandler", "Error getting node $id", e)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message}"}"""
            )
        }
    }

    fun handleGetAllNodes(): NanoHTTPD.Response {
        return try {
            val viewInfos = ViewQueryService.getAllViewInfos()
            val json = Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(ViewInfo.serializer()),
                viewInfos
            )
            NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", json)
        } catch (e: Exception) {
            Log.e("ApiHandler", "Error getting all nodes", e)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message}"}"""
            )
        }
    }

    fun handleModify(body: String): NanoHTTPD.Response {
        return try {
            val json = Json.parseToJsonElement(body)
            val id = json.jsonObject["id"]?.jsonPrimitive?.content ?: return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", """{"error":"Missing id"}"""
            )
            val propsElement = json.jsonObject["props"]
            if (propsElement != null) {
                val props = Json.decodeFromJsonElement(ViewProps.serializer(), propsElement)
                ViewModifier.apply(id, props)
            }
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json",
                """{"ok":true}"""
            )
        } catch (e: Exception) {
            Log.e("ApiHandler", "Error modifying view", e)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message}"}"""
            )
        }
    }

    fun handleCaptureView(id: String): NanoHTTPD.Response {
        return try {
            val bytes = ViewQueryService.captureView(id)
            if (bytes != null) {
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "image/png",
                    bytes.inputStream(),
                    bytes.size.toLong()
                )
            } else {
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND,
                    "application/json",
                    """{"error":"View not found or has no size"}"""
                )
            }
        } catch (e: Exception) {
            Log.e("ApiHandler", "Error capturing view $id", e)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message}"}"""
            )
        }
    }

    fun handleOverlayShow(body: String): NanoHTTPD.Response {
        return try {
            val json = Json.parseToJsonElement(body)
            val url = json.jsonObject["url"]?.jsonPrimitive?.content ?: ""
            val opacity = json.jsonObject["opacity"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1.0f
            OverlayManager.show(url, opacity)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json",
                """{"ok":true}"""
            )
        } catch (e: Exception) {
            Log.e("ApiHandler", "Error showing overlay", e)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message}"}"""
            )
        }
    }

    fun handleOverlayHide(): NanoHTTPD.Response {
        return try {
            OverlayManager.hide()
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json",
                """{"ok":true}"""
            )
        } catch (e: Exception) {
            Log.e("ApiHandler", "Error hiding overlay", e)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message}"}"""
            )
        }
    }

    fun handleOverlayOpacity(body: String): NanoHTTPD.Response {
        return try {
            val json = Json.parseToJsonElement(body)
            val opacity = json.jsonObject["opacity"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1.0f
            OverlayManager.setOpacity(opacity)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json",
                """{"ok":true}"""
            )
        } catch (e: Exception) {
            Log.e("ApiHandler", "Error setting opacity", e)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message}"}"""
            )
        }
    }
}

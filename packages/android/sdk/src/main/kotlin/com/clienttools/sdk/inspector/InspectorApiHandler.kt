package com.clienttools.sdk.inspector

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class InspectorApiHandler(
    private val fileStore: InspectorFileStore,
    private val getTopViewModel: () -> InspectorViewModel?
) {
    private val TAG = "InspectorApiHandler"
    private val json = Json { ignoreUnknownKeys = true }

    fun handlePushHtml(body: String): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val tag = obj["tag"]?.jsonPrimitive?.content ?: return error(400, "Missing tag")
        val html = obj["html"]?.jsonPrimitive?.content ?: return error(400, "Missing html")
        val timestamp = obj["timestamp"]?.jsonPrimitive?.content ?: fileStore.generateTimestamp()

        val saved = fileStore.saveHtmlFile(tag, timestamp, html)
            ?: return error(500, "Failed to save file")

        ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp","filePath":"${saved.fileUrl}","fileSize":${html.length}}}""")
    } catch (e: Exception) {
        Log.e(TAG, "pushHtml error", e)
        error(400, "Invalid request: ${e.message}")
    }

    fun handleShow(body: String): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val tag = obj["tag"]?.jsonPrimitive?.content ?: return error(400, "Missing tag")
        val timestamp = obj["timestamp"]?.jsonPrimitive?.content ?: return error(400, "Missing timestamp")

        val fileUrl = fileStore.getFilePath(tag, timestamp) ?: return error(404, "File not found")

        getTopViewModel()?.let { vm ->
            vm.currentFile.value = FileInfo(tag, timestamp, fileUrl)
            vm.isVisible.value = true
            val opacity = vm.opacity.value
            val offsetX = vm.offsetX.value
            val offsetY = vm.offsetY.value
            ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp","opacity":$opacity,"offsetX":$offsetX,"offsetY":$offsetY}}""")
        } ?: ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp"}}""")
    } catch (e: Exception) {
        Log.e(TAG, "show error", e)
        error(500, "Internal error: ${e.message}")
    }

    fun handleHide(): NanoHTTPD.Response = try {
        getTopViewModel()?.isVisible?.value = false
        ok("""{"code":0,"message":"success"}""")
    } catch (e: Exception) {
        Log.e(TAG, "hide error", e)
        error(500, "Internal error: ${e.message}")
    }

    fun handleAdjust(body: String, currentOffsetX: Int = 0, currentOffsetY: Int = 0, currentOpacity: Float = 0.5f): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val dx = obj["offsetX"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val dy = obj["offsetY"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val opacity = obj["opacity"]?.jsonPrimitive?.content?.toFloatOrNull()

        val vm = getTopViewModel()
        val newX = (vm?.offsetX?.value ?: currentOffsetX) + dx
        val newY = (vm?.offsetY?.value ?: currentOffsetY) + dy
        val newOpacity = opacity?.coerceIn(0f, 1f) ?: (vm?.opacity?.value ?: currentOpacity)

        vm?.offsetX?.value = newX
        vm?.offsetY?.value = newY
        if (opacity != null) vm?.opacity?.value = newOpacity

        ok("""{"code":0,"data":{"offsetX":$newX,"offsetY":$newY,"opacity":$newOpacity}}""")
    } catch (e: Exception) {
        Log.e(TAG, "adjust error", e)
        error(500, "Internal error: ${e.message}")
    }

    fun handleGetFiles(currentFile: FileInfo?): NanoHTTPD.Response = try {
        val vmCurrentFile = getTopViewModel()?.currentFile?.value ?: currentFile
        val files = fileStore.getAllFiles()
        val filesJson = files.joinToString(",") { f ->
            val isCurrent = vmCurrentFile?.tag == f.tag && vmCurrentFile.timestamp == f.timestamp
            val size = f.fileUrl.let { java.io.File(it.removePrefix("file://")).length() }
            """{"tag":"${f.tag}","timestamp":"${f.timestamp}","size":$size,"isCurrent":$isCurrent}"""
        }
        ok("""{"code":0,"data":{"files":[$filesJson]}}""")
    } catch (e: Exception) {
        Log.e(TAG, "getFiles error", e)
        error(500, "Internal error: ${e.message}")
    }

    private fun ok(json: String) = NanoHTTPD.newFixedLengthResponse(
        NanoHTTPD.Response.Status.OK, "application/json", json
    )

    private fun error(code: Int, message: String): NanoHTTPD.Response {
        val status = if (code == 404) NanoHTTPD.Response.Status.NOT_FOUND
                     else if (code >= 500) NanoHTTPD.Response.Status.INTERNAL_ERROR
                     else NanoHTTPD.Response.Status.BAD_REQUEST
        return NanoHTTPD.newFixedLengthResponse(status, "application/json",
            """{"code":$code,"message":"$message"}""")
    }
}

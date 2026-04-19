package com.clienttools.sdk.inspector

import android.util.Base64
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class InspectorApiHandler(
    private val fileStore: InspectorFileStore,
    private val imageFileStore: ImageFileStore,
    private val getTopViewModel: () -> InspectorViewModel?
) {
    private val TAG = "InspectorApiHandler"
    private val json = Json { ignoreUnknownKeys = true }

    // ── WebView HTML ──────────────────────────────────────────────────────────

    fun handlePushHtml(body: String): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val tag = obj["tag"]?.jsonPrimitive?.content ?: return error(400, "Missing tag")
        val html = obj["html"]?.jsonPrimitive?.content ?: return error(400, "Missing html")
        val timestamp = obj["timestamp"]?.jsonPrimitive?.content ?: fileStore.generateTimestamp()

        val saved = fileStore.saveHtmlFile(tag, timestamp, html)
            ?: return error(500, "Failed to save file")

        getTopViewModel()?.let { vm ->
            vm.webView.value = vm.webView.value.copy(
                currentFile = FileInfo(tag, timestamp, saved.fileUrl),
                isVisible = true
            )
        }

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
            vm.webView.value = vm.webView.value.copy(
                currentFile = FileInfo(tag, timestamp, fileUrl),
                isVisible = true
            )
            val s = vm.webView.value
            return ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp","opacity":${s.opacity},"offsetX":${s.offsetX},"offsetY":${s.offsetY}}}""")
        }
        ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp"}}""")
    } catch (e: Exception) {
        Log.e(TAG, "show error", e)
        error(500, "Internal error: ${e.message}")
    }

    fun handleGetFiles(currentFile: FileInfo?): NanoHTTPD.Response = try {
        val vmCurrentFile = getTopViewModel()?.webView?.value?.currentFile ?: currentFile
        val files = fileStore.getAllFiles()
        val filesJson = files.joinToString(",") { f ->
            val isCurrent = vmCurrentFile?.tag == f.tag && vmCurrentFile?.timestamp == f.timestamp
            val size = java.io.File(f.fileUrl.removePrefix("file://")).length()
            """{"tag":"${f.tag}","timestamp":"${f.timestamp}","size":$size,"isCurrent":$isCurrent}"""
        }
        ok("""{"code":0,"data":{"files":[$filesJson]}}""")
    } catch (e: Exception) {
        Log.e(TAG, "getFiles error", e)
        error(500, "Internal error: ${e.message}")
    }

    // ── Image ─────────────────────────────────────────────────────────────────

    fun handlePushImage(body: String): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val tag = obj["tag"]?.jsonPrimitive?.content ?: return error(400, "Missing tag")
        val imageBase64 = obj["image"]?.jsonPrimitive?.content ?: return error(400, "Missing image")
        val ext = obj["ext"]?.jsonPrimitive?.content?.lowercase() ?: "png"
        val timestamp = obj["timestamp"]?.jsonPrimitive?.content ?: imageFileStore.generateTimestamp()

        val bytes = try {
            Base64.decode(imageBase64, Base64.DEFAULT)
        } catch (e: Exception) {
            return error(400, "Invalid base64: ${e.message}")
        }

        val saved = imageFileStore.saveImage(tag, timestamp, bytes, ext)
            ?: return error(500, "Failed to save image")

        getTopViewModel()?.let { vm ->
            vm.image.value = vm.image.value.copy(currentImage = saved, isVisible = true)
        }

        ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp","filePath":"${saved.filePath}","fileSize":${bytes.size}}}""")
    } catch (e: Exception) {
        Log.e(TAG, "pushImage error", e)
        error(400, "Invalid request: ${e.message}")
    }

    fun handleShowImage(body: String): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val tag = obj["tag"]?.jsonPrimitive?.content ?: return error(400, "Missing tag")
        val timestamp = obj["timestamp"]?.jsonPrimitive?.content ?: return error(400, "Missing timestamp")

        val filePath = imageFileStore.getFilePath(tag, timestamp) ?: return error(404, "Image not found")
        val ext = java.io.File(filePath).extension.lowercase()

        getTopViewModel()?.let { vm ->
            vm.image.value = vm.image.value.copy(
                currentImage = ImageInfo(tag, timestamp, filePath, ext),
                isVisible = true
            )
            val s = vm.image.value
            return ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp","opacity":${s.opacity},"offsetX":${s.offsetX},"offsetY":${s.offsetY}}}""")
        }
        ok("""{"code":0,"message":"success","data":{"tag":"$tag","timestamp":"$timestamp"}}""")
    } catch (e: Exception) {
        Log.e(TAG, "showImage error", e)
        error(500, "Internal error: ${e.message}")
    }

    fun handleGetImages(currentImage: ImageInfo?): NanoHTTPD.Response = try {
        val vmCurrentImage = getTopViewModel()?.image?.value?.currentImage ?: currentImage
        val images = imageFileStore.getAllImages()
        val imagesJson = images.joinToString(",") { img ->
            val isCurrent = vmCurrentImage?.tag == img.tag && vmCurrentImage?.timestamp == img.timestamp
            val size = java.io.File(img.filePath).length()
            """{"tag":"${img.tag}","timestamp":"${img.timestamp}","ext":"${img.ext}","size":$size,"isCurrent":$isCurrent}"""
        }
        ok("""{"code":0,"data":{"images":[$imagesJson]}}""")
    } catch (e: Exception) {
        Log.e(TAG, "getImages error", e)
        error(500, "Internal error: ${e.message}")
    }

    // ── 共用：hide / adjust ────────────────────────────────────────────────────

    fun handleHide(body: String = "{}"): NanoHTTPD.Response = try {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val typeStr = obj?.get("type")?.jsonPrimitive?.content
        val vm = getTopViewModel()
        when {
            typeStr == "image" -> vm?.image?.value = vm?.image?.value?.copy(isVisible = false) ?: ImageState()
            typeStr == "webview" -> vm?.webView?.value = vm?.webView?.value?.copy(isVisible = false) ?: WebViewState()
            else -> when (vm?.activeTab?.value) {
                ActiveTab.IMAGE -> vm.image.value = vm.image.value.copy(isVisible = false)
                else -> vm?.webView?.value = vm?.webView?.value?.copy(isVisible = false) ?: WebViewState()
            }
        }
        ok("""{"code":0,"message":"success"}""")
    } catch (e: Exception) {
        Log.e(TAG, "hide error", e)
        error(500, "Internal error: ${e.message}")
    }

    fun handleAdjust(body: String): NanoHTTPD.Response = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val typeStr = obj["type"]?.jsonPrimitive?.content
        val dx = obj["offsetX"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val dy = obj["offsetY"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val opacity = obj["opacity"]?.jsonPrimitive?.content?.toFloatOrNull()

        val vm = getTopViewModel()
        val isImage = typeStr == "image" || (typeStr == null && vm?.activeTab?.value == ActiveTab.IMAGE)

        if (isImage) {
            val s = vm?.image?.value ?: ImageState()
            val newState = s.copy(
                offsetX = s.offsetX + dx,
                offsetY = s.offsetY + dy,
                opacity = opacity?.coerceIn(0f, 1f) ?: s.opacity
            )
            vm?.image?.value = newState
            ok("""{"code":0,"data":{"offsetX":${newState.offsetX},"offsetY":${newState.offsetY},"opacity":${newState.opacity}}}""")
        } else {
            val s = vm?.webView?.value ?: WebViewState()
            val newState = s.copy(
                offsetX = s.offsetX + dx,
                offsetY = s.offsetY + dy,
                opacity = opacity?.coerceIn(0f, 1f) ?: s.opacity
            )
            vm?.webView?.value = newState
            ok("""{"code":0,"data":{"offsetX":${newState.offsetX},"offsetY":${newState.offsetY},"opacity":${newState.opacity}}}""")
        }
    } catch (e: Exception) {
        Log.e(TAG, "adjust error", e)
        error(500, "Internal error: ${e.message}")
    }

    // ── DOM 查询 ────────────────────────────────────────────────────────────────

    private val domQueryService = DomQueryService(timeoutMs = 3000L)

    suspend fun handleDomAll(webView: android.webkit.WebView?): NanoHTTPD.Response {
        if (webView == null) return domError(3, "webview not ready")
        val vm = getTopViewModel()
        val offsetX = vm?.webView?.value?.offsetX ?: 0
        val offsetY = vm?.webView?.value?.offsetY ?: 0
        return try {
            val nodes = domQueryService.queryAll(webView, offsetX, offsetY)
            val nodesJson = nodes.joinToString(",") { n ->
                """{"id":"${n.id}","tagName":"${n.tagName}","x":${n.x},"y":${n.y},"width":${n.width},"height":${n.height},"text":${org.json.JSONObject.quote(n.text)}}"""
            }
            ok("""{"code":0,"data":{"count":${nodes.size},"nodes":[$nodesJson]}}""")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            domError(2, "timeout")
        } catch (e: Exception) {
            Log.e(TAG, "domAll error", e)
            domError(1, "parse error")
        }
    }

    suspend fun handleDomById(webView: android.webkit.WebView?, id: String): NanoHTTPD.Response {
        if (webView == null) return domError(3, "webview not ready")
        val vm = getTopViewModel()
        val offsetX = vm?.webView?.value?.offsetX ?: 0
        val offsetY = vm?.webView?.value?.offsetY ?: 0
        return try {
            val node = domQueryService.queryById(webView, id, offsetX, offsetY)
                ?: return domError(1, "not found")
            ok("""{"code":0,"data":{"id":"${node.id}","tagName":"${node.tagName}","x":${node.x},"y":${node.y},"width":${node.width},"height":${node.height},"text":${org.json.JSONObject.quote(node.text)}}}""")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            domError(2, "timeout")
        } catch (e: Exception) {
            Log.e(TAG, "domById error", e)
            domError(1, "parse error")
        }
    }

    private fun domError(code: Int, message: String) = ok("""{"code":$code,"message":"$message"}""")

    // ── 内部工具 ───────────────────────────────────────────────────────────────

    private fun ok(json: String) = NanoHTTPD.newFixedLengthResponse(
        NanoHTTPD.Response.Status.OK, "application/json", json
    )

    private fun error(code: Int, message: String): NanoHTTPD.Response {
        val status = when {
            code == 404 -> NanoHTTPD.Response.Status.NOT_FOUND
            code >= 500 -> NanoHTTPD.Response.Status.INTERNAL_ERROR
            else        -> NanoHTTPD.Response.Status.BAD_REQUEST
        }
        return NanoHTTPD.newFixedLengthResponse(status, "application/json",
            """{"code":$code,"message":"$message"}""")
    }
}

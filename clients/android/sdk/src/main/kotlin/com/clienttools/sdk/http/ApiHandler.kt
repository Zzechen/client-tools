package com.clienttools.sdk.http

import android.content.Context
import android.util.Log
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.inspector.ActiveTab
import com.clienttools.sdk.inspector.FileInfo
import com.clienttools.sdk.inspector.ImageFileStore
import com.clienttools.sdk.inspector.ImageInfo
import com.clienttools.sdk.inspector.ImageState
import com.clienttools.sdk.inspector.InspectorFileStore
import com.clienttools.sdk.inspector.DomQueryService
import com.clienttools.sdk.inspector.WebViewState
import com.clienttools.sdk.listener.PageChangeListener
import com.clienttools.sdk.mock.MockRuleEntry
import com.clienttools.sdk.mock.MockRuleStore
import com.clienttools.sdk.proto.*
import com.clienttools.sdk.webview.WebViewRedirectEntry
import com.clienttools.sdk.webview.WebViewRedirectStore
import java.util.UUID
import com.clienttools.sdk.runtime.ViewModifier
import com.clienttools.sdk.runtime.ViewQueryService
import com.google.protobuf.ByteString
import fi.iki.elonen.NanoHTTPD

object ApiHandler {
    private var pageChangeListener: PageChangeListener? = null
    private var appContext: Context? = null

    fun init(context: Context, listener: PageChangeListener) {
        appContext = context.applicationContext
        pageChangeListener = listener
    }

    fun setPageChangeListener(listener: PageChangeListener) {
        pageChangeListener = listener
    }

    private fun ctx() = appContext!!

    private fun okResponse(bytes: ByteArray): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/x-protobuf",
            bytes.inputStream(),
            bytes.size.toLong()
        )

    private fun errResponse(code: NanoHTTPD.Response.Status, message: String): NanoHTTPD.Response {
        val meta = ProtoHelper.errMeta(code.requestStatus, message, ctx())
        val resp = SimpleResponse.newBuilder().setMeta(meta).build()
        val bytes = resp.toByteArray()
        return NanoHTTPD.newFixedLengthResponse(code, "application/x-protobuf", bytes.inputStream(), bytes.size.toLong())
    }

    fun handleGetCurrentPage(): NanoHTTPD.Response {
        return try {
            val (pageName, timestamp) = pageChangeListener?.getCurrentPage() ?: Pair("", "")
            val data = PageInfo.newBuilder().setPageName(pageName).setTimestamp(timestamp).build()
            val resp = PageResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(data).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleGetCurrentPage", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleGetAllNodes(): NanoHTTPD.Response {
        return try {
            val nodes = ViewQueryService.getAllNodes()
            val nodeList = NodeList.newBuilder().addAllNodes(nodes).build()
            val resp = NodeListResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(nodeList).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleGetAllNodes", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleGetNode(id: String): NanoHTTPD.Response {
        return try {
            val node = ViewQueryService.getNode(id)
                ?: return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "View not found")
            val resp = NodeResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(node).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleGetNode $id", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleModify(bodyBytes: ByteArray): NanoHTTPD.Response {
        return try {
            val req = ModifyViewRequest.parseFrom(bodyBytes)
            val (ok, msg) = ViewModifier.modify(req.id, req)
            if (ok) {
                val resp = ModifyResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
                okResponse(resp.toByteArray())
            } else {
                errResponse(NanoHTTPD.Response.Status.NOT_FOUND, msg)
            }
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleModify", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleClick(bodyBytes: ByteArray): NanoHTTPD.Response {
        return try {
            val req = ClickRequest.parseFrom(bodyBytes)
            val offsetX = if (req.hasCenterOffsetX()) req.centerOffsetX.value else null
            val offsetY = if (req.hasCenterOffsetY()) req.centerOffsetY.value else null
            val success = ViewModifier.click(req.id, offsetX, offsetY)
            if (!success) return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "View not found")
            val result = ClickResult.newBuilder().setId(req.id).build()
            val resp = ClickResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleClick", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleScroll(bodyBytes: ByteArray): NanoHTTPD.Response {
        return try {
            val req = ScrollRequest.parseFrom(bodyBytes)
            val success = ViewModifier.scroll(req.id, req.dx, req.dy)
            if (!success) return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "View not found")
            val result = ScrollResult.newBuilder().setId(req.id).setDx(req.dx).setDy(req.dy).build()
            val resp = ScrollResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleScroll", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleCaptureView(id: String): NanoHTTPD.Response {
        return try {
            val bytes = ViewQueryService.captureView(id)
                ?: return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "View not found or has no size")
            val resp = CaptureResponse.newBuilder()
                .setMeta(ProtoHelper.okMeta(ctx()))
                .setImagePng(ByteString.copyFrom(bytes))
                .build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleCaptureView $id", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }


    fun handlePushHtml(bodyBytes: ByteArray, fileStore: InspectorFileStore): NanoHTTPD.Response {
        return try {
            val req = PushHtmlRequest.parseFrom(bodyBytes)
            val html = req.html.toStringUtf8()
            val timestamp = req.timestamp.ifEmpty { fileStore.generateTimestamp() }
            val saved = fileStore.saveHtmlFile(req.tag, timestamp, html)
                ?: return errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "Failed to save HTML")
            ClientToolsSDK.getTop()?.viewModel?.let { vm ->
                vm.webView.value = vm.webView.value.copy(
                    currentFile = FileInfo(req.tag, timestamp, saved.fileUrl),
                    isVisible = true
                )
            }
            val result = PushHtmlResult.newBuilder()
                .setTag(req.tag).setTimestamp(timestamp).setFilePath(saved.fileUrl).build()
            val resp = PushHtmlResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handlePushHtml", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleWebviewShow(bodyBytes: ByteArray, fileStore: InspectorFileStore): NanoHTTPD.Response {
        return try {
            val req = WebviewShowRequest.parseFrom(bodyBytes)
            val fileUrl = fileStore.getFilePath(req.tag, req.timestamp)
                ?: return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "File not found")
            ClientToolsSDK.getTop()?.viewModel?.let { vm ->
                vm.webView.value = vm.webView.value.copy(
                    currentFile = FileInfo(req.tag, req.timestamp, fileUrl),
                    isVisible = true
                )
            }
            val resp = SimpleResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleWebviewShow", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleWebviewHide(): NanoHTTPD.Response {
        ClientToolsSDK.getTop()?.viewModel?.let { vm ->
            vm.webView.value = vm.webView.value.copy(isVisible = false)
        }
        val resp = SimpleResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
        return okResponse(resp.toByteArray())
    }

    fun handleWebviewAdjust(bodyBytes: ByteArray): NanoHTTPD.Response {
        return try {
            val req = WebviewAdjustRequest.parseFrom(bodyBytes)
            ClientToolsSDK.getTop()?.viewModel?.let { vm ->
                val s = vm.webView.value
                vm.webView.value = s.copy(
                    offsetX = s.offsetX + req.offsetX,
                    offsetY = s.offsetY + req.offsetY,
                    opacity = if (req.opacity > 0f) req.opacity.coerceIn(0f, 1f) else s.opacity
                )
            }
            val resp = SimpleResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleWebviewAdjust", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleWebviewFiles(fileStore: InspectorFileStore): NanoHTTPD.Response {
        return try {
            val files = fileStore.getAllFiles()
            val currentFile = ClientToolsSDK.getTop()?.viewModel?.webView?.value?.currentFile
            val items = files.map { f ->
                val isCurrent = currentFile?.tag == f.tag && currentFile?.timestamp == f.timestamp
                FileItem.newBuilder()
                    .setTag(f.tag)
                    .setTimestamp(f.timestamp)
                    .setFilePath(f.fileUrl)
                    .setIsCurrent(isCurrent)
                    .build()
            }
            val result = FileListResult.newBuilder().addAllFiles(items).build()
            val resp = FileListResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleWebviewFiles", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handlePushImage(bodyBytes: ByteArray, imageFileStore: ImageFileStore): NanoHTTPD.Response {
        return try {
            val req = PushImageRequest.parseFrom(bodyBytes)
            val bytes = req.image.toByteArray()
            val timestamp = req.timestamp.ifEmpty { imageFileStore.generateTimestamp() }
            val saved = imageFileStore.saveImage(req.tag, timestamp, bytes, req.ext.ifEmpty { "png" })
                ?: return errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "Failed to save image")
            ClientToolsSDK.getTop()?.viewModel?.let { vm ->
                vm.image.value = vm.image.value.copy(currentImage = saved, isVisible = true)
            }
            val result = PushImageResult.newBuilder()
                .setTag(req.tag).setTimestamp(timestamp)
                .setFilePath(saved.filePath).setFileSize(bytes.size.toLong())
                .build()
            val resp = PushImageResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handlePushImage", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleShowImage(bodyBytes: ByteArray, imageFileStore: ImageFileStore): NanoHTTPD.Response {
        return try {
            val req = ShowImageRequest.parseFrom(bodyBytes)
            val filePath = imageFileStore.getFilePath(req.tag, req.timestamp)
                ?: return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Image not found")
            val ext = java.io.File(filePath).extension.lowercase()
            ClientToolsSDK.getTop()?.viewModel?.let { vm ->
                vm.image.value = vm.image.value.copy(
                    currentImage = ImageInfo(req.tag, req.timestamp, filePath, ext),
                    isVisible = true
                )
            }
            val s = ClientToolsSDK.getTop()?.viewModel?.image?.value ?: ImageState()
            val result = ShowImageResult.newBuilder()
                .setTag(req.tag).setTimestamp(req.timestamp)
                .setOpacity(s.opacity).setOffsetX(s.offsetX).setOffsetY(s.offsetY)
                .build()
            val resp = ShowImageResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleShowImage", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleGetImages(imageFileStore: ImageFileStore): NanoHTTPD.Response {
        return try {
            val vmCurrentImage = ClientToolsSDK.getTop()?.viewModel?.image?.value?.currentImage
            val images = imageFileStore.getAllImages()
            val items = images.map { img ->
                val isCurrent = vmCurrentImage?.tag == img.tag && vmCurrentImage?.timestamp == img.timestamp
                val size = java.io.File(img.filePath).length()
                ImageItem.newBuilder()
                    .setTag(img.tag).setTimestamp(img.timestamp)
                    .setExt(img.ext).setSize(size).setIsCurrent(isCurrent)
                    .build()
            }
            val result = ImageListResult.newBuilder().addAllImages(items).build()
            val resp = ImageListResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleGetImages", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleInspectorHide(bodyBytes: ByteArray): NanoHTTPD.Response {
        return try {
            val req = HideRequest.parseFrom(bodyBytes)
            val vm = ClientToolsSDK.getTop()?.viewModel
            when (req.type) {
                "image"   -> vm?.image?.value = vm?.image?.value?.copy(isVisible = false) ?: ImageState()
                "webview" -> vm?.webView?.value = vm?.webView?.value?.copy(isVisible = false) ?: WebViewState()
                else -> when (vm?.activeTab?.value) {
                    ActiveTab.IMAGE -> vm.image.value = vm.image.value.copy(isVisible = false)
                    else -> vm?.webView?.value = vm?.webView?.value?.copy(isVisible = false) ?: WebViewState()
                }
            }
            val resp = SimpleResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleInspectorHide", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleInspectorAdjust(bodyBytes: ByteArray): NanoHTTPD.Response {
        return try {
            val req = InspectorAdjustRequest.parseFrom(bodyBytes)
            val vm = ClientToolsSDK.getTop()?.viewModel
            val isImage = req.type == "image" || (req.type.isEmpty() && vm?.activeTab?.value == ActiveTab.IMAGE)
            val result: InspectorAdjustResult
            if (isImage) {
                val s = vm?.image?.value ?: ImageState()
                val newState = s.copy(
                    offsetX = s.offsetX + req.offsetX,
                    offsetY = s.offsetY + req.offsetY,
                    opacity = if (req.opacity > 0f) req.opacity.coerceIn(0f, 1f) else s.opacity
                )
                vm?.image?.value = newState
                result = InspectorAdjustResult.newBuilder()
                    .setOffsetX(newState.offsetX).setOffsetY(newState.offsetY).setOpacity(newState.opacity).build()
            } else {
                val s = vm?.webView?.value ?: WebViewState()
                val newState = s.copy(
                    offsetX = s.offsetX + req.offsetX,
                    offsetY = s.offsetY + req.offsetY,
                    opacity = if (req.opacity > 0f) req.opacity.coerceIn(0f, 1f) else s.opacity
                )
                vm?.webView?.value = newState
                result = InspectorAdjustResult.newBuilder()
                    .setOffsetX(newState.offsetX).setOffsetY(newState.offsetY).setOpacity(newState.opacity).build()
            }
            val resp = InspectorAdjustResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(result).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleInspectorAdjust", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    suspend fun handleDomAll(webView: android.webkit.WebView?): NanoHTTPD.Response {
        if (webView == null) return errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "webview not ready")
        return try {
            val nodes = DomQueryService(timeoutMs = 3000L).queryAll(webView)
            val domNodes = nodes.map { n ->
                DomNode.newBuilder()
                    .setId(n.id).setTag(n.tagName).setText(n.text)
                    .setX(n.x.toFloat()).setY(n.y.toFloat()).setWidth(n.width.toFloat()).setHeight(n.height.toFloat())
                    .build()
            }
            val nodeList = DomNodeList.newBuilder().addAllNodes(domNodes).build()
            val resp = DomAllResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(nodeList).build()
            okResponse(resp.toByteArray())
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "dom query timeout")
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleDomAll", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    suspend fun handleDomById(webView: android.webkit.WebView?, id: String): NanoHTTPD.Response {
        if (webView == null) return errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "webview not ready")
        return try {
            val n = DomQueryService(timeoutMs = 3000L).queryById(webView, id)
                ?: return errResponse(NanoHTTPD.Response.Status.NOT_FOUND, "dom node not found")
            val domNode = DomNode.newBuilder()
                .setId(n.id).setTag(n.tagName).setText(n.text)
                .setX(n.x.toFloat()).setY(n.y.toFloat()).setWidth(n.width.toFloat()).setHeight(n.height.toFloat())
                .build()
            val resp = DomNodeResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).setData(domNode).build()
            okResponse(resp.toByteArray())
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "dom query timeout")
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleDomById $id", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    private fun MockRuleEntry.toProto(): MockRule = MockRule.newBuilder()
        .setId(id)
        .setUrl(url)
        .setMethod(method)
        .setDelayMs(delayMs)
        .setError(error)
        .setStatus(status)
        .putAllHeaders(headers)
        .setBody(body)
        .build()

    fun handleMockAdd(body: ByteArray): NanoHTTPD.Response {
        return try {
            val req = AddMockRuleRequest.parseFrom(body)
            val entry = MockRuleEntry(
                id = UUID.randomUUID().toString(),
                url = req.url,
                method = req.method.uppercase().ifEmpty { "GET" },
                delayMs = req.delayMs,
                error = req.error,
                status = if (req.status == 0) 200 else req.status,
                headers = req.headersMap,
                body = req.body
            )
            MockRuleStore.add(entry)
            val resp = MockRuleResponse.newBuilder()
                .setMeta(ProtoHelper.okMeta(ctx()))
                .setData(entry.toProto())
                .build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleMockAdd", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleMockList(): NanoHTTPD.Response {
        return try {
            val protoRules = MockRuleStore.list().map { it.toProto() }
            val resp = MockRuleListResponse.newBuilder()
                .setMeta(ProtoHelper.okMeta(ctx()))
                .setData(MockRuleList.newBuilder().addAllRules(protoRules).build())
                .build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleMockList", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleMockDelete(id: String): NanoHTTPD.Response {
        return try {
            MockRuleStore.delete(id)
            val resp = SimpleResponse.newBuilder()
                .setMeta(ProtoHelper.okMeta(ctx()))
                .build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleMockDelete", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleMockClear(): NanoHTTPD.Response {
        return try {
            val count = MockRuleStore.clear()
            val resp = ClearMockRulesResponse.newBuilder()
                .setMeta(ProtoHelper.okMeta(ctx()))
                .setClearedCount(count)
                .build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleMockClear", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    private fun WebViewRedirectEntry.toProto(): WebViewRedirectRule = WebViewRedirectRule.newBuilder()
        .setId(id)
        .setUrlPattern(urlPattern)
        .setTargetUrl(targetUrl)
        .build()

    fun handleWebViewRedirectAdd(body: ByteArray): NanoHTTPD.Response {
        return try {
            val req = AddWebViewRedirectRequest.parseFrom(body)
            val entry = WebViewRedirectEntry(
                id = UUID.randomUUID().toString(),
                urlPattern = req.urlPattern,
                targetUrl = req.targetUrl
            )
            WebViewRedirectStore.add(entry)
            val resp = WebViewRedirectResponse.newBuilder()
                .setMeta(ProtoHelper.okMeta(ctx()))
                .setData(entry.toProto())
                .build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleWebViewRedirectAdd", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleWebViewRedirectList(): NanoHTTPD.Response {
        return try {
            val protoRules = WebViewRedirectStore.list().map { it.toProto() }
            val resp = WebViewRedirectListResponse.newBuilder()
                .setMeta(ProtoHelper.okMeta(ctx()))
                .setData(WebViewRedirectRuleList.newBuilder().addAllRules(protoRules).build())
                .build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleWebViewRedirectList", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleWebViewRedirectDelete(id: String): NanoHTTPD.Response {
        return try {
            WebViewRedirectStore.delete(id)
            val resp = SimpleResponse.newBuilder()
                .setMeta(ProtoHelper.okMeta(ctx()))
                .build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleWebViewRedirectDelete", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleWebViewRedirectClear(): NanoHTTPD.Response {
        return try {
            val count = WebViewRedirectStore.clear()
            val resp = ClearWebViewRedirectsResponse.newBuilder()
                .setMeta(ProtoHelper.okMeta(ctx()))
                .setClearedCount(count)
                .build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleWebViewRedirectClear", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleCustomRoutes(routes: List<CustomRoute>): NanoHTTPD.Response {
        fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
        val items = routes.joinToString(",") { route ->
            val params = route.params.entries.joinToString(",") { (k, v) ->
                "\"${k.esc()}\":\"${v.esc()}\""
            }
            """{"path":"/custom/${route.path}","method":"${route.method.value}","description":"${route.description.esc()}","params":{$params}}"""
        }
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "application/json", "[$items]"
        )
    }

    suspend fun handleCustomCall(
        route: CustomRoute,
        body: String?,
        timeoutMs: Long
    ): NanoHTTPD.Response {
        val result = try {
            kotlinx.coroutines.withTimeout(timeoutMs) { route.handler(body) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            CustomResult.error("handler timeout")
        } catch (e: Exception) {
            CustomResult.error("handler error: ${e.message}")
        }
        val json = buildCustomResultJson(result)
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", json)
    }
}

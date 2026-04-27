package com.clienttools.sdk.http

import android.content.Context
import android.util.Log
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.inspector.FileInfo
import com.clienttools.sdk.inspector.InspectorFileStore
import com.clienttools.sdk.inspector.WebViewState
import com.clienttools.sdk.listener.PageChangeListener
import com.clienttools.sdk.proto.*
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
            ViewModifier.apply(req.id, req.props)
            val resp = ModifyResponse.newBuilder().setMeta(ProtoHelper.okMeta(ctx())).build()
            okResponse(resp.toByteArray())
        } catch (e: Exception) {
            Log.e("ApiHandler", "handleModify", e)
            errResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.message ?: "error")
        }
    }

    fun handleClick(bodyBytes: ByteArray): NanoHTTPD.Response {
        return try {
            val req = ClickRequest.parseFrom(bodyBytes)
            val success = ViewModifier.click(req.id)
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
}

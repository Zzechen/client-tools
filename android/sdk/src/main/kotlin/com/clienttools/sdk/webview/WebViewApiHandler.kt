package com.clienttools.sdk.webview
import fi.iki.elonen.NanoHTTPD
// Deprecated: replaced by InspectorApiHandler
object WebViewApiHandler {
    fun handlePushHtml(body: String): NanoHTTPD.Response = stub()
    fun handleShow(body: String): NanoHTTPD.Response = stub()
    fun handleHide(): NanoHTTPD.Response = stub()
    fun handleAdjust(body: String): NanoHTTPD.Response = stub()
    fun handleGetFiles(): NanoHTTPD.Response = stub()
    private fun stub() = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "application/json", "{}")
}

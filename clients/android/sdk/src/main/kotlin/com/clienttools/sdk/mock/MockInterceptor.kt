package com.clienttools.sdk.mock

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class MockInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val rule = MockRuleStore.findMatch(req.url.toString(), req.method)
            ?: return chain.proceed(req)

        if (rule.delayMs > 0) Thread.sleep(rule.delayMs)

        if (rule.error.isNotEmpty()) {
            throw when (rule.error) {
                "timeout"            -> SocketTimeoutException("mock timeout")
                "connection_refused" -> ConnectException("mock connection refused")
                else                 -> IOException("mock error: ${rule.error}")
            }
        }

        val contentType = rule.headers["Content-Type"]?.toMediaTypeOrNull()
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(rule.status)
            .message("Mock")
            .apply { rule.headers.forEach { (k, v) -> header(k, v) } }
            .body(rule.body.toResponseBody(contentType))
            .build()
    }
}

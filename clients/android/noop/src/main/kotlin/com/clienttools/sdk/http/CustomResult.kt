package com.clienttools.sdk.http

data class CustomResult(val data: String?, val error: String?, val code: Int = 200) {
    companion object {
        fun ok(data: String) = CustomResult(data, null)
        fun error(message: String, code: Int = 400) = CustomResult(null, message, code)
    }
}

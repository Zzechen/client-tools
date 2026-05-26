package com.clienttools.sdk.http

/**
 * HTTP 方法枚举。value 字段防止混淆后枚举名变化影响路由匹配。
 */
enum class HttpMethod(val value: String) {
    GET("GET"),
    POST("POST")
}

/**
 * 自定义路由处理结果。构造函数私有，app 只能通过工厂方法构建。
 */
class CustomResult private constructor(
    internal val code: Int,
    internal val message: String,
    internal val data: String?
) {
    companion object {
        fun ok(data: String = "") = CustomResult(0, "ok", data)
        fun error(message: String, code: Int = -1) = CustomResult(code, message, null)
    }
}

/**
 * app 注册的自定义路由。
 * @param path        相对路径，不含 /custom/ 前缀，如 "user/profile"
 * @param method      HTTP 方法
 * @param description 路由用途描述，供 AI 理解
 * @param params      参数名 → 说明（body 字段描述）
 * @param handler     异步处理器，body 为原始请求体字符串
 */
data class CustomRoute(
    val path: String,
    val method: HttpMethod,
    val description: String,
    val params: Map<String, String> = emptyMap(),
    val handler: suspend (body: String?) -> CustomResult
)

/**
 * 将 CustomResult 序列化为标准 JSON 字符串。
 * data 字段始终作为 JSON string 类型（含 null）。
 */
internal fun buildCustomResultJson(result: CustomResult): String {
    fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
    val msg = result.message.esc()
    val dataVal = if (result.data != null) "\"${result.data.esc()}\"" else "null"
    return """{"code":${result.code},"message":"$msg","data":$dataVal}"""
}

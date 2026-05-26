# Custom Routes 设计文档

**日期：** 2026-05-26  
**状态：** 已确认，待实现

---

## 背景

现有 MCP 只能调用 SDK 内置接口，app 层无法扩展自定义业务接口供 AI 调用。本功能允许 app 在 SDK 初始化时注册自定义 HTTP 路由，MCP 通过通用工具发现并调用这些路由。

---

## 整体架构

```
App 初始化时
  └─ ClientToolsSDK.init(context, customRoutes = listOf(...))

SDK HTTP Server（NanoHTTPD / iOS HTTP Server）
  ├─ GET /custom/routes     → 返回路由元数据 JSON（供 MCP 发现）
  └─ {METHOD} /custom/{path} → runBlocking { withTimeout { handler(body) } } → 返回结果 JSON

MCP Server
  ├─ list_custom_routes     → GET /custom/routes → 返回路由列表给 AI
  └─ custom_call            → 转发 method/path/body → 返回响应 JSON 字符串
```

**关键约定：**
- 自定义路由统一挂载在 `/custom/` 前缀，避免与内置路由冲突
- `/custom/routes` 返回 `application/json`，其余自定义路由返回 `text/plain`（内容为 JSON 字符串）
- handler 是异步函数，SDK 用 `runBlocking + withTimeout` 执行
- 响应结构统一封装，app 只能通过工厂方法构建结果

---

## Android SDK

### HttpMethod 枚举

```kotlin
enum class HttpMethod(val value: String) {
    GET("GET"),
    POST("POST")
}
```

`value` 字段防止混淆后枚举名变化影响路由匹配。

### CustomResult

```kotlin
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
```

构造函数私有，app 只能通过 `ok()` / `error()` 构建，确保响应结构一致。

### CustomRoute

```kotlin
data class CustomRoute(
    val path: String,                              // 相对路径，如 "user/profile"（不含 /custom/ 前缀）
    val method: HttpMethod,                        // GET 或 POST
    val description: String,                       // 路由用途描述，供 AI 理解
    val params: Map<String, String> = emptyMap(),  // 参数名 → 说明（body 字段描述）
    val handler: suspend (body: String?) -> CustomResult
)
```

### SDK 初始化

```kotlin
ClientToolsSDK.init(
    context = this,
    customRoutes = listOf(
        CustomRoute(
            path = "user/profile",
            method = HttpMethod.GET,
            description = "获取当前登录用户的基本信息",
            handler = { _ -> CustomResult.ok(userRepo.getProfile().toJson()) }
        ),
        CustomRoute(
            path = "order/submit",
            method = HttpMethod.POST,
            description = "提交订单",
            params = mapOf("orderId" to "订单ID", "quantity" to "数量"),
            handler = { body ->
                val req = Json.decodeFromString<SubmitOrderReq>(body!!)
                CustomResult.ok(orderService.submit(req).toJson())
            }
        )
    ),
    customHandlerTimeoutMs = 4500L   // 默认值，略小于 MCP HTTP 超时
)
```

### HTTP Server 路由扩展

```
// HttpServer.serve() 新增分支（在 else 404 之前）：

method == Method.GET && uri == "/custom/routes" ->
    ApiHandler.handleCustomRoutes(customRoutes)

uri.startsWith("/custom/") -> {
    val path = uri.removePrefix("/custom/")
    val route = customRoutes.find {
        it.path == path && it.method.value == method.name
    }
    if (route != null)
        runBlocking { ApiHandler.handleCustomCall(route, readBody(session), customHandlerTimeoutMs) }
    else
        404 Not Found
}
```

### ApiHandler 处理逻辑

**`handleCustomRoutes`：**
```
返回 Content-Type: application/json
Body: [{"path":"/custom/user/profile","method":"GET","description":"...","params":{}}]
```

**`handleCustomCall`：**
```kotlin
suspend fun handleCustomCall(route: CustomRoute, body: String?, timeoutMs: Long): Response {
    val result = try {
        withTimeout(timeoutMs) { route.handler(body) }
    } catch (e: TimeoutCancellationException) {
        CustomResult.error("handler timeout")
    } catch (e: Exception) {
        CustomResult.error("handler error: ${e.message}")
    }
    val json = buildResultJson(result)  // {"code":0,"message":"ok","data":"..."}
    return newFixedLengthResponse(Status.OK, "text/plain", json)
}
```

HTTP 状态码始终返回 200，错误语义编码在 `code` 字段（与现有 protobuf `ResponseMeta.code` 风格一致）。

---

## iOS SDK

### HttpMethod 枚举

```swift
@objc public enum HttpMethod: Int {
    case get
    case post

    var value: String {
        switch self {
        case .get:  return "GET"
        case .post: return "POST"
        }
    }
}
```

### CustomResult

```swift
public final class CustomResult {
    internal let code: Int
    internal let message: String
    internal let data: String?

    private init(code: Int, message: String, data: String?) {
        self.code = code; self.message = message; self.data = data
    }

    public static func ok(_ data: String = "") -> CustomResult {
        CustomResult(code: 0, message: "ok", data: data)
    }
    public static func error(_ message: String, code: Int = -1) -> CustomResult {
        CustomResult(code: code, message: message, data: nil)
    }
}
```

### CustomRoute

```swift
public struct CustomRoute {
    public let path: String
    public let method: HttpMethod
    public let description: String
    public let params: [String: String]
    public let handler: (String?) async -> CustomResult

    public init(
        path: String,
        method: HttpMethod,
        description: String,
        params: [String: String] = [:],
        handler: @escaping (String?) async -> CustomResult
    )
}
```

### SDK 初始化

```swift
ClientToolsSDK.shared.start(
    customRoutes: [
        CustomRoute(
            path: "user/profile",
            method: .get,
            description: "获取当前登录用户的基本信息",
            handler: { _ in
                let json = await userRepo.getProfile().toJson()
                return .ok(json)
            }
        )
    ],
    customHandlerTimeoutMs: 4500
)
```

### HTTP Server 路由扩展

iOS HTTP Server 新增对称路由分支，handler 用 `Task { await handler(body) }` + `.value` 获取结果，超时用 `Task.sleep` + `withTaskGroup` 实现，与 Android `withTimeout` 语义对齐。

---

## MCP 工具

### 新增函数（sdk-client.ts）

```typescript
export async function sdkGetText(path: string): Promise<string>
export async function sdkPostText(path: string, body: string): Promise<string>
```

不做 protobuf 解码，直接返回 `res.text()`，超时由 `CLIENT_TOOLS_CUSTOM_TIMEOUT_MS` 环境变量控制（默认 5000ms）。

### list_custom_routes

```
工具名：list_custom_routes
描述：列出 app 层注册的所有自定义路由，包含路径、方法、描述和参数说明
参数：无
实现：sdkGetText("/custom/routes")
返回：路由数组 JSON 字符串
```

**返回示例：**
```json
[
  {
    "path": "/custom/user/profile",
    "method": "GET",
    "description": "获取当前登录用户的基本信息",
    "params": {}
  },
  {
    "path": "/custom/order/submit",
    "method": "POST",
    "description": "提交订单",
    "params": {
      "orderId": "订单ID",
      "quantity": "数量"
    }
  }
]
```

### custom_call

```
工具名：custom_call
描述：调用 app 层注册的自定义路由
参数：
  path   string  必填  路由路径，如 "user/profile"（不含 /custom/ 前缀）
  method string  必填  "GET" 或 "POST"
  body   string  可选  请求体字符串（POST 时使用，通常为 JSON）
实现：method=="GET" ? sdkGetText("/custom/{path}") : sdkPostText("/custom/{path}", body)
返回：{"code":0,"message":"ok","data":"..."} 或 {"code":-1,"message":"...","data":null}
```

---

## 响应结构

```json
// 成功
{ "code": 0, "message": "ok", "data": "业务数据字符串" }

// 业务错误（app 主动返回 CustomResult.error(...)）
{ "code": -1, "message": "订单不存在", "data": null }

// 系统异常（handler 抛异常，SDK 捕获）
{ "code": -1, "message": "handler error: ...", "data": null }

// 超时（SDK withTimeout 触发）
{ "code": -1, "message": "handler timeout", "data": null }
```

---

## 超时配置

| 位置 | 参数 | 默认值 | 说明 |
|------|------|--------|------|
| Android/iOS SDK init | `customHandlerTimeoutMs` | 4500ms | handler 执行超时 |
| MCP 环境变量 | `CLIENT_TOOLS_CUSTOM_TIMEOUT_MS` | 5000ms | MCP HTTP 请求超时 |

约定：`customHandlerTimeoutMs` < `CLIENT_TOOLS_CUSTOM_TIMEOUT_MS`，确保 handler 超时先于 HTTP 超时触发，MCP 能收到明确错误而非连接超时。

---

## 文档同步

实现完成后需同步更新：
- `docs/mcp-tools.md`：新增 `list_custom_routes`、`custom_call` 工具说明
- `docs/sdk-http-api.md`：新增 `GET /custom/routes`、`{METHOD} /custom/{path}` 接口说明

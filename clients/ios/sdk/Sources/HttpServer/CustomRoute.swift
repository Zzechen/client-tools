import Foundation

/// HTTP 方法枚举。value 属性防止混淆后枚举名变化影响路由匹配。
public enum HttpMethod: Int {
    case get
    case post

    var value: String {
        switch self {
        case .get:  return "GET"
        case .post: return "POST"
        }
    }
}

/// 自定义路由处理结果。构造函数私有，app 只能通过工厂方法构建。
public final class CustomResult {
    let code: Int
    let message: String
    let data: String?

    private init(code: Int, message: String, data: String?) {
        self.code = code
        self.message = message
        self.data = data
    }

    public static func ok(_ data: String = "") -> CustomResult {
        CustomResult(code: 0, message: "ok", data: data)
    }

    public static func error(_ message: String, code: Int = -1) -> CustomResult {
        CustomResult(code: code, message: message, data: nil)
    }

    /// 序列化为标准 JSON 字符串，data 始终作为 JSON string 类型（含 null）。
    func toJson() -> String {
        func esc(_ s: String) -> String {
            s.replacingOccurrences(of: "\\", with: "\\\\")
             .replacingOccurrences(of: "\"", with: "\\\"")
        }
        let msg = esc(message)
        if let d = data {
            return "{\"code\":\(code),\"message\":\"\(msg)\",\"data\":\"\(esc(d))\"}"
        } else {
            return "{\"code\":\(code),\"message\":\"\(msg)\",\"data\":null}"
        }
    }
}

/// app 注册的自定义路由。
/// - path: 相对路径，不含 /custom/ 前缀，如 "user/profile"
/// - method: HTTP 方法
/// - description: 路由用途描述，供 AI 理解
/// - params: 参数名 → 说明（body 字段描述）
/// - handler: 异步处理器，可抛异常，SDK 统一捕获并包装为 error 响应
public struct CustomRoute {
    public let path: String
    public let method: HttpMethod
    public let description: String
    public let params: [String: String]
    public let handler: (String?) async throws -> CustomResult

    public init(
        path: String,
        method: HttpMethod,
        description: String,
        params: [String: String] = [:],
        handler: @escaping (String?) async throws -> CustomResult
    ) {
        self.path = path
        self.method = method
        self.description = description
        self.params = params
        self.handler = handler
    }
}

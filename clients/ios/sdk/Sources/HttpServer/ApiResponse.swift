import Foundation

public struct ApiResponse<T: Codable>: Codable {
    public let code: Int
    public let message: String
    public let sdkVersion: Int
    public let data: T?

    public init(code: Int, message: String, data: T?) {
        self.code = code
        self.message = message
        self.sdkVersion = 1
        self.data = data
    }

    public static func success(_ data: T) -> ApiResponse<T> {
        return ApiResponse(code: 0, message: "success", data: data)
    }

    public static func error(_ message: String) -> ApiResponse<T> {
        return ApiResponse(code: -1, message: message, data: nil)
    }
}

import Foundation

public class ClientToolsSDK {
    public static let shared = ClientToolsSDK()
    private init() {}

    public func start(port: Int = 8080, customRoutes: [Any] = [], customHandlerTimeoutMs: Int = 4500) {}
    public func resolveRedirect(_ url: String) -> String { return url }
    public func getCurrentPage() -> (pageName: String, timestamp: String) { return ("", "") }
    public func recordPageChange(_ pageName: String) {}
}

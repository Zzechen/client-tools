import UIKit

public class ClientToolsSDK {

    public static let shared = ClientToolsSDK()

    private var httpServer: HttpServer?
    private var pageTracker: PageTracker?
    private var _overlayManager: OverlayManager?
    private var isRunning = false

    private init() {}

    /// 启动 SDK（DEBUG 模式下生效，生产环境自动跳过）
    public func start(port: Int = 8080) {
        #if DEBUG
        guard !isRunning else { return }
        isRunning = true

        startHttpServer(port: port)
        startPageTracking()
        startOverlayManager()
        print("[ClientToolsSDK] started on port \(port)")
        #endif
    }

    private func startHttpServer(port: Int) {
        httpServer = HttpServer(port: port)
        httpServer?.start()
    }

    private func startPageTracking() {
        pageTracker = PageTracker()
        pageTracker?.start()
    }

    private func startOverlayManager() {
        _overlayManager = OverlayManager()
    }

    public func getCurrentPage() -> (pageName: String, timestamp: String) {
        return pageTracker?.getCurrentPage() ?? ("", "")
    }

    public func overlayManager() -> OverlayManager? {
        return _overlayManager
    }

    public func recordPageChange(_ pageName: String) {
        pageTracker?.recordPageChange(pageName)
    }
}

import UIKit
import WebKit

public class OverlayManager {

    public static let overlayTag = 998  // 全局唯一的 tag，用于遍历时排除

    private var webView: WKWebView?
    private var overlayWindow: UIWindow?
    public let fileStore = HtmlFileStore()

    public init() {}

    public func show(url: String, opacity: Float) {
        hide()

        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
            return
        }

        overlayWindow = UIWindow(windowScene: windowScene)
        overlayWindow?.windowLevel = .alert - 1

        let configuration = WKWebViewConfiguration()
        webView = WKWebView(frame: UIScreen.main.bounds, configuration: configuration)
        webView?.alpha = CGFloat(opacity)
        webView?.load(URLRequest(url: URL(string: url)!))

        let vc = UIViewController()
        vc.view.addSubview(webView!)
        overlayWindow?.rootViewController = vc
        overlayWindow?.makeKeyAndVisible()
    }

    public func hide() {
        overlayWindow?.isHidden = true
        overlayWindow = nil
        webView = nil
    }

    public func setOpacity(_ opacity: Float) {
        webView?.alpha = CGFloat(opacity)
    }
}

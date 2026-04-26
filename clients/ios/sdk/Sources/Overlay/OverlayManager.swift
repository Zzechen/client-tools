import UIKit
import WebKit

public class OverlayManager {

    public static let overlayTag = 998

    private var webView: WKWebView?
    private var overlayWindow: UIWindow?
    private var currentOpacity: Float = 0.5
    private var offsetX: CGFloat = 0
    private var offsetY: CGFloat = 0
    public let fileStore = HtmlFileStore()

    public init() {}

    public func showFile(at fileURL: URL, opacity: Float) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.ensureWebView(opacity: opacity)
            self.webView?.loadFileURL(fileURL, allowingReadAccessTo: fileURL.deletingLastPathComponent())
        }
    }

    public func hide() {
        DispatchQueue.main.async { [weak self] in
            self?.overlayWindow?.isHidden = true
            self?.overlayWindow = nil
            self?.webView = nil
        }
    }

    public func adjust(offsetX: Float?, offsetY: Float?, opacity: Float?) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if let ox = offsetX { self.offsetX = CGFloat(ox) }
            if let oy = offsetY { self.offsetY = CGFloat(oy) }
            if let op = opacity {
                self.currentOpacity = op
                self.webView?.alpha = CGFloat(op)
            }
            self.updateWebViewFrame()
        }
    }

    private func ensureWebView(opacity: Float) {
        if webView == nil {
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
            overlayWindow = UIWindow(windowScene: windowScene)
            overlayWindow?.windowLevel = .alert - 1
            overlayWindow?.tag = OverlayManager.overlayTag

            let wv = WKWebView(frame: .zero)
            wv.isOpaque = false
            wv.backgroundColor = .clear
            wv.scrollView.isScrollEnabled = false
            webView = wv

            let vc = UIViewController()
            vc.view.backgroundColor = .clear
            vc.view.addSubview(wv)
            overlayWindow?.rootViewController = vc
            overlayWindow?.makeKeyAndVisible()
        }
        currentOpacity = opacity
        webView?.alpha = CGFloat(opacity)
        updateWebViewFrame()
    }

    private func updateWebViewFrame() {
        guard let screen = overlayWindow?.screen else { return }
        webView?.frame = CGRect(
            x: offsetX,
            y: offsetY,
            width: screen.bounds.width,
            height: screen.bounds.height
        )
    }
}

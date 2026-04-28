import UIKit
import WebKit

/// 触摸透传：只消费命中可见子视图的触摸，其余透传给下层窗口
private class PassthroughWindow: UIWindow {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let hit = super.hitTest(point, with: event)
        return hit == rootViewController?.view ? nil : hit
    }
}

public class OverlayManager {

    public static let overlayTag = 998

    private var viewModel: InspectorViewModel
    private var overlayWindow: UIWindow?
    private var webView: WKWebView?
    private var imageView: UIImageView?
    private var inspectorPanel: InspectorPanel?

    public let fileStore = HtmlFileStore()

    public init(viewModel: InspectorViewModel) {
        self.viewModel = viewModel
        setupObservers()
        // 启动时主动创建 UIWindow，确保 InspectorPanel 悬浮按钮立即可见
        DispatchQueue.main.async { self.ensureWindow() }
    }

    private func setupObservers() {
        viewModel.addWebViewStateObserver { [weak self] state in
            DispatchQueue.main.async { self?.applyWebViewState(state) }
        }
        viewModel.addImageStateObserver { [weak self] state in
            DispatchQueue.main.async { self?.applyImageState(state) }
        }
    }

    // MARK: - 公开方法（向后兼容）

    public func showFile(at fileURL: URL, opacity: Float) {
        let info = FileInfo(tag: "", timestamp: "", filePath: fileURL.path)
        viewModel.webViewState = WebViewState(
            currentFile: info,
            isVisible: true,
            offsetX: viewModel.webViewState.offsetX,
            offsetY: viewModel.webViewState.offsetY,
            opacity: opacity
        )
    }

    public func hide() {
        viewModel.webViewState.isVisible = false
    }

    public func adjust(offsetX: Float?, offsetY: Float?, opacity: Float?) {
        var s = viewModel.webViewState
        if let ox = offsetX { s.offsetX = ox }
        if let oy = offsetY { s.offsetY = oy }
        if let op = opacity { s.opacity = min(max(op, 0), 1) }
        viewModel.webViewState = s
    }

    // MARK: - 状态应用

    private func applyWebViewState(_ state: WebViewState) {
        ensureWindow()
        guard let wv = webView else { return }
        wv.isHidden = !state.isVisible
        wv.alpha = CGFloat(state.opacity)
        updateFrame(of: wv, offsetX: state.offsetX, offsetY: state.offsetY)
        if state.isVisible, let filePath = state.currentFile?.filePath, !filePath.isEmpty {
            let fileURL = URL(fileURLWithPath: filePath)
            wv.loadFileURL(fileURL, allowingReadAccessTo: fileURL.deletingLastPathComponent())
        }
        hideWindowIfBothHidden()
    }

    private func applyImageState(_ state: ImageState) {
        ensureWindow()
        guard let iv = imageView else { return }
        iv.isHidden = !state.isVisible
        iv.alpha = CGFloat(state.opacity)
        updateFrame(of: iv, offsetX: state.offsetX, offsetY: state.offsetY)
        if state.isVisible, let filePath = state.currentImage?.filePath {
            iv.image = UIImage(contentsOfFile: filePath)
        }
        hideWindowIfBothHidden()
    }

    private func hideWindowIfBothHidden() {
        // InspectorPanel 的悬浮按钮需要始终可见，不随覆盖层隐藏
        overlayWindow?.isHidden = false
    }

    // MARK: - UIWindow 管理

    private func ensureWindow() {
        guard overlayWindow == nil else { return }
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }

        let window = PassthroughWindow(windowScene: windowScene)
        window.windowLevel = .alert - 1
        window.tag = OverlayManager.overlayTag

        let vc = UIViewController()
        vc.view.backgroundColor = .clear
        window.rootViewController = vc
        window.isHidden = false
        overlayWindow = window

        let wv = WKWebView(frame: .zero)
        wv.isOpaque = false
        wv.backgroundColor = .clear
        wv.scrollView.isScrollEnabled = false
        wv.isHidden = true
        vc.view.addSubview(wv)
        webView = wv

        let iv = UIImageView(frame: .zero)
        iv.contentMode = .scaleAspectFit
        iv.isHidden = true
        vc.view.addSubview(iv)
        imageView = iv

        let panel = InspectorPanel(
            viewModel: viewModel,
            imageFileStore: ClientToolsSDK.shared.imageFileStore,
            htmlFileStore: fileStore,
            port: ClientToolsSDK.shared.port
        )
        panel.attach(to: vc.view)
        inspectorPanel = panel
    }

    private func updateFrame(of view: UIView, offsetX: Float, offsetY: Float) {
        guard let screen = overlayWindow?.screen else { return }
        view.frame = CGRect(
            x: CGFloat(offsetX),
            y: CGFloat(offsetY),
            width: screen.bounds.width,
            height: screen.bounds.height
        )
    }
}

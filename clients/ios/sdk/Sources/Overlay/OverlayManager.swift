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
    private var loadedWebFilePath: String?

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

    public var currentWebViewState: WebViewState { viewModel.webViewState }

    public func applyState(_ state: WebViewState) {
        viewModel.webViewState = state
    }

    public func adjust(offsetX: Float?, offsetY: Float?, opacity: Float?) {
        var s = viewModel.webViewState
        if let ox = offsetX { s.offsetX = ox }
        if let oy = offsetY { s.offsetY = oy }
        if let op = opacity { s.opacity = min(max(op, 0), 1) }
        viewModel.webViewState = s
    }

    // MARK: - DOM 查询

    /// 返回 WebView 中所有有 id 的 DOM 节点，坐标已换算为屏幕绝对坐标（pt）
    func queryDomAll(completion: @escaping ([Clienttools_DomNode]) -> Void) {
        queryDomRaw(js: domAllJS()) { json in
            completion(Self.parseDomNodes(from: json))
        }
    }

    /// 返回指定 id 的 DOM 节点，坐标已换算为屏幕绝对坐标（pt）
    func queryDomById(_ id: String, completion: @escaping (Clienttools_DomNode?) -> Void) {
        let escaped = id.replacingOccurrences(of: "\"", with: "\\\"")
        queryDomRaw(js: domByIdJS(escaped)) { json in
            completion(Self.parseDomNode(from: json))
        }
    }

    private static func parseDomNodes(from json: String) -> [Clienttools_DomNode] {
        guard let data = json.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return arr.compactMap { parseDomNodeDict($0) }
    }

    private static func parseDomNode(from json: String) -> Clienttools_DomNode? {
        guard let data = json.data(using: .utf8),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return parseDomNodeDict(dict)
    }

    private static func parseDomNodeDict(_ d: [String: Any]) -> Clienttools_DomNode? {
        guard let id = d["id"] as? String else { return nil }
        var node = Clienttools_DomNode()
        node.id     = id
        node.tag    = (d["tag"] as? String) ?? ""
        node.text   = (d["text"] as? String) ?? ""
        node.x      = Float((d["x"] as? NSNumber)?.doubleValue ?? 0)
        node.y      = Float((d["y"] as? NSNumber)?.doubleValue ?? 0)
        node.width  = Float((d["width"]  as? NSNumber)?.doubleValue ?? 0)
        node.height = Float((d["height"] as? NSNumber)?.doubleValue ?? 0)
        return node
    }

    private func queryDomRaw(js: String, completion: @escaping (String) -> Void) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self, let wv = self.webView else {
                completion("{\"error\":\"WebView not ready\"}")
                return
            }
            // wv.convert(.zero, to:nil) 返回 WebView 左上角的屏幕绝对坐标（含 transform 偏移）
            // getBoundingClientRect 是 viewport 内坐标，两者相加即元素的屏幕坐标
            let screenOrigin = wv.convert(CGPoint.zero, to: nil)
            let originX = screenOrigin.x
            // wv.safeAreaInsets.top 补偿状态栏高度：WebView 内容实际从 safeArea 下方开始渲染，
            // 但 wv.convert(.zero, to:nil) 返回的是 frame 原点（不含 safeArea 偏移）
            let originY = screenOrigin.y
            print("[CT-DEBUG] DOM origin: wv.frame=\(wv.frame) safeAreaInsets.top=\(wv.safeAreaInsets.top) wv.convert(.zero,nil)=\(screenOrigin) originY=\(originY)")
            let injected = "(\(js))(\(originX), \(originY))"
            wv.evaluateJavaScript(injected) { result, error in
                if let json = result as? String {
                    completion(json)
                } else {
                    completion("{\"error\":\"\(error?.localizedDescription ?? "unknown")\"}")
                }
            }
        }
    }

    private func domAllJS() -> String {
        return """
        function(originX, originY) {
            var nodes = [];
            document.querySelectorAll('[id]').forEach(function(el) {
                var r = el.getBoundingClientRect();
                if (r.width === 0 && r.height === 0) return;
                nodes.push({
                    id: el.id,
                    tag: el.tagName.toLowerCase(),
                    text: (el.innerText || '').trim().substring(0, 100),
                    x: Math.round((originX + r.left) * 100) / 100,
                    y: Math.round((originY + r.top) * 100) / 100,
                    width: Math.round(r.width * 100) / 100,
                    height: Math.round(r.height * 100) / 100
                });
            });
            return JSON.stringify(nodes);
        }
        """
    }

    private func domByIdJS(_ id: String) -> String {
        return """
        function(originX, originY) {
            var el = document.getElementById("\(id)");
            if (!el) return JSON.stringify(null);
            var r = el.getBoundingClientRect();
            return JSON.stringify({
                id: el.id,
                tag: el.tagName.toLowerCase(),
                text: (el.innerText || '').trim().substring(0, 100),
                x: Math.round((originX + r.left) * 100) / 100,
                y: Math.round((originY + r.top) * 100) / 100,
                width: Math.round(r.width * 100) / 100,
                height: Math.round(r.height * 100) / 100
            });
        }
        """
    }

    // MARK: - 状态应用

    private func applyWebViewState(_ state: WebViewState) {
        ensureWindow()
        guard let wv = webView else { return }
        wv.isHidden = !state.isVisible
        wv.alpha = CGFloat(state.opacity)
        wv.transform = CGAffineTransform(translationX: CGFloat(state.offsetX), y: CGFloat(state.offsetY))
        let filePath = state.currentFile?.filePath ?? ""
        if state.isVisible && !filePath.isEmpty && filePath != loadedWebFilePath {
            loadedWebFilePath = filePath
            wv.stopLoading()
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
        iv.transform = CGAffineTransform(translationX: CGFloat(state.offsetX), y: CGFloat(state.offsetY))
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

        let wv = WKWebView(frame: vc.view.bounds)
        wv.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        wv.isOpaque = false
        wv.backgroundColor = .clear
        wv.scrollView.isScrollEnabled = false
        wv.scrollView.contentInsetAdjustmentBehavior = .never
        wv.isHidden = true
        vc.view.addSubview(wv)
        webView = wv

        let iv = UIImageView(frame: vc.view.bounds)
        iv.autoresizingMask = [.flexibleWidth, .flexibleHeight]
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

}

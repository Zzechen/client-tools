import Foundation

public struct FileInfo {
    public let tag: String
    public let timestamp: String
    public let filePath: String

    public init(tag: String, timestamp: String, filePath: String) {
        self.tag = tag; self.timestamp = timestamp; self.filePath = filePath
    }
}

public struct ImageInfo {
    public let tag: String
    public let timestamp: String
    public let filePath: String
    public let ext: String

    public init(tag: String, timestamp: String, filePath: String, ext: String) {
        self.tag = tag; self.timestamp = timestamp; self.filePath = filePath; self.ext = ext
    }
}

public struct WebViewState {
    public var currentFile: FileInfo? = nil
    public var isVisible: Bool = false
    public var offsetX: Float = 0
    public var offsetY: Float = 0
    public var opacity: Float = 0.5

    public init(currentFile: FileInfo? = nil, isVisible: Bool = false, offsetX: Float = 0, offsetY: Float = 0, opacity: Float = 0.5) {
        self.currentFile = currentFile; self.isVisible = isVisible
        self.offsetX = offsetX; self.offsetY = offsetY; self.opacity = opacity
    }
}

public struct ImageState {
    public var currentImage: ImageInfo? = nil
    public var isVisible: Bool = false
    public var offsetX: Float = 0
    public var offsetY: Float = 0
    public var opacity: Float = 0.5

    public init(currentImage: ImageInfo? = nil, isVisible: Bool = false, offsetX: Float = 0, offsetY: Float = 0, opacity: Float = 0.5) {
        self.currentImage = currentImage; self.isVisible = isVisible
        self.offsetX = offsetX; self.offsetY = offsetY; self.opacity = opacity
    }
}

public enum ActiveTab { case webview, image, status }

public class InspectorViewModel {
    public var webViewState: WebViewState = WebViewState() { didSet { webViewStateObservers.forEach { $0(webViewState) } } }
    public var imageState: ImageState = ImageState()       { didSet { imageStateObservers.forEach { $0(imageState) } } }
    public var activeTab: ActiveTab = .webview             { didSet { activeTabObservers.forEach { $0(activeTab) } } }

    private var webViewStateObservers: [(WebViewState) -> Void] = []
    private var imageStateObservers:   [(ImageState) -> Void]   = []
    private var activeTabObservers:    [(ActiveTab) -> Void]    = []

    public func addWebViewStateObserver(_ cb: @escaping (WebViewState) -> Void) { webViewStateObservers.append(cb) }
    public func addImageStateObserver(_ cb: @escaping (ImageState) -> Void)     { imageStateObservers.append(cb) }
    public func addActiveTabObserver(_ cb: @escaping (ActiveTab) -> Void)       { activeTabObservers.append(cb) }

    // 向后兼容单回调写法（set 会替换第一个订阅者）
    public var onWebViewStateChanged: ((WebViewState) -> Void)? {
        get { webViewStateObservers.first }
        set { if webViewStateObservers.isEmpty { webViewStateObservers.append(newValue!) } else if let v = newValue { webViewStateObservers[0] = v } else { webViewStateObservers.removeAll() } }
    }
    public var onImageStateChanged: ((ImageState) -> Void)? {
        get { imageStateObservers.first }
        set { if imageStateObservers.isEmpty { if let v = newValue { imageStateObservers.append(v) } } else if let v = newValue { imageStateObservers[0] = v } else { imageStateObservers.removeAll() } }
    }
    public var onActiveTabChanged: ((ActiveTab) -> Void)? {
        get { activeTabObservers.first }
        set { if activeTabObservers.isEmpty { if let v = newValue { activeTabObservers.append(v) } } else if let v = newValue { activeTabObservers[0] = v } else { activeTabObservers.removeAll() } }
    }

    public init() {}
}

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
    public var webViewState: WebViewState = WebViewState() { didSet { onWebViewStateChanged?(webViewState) } }
    public var imageState: ImageState = ImageState()       { didSet { onImageStateChanged?(imageState) } }
    public var activeTab: ActiveTab = .webview             { didSet { onActiveTabChanged?(activeTab) } }

    public var onWebViewStateChanged: ((WebViewState) -> Void)?
    public var onImageStateChanged: ((ImageState) -> Void)?
    public var onActiveTabChanged: ((ActiveTab) -> Void)?

    public init() {}
}

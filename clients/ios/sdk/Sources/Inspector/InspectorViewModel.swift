import Foundation

struct FileInfo {
    let tag: String
    let timestamp: String
    let filePath: String
}

struct ImageInfo {
    let tag: String
    let timestamp: String
    let filePath: String
    let ext: String
}

struct WebViewState {
    var currentFile: FileInfo? = nil
    var isVisible: Bool = false
    var offsetX: Float = 0
    var offsetY: Float = 0
    var opacity: Float = 0.5
}

struct ImageState {
    var currentImage: ImageInfo? = nil
    var isVisible: Bool = false
    var offsetX: Float = 0
    var offsetY: Float = 0
    var opacity: Float = 0.5
}

enum ActiveTab { case webview, image }

class InspectorViewModel {
    var webViewState: WebViewState = WebViewState() { didSet { onWebViewStateChanged?(webViewState) } }
    var imageState: ImageState = ImageState()       { didSet { onImageStateChanged?(imageState) } }
    var activeTab: ActiveTab = .webview

    var onWebViewStateChanged: ((WebViewState) -> Void)?
    var onImageStateChanged: ((ImageState) -> Void)?
}

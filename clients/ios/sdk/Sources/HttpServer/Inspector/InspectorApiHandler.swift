import Foundation

class InspectorApiHandler {

    private let viewModel: InspectorViewModel
    private let imageFileStore: ImageFileStore

    init(viewModel: InspectorViewModel, imageFileStore: ImageFileStore) {
        self.viewModel = viewModel
        self.imageFileStore = imageFileStore
    }

    // MARK: - push-image

    func handlePushImage(_ body: Data) -> (Int, String) {
        guard let obj = try? JSONSerialization.jsonObject(with: body) as? [String: Any],
              let tag = obj["tag"] as? String,
              let imageBase64 = obj["image"] as? String else {
            return (400, #"{"code":400,"message":"Missing tag or image"}"#)
        }
        let ext = (obj["ext"] as? String)?.lowercased() ?? "png"
        let timestamp = (obj["timestamp"] as? String) ?? imageFileStore.generateTimestamp()

        guard let bytes = Data(base64Encoded: imageBase64) else {
            return (400, #"{"code":400,"message":"Invalid base64"}"#)
        }
        guard let saved = imageFileStore.saveImage(tag: tag, timestamp: timestamp, bytes: bytes, ext: ext) else {
            return (500, #"{"code":500,"message":"Failed to save image"}"#)
        }

        viewModel.imageState = ImageState(
            currentImage: saved,
            isVisible: true,
            offsetX: viewModel.imageState.offsetX,
            offsetY: viewModel.imageState.offsetY,
            opacity: viewModel.imageState.opacity
        )
        viewModel.activeTab = .image

        let json = #"{"code":0,"message":"success","data":{"tag":"\#(tag)","timestamp":"\#(timestamp)","filePath":"\#(saved.filePath)","fileSize":\#(bytes.count)}}"#
        return (200, json)
    }

    // MARK: - show-image

    func handleShowImage(_ body: Data) -> (Int, String) {
        guard let obj = try? JSONSerialization.jsonObject(with: body) as? [String: Any],
              let tag = obj["tag"] as? String,
              let timestamp = obj["timestamp"] as? String else {
            return (400, #"{"code":400,"message":"Missing tag or timestamp"}"#)
        }
        guard let filePath = imageFileStore.getFilePath(tag: tag, timestamp: timestamp) else {
            return (404, #"{"code":404,"message":"Image not found"}"#)
        }
        let ext = URL(fileURLWithPath: filePath).pathExtension.lowercased()
        let info = ImageInfo(tag: tag, timestamp: timestamp, filePath: filePath, ext: ext)

        viewModel.imageState = ImageState(
            currentImage: info,
            isVisible: true,
            offsetX: viewModel.imageState.offsetX,
            offsetY: viewModel.imageState.offsetY,
            opacity: viewModel.imageState.opacity
        )
        viewModel.activeTab = .image

        let s = viewModel.imageState
        let json = #"{"code":0,"message":"success","data":{"tag":"\#(tag)","timestamp":"\#(timestamp)","opacity":\#(s.opacity),"offsetX":\#(s.offsetX),"offsetY":\#(s.offsetY)}}"#
        return (200, json)
    }

    // MARK: - images

    func handleGetImages() -> (Int, String) {
        let currentImage = viewModel.imageState.currentImage
        let images = imageFileStore.getAllImages()
        let items = images.map { img -> String in
            let isCurrent = img.tag == currentImage?.tag && img.timestamp == currentImage?.timestamp
            let size = (try? FileManager.default.attributesOfItem(atPath: img.filePath)[.size] as? Int) ?? 0
            return #"{"tag":"\#(img.tag)","timestamp":"\#(img.timestamp)","ext":"\#(img.ext)","size":\#(size),"isCurrent":\#(isCurrent)}"#
        }.joined(separator: ",")
        return (200, #"{"code":0,"data":{"images":[\#(items)]}}"#)
    }

    // MARK: - hide

    func handleHide(_ body: Data) -> (Int, String) {
        let obj = (try? JSONSerialization.jsonObject(with: body) as? [String: Any]) ?? [:]
        let typeStr = obj["type"] as? String
        let isImage = typeStr == "image" || (typeStr == nil && viewModel.activeTab == .image)
        if isImage {
            viewModel.imageState.isVisible = false
        } else {
            viewModel.webViewState.isVisible = false
        }
        return (200, #"{"code":0,"message":"success"}"#)
    }

    // MARK: - adjust

    func handleAdjust(_ body: Data) -> (Int, String) {
        guard let obj = try? JSONSerialization.jsonObject(with: body) as? [String: Any] else {
            return (400, #"{"code":400,"message":"Invalid JSON"}"#)
        }
        let typeStr = obj["type"] as? String
        let dx = (obj["offsetX"] as? NSNumber)?.floatValue ?? 0
        let dy = (obj["offsetY"] as? NSNumber)?.floatValue ?? 0
        let opacity = (obj["opacity"] as? NSNumber)?.floatValue

        let isImage = typeStr == "image" || (typeStr == nil && viewModel.activeTab == .image)
        if isImage {
            var s = viewModel.imageState
            s.offsetX += dx; s.offsetY += dy
            if let op = opacity { s.opacity = min(max(op, 0), 1) }
            viewModel.imageState = s
            return (200, #"{"code":0,"data":{"offsetX":\#(s.offsetX),"offsetY":\#(s.offsetY),"opacity":\#(s.opacity)}}"#)
        } else {
            var s = viewModel.webViewState
            s.offsetX += dx; s.offsetY += dy
            if let op = opacity { s.opacity = min(max(op, 0), 1) }
            viewModel.webViewState = s
            return (200, #"{"code":0,"data":{"offsetX":\#(s.offsetX),"offsetY":\#(s.offsetY),"opacity":\#(s.opacity)}}"#)
        }
    }
}

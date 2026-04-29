import Foundation
import Network
import SwiftProtobuf

class InspectorApiHandler {

    private let viewModel: InspectorViewModel
    private let imageFileStore: ImageFileStore
    private let sendProtoFn: (SwiftProtobuf.Message, Int, NWConnection) -> Void
    private let sendErrorFn: (Int32, String, Int, NWConnection) -> Void

    init(
        viewModel: InspectorViewModel,
        imageFileStore: ImageFileStore,
        sendProto: @escaping (SwiftProtobuf.Message, Int, NWConnection) -> Void,
        sendError: @escaping (Int32, String, Int, NWConnection) -> Void
    ) {
        self.viewModel = viewModel
        self.imageFileStore = imageFileStore
        self.sendProtoFn = sendProto
        self.sendErrorFn = sendError
    }

    // MARK: - push-image

    func handlePushImage(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_PushImageRequest(serializedBytes: body) else {
            sendErrorFn(400, "Invalid request", 400, connection); return
        }
        let bytes = Data(req.image)
        let timestamp = req.timestamp.isEmpty ? imageFileStore.generateTimestamp() : req.timestamp
        let ext = req.ext.isEmpty ? "png" : req.ext
        guard let saved = imageFileStore.saveImage(tag: req.tag, timestamp: timestamp, bytes: bytes, ext: ext) else {
            sendErrorFn(500, "Failed to save image", 500, connection); return
        }
        viewModel.imageState = ImageState(
            currentImage: saved, isVisible: true,
            offsetX: viewModel.imageState.offsetX,
            offsetY: viewModel.imageState.offsetY,
            opacity: viewModel.imageState.opacity
        )
        viewModel.activeTab = .image
        var result = Clienttools_PushImageResult()
        result.tag = req.tag; result.timestamp = timestamp
        result.filePath = saved.filePath; result.fileSize = Int64(bytes.count)
        var resp = Clienttools_PushImageResponse()
        resp.meta = okMeta(); resp.data = result
        sendProtoFn(resp, 200, connection)
    }

    // MARK: - show-image

    func handleShowImage(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_ShowImageRequest(serializedBytes: body) else {
            sendErrorFn(400, "Invalid request", 400, connection); return
        }
        guard let filePath = imageFileStore.getFilePath(tag: req.tag, timestamp: req.timestamp) else {
            sendErrorFn(404, "Image not found", 404, connection); return
        }
        let ext = URL(fileURLWithPath: filePath).pathExtension.lowercased()
        let info = ImageInfo(tag: req.tag, timestamp: req.timestamp, filePath: filePath, ext: ext)
        viewModel.imageState = ImageState(
            currentImage: info, isVisible: true,
            offsetX: viewModel.imageState.offsetX,
            offsetY: viewModel.imageState.offsetY,
            opacity: viewModel.imageState.opacity
        )
        viewModel.activeTab = .image
        let s = viewModel.imageState
        var result = Clienttools_ShowImageResult()
        result.tag = req.tag; result.timestamp = req.timestamp
        result.opacity = s.opacity; result.offsetX = s.offsetX; result.offsetY = s.offsetY
        var resp = Clienttools_ShowImageResponse()
        resp.meta = okMeta(); resp.data = result
        sendProtoFn(resp, 200, connection)
    }

    // MARK: - images

    func handleGetImages(connection: NWConnection) {
        let currentImage = viewModel.imageState.currentImage
        let images = imageFileStore.getAllImages()
        let items: [Clienttools_ImageItem] = images.map { img in
            let isCurrent = img.tag == currentImage?.tag && img.timestamp == currentImage?.timestamp
            let size = (try? FileManager.default.attributesOfItem(atPath: img.filePath)[.size] as? Int) ?? 0
            var item = Clienttools_ImageItem()
            item.tag = img.tag; item.timestamp = img.timestamp
            item.ext = img.ext; item.size = Int64(size); item.isCurrent = isCurrent
            return item
        }
        var result = Clienttools_ImageListResult()
        result.images = items
        var resp = Clienttools_ImageListResponse()
        resp.meta = okMeta(); resp.data = result
        sendProtoFn(resp, 200, connection)
    }

    // MARK: - hide

    func handleHide(_ body: Data, connection: NWConnection) {
        let req = (try? Clienttools_HideRequest(serializedBytes: body)) ?? Clienttools_HideRequest()
        switch req.type {
        case "image":
            viewModel.imageState.isVisible = false
        case "webview":
            viewModel.webViewState.isVisible = false
        default:
            if viewModel.activeTab == .image {
                viewModel.imageState.isVisible = false
            } else {
                viewModel.webViewState.isVisible = false
            }
        }
        var resp = Clienttools_SimpleResponse()
        resp.meta = okMeta()
        sendProtoFn(resp, 200, connection)
    }

    // MARK: - adjust

    func handleAdjust(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_InspectorAdjustRequest(serializedBytes: body) else {
            sendErrorFn(400, "Invalid request", 400, connection); return
        }
        let isImage = req.type == "image" || (req.type.isEmpty && viewModel.activeTab == .image)
        var result = Clienttools_InspectorAdjustResult()
        if isImage {
            var s = viewModel.imageState
            s.offsetX += req.offsetX; s.offsetY += req.offsetY
            if req.opacity > 0 { s.opacity = min(max(req.opacity, 0), 1) }
            viewModel.imageState = s
            result.offsetX = s.offsetX; result.offsetY = s.offsetY; result.opacity = s.opacity
        } else {
            var s = viewModel.webViewState
            s.offsetX += req.offsetX; s.offsetY += req.offsetY
            if req.opacity > 0 { s.opacity = min(max(req.opacity, 0), 1) }
            viewModel.webViewState = s
            result.offsetX = s.offsetX; result.offsetY = s.offsetY; result.opacity = s.opacity
        }
        var resp = Clienttools_InspectorAdjustResponse()
        resp.meta = okMeta(); resp.data = result
        sendProtoFn(resp, 200, connection)
    }

    // MARK: - private

    private func okMeta() -> Clienttools_ResponseMeta {
        var meta = Clienttools_ResponseMeta()
        meta.code = 0; meta.message = "success"
        return meta
    }
}

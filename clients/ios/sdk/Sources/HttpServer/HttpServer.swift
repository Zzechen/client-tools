import Foundation
import Network
import UIKit
import SwiftProtobuf

class HttpServer {

    private let port: Int
    private let listener: NWListener?
    private let queue = DispatchQueue(label: "HttpServer", qos: .userInitiated)
    private let viewQueryService = ViewQueryService()
    private let viewModifyService = ViewModifyService()
    private lazy var inspectorHandler = InspectorApiHandler(
        viewModel: ClientToolsSDK.shared.inspectorViewModel,
        imageFileStore: ClientToolsSDK.shared.imageFileStore,
        sendProto: { [weak self] msg, code, conn in self?.sendProto(msg, statusCode: code, connection: conn) },
        sendError: { [weak self] code, msg, httpCode, conn in self?.sendError(code: code, message: msg, httpCode: httpCode, connection: conn) }
    )
    private static let sdkVersion: Int32 = 1

    init(port: Int = 8080) {
        self.port = port
        self.listener = try? NWListener(using: .tcp, on: NWEndpoint.Port(integerLiteral: UInt16(port)))
    }

    func start() {
        listener?.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                print("[HttpServer] listening on port \(self?.port ?? 0)")
            case .failed(let error):
                print("[HttpServer] failed: \(error)")
            default:
                break
            }
        }
        listener?.newConnectionHandler = { [weak self] connection in
            self?.handleConnection(connection)
        }
        listener?.start(queue: queue)
    }

    private func handleConnection(_ connection: NWConnection) {
        connection.start(queue: queue)
        receiveAll(connection: connection, accumulated: Data()) { [weak self] fullData in
            guard let self = self else { connection.cancel(); return }
            self.processRequest(fullData, connection: connection)
        }
    }

    private func receiveAll(connection: NWConnection, accumulated: Data, completion: @escaping (Data) -> Void) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { data, _, isComplete, _ in
            var newData = accumulated
            if let data = data { newData.append(data) }
            if isComplete || data == nil {
                completion(newData)
                return
            }
            if self.isHttpRequestComplete(newData) {
                completion(newData)
                return
            }
            self.receiveAll(connection: connection, accumulated: newData, completion: completion)
        }
    }

    // 判断 HTTP 请求是否已完整接收：header 结束后，body 字节数达到 Content-Length
    private func isHttpRequestComplete(_ data: Data) -> Bool {
        // 用字节序列定位 \r\n\r\n，避免把 binary body 当 UTF-8 解析
        let separator: [UInt8] = [0x0D, 0x0A, 0x0D, 0x0A]
        guard let sepRange = data.range(of: Data(separator)) else { return false }
        let headerData = data[data.startIndex..<sepRange.lowerBound]
        let bodyStart  = sepRange.upperBound
        let bodyLen    = data.count - bodyStart
        guard let headerStr = String(data: headerData, encoding: .utf8) else { return false }
        if let clLine = headerStr.components(separatedBy: "\r\n")
            .first(where: { $0.lowercased().hasPrefix("content-length:") }),
           let cl = Int(clLine.components(separatedBy: ":").last?.trimmingCharacters(in: .whitespaces) ?? "") {
            return bodyLen >= cl
        }
        return true
    }

    private func okMeta() -> Clienttools_ResponseMeta {
        var meta = Clienttools_ResponseMeta()
        meta.code = 0
        meta.message = "success"
        meta.sdkVersion = HttpServer.sdkVersion
        meta.device = deviceInfo()
        return meta
    }

    private func errMeta(code: Int32, message: String) -> Clienttools_ResponseMeta {
        var meta = Clienttools_ResponseMeta()
        meta.code = code
        meta.message = message
        meta.sdkVersion = HttpServer.sdkVersion
        meta.device = deviceInfo()
        return meta
    }

    private func deviceInfo() -> Clienttools_DeviceInfo {
        var info = Clienttools_DeviceInfo()
        let screen = UIScreen.main.bounds
        let scale = UIScreen.main.scale
        info.screenWidthDp = Float(screen.width)
        info.screenHeightDp = Float(screen.height)
        info.density = Float(scale)
        return info
    }

    private func sendProto(_ message: SwiftProtobuf.Message, statusCode: Int = 200, connection: NWConnection) {
        guard let data = try? message.serializedData() else {
            connection.cancel(); return
        }
        let header = "HTTP/1.1 \(statusCode) OK\r\nContent-Type: application/x-protobuf\r\nContent-Length: \(data.count)\r\n\r\n"
        var full = header.data(using: .utf8)!
        full.append(data)
        connection.send(content: full, completion: .contentProcessed { _ in connection.cancel() })
    }

    private func sendJson(_ json: String, statusCode: Int = 200, connection: NWConnection) {
        let body = json.data(using: .utf8) ?? Data()
        let header = "HTTP/1.1 \(statusCode) OK\r\nContent-Type: application/json\r\nContent-Length: \(body.count)\r\n\r\n"
        var full = header.data(using: .utf8)!
        full.append(body)
        connection.send(content: full, completion: .contentProcessed { _ in connection.cancel() })
    }

    private func sendError(code: Int32, message: String, httpCode: Int = 400, connection: NWConnection) {
        var resp = Clienttools_SimpleResponse()
        resp.meta = errMeta(code: code, message: message)
        sendProto(resp, statusCode: httpCode, connection: connection)
    }

    private func processRequest(_ rawData: Data, connection: NWConnection) {
        // 用字节定位 header/body 分隔符，避免 binary body 被 UTF-8 解码破坏
        let separator = Data([0x0D, 0x0A, 0x0D, 0x0A])
        guard let sepRange = rawData.range(of: separator) else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        let headerData = rawData[rawData.startIndex..<sepRange.lowerBound]
        let bodyData   = rawData[sepRange.upperBound...]

        guard let requestStr = String(data: headerData, encoding: .utf8) else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }

        let lines = requestStr.components(separatedBy: "\r\n")
        guard let firstLine = lines.first else {
            sendError(code: 400, message: "Empty request", connection: connection); return
        }
        let parts = firstLine.components(separatedBy: " ")
        guard parts.count >= 2 else {
            sendError(code: 400, message: "Invalid request line", connection: connection); return
        }

        let method = parts[0]
        let path = parts[1]

        switch (method, path) {
        case ("GET", "/dom/all"):
            handleDomAll(connection: connection)
        case ("GET", "/api/page/current"):
            handlePageCurrent(connection: connection)
        case ("GET", "/api/nodes/all"):
            handleNodesAll(connection: connection)
        case ("POST", "/api/click"):
            handleClick(bodyData, connection: connection)
        case ("POST", "/api/scroll"):
            handleScroll(bodyData, connection: connection)
        case ("POST", "/api/modify"):
            handleModify(bodyData, connection: connection)
        case ("POST", "/webview/push-html"):
            handleWebviewPushHtml(bodyData, connection: connection)
        case ("POST", "/webview/show"):
            handleWebviewShow(bodyData, connection: connection)
        case ("POST", "/webview/hide"):
            handleWebviewHide(connection: connection)
        case ("POST", "/webview/adjust"):
            handleWebviewAdjust(bodyData, connection: connection)
        case ("GET", "/webview/files"):
            handleWebviewFiles(connection: connection)
        case ("POST", "/inspector/push-image"):
            inspectorHandler.handlePushImage(bodyData, connection: connection)
        case ("POST", "/inspector/show-image"):
            inspectorHandler.handleShowImage(bodyData, connection: connection)
        case ("GET", "/inspector/images"):
            inspectorHandler.handleGetImages(connection: connection)
        case ("POST", "/inspector/hide"):
            inspectorHandler.handleHide(bodyData, connection: connection)
        case ("POST", "/inspector/adjust"):
            inspectorHandler.handleAdjust(bodyData, connection: connection)
        default:
            if method == "GET" && path.hasPrefix("/api/capture/") {
                let nodeId = String(path.dropFirst("/api/capture/".count))
                handleCaptureView(nodeId, connection: connection)
            } else if method == "GET" && path.hasPrefix("/api/nodes/") {
                let nodeId = String(path.dropFirst("/api/nodes/".count))
                handleNodeById(nodeId, connection: connection)
            } else if method == "GET" && path.hasPrefix("/dom/") {
                let domId = String(path.dropFirst("/dom/".count))
                handleDomById(domId, connection: connection)
            } else {
                sendError(code: 404, message: "Not found", httpCode: 404, connection: connection)
            }
        }
    }

    private func handlePageCurrent(connection: NWConnection) {
        let pageInfo = ClientToolsSDK.shared.getCurrentPage()
        var data = Clienttools_PageInfo()
        data.pageName = pageInfo.pageName
        data.timestamp = pageInfo.timestamp
        var resp = Clienttools_PageResponse()
        resp.meta = okMeta()
        resp.data = data
        sendProto(resp, connection: connection)
    }

    private func handleNodesAll(connection: NWConnection) {
        let nodes = viewQueryService.getAllProtoNodes()
        var nodeList = Clienttools_NodeList()
        nodeList.nodes = nodes
        var resp = Clienttools_NodeListResponse()
        resp.meta = okMeta()
        resp.data = nodeList
        sendProto(resp, connection: connection)
    }

    private func handleNodeById(_ id: String, connection: NWConnection) {
        guard let node = viewQueryService.getProtoNode(byId: id) else {
            sendError(code: 404, message: "Node not found", httpCode: 404, connection: connection); return
        }
        var resp = Clienttools_NodeResponse()
        resp.meta = okMeta()
        resp.data = node
        sendProto(resp, connection: connection)
    }

    private func handleClick(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_ClickRequest(serializedBytes: body) else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        guard let view = viewQueryService.findView(byId: req.id) else {
            sendError(code: 404, message: "View not found", httpCode: 404, connection: connection); return
        }
        DispatchQueue.main.async {
            if let control = view as? UIControl {
                control.sendActions(for: .touchUpInside)
            }
        }
        var result = Clienttools_ClickResult()
        result.id = req.id
        var resp = Clienttools_ClickResponse()
        resp.meta = okMeta()
        resp.data = result
        sendProto(resp, connection: connection)
    }

    private func handleScroll(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_ScrollRequest(serializedBytes: body) else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        guard let view = viewQueryService.findView(byId: req.id),
              let scrollView = view as? UIScrollView else {
            sendError(code: 400, message: "View is not a scroll view", connection: connection); return
        }
        DispatchQueue.main.async {
            scrollView.setContentOffset(
                CGPoint(x: scrollView.contentOffset.x + CGFloat(req.dx),
                        y: scrollView.contentOffset.y + CGFloat(req.dy)),
                animated: false
            )
        }
        var result = Clienttools_ScrollResult()
        result.id = req.id; result.dx = req.dx; result.dy = req.dy
        var resp = Clienttools_ScrollResponse()
        resp.meta = okMeta()
        resp.data = result
        sendProto(resp, connection: connection)
    }

    private func handleModify(_ body: Data, connection: NWConnection) {
        sendError(code: 501, message: "iOS 暂不支持 modify_view", httpCode: 501, connection: connection)
    }

    private func handleWebviewPushHtml(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_PushHtmlRequest(serializedBytes: body),
              let overlayManager = ClientToolsSDK.shared.overlayManager() else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        let html = String(bytes: req.html, encoding: .utf8) ?? ""
        let ts = req.timestamp.isEmpty ? HtmlFileStore.generateTimestamp() : req.timestamp
        guard let fileURL = overlayManager.fileStore.save(tag: req.tag, timestamp: ts, html: html) else {
            sendError(code: 500, message: "Failed to save HTML file", httpCode: 500, connection: connection); return
        }
        overlayManager.showFile(at: fileURL, opacity: 0.5)
        var result = Clienttools_PushHtmlResult()
        result.tag = req.tag; result.timestamp = ts; result.filePath = fileURL.path
        var resp = Clienttools_PushHtmlResponse()
        resp.meta = okMeta()
        resp.data = result
        sendProto(resp, connection: connection)
    }

    private func handleWebviewShow(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_WebviewShowRequest(serializedBytes: body),
              let overlayManager = ClientToolsSDK.shared.overlayManager() else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        guard let fileURL = overlayManager.fileStore.findFile(tag: req.tag, timestamp: req.timestamp) else {
            sendError(code: 404, message: "File not found", httpCode: 404, connection: connection); return
        }
        overlayManager.showFile(at: fileURL, opacity: 0.5)
        var resp = Clienttools_SimpleResponse()
        resp.meta = okMeta()
        sendProto(resp, connection: connection)
    }

    private func handleWebviewHide(connection: NWConnection) {
        ClientToolsSDK.shared.overlayManager()?.hide()
        var resp = Clienttools_SimpleResponse()
        resp.meta = okMeta()
        sendProto(resp, connection: connection)
    }

    private func handleCaptureView(_ id: String, connection: NWConnection) {
        guard let data = viewQueryService.captureView(id: id) else {
            sendError(code: 404, message: "View not found or has no size", httpCode: 404, connection: connection)
            return
        }
        var resp = Clienttools_CaptureResponse()
        resp.meta = okMeta()
        resp.imagePng = data
        sendProto(resp, connection: connection)
    }

    private func handleWebviewAdjust(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_WebviewAdjustRequest(serializedBytes: body),
              let overlayManager = ClientToolsSDK.shared.overlayManager() else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        // offsetX/Y 是增量，opacity 是绝对值（0 表示未传，不更新）
        var s = overlayManager.currentWebViewState
        s.offsetX += req.offsetX
        s.offsetY += req.offsetY
        if req.opacity > 0 { s.opacity = min(max(req.opacity, 0), 1) }
        overlayManager.applyState(s)
        var resp = Clienttools_SimpleResponse()
        resp.meta = okMeta()
        sendProto(resp, connection: connection)
    }

    private func handleWebviewFiles(connection: NWConnection) {
        guard let overlayManager = ClientToolsSDK.shared.overlayManager() else {
            sendError(code: 503, message: "OverlayManager not ready", httpCode: 503, connection: connection); return
        }
        let files = overlayManager.fileStore.getAllFiles()
        let currentFile = ClientToolsSDK.shared.inspectorViewModel.webViewState.currentFile
        let items: [Clienttools_FileItem] = files.map { f in
            var item = Clienttools_FileItem()
            item.tag = f.tag; item.timestamp = f.timestamp
            item.filePath = f.filePath
            item.isCurrent = f.tag == currentFile?.tag && f.timestamp == currentFile?.timestamp
            return item
        }
        var result = Clienttools_FileListResult()
        result.files = items
        var resp = Clienttools_FileListResponse()
        resp.meta = okMeta(); resp.data = result
        sendProto(resp, connection: connection)
    }

    private func handleDomAll(connection: NWConnection) {
        guard let overlayManager = ClientToolsSDK.shared.overlayManager() else {
            sendError(code: 503, message: "OverlayManager not ready", httpCode: 503, connection: connection); return
        }
        overlayManager.queryDomAll { nodes in
            var nodeList = Clienttools_DomNodeList()
            nodeList.nodes = nodes
            var resp = Clienttools_DomAllResponse()
            resp.meta = self.okMeta(); resp.data = nodeList
            self.sendProto(resp, connection: connection)
        }
    }

    private func handleDomById(_ id: String, connection: NWConnection) {
        guard let overlayManager = ClientToolsSDK.shared.overlayManager() else {
            sendError(code: 503, message: "OverlayManager not ready", httpCode: 503, connection: connection); return
        }
        overlayManager.queryDomById(id) { node in
            guard let node = node else {
                self.sendError(code: 404, message: "DOM node not found", httpCode: 404, connection: connection); return
            }
            var resp = Clienttools_DomNodeResponse()
            resp.meta = self.okMeta(); resp.data = node
            self.sendProto(resp, connection: connection)
        }
    }
}

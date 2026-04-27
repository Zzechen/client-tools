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
            } else {
                self.receiveAll(connection: connection, accumulated: newData, completion: completion)
            }
        }
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

    private func sendError(code: Int32, message: String, httpCode: Int = 400, connection: NWConnection) {
        var resp = Clienttools_SimpleResponse()
        resp.meta = errMeta(code: code, message: message)
        sendProto(resp, statusCode: httpCode, connection: connection)
    }

    private func processRequest(_ rawData: Data, connection: NWConnection) {
        guard let requestStr = String(data: rawData, encoding: .utf8) else {
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

        var bodyData = Data()
        if let bodyRange = requestStr.range(of: "\r\n\r\n") {
            let bodyStr = String(requestStr[bodyRange.upperBound...])
            bodyData = bodyStr.data(using: .utf8) ?? Data()
        }

        switch (method, path) {
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
        default:
            if method == "GET" && path.hasPrefix("/api/nodes/") {
                let nodeId = String(path.dropFirst("/api/nodes/".count))
                handleNodeById(nodeId, connection: connection)
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
        guard let req = try? Clienttools_ModifyViewRequest(serializedBytes: body) else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        let success = viewModifyService.modifyProto(id: req.id, props: req.props)
        if success {
            var resp = Clienttools_ModifyResponse()
            resp.meta = okMeta()
            sendProto(resp, connection: connection)
        } else {
            sendError(code: 500, message: "Failed to modify view", httpCode: 500, connection: connection)
        }
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

    private func handleWebviewAdjust(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_WebviewAdjustRequest(serializedBytes: body),
              let overlayManager = ClientToolsSDK.shared.overlayManager() else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        overlayManager.adjust(offsetX: req.offsetX, offsetY: req.offsetY, opacity: req.opacity)
        var resp = Clienttools_SimpleResponse()
        resp.meta = okMeta()
        sendProto(resp, connection: connection)
    }
}

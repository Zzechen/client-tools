import Foundation
import Network
import UIKit

class HttpServer {

    private let port: Int
    private let listener: NWListener?
    private let queue = DispatchQueue(label: "HttpServer", qos: .userInitiated)
    private let viewQueryService = ViewQueryService()
    private let viewModifyService = ViewModifyService()

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

        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, _, _ in
            guard let self = self, let data = data, !data.isEmpty else {
                connection.cancel()
                return
            }

            if let request = String(data: data, encoding: .utf8) {
                let response = self.processRequest(request)
                let responseData = response.data(using: .utf8)!
                let httpResponse = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: \(responseData.count)\r\n\r\n"
                var fullResponse = httpResponse.data(using: .utf8)!
                fullResponse.append(responseData)

                connection.send(content: fullResponse, completion: .contentProcessed { _ in
                    connection.cancel()
                })
            } else {
                connection.cancel()
            }
        }
    }

    private func processRequest(_ request: String) -> String {
        let lines = request.components(separatedBy: "\r\n")
        guard let firstLine = lines.first else { return errorJson("Empty request") }

        let parts = firstLine.components(separatedBy: " ")
        guard parts.count >= 2 else { return errorJson("Invalid request") }

        let method = parts[0]
        let path = parts[1]

        var body = ""
        if let bodyStart = request.range(of: "\r\n\r\n") {
            body = String(request[bodyStart.upperBound...])
        }

        switch (method, path) {
        case ("GET", "/api/page/current"):
            return handlePageCurrent()
        case ("GET", "/api/nodes/all"):
            return handleNodesAll()
        case ("POST", "/api/click"):
            return handleClick(body)
        case ("POST", "/api/scroll"):
            return handleScroll(body)
        case ("POST", "/api/modify"):
            return handleModify(body)
        case ("POST", "/webview/push-html"):
            return handleWebviewPushHtml(body)
        case ("POST", "/webview/show"):
            return handleWebviewShow(body)
        case ("POST", "/webview/hide"):
            return handleWebviewHide()
        case ("POST", "/webview/adjust"):
            return handleWebviewAdjust(body)
        default:
            if method == "GET" && path.hasPrefix("/api/nodes/") {
                let nodeId = String(path.dropFirst("/api/nodes/".count))
                return handleNodeById(nodeId)
            }
            return errorJson("Not found", code: 404)
        }
    }

    private func handlePageCurrent() -> String {
        let pageInfo = ClientToolsSDK.shared.getCurrentPage()
        return jsonString(ApiResponse.success(PageInfo(pageName: pageInfo.pageName, timestamp: pageInfo.timestamp)))
    }

    private func handleNodesAll() -> String {
        let nodes = viewQueryService.getAllNodes()
        return jsonString(ApiResponse.success(nodes))
    }

    private func handleNodeById(_ id: String) -> String {
        if let node = viewQueryService.getNode(byId: id) {
            return jsonString(ApiResponse.success(node))
        }
        return errorJson("Node not found", code: 404)
    }

    private func handleClick(_ body: String) -> String {
        guard let data = body.data(using: .utf8),
              let req = try? JSONDecoder().decode(ClickRequest.self, from: data) else {
            return errorJson("Invalid request")
        }
        guard let view = viewQueryService.findView(byId: req.id) else {
            return errorJson("View not found", code: 404)
        }
        DispatchQueue.main.async {
            if let control = view as? UIControl {
                control.sendActions(for: .touchUpInside)
            }
        }
        return jsonString(ApiResponse.success(ClickResult(id: req.id)))
    }

    private func handleScroll(_ body: String) -> String {
        guard let data = body.data(using: .utf8),
              let req = try? JSONDecoder().decode(ScrollRequest.self, from: data) else {
            return errorJson("Invalid request")
        }
        guard let view = viewQueryService.findView(byId: req.id),
              let scrollView = view as? UIScrollView else {
            return errorJson("View is not a scroll view", code: 400)
        }
        DispatchQueue.main.async {
            scrollView.setContentOffset(
                CGPoint(x: scrollView.contentOffset.x + CGFloat(req.dx),
                        y: scrollView.contentOffset.y + CGFloat(req.dy)),
                animated: false
            )
        }
        return jsonString(ApiResponse.success(ScrollResult(id: req.id, dx: req.dx, dy: req.dy)))
    }

    private func handleModify(_ body: String) -> String {
        guard let data = body.data(using: .utf8),
              let req = try? JSONDecoder().decode(ModifyRequest.self, from: data) else {
            return errorJson("Invalid request")
        }
        let success = viewModifyService.modify(id: req.id, props: req.props)
        if success {
            return "{\"code\":0,\"message\":\"success\",\"sdkVersion\":1,\"data\":{\"success\":true}}"
        } else {
            return errorJson("Failed to modify view", code: 500)
        }
    }

    private func handleWebviewPushHtml(_ body: String) -> String {
        guard let data = body.data(using: .utf8),
              let req = try? JSONDecoder().decode(WebviewPushHtmlRequest.self, from: data),
              let overlayManager = ClientToolsSDK.shared.overlayManager() else {
            return errorJson("Invalid request")
        }
        guard let fileURL = overlayManager.fileStore.save(tag: req.tag, timestamp: req.timestamp, html: req.html) else {
            return errorJson("Failed to save HTML file")
        }
        overlayManager.showFile(at: fileURL, opacity: 0.5)
        let result = ["tag": req.tag, "timestamp": req.timestamp, "filePath": fileURL.path]
        return jsonString(ApiResponse.success(result))
    }

    private func handleWebviewShow(_ body: String) -> String {
        guard let data = body.data(using: .utf8),
              let req = try? JSONDecoder().decode(WebviewShowRequest.self, from: data),
              let overlayManager = ClientToolsSDK.shared.overlayManager() else {
            return errorJson("Invalid request")
        }
        if let fileURL = overlayManager.fileStore.findFile(tag: req.tag, timestamp: req.timestamp) {
            overlayManager.showFile(at: fileURL, opacity: 0.5)
            return "{\"code\":0,\"message\":\"success\",\"sdkVersion\":1,\"data\":null}"
        }
        return errorJson("File not found", code: 404)
    }

    private func handleWebviewHide() -> String {
        ClientToolsSDK.shared.overlayManager()?.hide()
        return "{\"code\":0,\"message\":\"success\",\"sdkVersion\":1,\"data\":null}"
    }

    private func handleWebviewAdjust(_ body: String) -> String {
        guard let data = body.data(using: .utf8),
              let req = try? JSONDecoder().decode(WebviewAdjustRequest.self, from: data),
              let overlayManager = ClientToolsSDK.shared.overlayManager() else {
            return errorJson("Invalid request")
        }
        overlayManager.adjust(offsetX: req.offsetX, offsetY: req.offsetY, opacity: req.opacity)
        return "{\"code\":0,\"message\":\"success\",\"sdkVersion\":1,\"data\":null}"
    }

    // MARK: - Helpers

    private func jsonString<T: Codable>(_ response: ApiResponse<T>) -> String {
        let encoder = JSONEncoder()
        guard let data = try? encoder.encode(response),
              let str = String(data: data, encoding: .utf8) else {
            return errorJson("Encoding failed")
        }
        return str
    }

    private func errorJson(_ message: String, code: Int = 400) -> String {
        return "{\"code\":\(code),\"message\":\"\(message)\",\"sdkVersion\":1,\"data\":null}"
    }
}

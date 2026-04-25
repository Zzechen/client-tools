import Foundation
import Network

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
        
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, isComplete, error in
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
        
        // Extract body if present
        var body = ""
        if let bodyStart = request.range(of: "\r\n\r\n") {
            body = String(request[bodyStart.upperBound...])
        }
        
        // Route handling
        switch (method, path) {
        case ("GET", "/api/page/current"):
            return handlePageCurrent()
        case ("GET", "/api/nodes/all"):
            return handleNodesAll()
        default:
            if path.hasPrefix("/api/nodes/") {
                let nodeId = String(path.dropFirst("/api/nodes/".count))
                return handleNodeById(nodeId)
            }
            return errorJson("Not found", code: 404)
        }
    }

    private func handlePageCurrent() -> String {
        let pageInfo = ClientToolsSDK.shared.getCurrentPage()
        let response = ApiResponse.success(PageInfo(pageName: pageInfo.pageName, timestamp: pageInfo.timestamp))
        return jsonString(response)
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
              let clickRequest = try? JSONDecoder().decode(ClickRequest.self, from: data) else {
            return errorJson("Invalid request")
        }

        let view = viewQueryService.findView(byId: clickRequest.id)
        guard let targetView = view else {
            return errorJson("View not found", code: 404)
        }

        if let control = targetView as? UIControl {
            control.sendActions(for: UIControl.Event.touchUpInside)
        }
        let result = ClickResult(id: clickRequest.id)
        return jsonString(ApiResponse.success(result))
    }

    private func handleScroll(_ body: String) -> String {
        guard let data = body.data(using: .utf8),
              let scrollRequest = try? JSONDecoder().decode(ScrollRequest.self, from: data) else {
            return errorJson("Invalid request")
        }

        let view = viewQueryService.findView(byId: scrollRequest.id)
        guard let scrollView = view as? UIScrollView else {
            return errorJson("View is not a scroll view", code: 400)
        }

        let dx = CGFloat(scrollRequest.dx)
        let dy = CGFloat(scrollRequest.dy)
        scrollView.setContentOffset(
            CGPoint(x: scrollView.contentOffset.x + dx, y: scrollView.contentOffset.y + dy),
            animated: false
        )

        let result = ScrollResult(id: scrollRequest.id, dx: scrollRequest.dx, dy: scrollRequest.dy)
        return jsonString(ApiResponse.success(result))
    }

    private func handleModify(_ body: String) -> String {
        guard let data = body.data(using: .utf8),
              let modifyRequest = try? JSONDecoder().decode(ModifyRequest.self, from: data) else {
            return errorJson("Invalid request")
        }

        let success = viewModifyService.modify(id: modifyRequest.id, props: modifyRequest.props)
        if success {
            return "{\"code\":0,\"message\":\"success\",\"sdkVersion\":1,\"data\":{\"success\":true}}"
        } else {
            return errorJson("Failed to modify view", code: 500)
        }
    }

    // MARK: - Helpers

    private func jsonString<T: Codable>(_ response: ApiResponse<T>) -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .prettyPrinted
        guard let data = try? encoder.encode(response),
              let jsonString = String(data: data, encoding: .utf8) else {
            return errorJson("Encoding failed")
        }
        return jsonString
    }

    private func errorJson(_ message: String, code: Int = 400) -> String {
        return "{\"code\":\(code),\"message\":\"\(message)\",\"sdkVersion\":1,\"data\":null}"
    }
}

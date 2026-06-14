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

    private let customRoutes: [CustomRoute]
    private let customHandlerTimeoutMs: Int

    init(port: Int = 8080, customRoutes: [CustomRoute] = [], customHandlerTimeoutMs: Int = 4500) {
        self.port = port
        self.customRoutes = customRoutes
        self.customHandlerTimeoutMs = customHandlerTimeoutMs
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
        case ("GET", "/api/info"):
            handleGetInfo(connection: connection)
        case ("POST", "/api/screen/wake"):
            handleScreenWake(connection: connection)
        case ("GET", "/api/page/current"):
            handlePageCurrent(connection: connection)
        case ("GET", "/api/nodes/all"):
            handleNodesAll(connection: connection)
        case ("POST", "/api/click"):
            handleClick(bodyData, connection: connection)
        case ("POST", "/api/scroll"):
            handleScroll(bodyData, connection: connection)
        case ("POST", "/api/input"):
            handleInputText(bodyData, connection: connection)
        case ("POST", "/api/gesture"):
            handleGesture(bodyData, connection: connection)
        case ("POST", "/api/wait"):
            handleWaitFor(bodyData, connection: connection)
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
        case ("POST", "/mock/rules"):
            handleMockAdd(bodyData, connection: connection)
        case ("GET", "/mock/rules"):
            handleMockList(connection: connection)
        case ("DELETE", "/mock/rules"):
            handleMockClear(connection: connection)
        case ("POST", "/webview/redirects"):
            handleWebViewRedirectAdd(bodyData, connection: connection)
        case ("GET", "/webview/redirects"):
            handleWebViewRedirectList(connection: connection)
        case ("DELETE", "/webview/redirects"):
            handleWebViewRedirectClear(connection: connection)
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
            } else if method == "DELETE" && path.hasPrefix("/mock/rules/") {
                let ruleId = String(path.dropFirst("/mock/rules/".count))
                handleMockDelete(ruleId, connection: connection)
            } else if method == "DELETE" && path.hasPrefix("/webview/redirects/") {
                let ruleId = String(path.dropFirst("/webview/redirects/".count))
                handleWebViewRedirectDelete(ruleId, connection: connection)
            } else if method == "GET" && path == "/custom/routes" {
                handleCustomRoutes(connection: connection)
            } else if path.hasPrefix("/custom/") {
                let customPath = String(path.dropFirst("/custom/".count))
                if let route = customRoutes.first(where: {
                    $0.path == customPath && $0.method.value == method
                }) {
                    let bodyStr = String(data: bodyData, encoding: .utf8)
                    handleCustomCall(route, body: bodyStr, connection: connection)
                } else {
                    sendError(code: 404, message: "Not found", httpCode: 404, connection: connection)
                }
            } else {
                sendError(code: 404, message: "Not found", httpCode: 404, connection: connection)
            }
        }
    }

    private func handleGetInfo(connection: NWConnection) {
        let sema = DispatchSemaphore(value: 0)
        var screenState = Clienttools_ScreenState()
        var device = Clienttools_DeviceInfoFull()
        var app = Clienttools_AppInfo()
        var pageName = ""

        DispatchQueue.main.async {
            let screen = UIScreen.main
            let bounds = screen.bounds
            let scale = screen.scale

            screenState.isAwake = true
            screenState.isLocked = !UIApplication.shared.isProtectedDataAvailable

            device.screenWidthDp  = Float(bounds.width)
            device.screenHeightDp = Float(bounds.height)
            device.density        = Float(scale)
            device.screenWidthPx  = Int32(bounds.width * scale)
            device.screenHeightPx = Int32(bounds.height * scale)
            device.model           = UIDevice.current.model
            device.osMajorVersion = Int32(ProcessInfo.processInfo.operatingSystemVersion.majorVersion)
            device.osVersion      = UIDevice.current.systemVersion

            app.packageName  = Bundle.main.bundleIdentifier ?? ""
            app.versionName  = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
            app.versionCode  = Int32(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0") ?? 0

            pageName = ClientToolsSDK.shared.getCurrentPage().pageName
            sema.signal()
        }
        sema.wait()

        var data = Clienttools_InfoData()
        data.pageName = pageName
        data.screen   = screenState
        data.device   = device
        data.app      = app

        var resp = Clienttools_InfoResponse()
        resp.meta = okMeta()
        resp.data = data
        sendProto(resp, connection: connection)
    }

    private func handleScreenWake(connection: NWConnection) {
        // iOS 不支持通过 app 唤醒屏幕，返回成功（no-op）
        var resp = Clienttools_SimpleResponse()
        resp.meta = okMeta()
        sendProto(resp, connection: connection)
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
        let sema = DispatchSemaphore(value: 0)
        var nodes: [Clienttools_Node] = []
        DispatchQueue.main.async {
            nodes = self.viewQueryService.getAllProtoNodes()
            sema.signal()
        }
        sema.wait()
        var nodeList = Clienttools_NodeList()
        nodeList.nodes = nodes
        var resp = Clienttools_NodeListResponse()
        resp.meta = okMeta()
        resp.data = nodeList
        sendProto(resp, connection: connection)
    }

    private func handleNodeById(_ id: String, connection: NWConnection) {
        let sema = DispatchSemaphore(value: 0)
        var node: Clienttools_Node? = nil
        DispatchQueue.main.async {
            node = self.viewQueryService.getProtoNode(byId: id)
            sema.signal()
        }
        sema.wait()
        guard let node = node else {
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
        let clickIndex = req.hasIndex ? Int(req.index) : 0
        guard let view = viewQueryService.findView(byId: req.id, index: clickIndex) else {
            sendError(code: 404, message: "View not found", httpCode: 404, connection: connection); return
        }

        var clickError: String? = nil
        let sema = DispatchSemaphore(value: 0)
        DispatchQueue.main.async {
            guard let window = view.window else {
                clickError = "View has no window"
                sema.signal(); return
            }
            // 触点坐标（window 坐标系），iOS 1pt = 1dp，无需 density 换算
            let offsetX = req.hasCenterOffsetX ? CGFloat(req.centerOffsetX.value) : 0
            let offsetY = req.hasCenterOffsetY ? CGFloat(req.centerOffsetY.value) : 0
            let localPoint = CGPoint(x: view.bounds.midX + offsetX, y: view.bounds.midY + offsetY)
            let pointInWindow = view.convert(localPoint, to: window)

            // 0. 直接对目标 view 尝试 UIControl touchUpInside（离屏滚动场景 hitTest 找不到）
            if let control = view as? UIControl,
               control.allTargets.contains(where: {
                   control.actions(forTarget: $0, forControlEvent: .touchUpInside)?.isEmpty == false
               }) {
                control.sendActions(for: .touchUpInside)
                sema.signal(); return
            }

            let hitView = window.hitTest(pointInWindow, with: nil) ?? view

            // 1. UIControl
            if let control = hitView as? UIControl {
                control.sendActions(for: .touchUpInside)
                sema.signal(); return
            }
            // 2. UITableViewCell
            if let cell = self.findSuperview(of: hitView, type: UITableViewCell.self),
               let tableView = self.findSuperview(of: cell, type: UITableView.self),
               let indexPath = tableView.indexPath(for: cell) {
                tableView.selectRow(at: indexPath, animated: false, scrollPosition: .none)
                tableView.delegate?.tableView?(tableView, didSelectRowAt: indexPath)
                sema.signal(); return
            }
            // 3. UICollectionViewCell
            if let cell = self.findSuperview(of: hitView, type: UICollectionViewCell.self),
               let cv = self.findSuperview(of: cell, type: UICollectionView.self),
               let indexPath = cv.indexPath(for: cell) {
                cv.delegate?.collectionView?(cv, didSelectItemAt: indexPath)
                sema.signal(); return
            }
            // 4. IOHIDEvent tap（真机）/ UITapGestureRecognizer（模拟器）
            TouchInjector.tap(at: pointInWindow, on: hitView) { sema.signal() }
        }
        sema.wait()

        if let err = clickError {
            sendError(code: 400, message: err, connection: connection); return
        }
        var result = Clienttools_ClickResult()
        result.id = req.id
        var resp = Clienttools_ClickResponse()
        resp.meta = okMeta()
        resp.data = result
        sendProto(resp, connection: connection)
    }

    private func findSuperview<T: UIView>(of view: UIView, type: T.Type) -> T? {
        var v: UIView? = view
        while let current = v {
            if let typed = current as? T { return typed }
            v = current.superview
        }
        return nil
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
        let (success, message) = viewModifyService.modify(id: req.id, req: req)
        var resp = Clienttools_ModifyResponse()
        resp.meta = okMeta()
        resp.message = message
        if success {
            sendProto(resp, connection: connection)
        } else {
            sendError(code: 404, message: message, httpCode: 404, connection: connection)
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

    private func handleMockAdd(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_AddMockRuleRequest(serializedBytes: body) else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        let entry = MockRuleEntry(
            id: UUID().uuidString,
            url: req.url,
            method: req.method.uppercased().isEmpty ? "GET" : req.method.uppercased(),
            delayMs: req.delayMs,
            error: req.error,
            status: req.status == 0 ? 200 : req.status,
            headers: req.headers,
            body: req.body
        )
        MockRuleStore.shared.add(entry)
        var rule = Clienttools_MockRule()
        rule.id = entry.id; rule.url = entry.url; rule.method = entry.method
        rule.delayMs = entry.delayMs; rule.error = entry.error; rule.status = entry.status
        rule.headers = entry.headers; rule.body = entry.body
        var resp = Clienttools_MockRuleResponse()
        resp.meta = okMeta(); resp.data = rule
        sendProto(resp, connection: connection)
    }

    private func handleMockList(connection: NWConnection) {
        let entries = MockRuleStore.shared.list()
        let rules: [Clienttools_MockRule] = entries.map { entry in
            var r = Clienttools_MockRule()
            r.id = entry.id; r.url = entry.url; r.method = entry.method
            r.delayMs = entry.delayMs; r.error = entry.error; r.status = entry.status
            r.headers = entry.headers; r.body = entry.body
            return r
        }
        var ruleList = Clienttools_MockRuleList()
        ruleList.rules = rules
        var resp = Clienttools_MockRuleListResponse()
        resp.meta = okMeta(); resp.data = ruleList
        sendProto(resp, connection: connection)
    }

    private func handleMockDelete(_ id: String, connection: NWConnection) {
        MockRuleStore.shared.delete(id: id)
        var resp = Clienttools_SimpleResponse()
        resp.meta = okMeta()
        sendProto(resp, connection: connection)
    }

    private func handleMockClear(connection: NWConnection) {
        let count = MockRuleStore.shared.clear()
        var resp = Clienttools_ClearMockRulesResponse()
        resp.meta = okMeta()
        resp.clearedCount = Int32(count)
        sendProto(resp, connection: connection)
    }

    private func makeRedirectProto(_ entry: WebViewRedirectEntry) -> Clienttools_WebViewRedirectRule {
        var rule = Clienttools_WebViewRedirectRule()
        rule.id = entry.id
        rule.urlPattern = entry.urlPattern
        rule.targetURL = entry.targetUrl
        return rule
    }

    private func handleWebViewRedirectAdd(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_AddWebViewRedirectRequest(serializedBytes: body) else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        let entry = WebViewRedirectEntry(
            id: UUID().uuidString,
            urlPattern: req.urlPattern,
            targetUrl: req.targetURL
        )
        WebViewRedirectStore.shared.add(entry)
        var resp = Clienttools_WebViewRedirectResponse()
        resp.meta = okMeta()
        resp.data = makeRedirectProto(entry)
        sendProto(resp, connection: connection)
    }

    private func handleWebViewRedirectList(connection: NWConnection) {
        let entries = WebViewRedirectStore.shared.list()
        var ruleList = Clienttools_WebViewRedirectRuleList()
        ruleList.rules = entries.map { makeRedirectProto($0) }
        var resp = Clienttools_WebViewRedirectListResponse()
        resp.meta = okMeta()
        resp.data = ruleList
        sendProto(resp, connection: connection)
    }

    private func handleWebViewRedirectDelete(_ id: String, connection: NWConnection) {
        WebViewRedirectStore.shared.delete(id: id)
        var resp = Clienttools_SimpleResponse()
        resp.meta = okMeta()
        sendProto(resp, connection: connection)
    }

    private func handleWebViewRedirectClear(connection: NWConnection) {
        let count = WebViewRedirectStore.shared.clear()
        var resp = Clienttools_ClearWebViewRedirectsResponse()
        resp.meta = okMeta()
        resp.clearedCount = Int32(count)
        sendProto(resp, connection: connection)
    }

    private func handleCustomRoutes(connection: NWConnection) {
        func esc(_ s: String) -> String {
            s.replacingOccurrences(of: "\\", with: "\\\\")
             .replacingOccurrences(of: "\"", with: "\\\"")
        }
        let items = customRoutes.map { route -> String in
            let paramsJson = route.params.map { k, v in
                "\"\(esc(k))\":\"\(esc(v))\""
            }.joined(separator: ",")
            return "{\"path\":\"/custom/\(route.path)\",\"method\":\"\(route.method.value)\",\"description\":\"\(esc(route.description))\",\"params\":{\(paramsJson)}}"
        }.joined(separator: ",")
        sendJson("[\(items)]", connection: connection)
    }

    // MARK: - input_text

    private func handleInputText(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_InputTextRequest(serializedBytes: body) else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        guard let view = viewQueryService.findView(byId: req.id) else {
            sendError(code: 404, message: "View not found", httpCode: 404, connection: connection); return
        }
        guard view is UITextField || view is UITextView else {
            sendError(code: 400, message: "View is not UITextField or UITextView", connection: connection); return
        }

        let sema = DispatchSemaphore(value: 0)
        DispatchQueue.main.async {
            view.becomeFirstResponder()
            if let tf = view as? UITextField {
                tf.text = req.append ? (tf.text ?? "") + req.text : req.text
                NotificationCenter.default.post(name: UITextField.textDidChangeNotification, object: tf)
            } else if let tv = view as? UITextView {
                tv.text = req.append ? (tv.text ?? "") + req.text : req.text
                NotificationCenter.default.post(name: UITextView.textDidChangeNotification, object: tv)
            }
            sema.signal()
        }
        sema.wait()

        var resp = Clienttools_InputTextResponse()
        resp.meta = okMeta()
        sendProto(resp, connection: connection)
    }

    // MARK: - gesture

    private func handleGesture(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_GestureRequest(serializedBytes: body) else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        guard let view = viewQueryService.findView(byId: req.id),
              let window = view.window else {
            sendError(code: 404, message: "View not found", httpCode: 404, connection: connection); return
        }

        let localCenter = CGPoint(x: view.bounds.midX, y: view.bounds.midY)
        let center = view.convert(localCenter, to: window)
        let durationMs = req.durationMs > 0 ? Int(req.durationMs) : 500
        let distanceDp = req.distanceDp > 0 ? CGFloat(req.distanceDp) : 200
        let swipeDurationMs = req.swipeDurationMs > 0 ? Int(req.swipeDurationMs) : 300

        let sema = DispatchSemaphore(value: 0)
        DispatchQueue.main.async {
            switch req.type {
            case "long_press":
                TouchInjector.longPress(at: center, on: view, durationMs: durationMs) { sema.signal() }
            case "double_tap":
                TouchInjector.doubleTap(at: center, on: view) { sema.signal() }
            case "swipe":
                let end: CGPoint
                switch req.direction {
                case "up":    end = CGPoint(x: center.x, y: center.y - distanceDp)
                case "down":  end = CGPoint(x: center.x, y: center.y + distanceDp)
                case "left":  end = CGPoint(x: center.x - distanceDp, y: center.y)
                default:      end = CGPoint(x: center.x + distanceDp, y: center.y)
                }
                // UIScrollView 直接操控 contentOffset，其余用 IOHIDEvent/手势识别器
                if let scrollView = view as? UIScrollView {
                    let dx = end.x - center.x
                    let dy = end.y - center.y
                    let newOffset = CGPoint(
                        x: max(0, scrollView.contentOffset.x - dx),
                        y: max(0, scrollView.contentOffset.y - dy)
                    )
                    scrollView.setContentOffset(newOffset, animated: true)
                    sema.signal()
                } else {
                    TouchInjector.swipe(from: center, to: end, on: view, durationMs: swipeDurationMs) { sema.signal() }
                }
            default:
                sema.signal()
            }
        }
        sema.wait()

        var resp = Clienttools_GestureResponse()
        resp.meta = okMeta()
        sendProto(resp, connection: connection)
    }


    // MARK: - wait_for

    private func handleWaitFor(_ body: Data, connection: NWConnection) {
        guard let req = try? Clienttools_WaitForRequest(serializedBytes: body) else {
            sendError(code: 400, message: "Invalid request", connection: connection); return
        }
        let timeoutMs = req.timeoutMs > 0 ? Int(req.timeoutMs) : 5000
        let intervalMs = req.intervalMs > 0 ? Int(req.intervalMs) : 200

        let latch = DispatchSemaphore(value: 0)
        var met = false
        let startTime = Date()

        func checkCondition() {
            let elapsed = Int(Date().timeIntervalSince(startTime) * 1000)
            let view = self.viewQueryService.findView(byId: req.id)
            let conditionMet: Bool
            switch req.condition {
            case "exists":     conditionMet = view != nil
            case "not_exists": conditionMet = view == nil
            case "visible":    conditionMet = view != nil && !view!.isHidden && view!.alpha > 0
            case "gone":       conditionMet = view == nil || view!.isHidden || view!.alpha == 0
            default:           conditionMet = false
            }
            if conditionMet {
                met = true
                latch.signal()
                return
            }
            if elapsed >= timeoutMs {
                latch.signal()
                return
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(intervalMs)) {
                checkCondition()
            }
        }

        DispatchQueue.main.async { checkCondition() }
        _ = latch.wait(timeout: .now() + .milliseconds(timeoutMs + 1000))

        let elapsed = Int(Date().timeIntervalSince(startTime) * 1000)
        var resp = Clienttools_WaitForResponse()
        resp.meta = okMeta()
        resp.met = met
        resp.elapsedMs = Int32(elapsed)
        sendProto(resp, connection: connection)
    }

    private func handleCustomCall(_ route: CustomRoute, body: String?, connection: NWConnection) {
        let timeoutMs = customHandlerTimeoutMs
        let sema = DispatchSemaphore(value: 0)
        let lock = NSLock()
        var result = CustomResult.error("handler timeout")
        var signaled = false

        func signalOnce(_ r: CustomResult) {
            lock.lock(); defer { lock.unlock() }
            guard !signaled else { return }
            signaled = true
            result = r
            sema.signal()
        }

        Task {
            do {
                let r = try await route.handler(body)
                signalOnce(r)
            } catch {
                signalOnce(CustomResult.error("handler error: \(error.localizedDescription)"))
            }
        }

        DispatchQueue.global().asyncAfter(deadline: .now() + .milliseconds(timeoutMs)) {
            signalOnce(CustomResult.error("handler timeout"))
        }

        sema.wait()
        sendJson(result.toJson(), connection: connection)
    }
}

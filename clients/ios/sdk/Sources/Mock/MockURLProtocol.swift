import Foundation

class MockURLProtocol: URLProtocol {

    override class func canInit(with request: URLRequest) -> Bool {
        guard let url = request.url?.absoluteString,
              let method = request.httpMethod else { return false }
        return MockRuleStore.shared.findMatch(url: url, method: method) != nil
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        return request
    }

    override func startLoading() {
        guard let url = request.url,
              let method = request.httpMethod,
              let rule = MockRuleStore.shared.findMatch(url: url.absoluteString, method: method) else {
            client?.urlProtocol(self, didFailWithError: URLError(.fileDoesNotExist))
            return
        }

        let statusCode = rule.status > 0 ? Int(rule.status) : 200
        let bodyData = rule.body.data(using: .utf8) ?? Data()

        guard let response = HTTPURLResponse(
            url: url,
            statusCode: statusCode,
            httpVersion: "HTTP/1.1",
            headerFields: rule.headers
        ) else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }

        let deliver = {
            self.client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            self.client?.urlProtocol(self, didLoad: bodyData)
            self.client?.urlProtocolDidFinishLoading(self)
        }

        if rule.delayMs > 0 {
            DispatchQueue.global().asyncAfter(deadline: .now() + Double(rule.delayMs) / 1000.0, execute: deliver)
        } else {
            deliver()
        }
    }

    override func stopLoading() {}
}

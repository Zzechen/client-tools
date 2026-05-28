import Foundation

struct WebViewRedirectEntry {
    let id: String
    let urlPattern: String
    let targetUrl: String
}

class WebViewRedirectStore {
    static let shared = WebViewRedirectStore()
    private var rules: [String: WebViewRedirectEntry] = [:]
    private var insertOrder: [String] = []
    private let lock = NSLock()

    private init() {}

    @discardableResult
    func add(_ entry: WebViewRedirectEntry) -> WebViewRedirectEntry {
        lock.lock(); defer { lock.unlock() }
        rules[entry.id] = entry
        insertOrder.append(entry.id)
        return entry
    }

    func delete(id: String) -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard rules[id] != nil else { return false }
        rules.removeValue(forKey: id)
        insertOrder.removeAll { $0 == id }
        return true
    }

    func list() -> [WebViewRedirectEntry] {
        lock.lock(); defer { lock.unlock() }
        return insertOrder.compactMap { rules[$0] }
    }

    func clear() -> Int {
        lock.lock(); defer { lock.unlock() }
        let count = rules.count
        rules.removeAll()
        insertOrder.removeAll()
        return count
    }

    func resolveRedirect(_ url: String) -> String {
        lock.lock()
        let snapshot = insertOrder.compactMap { rules[$0] }
        lock.unlock()

        let urlWithoutQuery = url.components(separatedBy: "?").first ?? url
        let originalQuery = url.contains("?") ? String(url.dropFirst(urlWithoutQuery.count + 1)) : ""

        guard let match = snapshot.first(where: { entry in
            (try? NSRegularExpression(pattern: entry.urlPattern))
                .map { regex in
                    let range = NSRange(urlWithoutQuery.startIndex..., in: urlWithoutQuery)
                    return regex.firstMatch(in: urlWithoutQuery, range: range) != nil
                } ?? false
        }) else { return url }

        return mergeQueryParams(targetUrl: match.targetUrl, originalQuery: originalQuery)
    }

    private func mergeQueryParams(targetUrl: String, originalQuery: String) -> String {
        guard !originalQuery.isEmpty else { return targetUrl }

        let targetBase = targetUrl.components(separatedBy: "?").first ?? targetUrl
        let targetQuery = targetUrl.contains("?") ? String(targetUrl.dropFirst(targetBase.count + 1)) : ""

        var params: [String: String] = [:]
        if !targetQuery.isEmpty {
            targetQuery.split(separator: "&").forEach { pair in
                let parts = pair.split(separator: "=", maxSplits: 1)
                if parts.count == 2 { params[String(parts[0])] = String(parts[1]) }
            }
        }
        // Original overwrites target on conflict
        originalQuery.split(separator: "&").forEach { pair in
            let parts = pair.split(separator: "=", maxSplits: 1)
            if parts.count == 2 { params[String(parts[0])] = String(parts[1]) }
        }

        let merged = params.map { "\($0.key)=\($0.value)" }.joined(separator: "&")
        return "\(targetBase)?\(merged)"
    }
}

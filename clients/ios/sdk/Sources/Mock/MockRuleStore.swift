import Foundation

struct MockRuleEntry {
    let id: String
    let url: String
    let method: String
    let delayMs: Int64
    let error: String
    let status: Int32
    let headers: [String: String]
    let body: String
}

class MockRuleStore {
    static let shared = MockRuleStore()
    private var rules: [String: MockRuleEntry] = [:]
    private var insertOrder: [String] = []
    private let lock = NSLock()

    private init() {}

    @discardableResult
    func add(_ entry: MockRuleEntry) -> MockRuleEntry {
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

    func list() -> [MockRuleEntry] {
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

    func findMatch(url: String, method: String) -> MockRuleEntry? {
        lock.lock(); defer { lock.unlock() }
        let upperMethod = method.uppercased()
        return insertOrder.reversed().compactMap { rules[$0] }.first { entry in
            guard entry.method == upperMethod else { return false }
            guard let regex = try? NSRegularExpression(pattern: entry.url) else { return false }
            let range = NSRange(url.startIndex..., in: url)
            return regex.firstMatch(in: url, range: range) != nil
        }
    }
}

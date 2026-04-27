import Foundation

public class HtmlFileStore {

    private let fileManager = FileManager.default
    private lazy var baseDir: URL = {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("client_tools/overlay")
    }()

    public init() {
        try? fileManager.createDirectory(at: baseDir, withIntermediateDirectories: true)
    }

    public func save(tag: String, timestamp: String, html: String) -> URL? {
        let filename = "\(tag)_\(timestamp).html"
        let fileURL = baseDir.appendingPathComponent(filename)

        do {
            try html.write(to: fileURL, atomically: true, encoding: .utf8)
            return fileURL
        } catch {
            print("[HtmlFileStore] save error: \(error)")
            return nil
        }
    }

    public func findFile(tag: String, timestamp: String) -> URL? {
        let filename = "\(tag)_\(timestamp).html"
        let fileURL = baseDir.appendingPathComponent(filename)
        return fileManager.fileExists(atPath: fileURL.path) ? fileURL : nil
    }

    public static func generateTimestamp() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMddHHmmss"
        return formatter.string(from: Date())
    }
}

import Foundation

class ImageFileStore {

    private let fileManager = FileManager.default
    private lazy var baseDir: URL = {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("client_tools/images")
    }()

    init() {
        try? fileManager.createDirectory(at: baseDir, withIntermediateDirectories: true)
    }

    func saveImage(tag: String, timestamp: String, bytes: Data, ext: String) -> ImageInfo? {
        let tagDir = baseDir.appendingPathComponent(tag)
        do {
            try fileManager.createDirectory(at: tagDir, withIntermediateDirectories: true)
            let fileURL = tagDir.appendingPathComponent("\(tag)_\(timestamp).\(ext)")
            try bytes.write(to: fileURL)
            return ImageInfo(tag: tag, timestamp: timestamp, filePath: fileURL.path, ext: ext)
        } catch {
            print("[ImageFileStore] saveImage error: \(error)")
            return nil
        }
    }

    func getAllImages() -> [ImageInfo] {
        var result: [ImageInfo] = []
        guard let tagDirs = try? fileManager.contentsOfDirectory(at: baseDir, includingPropertiesForKeys: [.isDirectoryKey]) else { return [] }
        for tagDir in tagDirs {
            guard (try? tagDir.resourceValues(forKeys: [.isDirectoryKey]))?.isDirectory == true else { continue }
            guard let files = try? fileManager.contentsOfDirectory(at: tagDir, includingPropertiesForKeys: nil) else { continue }
            for file in files {
                let ext = file.pathExtension.lowercased()
                guard ext == "png" || ext == "jpg" || ext == "jpeg" else { continue }
                guard let timestamp = parseTimestamp(file.lastPathComponent) else { continue }
                result.append(ImageInfo(tag: tagDir.lastPathComponent, timestamp: timestamp, filePath: file.path, ext: ext))
            }
        }
        return result.sorted { $0.timestamp > $1.timestamp }
    }

    func getFilePath(tag: String, timestamp: String) -> String? {
        let tagDir = baseDir.appendingPathComponent(tag)
        guard let files = try? fileManager.contentsOfDirectory(at: tagDir, includingPropertiesForKeys: nil) else { return nil }
        return files.first { f in
            let ext = f.pathExtension.lowercased()
            return (ext == "png" || ext == "jpg" || ext == "jpeg") && parseTimestamp(f.lastPathComponent) == timestamp
        }?.path
    }

    func deleteAll() -> Bool {
        do {
            try fileManager.removeItem(at: baseDir)
            try fileManager.createDirectory(at: baseDir, withIntermediateDirectories: true)
            return true
        } catch {
            print("[ImageFileStore] deleteAll error: \(error)")
            return false
        }
    }

    func generateTimestamp() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMdd-HHmm"
        return formatter.string(from: Date())
    }

    private func parseTimestamp(_ filename: String) -> String? {
        let pattern = #"_(\d{4}-\d{4})\.\w+$"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: filename, range: NSRange(filename.startIndex..., in: filename)),
              let range = Range(match.range(at: 1), in: filename) else { return nil }
        return String(filename[range])
    }
}

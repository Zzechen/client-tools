import UIKit

class ViewHashGenerator {

    static func generateId(for view: UIView, path: String = "") -> String {
        // 优先使用 accessibilityIdentifier
        if let identifier = view.accessibilityIdentifier, !identifier.isEmpty {
            return identifier
        }

        // 降级：Runtime Hash
        let className = String(describing: type(of: view))
        let address = String(format: "%p", view)
        let hash = "\(className)_\(address)"

        if path.isEmpty {
            return hash
        }
        return "\(hash)_\(path)"
    }
}

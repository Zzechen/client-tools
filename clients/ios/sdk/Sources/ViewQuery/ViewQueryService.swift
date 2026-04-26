import UIKit

class ViewQueryService {

    func getAllNodes() -> [ViewNode] {
        return ViewTraverser.traverseFromWindow()
    }

    func getNode(byId id: String) -> ViewNode? {
        return getAllNodes().first { $0.id == id }
    }

    func findView(byId id: String) -> UIView? {
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow }) else { return nil }
        return findView(in: window, byId: id, path: "")
    }

    private func findView(in view: UIView, byId id: String, path: String) -> UIView? {
        for (index, subview) in view.subviews.enumerated() {
            if subview.tag == OverlayManager.overlayTag { continue }
            let childPath = path.isEmpty ? "\(index)" : "\(path).\(index)"
            let viewId = ViewHashGenerator.generateId(for: subview, path: childPath)
            if viewId == id { return subview }
            if let found = findView(in: subview, byId: id, path: childPath) { return found }
        }
        return nil
    }
}

import UIKit

class ViewQueryService {

    func getAllNodes() -> [ViewNode] {
        return ViewTraverser.traverseFromWindow()
    }

    func getNode(byId id: String) -> ViewNode? {
        let allNodes = getAllNodes()
        return allNodes.first { $0.id == id }
    }

    func findView(byId id: String) -> UIView? {
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow }) else {
            return nil
        }
        return findView(in: window, byId: id)
    }

    private func findView(in view: UIView, byId id: String) -> UIView? {
        if ViewHashGenerator.generateId(for: view) == id {
            return view
        }

        for subview in view.subviews {
            if let found = findView(in: subview, byId: id) {
                return found
            }
        }

        return nil
    }
}

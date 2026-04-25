import UIKit

class ViewTraverser {

    static func traverse(_ view: UIView, path: String = "") -> [ViewNode] {
        var nodes: [ViewNode] = []

        for (index, subview) in view.subviews.enumerated() {
            // 跳过叠加层（通过 tag 过滤）
            if subview.tag == OverlayManager.overlayTag { continue }

            let childPath = path.isEmpty ? "\(index)" : "\(path).\(index)"
            let viewId = ViewHashGenerator.generateId(for: subview, path: childPath)

            let node = ViewNode(
                id: viewId,
                type: ViewTypeMapper.map(subview),
                screenX: Float(subview.frame.origin.x) / Float(UIScreen.main.scale),
                screenY: Float(subview.frame.origin.y) / Float(UIScreen.main.scale),
                widthDp: Float(subview.frame.width) / Float(UIScreen.main.scale),
                heightDp: Float(subview.frame.height) / Float(UIScreen.main.scale),
                attrs: StyleQuerier.query(subview)
            )

            nodes.append(node)
            nodes.append(contentsOf: traverse(subview, path: childPath))
        }

        return nodes
    }

    static func traverseFromWindow() -> [ViewNode] {
        var result: [ViewNode] = []
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow }) else {
            return result
        }
        result = traverse(window)
        return result
    }
}

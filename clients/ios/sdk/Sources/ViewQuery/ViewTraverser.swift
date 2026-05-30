import UIKit

class ViewTraverser {

    static func traverse(_ view: UIView, path: String = "") -> [ViewNode] {
        var nodes: [ViewNode] = []

        for (index, subview) in view.subviews.enumerated() {
            if subview.tag == OverlayManager.overlayTag { continue }

            let childPath = path.isEmpty ? "\(index)" : "\(path).\(index)"
            let viewId = ViewHashGenerator.generateId(for: subview, path: childPath)

            let origin = subview.convert(CGPoint.zero, to: nil)
            let visibilityCode: Int = subview.isHidden ? 8 : (subview.alpha == 0 ? 4 : 0)

            // 计算视觉尺寸（layout 原始尺寸 × scale）
            let t = subview.transform
            let sx = Float(sqrt(t.a * t.a + t.c * t.c))
            let sy = Float(sqrt(t.b * t.b + t.d * t.d))
            let visualSx = sx == 0 ? 1 : sx
            let visualSy = sy == 0 ? 1 : sy

            let node = ViewNode(
                id: viewId,
                type: ViewTypeMapper.map(subview),
                screenX: Float(origin.x),
                screenY: Float(origin.y),
                widthDp: Float(subview.bounds.width) * visualSx,
                heightDp: Float(subview.bounds.height) * visualSy,
                visibility: visibilityCode,
                isEnabled: (subview as? UIControl)?.isEnabled ?? subview.isUserInteractionEnabled,
                attrs: StyleQuerier.query(subview)
            )

            nodes.append(node)
            nodes.append(contentsOf: traverse(subview, path: childPath))
        }

        return nodes
    }

    static func traverseFromWindow() -> [ViewNode] {
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .filter({ $0.tag != OverlayManager.overlayTag && !$0.isHidden })
            .min(by: { $0.windowLevel < $1.windowLevel }) else {
            return []
        }
        return traverse(window)
    }
}

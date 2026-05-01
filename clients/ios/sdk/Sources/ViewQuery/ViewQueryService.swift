import UIKit

class ViewQueryService {

    func getAllNodes() -> [ViewNode] {
        return ViewTraverser.traverseFromWindow()
    }

    func getNode(byId id: String) -> ViewNode? {
        return getAllNodes().first { $0.id == id }
    }

    func getAllProtoNodes() -> [Clienttools_Node] {
        return getAllNodes().map { toProtoNode($0) }
    }

    func getProtoNode(byId id: String) -> Clienttools_Node? {
        guard let node = getNode(byId: id) else { return nil }
        return toProtoNode(node)
    }

    private func toProtoNode(_ vn: ViewNode) -> Clienttools_Node {
        var node = Clienttools_Node()
        node.id = vn.id
        node.screenX = vn.screenX
        node.screenY = vn.screenY
        node.widthDp = vn.widthDp
        node.heightDp = vn.heightDp
        node.translateX = vn.translateX
        node.translateY = vn.translateY
        node.scaleX = vn.scaleX
        node.scaleY = vn.scaleY

        var nodeAttrs = Clienttools_NodeAttrs()
        switch vn.attrs {
        case .text(let ta):
            node.type = .text
            var textAttrs = Clienttools_TextAttrs()
            textAttrs.fontSize = ta.fontSize ?? 0
            textAttrs.color = ta.color ?? ""
            textAttrs.fontWeight = ta.fontWeight ?? ""
            nodeAttrs.text = textAttrs
        case .image(let ia):
            node.type = .image
            var imageAttrs = Clienttools_ImageAttrs()
            imageAttrs.scaleType = ia.scaleType ?? ""
            nodeAttrs.image = imageAttrs
        case .list(let la):
            node.type = .list
            var listAttrs = Clienttools_ListAttrs()
            listAttrs.itemSpacing = la.itemSpacing ?? 0
            listAttrs.orientation = la.orientation ?? ""
            nodeAttrs.list = listAttrs
        case nil:
            node.type = .container
            nodeAttrs.container = Clienttools_ContainerAttrs()
        }
        node.attrs = nodeAttrs
        return node
    }

    func captureView(id: String) -> Data? {
        var result: Data?
        let sema = DispatchSemaphore(value: 0)
        DispatchQueue.main.async {
            guard let view = self.findView(byId: id),
                  view.bounds.width > 0, view.bounds.height > 0 else {
                sema.signal()
                return
            }
            let renderer = UIGraphicsImageRenderer(bounds: view.bounds)
            let image = renderer.image { ctx in
                view.layer.render(in: ctx.cgContext)
            }
            result = image.pngData()
            sema.signal()
        }
        sema.wait()
        return result
    }

    func findView(byId id: String) -> UIView? {
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .filter({ $0.tag != OverlayManager.overlayTag && !$0.isHidden })
            .min(by: { $0.windowLevel < $1.windowLevel }) else { return nil }
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

import UIKit

class ViewModifyService {

    private let viewQueryService = ViewQueryService()

    func modify(id: String, req: Clienttools_ModifyViewRequest) -> (Bool, String) {
        guard let view = viewQueryService.findView(byId: id) else {
            return (false, "View not found: \(id)")
        }

        if req.hasText {
            guard view is UILabel || view is UITextField else {
                return (false, "text requires UILabel or UITextField, but '\(id)' is \(type(of: view))")
            }
        }

        let sema = DispatchSemaphore(value: 0)
        DispatchQueue.main.async {
            if req.hasMove || req.hasSize {
                Self.ensureTopLeftAnchor(view)
            }
            if req.hasMove {
                Self.applyMove(to: view, move: req.move)
            }
            if req.hasSize {
                Self.applySize(to: view, size: req.size)
            }
            if req.hasText {
                if let label = view as? UILabel {
                    label.text = req.text.content
                } else if let tf = view as? UITextField {
                    tf.text = req.text.content
                }
            }
            view.setNeedsLayout()
            view.layoutIfNeeded()
            sema.signal()
        }
        sema.wait()
        return (true, "")
    }

    // pivot 设到左上角（幂等），补偿 position 保持视觉位置不变
    private static func ensureTopLeftAnchor(_ view: UIView) {
        guard view.layer.anchorPoint != CGPoint(x: 0, y: 0) else { return }
        let old = view.layer.anchorPoint
        let size = view.bounds.size
        view.layer.position = CGPoint(
            x: view.layer.position.x - old.x * size.width,
            y: view.layer.position.y - old.y * size.height
        )
        view.layer.anchorPoint = CGPoint(x: 0, y: 0)
    }

    // 增量平移：在当前 translation 基础上叠加 dx/dy，保持 scale 不变
    private static func applyMove(to view: UIView, move: Clienttools_MoveProps) {
        let t = view.transform
        let currentTx = t.tx
        let currentTy = t.ty
        let sx = sqrt(t.a * t.a + t.c * t.c)
        let sy = sqrt(t.b * t.b + t.d * t.d)
        let newTx = currentTx + (move.hasDx ? CGFloat(move.dx.value) : 0)
        let newTy = currentTy + (move.hasDy ? CGFloat(move.dy.value) : 0)
        // scale first, then translate（屏幕空间：translate 不受 scale 影响）
        view.transform = CGAffineTransform(scaleX: sx == 0 ? 1 : sx, y: sy == 0 ? 1 : sy)
            .concatenating(CGAffineTransform(translationX: newTx, y: newTy))
    }

    // 绝对尺寸：根据目标 dp 算出 scaleX/Y，保持 translation 不变
    private static func applySize(to view: UIView, size: Clienttools_SizeProps) {
        let t = view.transform
        let currentTx = t.tx
        let currentTy = t.ty
        let currentSx = sqrt(t.a * t.a + t.c * t.c)
        let currentSy = sqrt(t.b * t.b + t.d * t.d)
        let originalW = view.bounds.width   // layout 原始宽，不受 transform 影响
        let originalH = view.bounds.height
        let newSx = (size.hasWidth && originalW > 0)
            ? CGFloat(size.width.value) / originalW
            : (currentSx == 0 ? 1 : currentSx)
        let newSy = (size.hasHeight && originalH > 0)
            ? CGFloat(size.height.value) / originalH
            : (currentSy == 0 ? 1 : currentSy)
        view.transform = CGAffineTransform(scaleX: newSx, y: newSy)
            .concatenating(CGAffineTransform(translationX: currentTx, y: currentTy))
    }
}

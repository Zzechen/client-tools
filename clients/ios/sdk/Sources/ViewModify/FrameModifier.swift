import UIKit

class FrameModifier {
    
    // 保存 view id -> 约束列表
    private static var savedConstraints: [String: [NSLayoutConstraint]] = [:]
    // 保存 view id -> 是否在 frame 模式
    private static var frameModeViews: Set<String> = []

    static func modifyFrame(_ view: UIView, widthDp: String?, heightDp: String?) {
        guard widthDp != nil || heightDp != nil else { return }

        // 先记录当前屏幕坐标下的 frame（约束禁掉前）
        let currentFrame = view.superview.map { view.convert(view.bounds, to: $0) } ?? view.frame

        // 判断是否有约束
        let hasConstraints = !view.constraints.isEmpty ||
            (view.superview?.constraints.contains(where: { $0.firstItem === view }) ?? false)

        if hasConstraints && !isInFrameMode(view) {
            enableFrameMode(view)
            // 约束禁掉后立即把 frame 恢复到禁约束前的位置/尺寸，防止 layout pass 把它归零
            view.frame = currentFrame
        }

        var newSize = view.frame.size
        if let widthStr = widthDp {
            if widthStr == "wrap_content" { view.sizeToFit(); return }
            if let width = parseDp(widthStr) { newSize.width = width }
        }
        if let heightStr = heightDp {
            if heightStr == "wrap_content" { view.sizeToFit(); return }
            if let height = parseDp(heightStr) { newSize.height = height }
        }
        view.frame.size = newSize
    }
    
    static func enableFrameMode(_ view: UIView) -> Bool {
        let viewId = ObjectIdentifier(view).debugDescription
        
        // 如果已经在 frame 模式，直接返回
        if frameModeViews.contains(viewId) {
            return true
        }
        
        // 保存当前约束
        var constraintsToSave: [NSLayoutConstraint] = []
        
        // 收集 view 自身的约束
        constraintsToSave.append(contentsOf: view.constraints)
        
        // 收集父视图上关于该 view 的约束
        if let superview = view.superview {
            let superviewConstraints = superview.constraints.filter { $0.firstItem === view || $0.secondItem === view }
            constraintsToSave.append(contentsOf: superviewConstraints)
        }
        
        if !constraintsToSave.isEmpty {
            savedConstraints[viewId] = constraintsToSave
        }
        
        // 禁用所有约束
        for constraint in constraintsToSave {
            constraint.isActive = false
        }
        
        frameModeViews.insert(viewId)
        return true
    }
    
    static func disableFrameMode(_ view: UIView) -> Bool {
        let viewId = ObjectIdentifier(view).debugDescription
        
        // 如果不在 frame 模式，直接返回
        guard frameModeViews.contains(viewId) else {
            return false
        }
        
        // 恢复约束
        if let constraints = savedConstraints[viewId] {
            for constraint in constraints {
                constraint.isActive = true
            }
            savedConstraints.removeValue(forKey: viewId)
        }
        
        frameModeViews.remove(viewId)
        return true
    }
    
    private static func isInFrameMode(_ view: UIView) -> Bool {
        let viewId = ObjectIdentifier(view).debugDescription
        return frameModeViews.contains(viewId)
    }
    
    private static func parseDp(_ str: String) -> CGFloat? {
        if str.hasSuffix("dp") {
            let value = String(str.dropLast(2))
            if let width = Float(value) {
                return CGFloat(width)
            }
        } else if let value = Float(str) {
            return CGFloat(value)
        }
        return nil
    }
}

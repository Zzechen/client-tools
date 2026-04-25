import UIKit

class ConstraintModifier {

    static func modifyMargin(_ view: UIView, attribute: NSLayoutConstraint.Attribute, constant: CGFloat) {
        let scale = UIScreen.main.scale
        
        // 1. 优先查找 superview 上的外部约束
        if let superview = view.superview {
            if let constraint = superview.constraints.first(where: {
                ($0.firstItem as? UIView) === view && $0.firstAttribute == attribute
            }) {
                constraint.constant += constant
                return
            }

            // 检查 secondItem
            if let constraint = superview.constraints.first(where: {
                ($0.secondItem as? UIView) === view && $0.secondAttribute == attribute
            }) {
                constraint.constant += constant
                return
            }
        }

        // 2. 查找内部约束
        if let constraint = view.constraints.first(where: { $0.firstAttribute == attribute }) {
            constraint.constant += constant
            return
        }

        // 3. 找不到则添加新约束
        addConstraint(to: view, attribute: attribute, constant: constant)
    }

    static func modifyMarginTop(_ view: UIView, diffDp: CGFloat) {
        modifyMargin(view, attribute: .top, constant: diffDp * UIScreen.main.scale)
    }

    static func modifyMarginBottom(_ view: UIView, diffDp: CGFloat) {
        modifyMargin(view, attribute: .bottom, constant: diffDp * UIScreen.main.scale)
    }

    static func modifyMarginLeading(_ view: UIView, diffDp: CGFloat) {
        modifyMargin(view, attribute: .leading, constant: diffDp * UIScreen.main.scale)
    }

    static func modifyMarginTrailing(_ view: UIView, diffDp: CGFloat) {
        modifyMargin(view, attribute: .trailing, constant: diffDp * UIScreen.main.scale)
    }
    
    private static func addConstraint(to view: UIView, attribute: NSLayoutConstraint.Attribute, constant: CGFloat) {
        guard let superview = view.superview else { return }
        
        view.translatesAutoresizingMaskIntoConstraints = false
        
        let constraint = NSLayoutConstraint(
            item: view,
            attribute: attribute,
            relatedBy: .equal,
            toItem: superview,
            attribute: attribute,
            multiplier: 1.0,
            constant: constant
        )
        constraint.isActive = true
    }
}

import UIKit

class ConstraintModifier {

    static func modifyMargin(_ view: UIView, attribute: NSLayoutConstraint.Attribute, constant: CGFloat) {
        // 1. 遍历父视图的外部约束（加在 superview 上的）
        if let superview = view.superview {
            if let constraint = superview.constraints.first(where: {
                ($0.firstItem as? UIView) === view && $0.firstAttribute == attribute
            }) {
                constraint.constant = constant
                return
            }

            // 检查 secondItem
            if let constraint = superview.constraints.first(where: {
                ($0.secondItem as? UIView) === view && $0.secondAttribute == attribute
            }) {
                constraint.constant = constant
                return
            }
        }

        // 2. 遍历目标视图自身的内部约束（加在 view 上的，如 height/width）
        if let constraint = view.constraints.first(where: { $0.firstAttribute == attribute }) {
            constraint.constant = constant
            return
        }

        // 3. 通过私有属性查找（KVC 访问 _constraints）
        if let allConstraints = view.value(forKey: "_constraints") as? [NSLayoutConstraint] {
            if let constraint = allConstraints.first(where: { $0.firstAttribute == attribute }) {
                constraint.constant = constant
            }
        }
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
}

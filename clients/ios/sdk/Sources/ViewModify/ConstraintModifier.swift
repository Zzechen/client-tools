import UIKit

class ConstraintModifier {

    static func modifyMargin(_ view: UIView, attribute: NSLayoutConstraint.Attribute, constant: CGFloat) {
        if let superview = view.superview {
            if let constraint = superview.constraints.first(where: {
                ($0.firstItem as? UIView) === view && $0.firstAttribute == attribute
            }) {
                constraint.constant += constant
                return
            }
            if let constraint = superview.constraints.first(where: {
                ($0.secondItem as? UIView) === view && $0.secondAttribute == attribute
            }) {
                constraint.constant -= constant
                return
            }
        }

        if let constraint = view.constraints.first(where: { $0.firstAttribute == attribute }) {
            constraint.constant += constant
            return
        }

        addConstraint(to: view, attribute: attribute, constant: constant)
    }

    static func modifyMarginTop(_ view: UIView, diffDp: CGFloat) {
        modifyMargin(view, attribute: .top, constant: diffDp)
    }

    static func modifyMarginBottom(_ view: UIView, diffDp: CGFloat) {
        modifyMargin(view, attribute: .bottom, constant: -diffDp)
    }

    static func modifyMarginLeading(_ view: UIView, diffDp: CGFloat) {
        modifyMargin(view, attribute: .leading, constant: diffDp)
    }

    static func modifyMarginTrailing(_ view: UIView, diffDp: CGFloat) {
        modifyMargin(view, attribute: .trailing, constant: -diffDp)
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

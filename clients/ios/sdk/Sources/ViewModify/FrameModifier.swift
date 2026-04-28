import UIKit

class FrameModifier {

    static func modifyFrame(_ view: UIView, widthDp: String?, heightDp: String?) {
        guard widthDp != nil || heightDp != nil else { return }

        if let widthStr = widthDp {
            if widthStr == "wrap_content" {
                view.sizeToFit()
            } else if let value = parseDp(widthStr) {
                setDimension(view, attribute: .width, value: value)
            }
        }
        if let heightStr = heightDp {
            if heightStr == "wrap_content" {
                view.sizeToFit()
            } else if let value = parseDp(heightStr) {
                setDimension(view, attribute: .height, value: value)
            }
        }
    }

    // 找到现有尺寸约束改 constant；找不到则添加一条
    private static func setDimension(_ view: UIView, attribute: NSLayoutConstraint.Attribute, value: CGFloat) {
        // 先找 view 自身带的固定尺寸约束（firstItem == view, secondItem == nil）
        if let existing = view.constraints.first(where: {
            $0.firstItem === view && $0.firstAttribute == attribute && $0.secondItem == nil
        }) {
            existing.constant = value
            return
        }
        // 再找父视图上关于该 view 的尺寸约束
        if let existing = view.superview?.constraints.first(where: {
            $0.firstItem === view && $0.firstAttribute == attribute && $0.secondItem == nil
        }) {
            existing.constant = value
            return
        }
        // 都没找到：添加一条（同时把 translatesAutoresizingMask 约束关掉，避免冲突）
        view.translatesAutoresizingMaskIntoConstraints = false
        let c = NSLayoutConstraint(item: view, attribute: attribute, relatedBy: .equal,
                                   toItem: nil, attribute: .notAnAttribute,
                                   multiplier: 1, constant: value)
        c.priority = .required
        view.addConstraint(c)
    }

    private static func parseDp(_ str: String) -> CGFloat? {
        if str.hasSuffix("dp") {
            return Float(String(str.dropLast(2))).map { CGFloat($0) }
        }
        return Float(str).map { CGFloat($0) }
    }
}

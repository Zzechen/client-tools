import UIKit

class FrameModifier {

    // Android 路径：解析字符串形式的 dp 值
    static func modifyFrame(_ view: UIView, widthDp: String?, heightDp: String?) {
        guard widthDp != nil || heightDp != nil else { return }
        if let widthStr = widthDp, let value = parseDp(widthStr) {
            setFixedDimension(view, attribute: .width, value: value)
        }
        if let heightStr = heightDp, let value = parseDp(heightStr) {
            setFixedDimension(view, attribute: .height, value: value)
        }
    }

    // iOS 路径直接传 CGFloat
    static func setFixedDimension(_ view: UIView, attribute: NSLayoutConstraint.Attribute, value: CGFloat) {
        guard let superview = view.superview else { return }
        view.translatesAutoresizingMaskIntoConstraints = false
        let allConstraints = view.constraints + superview.constraints
        for c in allConstraints where c.firstAttribute == attribute && c.secondItem == nil
            && (c.firstItem as? UIView) === view {
            c.isActive = false
        }
        NSLayoutConstraint.activate([
            NSLayoutConstraint(item: view, attribute: attribute, relatedBy: .equal,
                               toItem: nil, attribute: .notAnAttribute,
                               multiplier: 1, constant: value)
        ])
    }

    private static func parseDp(_ str: String) -> CGFloat? {
        if str.hasSuffix("dp") {
            return Float(String(str.dropLast(2))).map { CGFloat($0) }
        }
        return Float(str).map { CGFloat($0) }
    }
}

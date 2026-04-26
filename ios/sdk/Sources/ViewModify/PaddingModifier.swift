import UIKit

class PaddingModifier {

    static func modifyPadding(_ view: UIView, insets: UIEdgeInsets) {
        switch view {
        // UITextField doesn't have contentEdgeInsets - skip
        case let textView as UITextView:
            textView.textContainerInset = UIEdgeInsets(
                top: insets.top, left: insets.left, bottom: insets.bottom, right: insets.right
            )
        case let button as UIButton:
            button.contentEdgeInsets = insets
        default:
            break
        }
    }
}

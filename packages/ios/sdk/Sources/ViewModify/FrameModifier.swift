import UIKit

class FrameModifier {

    static func modifyFrame(_ view: UIView, widthDp: String?, heightDp: String?) {
        let scale = CGFloat(UIScreen.main.scale)

        if let widthStr = widthDp {
            if widthStr == "wrap_content" {
                view.sizeToFit()
            } else if widthStr.hasSuffix("dp") {
                let widthValue = String(widthStr.dropLast(2))
                if let width = Float(widthValue) {
                    view.frame.size.width = CGFloat(width) * scale
                }
            }
        }

        if let heightStr = heightDp {
            if heightStr == "wrap_content" {
                view.sizeToFit()
            } else if heightStr.hasSuffix("dp") {
                let heightValue = String(heightStr.dropLast(2))
                if let height = Float(heightValue) {
                    view.frame.size.height = CGFloat(height) * scale
                }
            }
        }
    }
}

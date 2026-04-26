import UIKit

class StyleQuerier {

    static func query(_ view: UIView) -> NodeAttrs? {
        switch view {
        case let label as UILabel:
            let font = label.font
            let color = label.textColor
            var fontWeight: String? = nil
            if let traits = font?.fontDescriptor.fontAttributes[.traits] as? [UIFontDescriptor.TraitKey: Any] {
                if let weight = traits[.weight] as? CGFloat {
                    if weight >= 0.7 {
                        fontWeight = "bold"
                    } else if weight <= 0.3 {
                        fontWeight = "light"
                    }
                }
            }
            return .text(TextAttrs(
                fontSize: Float(font?.pointSize ?? 0),
                color: color?.toHex(),
                fontWeight: fontWeight
            ))

        case let textField as UITextField:
            let font = textField.font
            let color = textField.textColor
            return .text(TextAttrs(
                fontSize: Float(font?.pointSize ?? 0),
                color: color?.toHex(),
                fontWeight: nil
            ))

        case let textView as UITextView:
            let font = textView.font
            let color = textView.textColor
            return .text(TextAttrs(
                fontSize: Float(font?.pointSize ?? 0),
                color: color?.toHex(),
                fontWeight: nil
            ))

        case let imageView as UIImageView:
            return .image(ImageAttrs(
                scaleType: "\(imageView.contentMode)"
            ))

        case let tableView as UITableView:
            return .list(ListAttrs(
                itemSpacing: Float(tableView.rowHeight),
                orientation: "vertical"
            ))

        case let collectionView as UICollectionView:
            var spacing: Float = 0
            if let flowLayout = collectionView.collectionViewLayout as? UICollectionViewFlowLayout {
                spacing = Float(flowLayout.minimumLineSpacing)
            }
            return .list(ListAttrs(
                itemSpacing: spacing,
                orientation: "vertical"
            ))

        default:
            return nil
        }
    }
}

// MARK: - UIColor to Hex
extension UIColor {
    func toHex() -> String? {
        guard let components = cgColor.components, components.count >= 3 else { return nil }
        let r = Float(components[0])
        let g = Float(components[1])
        let b = Float(components[2])
        return String(format: "#%02X%02X%02X", Int(r * 255), Int(g * 255), Int(b * 255))
    }
}

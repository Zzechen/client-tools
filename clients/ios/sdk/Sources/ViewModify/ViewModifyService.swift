import UIKit

class ViewModifyService {

    private let viewQueryService = ViewQueryService()

    // iOS 路径：transform + 尺寸约束 + padding + 文字属性
    func modifyIosProto(id: String, props: Clienttools_IosViewProps) -> (Bool, String) {
        guard let view = viewQueryService.findView(byId: id) else { return (false, "View not found: \(id)") }

        // 类型断言：有 text 字段则要求必须是 UILabel
        if props.hasText {
            guard view is UILabel else {
                let typeName = type(of: view)
                return (false, "text props requires UILabel, but view '\(id)' is \(typeName)")
            }
        }

        let sema = DispatchSemaphore(value: 0)
        DispatchQueue.main.async {
            // 1. transform
            Self.applyTransform(to: view, props: props)

            // 2. 文字属性（已断言是 UILabel）
            if props.hasText, let label = view as? UILabel {
                Self.applyTextProps(to: label, text: props.text)
            }

            view.setNeedsLayout()
            view.layoutIfNeeded()
            sema.signal()
        }
        sema.wait()
        return (true, "")
    }

    private static func applyTransform(to view: UIView, props: Clienttools_IosViewProps) {
        guard props.hasTranslateXDp || props.hasTranslateYDp || props.hasScaleX || props.hasScaleY else { return }

        // 将 anchorPoint 设为 (0,0)，补偿 position 保持视觉位置不变，幂等
        let oldAnchor = view.layer.anchorPoint
        if oldAnchor != CGPoint(x: 0, y: 0) {
            let size = view.bounds.size
            view.layer.position = CGPoint(
                x: view.layer.position.x - oldAnchor.x * size.width,
                y: view.layer.position.y - oldAnchor.y * size.height
            )
            view.layer.anchorPoint = CGPoint(x: 0, y: 0)
        }

        // 从当前 transform 反解分量（假设无 rotation）
        let t = view.transform
        let currentTx = t.tx
        let currentTy = t.ty
        let currentSx = sqrt(t.a * t.a + t.c * t.c)
        let currentSy = sqrt(t.b * t.b + t.d * t.d)

        let newTx = props.hasTranslateXDp ? CGFloat(props.translateXDp.value) : currentTx
        let newTy = props.hasTranslateYDp ? CGFloat(props.translateYDp.value) : currentTy
        let newSx = props.hasScaleX ? CGFloat(props.scaleX.value) : (currentSx == 0 ? 1 : currentSx)
        let newSy = props.hasScaleY ? CGFloat(props.scaleY.value) : (currentSy == 0 ? 1 : currentSy)

        // 先 scale 再 translate：屏幕空间语义，translate 不受 scale 影响
        view.transform = CGAffineTransform(scaleX: newSx, y: newSy)
            .concatenating(CGAffineTransform(translationX: newTx, y: newTy))
    }

    private static func applyTextProps(to label: UILabel, text: Clienttools_IosTextProps) {
        if text.hasContent {
            label.text = text.content.value
        }

        if text.hasLetterSpacingEm {
            let em = CGFloat(text.letterSpacingEm.value)
            let fontSize = label.font.pointSize
            if var attrs = label.attributedText?.mutableCopy() as? NSMutableAttributedString {
                attrs.addAttribute(.kern, value: em * fontSize, range: NSRange(location: 0, length: attrs.length))
                label.attributedText = attrs
            } else {
                label.attributedText = NSAttributedString(string: label.text ?? "", attributes: [
                    .font: label.font as Any,
                    .foregroundColor: label.textColor as Any,
                    .kern: em * fontSize
                ])
            }
        }

        if text.hasLineSpacingExtraDp {
            let extra = CGFloat(text.lineSpacingExtraDp.value)
            let style = NSMutableParagraphStyle()
            style.lineSpacing = extra
            let base = label.attributedText?.mutableCopy() as? NSMutableAttributedString
                ?? NSMutableAttributedString(string: label.text ?? "", attributes: [
                    .font: label.font as Any,
                    .foregroundColor: label.textColor as Any
                ])
            base.addAttribute(.paragraphStyle, value: style, range: NSRange(location: 0, length: base.length))
            label.attributedText = base
        }
    }
}

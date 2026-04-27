import UIKit

class ViewModifyService {

    private let viewQueryService = ViewQueryService()

    func modifyProto(id: String, props: Clienttools_ViewProps) -> Bool {
        guard let view = viewQueryService.findView(byId: id) else { return false }
        DispatchQueue.main.async {
            if props.hasMarginTopDiffDp {
                ConstraintModifier.modifyMarginTop(view, diffDp: CGFloat(props.marginTopDiffDp.value))
            }
            if props.hasMarginBottomDiffDp {
                ConstraintModifier.modifyMarginBottom(view, diffDp: CGFloat(props.marginBottomDiffDp.value))
            }
            if props.hasMarginLeftDiffDp {
                ConstraintModifier.modifyMarginLeading(view, diffDp: CGFloat(props.marginLeftDiffDp.value))
            }
            if props.hasMarginRightDiffDp {
                ConstraintModifier.modifyMarginTrailing(view, diffDp: CGFloat(props.marginRightDiffDp.value))
            }
            let hasPadding = props.hasPaddingTopDiffDp || props.hasPaddingBottomDiffDp ||
                             props.hasPaddingLeftDiffDp || props.hasPaddingRightDiffDp
            if hasPadding {
                let insets = UIEdgeInsets(
                    top: CGFloat(props.paddingTopDiffDp.value),
                    left: CGFloat(props.paddingLeftDiffDp.value),
                    bottom: CGFloat(props.paddingBottomDiffDp.value),
                    right: CGFloat(props.paddingRightDiffDp.value)
                )
                PaddingModifier.modifyPadding(view, insets: insets)
            }
            let widthStr = props.hasWidthDp ? props.widthDp.value : nil
            let heightStr = props.hasHeightDp ? props.heightDp.value : nil
            FrameModifier.modifyFrame(view, widthDp: widthStr, heightDp: heightStr)
            view.setNeedsLayout()
            view.layoutIfNeeded()
        }
        return true
    }

}

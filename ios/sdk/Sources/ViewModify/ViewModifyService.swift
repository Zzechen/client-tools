import UIKit

class ViewModifyService {

    private let viewQueryService = ViewQueryService()

    func modify(id: String, props: ModifyProps) -> Bool {
        guard let view = viewQueryService.findView(byId: id) else { return false }

        DispatchQueue.main.async {
            if let top = props.marginTopDiffDp {
                ConstraintModifier.modifyMarginTop(view, diffDp: CGFloat(top))
            }
            if let bottom = props.marginBottomDiffDp {
                ConstraintModifier.modifyMarginBottom(view, diffDp: CGFloat(bottom))
            }
            if let leading = props.marginLeftDiffDp {
                ConstraintModifier.modifyMarginLeading(view, diffDp: CGFloat(leading))
            }
            if let trailing = props.marginRightDiffDp {
                ConstraintModifier.modifyMarginTrailing(view, diffDp: CGFloat(trailing))
            }

            let hasPadding = props.paddingTopDiffDp != nil || props.paddingBottomDiffDp != nil ||
                             props.paddingLeftDiffDp != nil || props.paddingRightDiffDp != nil
            if hasPadding {
                let insets = UIEdgeInsets(
                    top: CGFloat(props.paddingTopDiffDp ?? 0),
                    left: CGFloat(props.paddingLeftDiffDp ?? 0),
                    bottom: CGFloat(props.paddingBottomDiffDp ?? 0),
                    right: CGFloat(props.paddingRightDiffDp ?? 0)
                )
                PaddingModifier.modifyPadding(view, insets: insets)
            }

            FrameModifier.modifyFrame(view, widthDp: props.widthDp, heightDp: props.heightDp)
            view.setNeedsLayout()
            view.layoutIfNeeded()
        }
        return true
    }
}

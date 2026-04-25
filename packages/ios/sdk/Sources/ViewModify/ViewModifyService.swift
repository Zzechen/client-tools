import UIKit

class ViewModifyService {

    private let viewQueryService = ViewQueryService()

    func modify(id: String, props: ModifyProps) -> Bool {
        guard let view = viewQueryService.findView(byId: id) else {
            return false
        }

        let scale = CGFloat(UIScreen.main.scale)

        // margin 修改
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

        // padding 修改
        let hasPadding = props.paddingTopDiffDp != nil || props.paddingBottomDiffDp != nil ||
                         props.paddingLeftDiffDp != nil || props.paddingRightDiffDp != nil
        if hasPadding {
            let insets = UIEdgeInsets(
                top: CGFloat(props.paddingTopDiffDp ?? 0) * scale,
                left: CGFloat(props.paddingLeftDiffDp ?? 0) * scale,
                bottom: CGFloat(props.paddingBottomDiffDp ?? 0) * scale,
                right: CGFloat(props.paddingRightDiffDp ?? 0) * scale
            )
            PaddingModifier.modifyPadding(view, insets: insets)
        }

        // frame 修改
        FrameModifier.modifyFrame(view, widthDp: props.widthDp, heightDp: props.heightDp)

        return true
    }
}

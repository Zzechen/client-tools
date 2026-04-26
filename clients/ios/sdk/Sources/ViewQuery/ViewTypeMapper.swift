import UIKit

class ViewTypeMapper {

    static func map(_ view: UIView) -> String {
        switch view {
        case is UILabel, is UITextField, is UITextView, is UIButton:
            return "TEXT"
        case is UIImageView:
            return "IMAGE"
        case is UITableView, is UICollectionView:
            return "LIST"
        default:
            return "CONTAINER"
        }
    }
}

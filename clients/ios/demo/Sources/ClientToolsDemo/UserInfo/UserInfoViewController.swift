import UIKit
class UserInfoViewController: UIViewController {
    let user: UserInfo
    let token: String
    init(user: UserInfo, token: String) {
        self.user = user; self.token = token
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError() }
}

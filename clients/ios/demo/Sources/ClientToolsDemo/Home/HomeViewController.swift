import UIKit
import SnapKit

class HomeViewController: UIViewController {

    private lazy var tableView: UITableView = {
        let tv = UITableView(frame: .zero, style: .insetGrouped)
        tv.accessibilityIdentifier = "home_list"
        tv.delegate = self
        tv.dataSource = self
        tv.register(HomeCell.self, forCellReuseIdentifier: HomeCell.identifier)
        return tv
    }()

    private lazy var pages: [(title: String, subtitle: String, icon: String, action: () -> Void)] = [
        ("Login Demo", "三种登录方式：验证码/密码/邮箱", "📱", { [weak self] in
            self?.navigationController?.pushViewController(LoginViewController(), animated: true)
        }),
        ("VerifyCode Demo", "验证码输入页", "🔐", { [weak self] in
            self?.navigationController?.pushViewController(VerifyCodeViewController(), animated: true)
        }),
        ("User Info (Demo)", "用户信息展示页", "👤", { [weak self] in
            let demoUser = UserInfo(
                id: "demo",
                name: "Demo User",
                phone: "138****8000",
                email: "demo@pulse.app",
                avatar_url: ""
            )
            let vc = UserInfoViewController(user: demoUser, token: "demo_token_12345")
            self?.navigationController?.pushViewController(vc, animated: true)
        }),
    ]

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "ClientTools Demo"
        navigationController?.navigationBar.prefersLargeTitles = true
        view.accessibilityIdentifier = "home_nav_bar"
        view.addSubview(tableView)
        tableView.snp.makeConstraints { make in
            make.edges.equalToSuperview()
        }
    }
}

extension HomeViewController: UITableViewDelegate, UITableViewDataSource {

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return pages.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: HomeCell.identifier, for: indexPath) as! HomeCell
        let page = pages[indexPath.row]
        cell.configure(title: page.title, subtitle: page.subtitle, icon: page.icon)
        cell.accessibilityIdentifier = "home_cell_\(indexPath.row)"
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        pages[indexPath.row].action()
    }
}

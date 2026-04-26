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

    private let pages: [(title: String, subtitle: String, icon: String, vcClass: String)] = [
        ("Login Demo", "登录页面测试", "📄", "LoginViewController"),
        ("Profile Demo", "个人资料页面测试", "👤", "ProfileViewController"),
        ("Settings Demo", "设置页面测试", "⚙️", "SettingsViewController"),
        ("List Demo", "列表页面测试", "📋", "ListViewController"),
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
        let identifier = page.vcClass.lowercased().replacingOccurrences(of: "viewcontroller", with: "")
        cell.accessibilityIdentifier = "home_cell_\(identifier)"
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let page = pages[indexPath.row]
        let vcClass = page.vcClass
        guard let vc = instantiateViewController(className: vcClass) else { return }
        navigationController?.pushViewController(vc, animated: true)
    }

    private func instantiateViewController(className: String) -> UIViewController? {
        guard let nameSpace = Bundle.main.infoDictionary?["CFBundleExecutable"] as? String,
              let clazz = NSClassFromString("\(nameSpace).\(className)") else { return nil }
        return (clazz as? UIViewController.Type)?.init()
    }
}

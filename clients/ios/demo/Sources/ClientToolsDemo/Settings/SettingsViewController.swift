import UIKit
import SnapKit

class SettingsViewController: UIViewController {

    private lazy var tableView: UITableView = {
        let tv = UITableView(frame: .zero, style: .insetGrouped)
        tv.delegate = self
        tv.dataSource = self
        tv.register(SettingsSwitchCell.self, forCellReuseIdentifier: SettingsSwitchCell.identifier)
        tv.register(SettingsDetailCell.self, forCellReuseIdentifier: SettingsDetailCell.identifier)
        return tv
    }()

    private let sections: [(title: String, rows: [SettingsRow])] = [
        ("通知设置", [
            .switchRow(id: "settings_notify_switch", title: "推送通知", isOn: true),
            .switchRow(id: "settings_privacy_switch", title: "隐私保护", isOn: false),
        ]),
        ("常规设置", [
            .detailRow(id: "settings_language", title: "语言"),
            .detailRow(id: "settings_clear_cache", title: "清除缓存"),
            .detailRow(id: "settings_about", title: "关于"),
        ]),
    ]

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "设置"
        view.backgroundColor = .systemBackground
        view.addSubview(tableView)
        tableView.snp.makeConstraints { make in
            make.edges.equalToSuperview()
        }
    }
}

extension SettingsViewController: UITableViewDelegate, UITableViewDataSource {

    func numberOfSections(in tableView: UITableView) -> Int {
        return sections.count
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return sections[section].rows.count
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        return sections[section].title
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let row = sections[indexPath.section].rows[indexPath.row]
        switch row {
        case .switchRow(let id, let title, let isOn):
            let cell = tableView.dequeueReusableCell(withIdentifier: SettingsSwitchCell.identifier, for: indexPath) as! SettingsSwitchCell
            cell.configure(id: id, title: title, isOn: isOn)
            return cell
        case .detailRow(let id, let title):
            let cell = tableView.dequeueReusableCell(withIdentifier: SettingsDetailCell.identifier, for: indexPath) as! SettingsDetailCell
            cell.configure(id: id, title: title)
            return cell
        }
    }
}

enum SettingsRow {
    case switchRow(id: String, title: String, isOn: Bool)
    case detailRow(id: String, title: String)
}

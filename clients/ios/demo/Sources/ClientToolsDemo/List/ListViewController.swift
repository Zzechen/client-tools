import UIKit
import SnapKit

class ListViewController: UIViewController {

    private lazy var tableView: UITableView = {
        let tv = UITableView(frame: .zero, style: .plain)
        tv.accessibilityIdentifier = "list_table_view"
        tv.delegate = self
        tv.dataSource = self
        tv.register(ListCell.self, forCellReuseIdentifier: ListCell.identifier)
        return tv
    }()

    private let scrollBottomButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("滚动到底部", for: .normal)
        btn.backgroundColor = .systemBlue
        btn.setTitleColor(.white, for: .normal)
        btn.layer.cornerRadius = 8
        btn.accessibilityIdentifier = "list_scroll_bottom_btn"
        return btn
    }()

    private let data = (1...20).map { "Item \($0)" }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "列表"
        view.backgroundColor = .systemBackground
        setupUI()
    }

    private func setupUI() {
        view.addSubview(tableView)
        view.addSubview(scrollBottomButton)

        tableView.snp.makeConstraints { make in
            make.top.leading.trailing.equalToSuperview()
            make.bottom.equalTo(scrollBottomButton.snp.top).offset(-16)
        }

        scrollBottomButton.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(32)
            make.bottom.equalTo(view.safeAreaLayoutGuide).offset(-16)
            make.height.equalTo(44)
        }

        scrollBottomButton.addTarget(self, action: #selector(scrollToBottom), for: .touchUpInside)
    }

    @objc private func scrollToBottom() {
        let lastRow = data.count - 1
        let indexPath = IndexPath(row: lastRow, section: 0)
        tableView.scrollToRow(at: indexPath, at: .bottom, animated: true)
    }
}

extension ListViewController: UITableViewDelegate, UITableViewDataSource {

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return data.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: ListCell.identifier, for: indexPath) as! ListCell
        cell.configure(title: data[indexPath.row])
        cell.accessibilityIdentifier = "list_cell_\(indexPath.row)"
        return cell
    }
}

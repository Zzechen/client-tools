import UIKit
import SnapKit

class UserInfoViewController: UIViewController {

    let user: UserInfo
    let token: String

    init(user: UserInfo, token: String) {
        self.user = user
        self.token = token
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    // MARK: - Views

    private lazy var scrollView: UIScrollView = {
        let sv = UIScrollView()
        sv.accessibilityIdentifier = "user_info_root"
        return sv
    }()

    private lazy var contentView: UIView = UIView()

    private lazy var avatarView: UIView = {
        let v = UIView()
        v.backgroundColor = UIColor(hex: "#00D4C2")
        v.layer.cornerRadius = 10
        v.accessibilityIdentifier = "user_info_avatar_container"
        return v
    }()

    private lazy var avatarLabel: UILabel = {
        let lbl = UILabel()
        lbl.textColor = UIColor(hex: "#0A0B0F")
        lbl.font = .boldSystemFont(ofSize: 32)
        lbl.textAlignment = .center
        lbl.accessibilityIdentifier = "user_info_avatar"
        return lbl
    }()

    private lazy var nameLabel: UILabel = {
        let lbl = UILabel()
        lbl.textColor = .white
        lbl.font = .boldSystemFont(ofSize: 22)
        lbl.textAlignment = .center
        lbl.accessibilityIdentifier = "user_info_name"
        return lbl
    }()

    private lazy var phoneLabel: UILabel = makeInfoLabel(identifier: "user_info_phone")
    private lazy var emailLabel: UILabel = makeInfoLabel(identifier: "user_info_email")
    private lazy var tokenLabel: UILabel = makeInfoLabel(identifier: "user_info_token")

    private lazy var logoutButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("退出登录", for: .normal)
        btn.setTitleColor(.black, for: .normal)
        btn.titleLabel?.font = .boldSystemFont(ofSize: 16)
        btn.backgroundColor = UIColor(hex: "#00D4C2")
        btn.layer.cornerRadius = 8
        btn.accessibilityIdentifier = "user_info_btn_logout"
        return btn
    }()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(hex: "#001015")
        view.accessibilityIdentifier = "user_info_page"
        setupHierarchy()
        setupConstraints()
        bindData()
        logoutButton.addTarget(self, action: #selector(logoutTapped), for: .touchUpInside)
    }

    // MARK: - Setup

    private func makeInfoLabel(identifier: String) -> UILabel {
        let lbl = UILabel()
        lbl.textColor = .white
        lbl.font = .systemFont(ofSize: 16)
        lbl.accessibilityIdentifier = identifier
        return lbl
    }

    private func makeSection(title: String, valueLabel: UILabel) -> UIView {
        let container = UIView()
        let titleLbl = UILabel()
        titleLbl.text = title
        titleLbl.textColor = UIColor(hex: "#8A9BB0")
        titleLbl.font = .systemFont(ofSize: 12)

        let row = UIView()
        row.backgroundColor = UIColor(hex: "#0A1A20")
        row.layer.cornerRadius = 8
        row.layer.borderWidth = 1
        row.layer.borderColor = UIColor(hex: "#1A3040").cgColor
        row.addSubview(valueLabel)
        valueLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.trailing.equalToSuperview().offset(-16)
            make.centerY.equalToSuperview()
        }

        [titleLbl, row].forEach { container.addSubview($0) }
        titleLbl.snp.makeConstraints { make in
            make.top.leading.trailing.equalToSuperview()
        }
        row.snp.makeConstraints { make in
            make.top.equalTo(titleLbl.snp.bottom).offset(4)
            make.leading.trailing.bottom.equalToSuperview()
            make.height.equalTo(52)
        }
        return container
    }

    private func setupHierarchy() {
        view.addSubview(scrollView)
        scrollView.addSubview(contentView)

        avatarView.addSubview(avatarLabel)

        let phoneSection = makeSection(title: "手机号", valueLabel: phoneLabel)
        let emailSection = makeSection(title: "邮箱", valueLabel: emailLabel)
        let tokenSection = makeSection(title: "Token", valueLabel: tokenLabel)

        [avatarView, nameLabel, phoneSection, emailSection, tokenSection, logoutButton]
            .forEach { contentView.addSubview($0) }
    }

    private func setupConstraints() {
        scrollView.snp.makeConstraints { $0.edges.equalToSuperview() }
        contentView.snp.makeConstraints { make in
            make.edges.equalToSuperview()
            make.width.equalToSuperview()
        }

        avatarView.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.equalToSuperview().offset(64)
            make.width.height.equalTo(80)
        }
        avatarLabel.snp.makeConstraints { $0.edges.equalToSuperview() }

        nameLabel.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.equalTo(avatarView.snp.bottom).offset(12)
            make.leading.trailing.equalToSuperview().inset(24)
        }

        let sections = contentView.subviews.filter { $0 != avatarView && $0 != nameLabel && $0 != logoutButton }
        var previousAnchor = nameLabel.snp.bottom
        for section in sections {
            section.snp.makeConstraints { make in
                make.top.equalTo(previousAnchor).offset(16)
                make.leading.trailing.equalToSuperview().inset(24)
            }
            previousAnchor = section.snp.bottom
        }

        logoutButton.snp.makeConstraints { make in
            make.top.equalTo(previousAnchor).offset(48)
            make.leading.trailing.equalToSuperview().inset(24)
            make.height.equalTo(52)
            make.bottom.equalToSuperview().offset(-40)
        }
    }

    private func bindData() {
        avatarLabel.text = String(user.name.prefix(1))
        nameLabel.text = user.name
        phoneLabel.text = user.phone.isEmpty ? "未绑定" : user.phone
        emailLabel.text = user.email.isEmpty ? "未绑定" : user.email
        let truncated = token.count > 20 ? String(token.prefix(20)) + "..." : token
        tokenLabel.text = truncated
    }

    // MARK: - Actions

    @objc private func logoutTapped() {
        navigationController?.popViewController(animated: true)
    }
}

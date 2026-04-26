import UIKit
import SnapKit

class ProfileViewController: UIViewController {

    private let avatarImageView: UIImageView = {
        let iv = UIImageView()
        iv.backgroundColor = .systemGray
        iv.layer.cornerRadius = 40
        iv.clipsToBounds = true
        iv.accessibilityIdentifier = "profile_avatar"
        return iv
    }()

    private let nameLabel: UILabel = {
        let label = UILabel()
        label.text = "昵称: John Doe"
        label.accessibilityIdentifier = "profile_name_label"
        return label
    }()

    private let bioLabel: UILabel = {
        let label = UILabel()
        label.text = "简介: iOS Developer"
        label.textColor = .secondaryLabel
        label.accessibilityIdentifier = "profile_bio_label"
        return label
    }()

    private let avatarButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("头像设置", for: .normal)
        btn.accessibilityIdentifier = "profile_avatar_btn"
        return btn
    }()

    private let editButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("编辑资料", for: .normal)
        btn.backgroundColor = .systemBlue
        btn.setTitleColor(.white, for: .normal)
        btn.layer.cornerRadius = 8
        btn.accessibilityIdentifier = "profile_edit_btn"
        return btn
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "个人资料"
        view.backgroundColor = .systemBackground
        setupUI()
    }

    private func setupUI() {
        view.addSubview(avatarImageView)
        view.addSubview(nameLabel)
        view.addSubview(bioLabel)
        view.addSubview(avatarButton)
        view.addSubview(editButton)

        avatarImageView.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.equalTo(view.safeAreaLayoutGuide).offset(40)
            make.width.height.equalTo(80)
        }

        nameLabel.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.equalTo(avatarImageView.snp.bottom).offset(16)
        }

        bioLabel.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.equalTo(nameLabel.snp.bottom).offset(8)
        }

        avatarButton.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.equalTo(bioLabel.snp.bottom).offset(24)
            make.width.equalTo(200)
            make.height.equalTo(44)
        }

        editButton.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(32)
            make.top.equalTo(avatarButton.snp.bottom).offset(16)
            make.height.equalTo(48)
        }
    }
}

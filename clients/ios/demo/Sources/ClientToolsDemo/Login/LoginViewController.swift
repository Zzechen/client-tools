import UIKit
import SnapKit

class LoginViewController: UIViewController {

    private let logoImageView: UIImageView = {
        let iv = UIImageView()
        iv.backgroundColor = .systemBlue
        iv.layer.cornerRadius = 50
        iv.clipsToBounds = true
        iv.accessibilityIdentifier = "login_logo"
        return iv
    }()

    private let usernameTextField: UITextField = {
        let tf = UITextField()
        tf.placeholder = "用户名"
        tf.borderStyle = .roundedRect
        tf.accessibilityIdentifier = "login_username_input"
        return tf
    }()

    private let passwordTextField: UITextField = {
        let tf = UITextField()
        tf.placeholder = "密码"
        tf.borderStyle = .roundedRect
        tf.isSecureTextEntry = true
        tf.accessibilityIdentifier = "login_password_input"
        return tf
    }()

    private let loginButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("登录", for: .normal)
        btn.backgroundColor = .systemBlue
        btn.setTitleColor(.white, for: .normal)
        btn.layer.cornerRadius = 8
        btn.accessibilityIdentifier = "login_btn"
        return btn
    }()

    private let registerButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("注册", for: .normal)
        btn.accessibilityIdentifier = "login_register_btn"
        return btn
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "登录"
        view.backgroundColor = .systemBackground
        setupUI()
    }

    private func setupUI() {
        [logoImageView, usernameTextField, passwordTextField, loginButton, registerButton].forEach {
            view.addSubview($0)
        }

        logoImageView.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.equalTo(view.safeAreaLayoutGuide).offset(40)
            make.width.height.equalTo(100)
        }

        usernameTextField.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(32)
            make.top.equalTo(logoImageView.snp.bottom).offset(40)
            make.height.equalTo(44)
        }

        passwordTextField.snp.makeConstraints { make in
            make.leading.trailing.equalTo(usernameTextField)
            make.top.equalTo(usernameTextField.snp.bottom).offset(16)
            make.height.equalTo(44)
        }

        loginButton.snp.makeConstraints { make in
            make.leading.trailing.equalTo(usernameTextField)
            make.top.equalTo(passwordTextField.snp.bottom).offset(24)
            make.height.equalTo(48)
        }

        registerButton.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.equalTo(loginButton.snp.bottom).offset(16)
        }

        loginButton.addTarget(self, action: #selector(loginTapped), for: .touchUpInside)
    }

    @objc private func loginTapped() {
        let alert = UIAlertController(title: "提示", message: "登录功能演示", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "确定", style: .default))
        present(alert, animated: true)
    }
}

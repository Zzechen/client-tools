import UIKit
import SnapKit

class LoginViewController: UIViewController {

    // MARK: - Properties

    private var viewModel = LoginViewModel()
    private var currentTab: Tab = .sms

    enum Tab { case sms, pwd, email }

    // MARK: - Nav Bar

    private lazy var closeButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("✕", for: .normal)
        btn.setTitleColor(.white, for: .normal)
        btn.titleLabel?.font = .systemFont(ofSize: 18)
        btn.accessibilityIdentifier = "login_btn_close"
        return btn
    }()

    private lazy var skipButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("跳过 ›", for: .normal)
        btn.setTitleColor(UIColor(hex: "#00D4C2"), for: .normal)
        btn.titleLabel?.font = .systemFont(ofSize: 14)
        btn.accessibilityIdentifier = "login_btn_skip"
        return btn
    }()

    // MARK: - Logo Section

    private lazy var logoIconView: UIView = {
        let v = UIView()
        v.backgroundColor = UIColor(hex: "#00D4C2")
        v.layer.cornerRadius = 10
        v.accessibilityIdentifier = "login_logo_icon"
        return v
    }()

    private lazy var brandLabel: UILabel = {
        let lbl = UILabel()
        lbl.text = "PULSE"
        lbl.textColor = .white
        lbl.font = .boldSystemFont(ofSize: 20)
        lbl.textAlignment = .center
        lbl.accessibilityIdentifier = "login_text_brand"
        return lbl
    }()

    // MARK: - Title Section

    private lazy var titleLabel: UILabel = {
        let lbl = UILabel()
        lbl.text = "欢迎回来"
        lbl.textColor = .white
        lbl.font = .boldSystemFont(ofSize: 28)
        lbl.accessibilityIdentifier = "login_text_title"
        return lbl
    }()

    private lazy var subtitleLabel: UILabel = {
        let lbl = UILabel()
        lbl.text = "// 登录以同步你的训练数据"
        lbl.textColor = UIColor(hex: "#8A9BB0")
        lbl.font = .systemFont(ofSize: 14)
        lbl.accessibilityIdentifier = "login_text_subtitle"
        return lbl
    }()

    // MARK: - Tab Selector

    private lazy var tabContainer: UIView = {
        let v = UIView()
        v.backgroundColor = UIColor(hex: "#0A1A20")
        v.layer.cornerRadius = 8
        v.accessibilityIdentifier = "login_tab_container"
        return v
    }()

    private lazy var tabSmsButton: UIButton = makeTabButton(title: "验证码", identifier: "login_tab_sms", selected: true)
    private lazy var tabPwdButton: UIButton = makeTabButton(title: "密码", identifier: "login_tab_pwd", selected: false)
    private lazy var tabEmailButton: UIButton = makeTabButton(title: "邮箱", identifier: "login_tab_email", selected: false)

    // MARK: - Input Area

    private lazy var inputStackView: UIStackView = {
        let sv = UIStackView()
        sv.axis = .vertical
        sv.spacing = 12
        sv.accessibilityIdentifier = "login_input_area"
        return sv
    }()

    // Phone container（SMS + PWD tabs）
    private lazy var phoneContainer: UIView = makeInputBg(identifier: "login_input_phone_container")
    private lazy var countryCodeLabel: UILabel = {
        let lbl = UILabel()
        lbl.text = "+86"
        lbl.textColor = .white
        lbl.font = .systemFont(ofSize: 15)
        lbl.accessibilityIdentifier = "login_text_country_code"
        return lbl
    }()
    private lazy var phoneSeparator: UIView = {
        let v = UIView()
        v.backgroundColor = UIColor(hex: "#1A3040")
        v.accessibilityIdentifier = "login_separator_phone"
        return v
    }()
    private lazy var phoneTextField: UITextField = {
        let tf = UITextField()
        tf.keyboardType = .phonePad
        tf.textColor = .white
        tf.font = .systemFont(ofSize: 15)
        tf.attributedPlaceholder = NSAttributedString(
            string: "请输入手机号",
            attributes: [.foregroundColor: UIColor(hex: "#4A6070")]
        )
        tf.accessibilityIdentifier = "login_input_phone"
        return tf
    }()

    // SMS section
    private lazy var smsContainer: UIView = makeInputBg(identifier: "login_input_sms_container")
    private lazy var smsCodeTextField: UITextField = {
        let tf = UITextField()
        tf.keyboardType = .numberPad
        tf.textColor = .white
        tf.font = .systemFont(ofSize: 16)
        tf.attributedPlaceholder = NSAttributedString(
            string: "6位验证码",
            attributes: [.foregroundColor: UIColor(hex: "#4A6070")]
        )
        tf.accessibilityIdentifier = "login_input_sms_code"
        return tf
    }()
    private lazy var sendCodeButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("发送验证码", for: .normal)
        btn.setTitleColor(UIColor(hex: "#00D4C2"), for: .normal)
        btn.setTitleColor(UIColor(hex: "#4A6070"), for: .disabled)
        btn.titleLabel?.font = .systemFont(ofSize: 13)
        btn.accessibilityIdentifier = "login_btn_send_code"
        return btn
    }()

    // PWD section
    private lazy var passwordContainer: UIView = makeInputBg(identifier: "login_input_password_container")
    private lazy var passwordTextField: UITextField = {
        let tf = UITextField()
        tf.isSecureTextEntry = true
        tf.textColor = .white
        tf.font = .systemFont(ofSize: 16)
        tf.attributedPlaceholder = NSAttributedString(
            string: "请输入密码",
            attributes: [.foregroundColor: UIColor(hex: "#4A6070")]
        )
        tf.accessibilityIdentifier = "login_input_password"
        return tf
    }()

    // EMAIL section
    private lazy var emailContainer: UIStackView = {
        let sv = UIStackView()
        sv.axis = .vertical
        sv.spacing = 12
        sv.accessibilityIdentifier = "login_input_email_container"
        return sv
    }()
    private lazy var emailTextField: UITextField = {
        let tf = UITextField()
        tf.keyboardType = .emailAddress
        tf.textColor = .white
        tf.font = .systemFont(ofSize: 16)
        tf.attributedPlaceholder = NSAttributedString(
            string: "请输入邮箱",
            attributes: [.foregroundColor: UIColor(hex: "#4A6070")]
        )
        tf.accessibilityIdentifier = "login_input_email"
        return tf
    }()
    private lazy var emailPasswordTextField: UITextField = {
        let tf = UITextField()
        tf.isSecureTextEntry = true
        tf.textColor = .white
        tf.font = .systemFont(ofSize: 16)
        tf.attributedPlaceholder = NSAttributedString(
            string: "请输入密码",
            attributes: [.foregroundColor: UIColor(hex: "#4A6070")]
        )
        tf.accessibilityIdentifier = "login_input_email_password"
        return tf
    }()

    // MARK: - Submit Button

    private lazy var submitButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("验证并登录 →", for: .normal)
        btn.setTitleColor(.black, for: .normal)
        btn.titleLabel?.font = .boldSystemFont(ofSize: 16)
        btn.backgroundColor = UIColor(hex: "#00D4C2")
        btn.layer.cornerRadius = 8
        btn.accessibilityIdentifier = "login_btn_submit"
        return btn
    }()

    private lazy var loadingIndicator: UIActivityIndicatorView = {
        let ai = UIActivityIndicatorView(style: .medium)
        ai.color = .black
        ai.hidesWhenStopped = true
        ai.accessibilityIdentifier = "login_btn_submit_progress"
        return ai
    }()

    // MARK: - Agreement Row

    private lazy var agreementCheckbox: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("○", for: .normal)
        btn.setTitle("●", for: .selected)
        btn.setTitleColor(UIColor(hex: "#8A9BB0"), for: .normal)
        btn.setTitleColor(UIColor(hex: "#00D4C2"), for: .selected)
        btn.titleLabel?.font = .systemFont(ofSize: 16)
        btn.isSelected = true
        btn.accessibilityIdentifier = "login_checkbox_agreement"
        return btn
    }()

    private lazy var agreementLabel: UILabel = {
        let lbl = UILabel()
        lbl.text = "已阅读并同意《用户协议》和《隐私政策》"
        lbl.textColor = UIColor(hex: "#8A9BB0")
        lbl.font = .systemFont(ofSize: 11)
        lbl.numberOfLines = 0
        lbl.accessibilityIdentifier = "login_text_agreement"
        return lbl
    }()

    // MARK: - OR Divider

    private lazy var orLeftLine: UIView = {
        let v = UIView()
        v.backgroundColor = UIColor(hex: "#1A3040")
        v.accessibilityIdentifier = "login_divider_left"
        return v
    }()
    private lazy var orLabel: UILabel = {
        let lbl = UILabel()
        lbl.text = "OR"
        lbl.textColor = UIColor(hex: "#4A6070")
        lbl.font = .systemFont(ofSize: 12)
        lbl.accessibilityIdentifier = "login_text_or"
        return lbl
    }()
    private lazy var orRightLine: UIView = {
        let v = UIView()
        v.backgroundColor = UIColor(hex: "#1A3040")
        v.accessibilityIdentifier = "login_divider_right"
        return v
    }()

    // MARK: - Social Icons

    private lazy var socialContainer: UIStackView = {
        let sv = UIStackView()
        sv.axis = .horizontal
        sv.distribution = .equalSpacing
        sv.alignment = .center
        sv.accessibilityIdentifier = "login_social_container"
        return sv
    }()

    // MARK: - Home Indicator

    private lazy var homeIndicator: UIView = {
        let v = UIView()
        v.backgroundColor = UIColor(hex: "#FFFFFF").withAlphaComponent(0.3)
        v.layer.cornerRadius = 2.5
        v.accessibilityIdentifier = "login_home_indicator"
        return v
    }()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        navigationController?.setNavigationBarHidden(true, animated: false)
        view.backgroundColor = UIColor(hex: "#001015")
        view.accessibilityIdentifier = "login_root"
        setupSocialIcons()
        setupHierarchy()
        setupConstraints()
        setupActions()
        setupViewModel()
        switchTab(.sms)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        navigationController?.setNavigationBarHidden(false, animated: animated)
    }

    // MARK: - Setup

    private func makeInputBg(identifier: String) -> UIView {
        let v = UIView()
        v.backgroundColor = UIColor(hex: "#0A1A20")
        v.layer.cornerRadius = 8
        v.layer.borderWidth = 1
        v.layer.borderColor = UIColor(hex: "#1A3040").cgColor
        v.accessibilityIdentifier = identifier
        return v
    }

    private func makeTabButton(title: String, identifier: String, selected: Bool) -> UIButton {
        let btn = UIButton(type: .system)
        btn.setTitle(title, for: .normal)
        btn.titleLabel?.font = .systemFont(ofSize: 13)
        btn.layer.cornerRadius = 6
        btn.accessibilityIdentifier = identifier
        if selected {
            btn.backgroundColor = UIColor(hex: "#00D4C2").withAlphaComponent(0.15)
            btn.setTitleColor(UIColor(hex: "#00D4C2"), for: .normal)
        } else {
            btn.backgroundColor = .clear
            btn.setTitleColor(UIColor(hex: "#8A9BB0"), for: .normal)
        }
        return btn
    }

    private func makeSocialIcon(label: String, identifier: String) -> UIButton {
        let btn = UIButton(type: .system)
        btn.backgroundColor = UIColor(hex: "#0A1A20")
        btn.layer.cornerRadius = 27
        btn.layer.borderWidth = 1
        btn.layer.borderColor = UIColor(hex: "#1A3040").cgColor
        btn.accessibilityIdentifier = identifier
        let lbl = UILabel()
        lbl.text = label
        lbl.textColor = UIColor(hex: "#8A9BB0")
        lbl.font = .boldSystemFont(ofSize: 16)
        lbl.textAlignment = .center
        lbl.isUserInteractionEnabled = false
        btn.addSubview(lbl)
        lbl.snp.makeConstraints { $0.edges.equalToSuperview() }
        btn.snp.makeConstraints { $0.width.height.equalTo(54) }
        return btn
    }

    private func setupSocialIcons() {
        let items: [(String, String)] = [
            ("W", "login_btn_social_wechat"),
            ("Q", "login_btn_social_qq"),
            ("A", "login_btn_social_apple"),
            ("G", "login_btn_social_google"),
        ]
        items.forEach { socialContainer.addArrangedSubview(makeSocialIcon(label: $0.0, identifier: $0.1)) }
    }

    private func setupHierarchy() {
        [closeButton, skipButton,
         logoIconView, brandLabel,
         titleLabel, subtitleLabel,
         tabContainer, inputStackView,
         submitButton,
         agreementCheckbox, agreementLabel,
         orLeftLine, orLabel, orRightLine,
         socialContainer, homeIndicator].forEach { view.addSubview($0) }

        submitButton.addSubview(loadingIndicator)

        [tabSmsButton, tabPwdButton, tabEmailButton].forEach { tabContainer.addSubview($0) }

        // Phone container children
        [countryCodeLabel, phoneSeparator, phoneTextField].forEach { phoneContainer.addSubview($0) }

        // SMS container children
        [smsCodeTextField, sendCodeButton].forEach { smsContainer.addSubview($0) }

        // Password container children
        passwordContainer.addSubview(passwordTextField)

        // Email container: two rows
        let emailRow = makeInputBg(identifier: "login_input_email_row")
        emailRow.addSubview(emailTextField)
        emailTextField.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.trailing.equalToSuperview().offset(-16)
            make.centerY.equalToSuperview()
        }
        let emailPwdRow = makeInputBg(identifier: "login_input_email_password_row")
        emailPwdRow.addSubview(emailPasswordTextField)
        emailPasswordTextField.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.trailing.equalToSuperview().offset(-16)
            make.centerY.equalToSuperview()
        }
        emailContainer.addArrangedSubview(emailRow)
        emailContainer.addArrangedSubview(emailPwdRow)

        // Add sections to inputStackView
        inputStackView.addArrangedSubview(phoneContainer)
        inputStackView.addArrangedSubview(smsContainer)
        inputStackView.addArrangedSubview(passwordContainer)
        inputStackView.addArrangedSubview(emailContainer)
    }

    private func setupConstraints() {
        closeButton.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(20)
            make.top.equalTo(view.safeAreaLayoutGuide).offset(12)
            make.width.height.equalTo(36)
        }
        skipButton.snp.makeConstraints { make in
            make.trailing.equalToSuperview().offset(-20)
            make.centerY.equalTo(closeButton)
            make.width.greaterThanOrEqualTo(54)
        }
        logoIconView.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(24)
            make.top.equalTo(closeButton.snp.bottom).offset(38)
            make.width.height.equalTo(44)
        }
        brandLabel.snp.makeConstraints { make in
            make.leading.equalTo(logoIconView.snp.trailing).offset(10)
            make.centerY.equalTo(logoIconView)
        }
        titleLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(24)
            make.top.equalTo(logoIconView.snp.bottom).offset(36)
        }
        subtitleLabel.snp.makeConstraints { make in
            make.leading.equalTo(titleLabel)
            make.top.equalTo(titleLabel.snp.bottom).offset(6)
        }
        tabContainer.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(24)
            make.top.equalTo(subtitleLabel.snp.bottom).offset(28)
            make.height.equalTo(40)
        }
        tabSmsButton.snp.makeConstraints { make in
            make.leading.top.bottom.equalToSuperview().inset(4)
            make.width.equalToSuperview().multipliedBy(1.0/3.0).offset(-4)
        }
        tabPwdButton.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.bottom.equalToSuperview().inset(4)
            make.width.equalTo(tabSmsButton)
        }
        tabEmailButton.snp.makeConstraints { make in
            make.trailing.top.bottom.equalToSuperview().inset(4)
            make.width.equalTo(tabSmsButton)
        }

        // inputStackView
        inputStackView.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(24)
            make.top.equalTo(tabContainer.snp.bottom).offset(16)
        }

        // Heights for stack children
        phoneContainer.snp.makeConstraints { $0.height.equalTo(52) }
        smsContainer.snp.makeConstraints { $0.height.equalTo(52) }
        passwordContainer.snp.makeConstraints { $0.height.equalTo(52) }
        emailContainer.arrangedSubviews.forEach { $0.snp.makeConstraints { $0.height.equalTo(52) } }

        // Phone container children
        countryCodeLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.centerY.equalToSuperview()
            make.width.equalTo(36)
        }
        phoneSeparator.snp.makeConstraints { make in
            make.leading.equalTo(countryCodeLabel.snp.trailing).offset(12)
            make.centerY.equalToSuperview()
            make.width.equalTo(1)
            make.height.equalTo(20)
        }
        phoneTextField.snp.makeConstraints { make in
            make.leading.equalTo(phoneSeparator.snp.trailing).offset(12)
            make.trailing.equalToSuperview().offset(-16)
            make.centerY.equalToSuperview()
        }

        // SMS container children
        smsCodeTextField.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.centerY.equalToSuperview()
            make.trailing.lessThanOrEqualTo(sendCodeButton.snp.leading).offset(-8)
        }
        sendCodeButton.snp.makeConstraints { make in
            make.trailing.equalToSuperview().offset(-16)
            make.centerY.equalToSuperview()
            make.width.greaterThanOrEqualTo(70)
        }

        // Password container children
        passwordTextField.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.trailing.equalToSuperview().offset(-16)
            make.centerY.equalToSuperview()
        }

        // Submit button
        submitButton.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(24)
            make.top.equalTo(inputStackView.snp.bottom).offset(22)
            make.height.equalTo(52)
        }
        loadingIndicator.snp.makeConstraints { make in
            make.center.equalToSuperview()
        }

        // Agreement
        agreementCheckbox.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(24)
            make.top.equalTo(submitButton.snp.bottom).offset(16)
            make.width.height.equalTo(20)
        }
        agreementLabel.snp.makeConstraints { make in
            make.leading.equalTo(agreementCheckbox.snp.trailing).offset(8)
            make.trailing.equalToSuperview().offset(-24)
            make.centerY.equalTo(agreementCheckbox)
        }

        // Social / OR
        socialContainer.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(40)
            make.bottom.equalTo(homeIndicator.snp.top).offset(-24)
            make.height.equalTo(54)
        }
        orLabel.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.bottom.equalTo(socialContainer.snp.top).offset(-18)
        }
        orLeftLine.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(24)
            make.centerY.equalTo(orLabel)
            make.height.equalTo(1)
            make.trailing.equalTo(orLabel.snp.leading).offset(-12)
        }
        orRightLine.snp.makeConstraints { make in
            make.trailing.equalToSuperview().offset(-24)
            make.centerY.equalTo(orLabel)
            make.height.equalTo(1)
            make.leading.equalTo(orLabel.snp.trailing).offset(12)
        }
        homeIndicator.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.bottom.equalTo(view.safeAreaLayoutGuide).offset(-8)
            make.width.equalTo(134)
            make.height.equalTo(5)
        }
    }

    private func setupActions() {
        closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        agreementCheckbox.addTarget(self, action: #selector(checkboxTapped), for: .touchUpInside)
        submitButton.addTarget(self, action: #selector(submitTapped), for: .touchUpInside)
        tabSmsButton.addTarget(self, action: #selector(tabSmsTapped), for: .touchUpInside)
        tabPwdButton.addTarget(self, action: #selector(tabPwdTapped), for: .touchUpInside)
        tabEmailButton.addTarget(self, action: #selector(tabEmailTapped), for: .touchUpInside)
        sendCodeButton.addTarget(self, action: #selector(sendCodeTapped), for: .touchUpInside)
    }

    private func setupViewModel() {
        viewModel.onStateChange = { [weak self] state in
            guard let self = self else { return }
            switch state {
            case .idle:
                self.setSubmitLoading(false)
            case .loading:
                self.setSubmitLoading(true)
            case .success(let user, let token):
                self.navigateToUserInfo(user: user, token: token)
            case .error(let message):
                self.setSubmitLoading(false)
                self.showToast(message)
                self.viewModel.resetState()
            }
        }
        viewModel.onCountdownChange = { [weak self] seconds in
            guard let self = self else { return }
            if seconds > 0 {
                self.sendCodeButton.isEnabled = false
                self.sendCodeButton.setTitle("\(seconds)s 后重发", for: .normal)
            } else {
                self.sendCodeButton.isEnabled = true
                self.sendCodeButton.setTitle("发送验证码", for: .normal)
            }
        }
    }

    // MARK: - Tab Switching

    private func switchTab(_ tab: Tab) {
        currentTab = tab
        [(tabSmsButton, tab == .sms), (tabPwdButton, tab == .pwd), (tabEmailButton, tab == .email)]
            .forEach { btn, selected in
                if selected {
                    btn.backgroundColor = UIColor(hex: "#00D4C2").withAlphaComponent(0.15)
                    btn.setTitleColor(UIColor(hex: "#00D4C2"), for: .normal)
                } else {
                    btn.backgroundColor = .clear
                    btn.setTitleColor(UIColor(hex: "#8A9BB0"), for: .normal)
                }
            }
        phoneContainer.isHidden = (tab == .email)
        smsContainer.isHidden = (tab != .sms)
        passwordContainer.isHidden = (tab != .pwd)
        emailContainer.isHidden = (tab != .email)
        switch tab {
        case .sms:   submitButton.setTitle("验证并登录 →", for: .normal)
        case .pwd:   submitButton.setTitle("密码登录 →", for: .normal)
        case .email: submitButton.setTitle("邮箱登录 →", for: .normal)
        }
    }

    // MARK: - Actions

    @objc private func closeTapped() {
        navigationController?.popViewController(animated: true)
    }

    @objc private func checkboxTapped() {
        agreementCheckbox.isSelected.toggle()
    }

    @objc private func tabSmsTapped()   { switchTab(.sms) }
    @objc private func tabPwdTapped()   { switchTab(.pwd) }
    @objc private func tabEmailTapped() { switchTab(.email) }

    @objc private func sendCodeTapped() {
        let phone = phoneTextField.text?.trimmingCharacters(in: .whitespaces) ?? ""
        guard phone.count == 11 else { showToast("请输入11位手机号"); return }
        viewModel.sendSmsCode(phone: phone)
    }

    @objc private func submitTapped() {
        switch currentTab {
        case .sms:
            let phone = phoneTextField.text?.trimmingCharacters(in: .whitespaces) ?? ""
            let code  = smsCodeTextField.text?.trimmingCharacters(in: .whitespaces) ?? ""
            guard phone.count == 11 else { showToast("请输入11位手机号"); return }
            guard code.count == 6  else { showToast("请输入6位验证码"); return }
            viewModel.loginSms(phone: phone, code: code)
        case .pwd:
            let phone = phoneTextField.text?.trimmingCharacters(in: .whitespaces) ?? ""
            let pwd   = passwordTextField.text ?? ""
            guard phone.count == 11 else { showToast("请输入11位手机号"); return }
            guard !pwd.isEmpty      else { showToast("请输入密码"); return }
            viewModel.loginPassword(phone: phone, password: pwd)
        case .email:
            let email = emailTextField.text?.trimmingCharacters(in: .whitespaces) ?? ""
            let pwd   = emailPasswordTextField.text ?? ""
            guard email.contains("@") else { showToast("请输入有效邮箱"); return }
            guard !pwd.isEmpty        else { showToast("请输入密码"); return }
            viewModel.loginEmail(email: email, password: pwd)
        }
    }

    // MARK: - Helpers

    private func setSubmitLoading(_ loading: Bool) {
        submitButton.isEnabled = !loading
        submitButton.setTitle(loading ? "" : titleForCurrentTab(), for: .normal)
        loading ? loadingIndicator.startAnimating() : loadingIndicator.stopAnimating()
    }

    private func titleForCurrentTab() -> String {
        switch currentTab {
        case .sms:   return "验证并登录 →"
        case .pwd:   return "密码登录 →"
        case .email: return "邮箱登录 →"
        }
    }

    private func navigateToUserInfo(user: UserInfo, token: String) {
        let vc = UserInfoViewController(user: user, token: token)
        navigationController?.pushViewController(vc, animated: true)
    }

    private func showToast(_ message: String) {
        let toast = UILabel()
        toast.text = message
        toast.textColor = .white
        toast.backgroundColor = UIColor.black.withAlphaComponent(0.7)
        toast.textAlignment = .center
        toast.font = .systemFont(ofSize: 14)
        toast.layer.cornerRadius = 6
        toast.clipsToBounds = true
        toast.numberOfLines = 0
        view.addSubview(toast)
        toast.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.bottom.equalTo(view.safeAreaLayoutGuide).offset(-60)
            make.leading.greaterThanOrEqualToSuperview().offset(24)
            make.trailing.lessThanOrEqualToSuperview().offset(-24)
            make.height.greaterThanOrEqualTo(36)
        }
        UIView.animate(withDuration: 0.3, delay: 2.0, options: [], animations: {
            toast.alpha = 0
        }) { _ in toast.removeFromSuperview() }
    }
}

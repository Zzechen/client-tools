# iOS Demo 重构 + Login/VerifyCode 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除 Demo 多余页面（Profile/Settings/List），重构 Home 只保留 Login 和 VerifyCode 入口，并按设计稿实现这两个页面。

**Architecture:** UIKit + SnapKit，纯代码布局，无 Storyboard。LoginViewController 对应 login-phone.html（手机号验证码登录），VerifyCodeViewController 对应 login-code.html（6 位验证码输入）。所有 View 设置 `accessibilityIdentifier`，命名规则 `<page>_<semantic>`。

**Tech Stack:** Swift 5, UIKit, SnapKit, iOS 14+

---

## 文件结构

```
clients/ios/demo/Sources/ClientToolsDemo/
├── Home/
│   ├── HomeViewController.swift       ← 修改：pages 改为 Login + VerifyCode
│   └── HomeCell.swift                 ← 不改
├── Login/
│   └── LoginViewController.swift      ← 重写：按 login-phone.html 实现
├── VerifyCode/
│   └── VerifyCodeViewController.swift ← 新建：按 login-code.html 实现
├── Profile/                           ← 删除整个目录
├── Settings/                          ← 删除整个目录
├── List/                              ← 删除整个目录
├── AppDelegate.swift                  ← 不改
└── SceneDelegate.swift                ← 不改
```

---

## Task 1: Demo 清理 + 骨架重建

**Files:**
- Delete: `Sources/ClientToolsDemo/Profile/ProfileViewController.swift`
- Delete: `Sources/ClientToolsDemo/Settings/SettingsViewController.swift`
- Delete: `Sources/ClientToolsDemo/Settings/SettingsDetailCell.swift`
- Delete: `Sources/ClientToolsDemo/Settings/SettingsSwitchCell.swift`
- Delete: `Sources/ClientToolsDemo/List/ListViewController.swift`
- Delete: `Sources/ClientToolsDemo/List/ListCell.swift`
- Modify: `Sources/ClientToolsDemo/Home/HomeViewController.swift`
- Create: `Sources/ClientToolsDemo/VerifyCode/VerifyCodeViewController.swift`

- [ ] **Step 1: 删除多余目录**

```bash
cd /Users/zzc/Desktop/works/client-tools/clients/ios/demo
rm -rf Sources/ClientToolsDemo/Profile
rm -rf Sources/ClientToolsDemo/Settings
rm -rf Sources/ClientToolsDemo/List
```

- [ ] **Step 2: 更新 HomeViewController — pages 改为 2 项**

将 `Sources/ClientToolsDemo/Home/HomeViewController.swift` 中的 `pages` 数组替换为：

```swift
private let pages: [(title: String, subtitle: String, icon: String, vcClass: String)] = [
    ("Login Demo", "手机号验证码登录", "📱", "LoginViewController"),
    ("VerifyCode Demo", "验证码输入页", "🔐", "VerifyCodeViewController"),
]
```

- [ ] **Step 3: 新建 VerifyCodeViewController 占位**

创建 `Sources/ClientToolsDemo/VerifyCode/VerifyCodeViewController.swift`：

```swift
import UIKit

class VerifyCodeViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(hex: "#0A0B0F")
        view.accessibilityIdentifier = "verify_root"
    }
}
```

（`UIColor(hex:)` 扩展在 Task 2 中定义，此处先留占位。）

- [ ] **Step 4: 确认 Xcode 可编译**

在 Xcode 中确认项目能 Build（⌘B），删除文件后如有 compile error 说明有残留引用，检查并移除。

- [ ] **Step 5: Commit**

```bash
git add -A clients/ios/demo/Sources/ClientToolsDemo/
git commit -m "feat(ios-demo): remove Profile/Settings/List, add VerifyCode placeholder"
```

---

## Task 2: 实现 LoginViewController（login-phone.html 设计稿）

**设计稿关键数据（login-phone.html，viewport=390）：**
- 背景色：`#001015`
- 顶部导航：关闭按钮（左）+ 跳过按钮（右），距顶 ~56pt
- Logo 区：44×44 图标 + "PULSE" 品牌文字，竖排
- 标题区："欢迎回来" + 副标题
- Tab 选择器：验证码 / 密码 / 邮箱（3 tab，选中高亮）
- 手机输入区：`+86` 区号 + 分隔线 + 号码输入框
- 提交按钮："获取验证码 →"，青色 `#00D4C2`，圆角 8
- 协议行：复选框 + "已阅读并同意..."
- OR 分隔线
- 社交登录：4 个圆形图标按钮（54dp）
- Home indicator bar

**Files:**
- Modify: `Sources/ClientToolsDemo/Login/LoginViewController.swift`

- [ ] **Step 1: 定义 UIColor hex 扩展**

在 `LoginViewController.swift` 顶部（import 之后，class 之前）添加：

```swift
extension UIColor {
    convenience init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default: (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(red: CGFloat(r)/255, green: CGFloat(g)/255, blue: CGFloat(b)/255, alpha: CGFloat(a)/255)
    }
}
```

- [ ] **Step 2: 完整替换 LoginViewController**

用以下代码完整替换 `Sources/ClientToolsDemo/Login/LoginViewController.swift`：

```swift
import UIKit
import SnapKit

extension UIColor {
    convenience init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default: (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(red: CGFloat(r)/255, green: CGFloat(g)/255, blue: CGFloat(b)/255, alpha: CGFloat(a)/255)
    }
}

class LoginViewController: UIViewController {

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
        btn.setTitle("跳过", for: .normal)
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
        lbl.text = "登录以继续使用"
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

    // MARK: - Phone Input

    private lazy var phoneContainer: UIView = {
        let v = UIView()
        v.backgroundColor = UIColor(hex: "#0A1A20")
        v.layer.cornerRadius = 8
        v.layer.borderWidth = 1
        v.layer.borderColor = UIColor(hex: "#1A3040").cgColor
        v.accessibilityIdentifier = "login_input_phone_container"
        return v
    }()

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
        tf.placeholder = "请输入手机号"
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

    // MARK: - Submit Button

    private lazy var submitButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("获取验证码 →", for: .normal)
        btn.setTitleColor(.black, for: .normal)
        btn.titleLabel?.font = .boldSystemFont(ofSize: 16)
        btn.backgroundColor = UIColor(hex: "#00D4C2")
        btn.layer.cornerRadius = 8
        btn.accessibilityIdentifier = "login_btn_submit"
        return btn
    }()

    // MARK: - Agreement Row

    private lazy var agreementCheckbox: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("○", for: .normal)
        btn.setTitle("●", for: .selected)
        btn.setTitleColor(UIColor(hex: "#8A9BB0"), for: .normal)
        btn.setTitleColor(UIColor(hex: "#00D4C2"), for: .selected)
        btn.titleLabel?.font = .systemFont(ofSize: 16)
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
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        navigationController?.setNavigationBarHidden(false, animated: animated)
    }

    // MARK: - Setup

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

    private func makeSocialIcon(identifier: String) -> UIButton {
        let btn = UIButton(type: .system)
        btn.backgroundColor = UIColor(hex: "#0A1A20")
        btn.layer.cornerRadius = 27
        btn.layer.borderWidth = 1
        btn.layer.borderColor = UIColor(hex: "#1A3040").cgColor
        btn.accessibilityIdentifier = identifier
        return btn
    }

    private func setupSocialIcons() {
        let identifiers = ["login_btn_social_wechat", "login_btn_social_qq", "login_btn_social_apple", "login_btn_social_google"]
        let labels = ["W", "Q", "A", "G"]
        for (i, id) in identifiers.enumerated() {
            let btn = makeSocialIcon(identifier: id)
            let lbl = UILabel()
            lbl.text = labels[i]
            lbl.textColor = UIColor(hex: "#8A9BB0")
            lbl.font = .boldSystemFont(ofSize: 16)
            lbl.textAlignment = .center
            btn.addSubview(lbl)
            lbl.snp.makeConstraints { $0.edges.equalToSuperview() }
            btn.snp.makeConstraints { $0.width.height.equalTo(54) }
            socialContainer.addArrangedSubview(btn)
        }
    }

    private func setupHierarchy() {
        [closeButton, skipButton,
         logoIconView, brandLabel,
         titleLabel, subtitleLabel,
         tabContainer, phoneContainer,
         submitButton,
         agreementCheckbox, agreementLabel,
         orLeftLine, orLabel, orRightLine,
         socialContainer, homeIndicator].forEach { view.addSubview($0) }

        [tabSmsButton, tabPwdButton, tabEmailButton].forEach { tabContainer.addSubview($0) }
        [countryCodeLabel, phoneSeparator, phoneTextField].forEach { phoneContainer.addSubview($0) }
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
        }

        logoIconView.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.equalTo(closeButton.snp.bottom).offset(28)
            make.width.height.equalTo(44)
        }

        brandLabel.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.equalTo(logoIconView.snp.bottom).offset(8)
        }

        titleLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(24)
            make.top.equalTo(brandLabel.snp.bottom).offset(32)
        }

        subtitleLabel.snp.makeConstraints { make in
            make.leading.equalTo(titleLabel)
            make.top.equalTo(titleLabel.snp.bottom).offset(6)
        }

        tabContainer.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(24)
            make.top.equalTo(subtitleLabel.snp.bottom).offset(20)
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

        phoneContainer.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(24)
            make.top.equalTo(tabContainer.snp.bottom).offset(16)
            make.height.equalTo(52)
        }

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

        submitButton.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(24)
            make.top.equalTo(phoneContainer.snp.bottom).offset(20)
            make.height.equalTo(50)
        }

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

        orLeftLine.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(24)
            make.top.equalTo(agreementLabel.snp.bottom).offset(28)
            make.height.equalTo(1)
            make.trailing.equalTo(orLabel.snp.leading).offset(-12)
        }

        orLabel.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.centerY.equalTo(orLeftLine)
        }

        orRightLine.snp.makeConstraints { make in
            make.trailing.equalToSuperview().offset(-24)
            make.centerY.equalTo(orLeftLine)
            make.height.equalTo(1)
            make.leading.equalTo(orLabel.snp.trailing).offset(12)
        }

        socialContainer.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(40)
            make.top.equalTo(orLeftLine.snp.bottom).offset(24)
            make.height.equalTo(54)
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
    }

    @objc private func closeTapped() {
        navigationController?.popViewController(animated: true)
    }

    @objc private func checkboxTapped() {
        agreementCheckbox.isSelected.toggle()
    }

    @objc private func submitTapped() {
        guard let phone = phoneTextField.text, !phone.isEmpty else { return }
        let vc = VerifyCodeViewController()
        navigationController?.pushViewController(vc, animated: true)
    }
}
```

- [ ] **Step 3: Build 确认无报错**

```
⌘B in Xcode — Expected: Build Succeeded
```

- [ ] **Step 4: Commit**

```bash
git add clients/ios/demo/Sources/ClientToolsDemo/Login/LoginViewController.swift
git commit -m "feat(ios-demo): implement LoginViewController from login-phone.html design"
```

---

## Task 3: 实现 VerifyCodeViewController（login-code.html 设计稿）

**设计稿关键数据（login-code.html，viewport=390）：**
- 背景色：`#0A0B0F`
- 顶部：返回按钮（左）+ "STEP 02/02" 标签（右）
- Hero 区："输入验证码" 大标题 + "+86 138****8888" 副标题
- 6 格验证码输入框（filled=有内容/current=当前激活/empty=待输入），每格约 46×56，间距 8
- 重新发送行："未收到验证码？" + "重新发送" (59s 倒计时)
- 确认登录按钮（禁用态，灰色，待填满后可点）
- 协议行（同 Login）
- Home indicator

**Files:**
- Modify: `Sources/ClientToolsDemo/VerifyCode/VerifyCodeViewController.swift`

- [ ] **Step 1: 完整实现 VerifyCodeViewController**

用以下代码替换 `Sources/ClientToolsDemo/VerifyCode/VerifyCodeViewController.swift`：

```swift
import UIKit
import SnapKit

class VerifyCodeViewController: UIViewController {

    private let codeLength = 6
    private var enteredCode: [String] = []
    private var countdownSeconds = 59
    private var countdownTimer: Timer?

    // MARK: - Nav

    private lazy var backButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("←", for: .normal)
        btn.setTitleColor(.white, for: .normal)
        btn.titleLabel?.font = .systemFont(ofSize: 20)
        btn.accessibilityIdentifier = "verify_btn_back"
        return btn
    }()

    private lazy var stepLabel: UILabel = {
        let lbl = UILabel()
        lbl.text = "STEP 02/02"
        lbl.textColor = UIColor(hex: "#00D4C2")
        lbl.font = .boldSystemFont(ofSize: 12)
        lbl.accessibilityIdentifier = "verify_text_step"
        return lbl
    }()

    // MARK: - Hero

    private lazy var titleLabel: UILabel = {
        let lbl = UILabel()
        lbl.text = "输入验证码"
        lbl.textColor = .white
        lbl.font = .boldSystemFont(ofSize: 28)
        lbl.accessibilityIdentifier = "verify_text_title"
        return lbl
    }()

    private lazy var phoneHintLabel: UILabel = {
        let lbl = UILabel()
        lbl.text = "已发送至 +86 138****8888"
        lbl.textColor = UIColor(hex: "#8A9BB0")
        lbl.font = .systemFont(ofSize: 14)
        lbl.accessibilityIdentifier = "verify_text_phone_hint"
        return lbl
    }()

    // MARK: - Code Boxes

    private lazy var codeBoxContainer: UIStackView = {
        let sv = UIStackView()
        sv.axis = .horizontal
        sv.spacing = 8
        sv.distribution = .fillEqually
        sv.accessibilityIdentifier = "verify_code_container"
        return sv
    }()

    private var codeBoxes: [UIView] = []

    private lazy var hiddenTextField: UITextField = {
        let tf = UITextField()
        tf.keyboardType = .numberPad
        tf.isHidden = true
        tf.accessibilityIdentifier = "verify_input_hidden"
        return tf
    }()

    // MARK: - Resend Row

    private lazy var resendHintLabel: UILabel = {
        let lbl = UILabel()
        lbl.text = "未收到验证码？"
        lbl.textColor = UIColor(hex: "#8A9BB0")
        lbl.font = .systemFont(ofSize: 13)
        lbl.accessibilityIdentifier = "verify_text_resend_hint"
        return lbl
    }()

    private lazy var resendButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("重新发送 (59s)", for: .normal)
        btn.setTitleColor(UIColor(hex: "#4A6070"), for: .normal)
        btn.titleLabel?.font = .systemFont(ofSize: 13)
        btn.isEnabled = false
        btn.accessibilityIdentifier = "verify_btn_resend"
        return btn
    }()

    // MARK: - Confirm Button

    private lazy var confirmButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("确认登录", for: .normal)
        btn.setTitleColor(UIColor(hex: "#4A6070"), for: .normal)
        btn.setTitleColor(.black, for: .normal)
        btn.titleLabel?.font = .boldSystemFont(ofSize: 16)
        btn.backgroundColor = UIColor(hex: "#1A3040")
        btn.layer.cornerRadius = 8
        btn.isEnabled = false
        btn.accessibilityIdentifier = "verify_btn_confirm"
        return btn
    }()

    // MARK: - Agreement

    private lazy var agreementCheckbox: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("○", for: .normal)
        btn.setTitle("●", for: .selected)
        btn.setTitleColor(UIColor(hex: "#8A9BB0"), for: .normal)
        btn.setTitleColor(UIColor(hex: "#00D4C2"), for: .selected)
        btn.titleLabel?.font = .systemFont(ofSize: 16)
        btn.accessibilityIdentifier = "verify_checkbox_agreement"
        return btn
    }()

    private lazy var agreementLabel: UILabel = {
        let lbl = UILabel()
        lbl.text = "已阅读并同意《用户协议》和《隐私政策》"
        lbl.textColor = UIColor(hex: "#8A9BB0")
        lbl.font = .systemFont(ofSize: 11)
        lbl.numberOfLines = 0
        lbl.accessibilityIdentifier = "verify_text_agreement"
        return lbl
    }()

    // MARK: - Home Indicator

    private lazy var homeIndicator: UIView = {
        let v = UIView()
        v.backgroundColor = UIColor(hex: "#FFFFFF").withAlphaComponent(0.3)
        v.layer.cornerRadius = 2.5
        v.accessibilityIdentifier = "verify_home_indicator"
        return v
    }()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        navigationController?.setNavigationBarHidden(true, animated: false)
        view.backgroundColor = UIColor(hex: "#0A0B0F")
        view.accessibilityIdentifier = "verify_root"
        buildCodeBoxes()
        setupHierarchy()
        setupConstraints()
        setupActions()
        startCountdown()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        countdownTimer?.invalidate()
        navigationController?.setNavigationBarHidden(false, animated: animated)
    }

    // MARK: - Setup

    private func buildCodeBoxes() {
        for i in 0..<codeLength {
            let box = makeCodeBox(index: i)
            codeBoxes.append(box)
            codeBoxContainer.addArrangedSubview(box)
        }
    }

    private func makeCodeBox(index: Int) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor(hex: "#0E1A24")
        container.layer.cornerRadius = 8
        container.layer.borderWidth = 1.5
        container.layer.borderColor = UIColor(hex: "#1A3040").cgColor
        container.accessibilityIdentifier = "verify_code_box_\(index)"

        let lbl = UILabel()
        lbl.textColor = .white
        lbl.font = .boldSystemFont(ofSize: 24)
        lbl.textAlignment = .center
        lbl.tag = 100 + index
        container.addSubview(lbl)
        lbl.snp.makeConstraints { $0.edges.equalToSuperview() }

        return container
    }

    private func setupHierarchy() {
        [backButton, stepLabel,
         titleLabel, phoneHintLabel,
         codeBoxContainer, hiddenTextField,
         resendHintLabel, resendButton,
         confirmButton,
         agreementCheckbox, agreementLabel,
         homeIndicator].forEach { view.addSubview($0) }
    }

    private func setupConstraints() {
        backButton.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(20)
            make.top.equalTo(view.safeAreaLayoutGuide).offset(12)
            make.width.height.equalTo(36)
        }

        stepLabel.snp.makeConstraints { make in
            make.trailing.equalToSuperview().offset(-24)
            make.centerY.equalTo(backButton)
        }

        titleLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(24)
            make.top.equalTo(backButton.snp.bottom).offset(40)
        }

        phoneHintLabel.snp.makeConstraints { make in
            make.leading.equalTo(titleLabel)
            make.top.equalTo(titleLabel.snp.bottom).offset(8)
        }

        codeBoxContainer.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(24)
            make.top.equalTo(phoneHintLabel.snp.bottom).offset(32)
            make.height.equalTo(60)
        }

        resendHintLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(24)
            make.top.equalTo(codeBoxContainer.snp.bottom).offset(20)
        }

        resendButton.snp.makeConstraints { make in
            make.leading.equalTo(resendHintLabel.snp.trailing).offset(4)
            make.centerY.equalTo(resendHintLabel)
        }

        confirmButton.snp.makeConstraints { make in
            make.leading.trailing.equalToSuperview().inset(24)
            make.top.equalTo(resendHintLabel.snp.bottom).offset(32)
            make.height.equalTo(50)
        }

        agreementCheckbox.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(24)
            make.top.equalTo(confirmButton.snp.bottom).offset(20)
            make.width.height.equalTo(20)
        }

        agreementLabel.snp.makeConstraints { make in
            make.leading.equalTo(agreementCheckbox.snp.trailing).offset(8)
            make.trailing.equalToSuperview().offset(-24)
            make.centerY.equalTo(agreementCheckbox)
        }

        homeIndicator.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.bottom.equalTo(view.safeAreaLayoutGuide).offset(-8)
            make.width.equalTo(134)
            make.height.equalTo(5)
        }
    }

    private func setupActions() {
        backButton.addTarget(self, action: #selector(backTapped), for: .touchUpInside)
        agreementCheckbox.addTarget(self, action: #selector(checkboxTapped), for: .touchUpInside)
        confirmButton.addTarget(self, action: #selector(confirmTapped), for: .touchUpInside)
        resendButton.addTarget(self, action: #selector(resendTapped), for: .touchUpInside)

        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(viewTapped)))
        hiddenTextField.addTarget(self, action: #selector(textChanged), for: .editingChanged)
    }

    // MARK: - Countdown

    private func startCountdown() {
        countdownSeconds = 59
        countdownTimer?.invalidate()
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self else { return }
            self.countdownSeconds -= 1
            if self.countdownSeconds <= 0 {
                self.countdownTimer?.invalidate()
                self.resendButton.setTitle("重新发送", for: .normal)
                self.resendButton.setTitleColor(UIColor(hex: "#00D4C2"), for: .normal)
                self.resendButton.isEnabled = true
            } else {
                self.resendButton.setTitle("重新发送 (\(self.countdownSeconds)s)", for: .normal)
            }
        }
    }

    // MARK: - Code Input

    private func updateCodeDisplay() {
        for i in 0..<codeLength {
            let box = codeBoxes[i]
            let lbl = box.viewWithTag(100 + i) as? UILabel
            let isCurrent = i == enteredCode.count
            let filled = i < enteredCode.count

            lbl?.text = filled ? enteredCode[i] : ""
            if filled {
                box.layer.borderColor = UIColor(hex: "#00D4C2").withAlphaComponent(0.5).cgColor
            } else if isCurrent {
                box.layer.borderColor = UIColor(hex: "#00D4C2").cgColor
            } else {
                box.layer.borderColor = UIColor(hex: "#1A3040").cgColor
            }
        }

        let complete = enteredCode.count == codeLength
        confirmButton.isEnabled = complete
        confirmButton.backgroundColor = complete ? UIColor(hex: "#00D4C2") : UIColor(hex: "#1A3040")
        confirmButton.setTitleColor(complete ? .black : UIColor(hex: "#4A6070"), for: .normal)
    }

    // MARK: - Actions

    @objc private func viewTapped() {
        hiddenTextField.becomeFirstResponder()
    }

    @objc private func textChanged() {
        let text = hiddenTextField.text ?? ""
        let digits = text.filter { $0.isNumber }
        enteredCode = Array(digits.prefix(codeLength)).map { String($0) }
        hiddenTextField.text = enteredCode.joined()
        updateCodeDisplay()
    }

    @objc private func backTapped() {
        navigationController?.popViewController(animated: true)
    }

    @objc private func checkboxTapped() {
        agreementCheckbox.isSelected.toggle()
    }

    @objc private func confirmTapped() {
        let alert = UIAlertController(title: "登录成功", message: "验证码：\(enteredCode.joined())", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "确定", style: .default))
        present(alert, animated: true)
    }

    @objc private func resendTapped() {
        enteredCode = []
        hiddenTextField.text = ""
        updateCodeDisplay()
        startCountdown()
    }
}
```

- [ ] **Step 2: Build 确认无报错**

```
⌘B in Xcode — Expected: Build Succeeded
```

- [ ] **Step 3: 真机/模拟器运行验证**

1. Home 页只显示 2 行（Login Demo、VerifyCode Demo）
2. 点 Login Demo → 深色背景登录页，关闭/跳过按钮正常
3. 输入手机号 → 点"获取验证码"→ 跳转验证码页
4. 验证码页 6 格高亮、倒计时正常、填满后确认按钮变青色

- [ ] **Step 4: Commit**

```bash
git add clients/ios/demo/Sources/ClientToolsDemo/VerifyCode/VerifyCodeViewController.swift
git commit -m "feat(ios-demo): implement VerifyCodeViewController from login-code.html design"
```

---

## 自检

| 需求点 | 覆盖 Task |
|--------|-----------|
| 删除 Profile/Settings/List | Task 1 Step 1 |
| Home 只剩 Login + VerifyCode | Task 1 Step 2 |
| Login 深色背景 #001015 | Task 2 Step 2 |
| Login accessibilityIdentifier 全覆盖 | Task 2 Step 2 |
| Login → VerifyCode 导航 | Task 2 Step 2 (`submitTapped`) |
| VerifyCode 深色背景 #0A0B0F | Task 3 Step 1 |
| VerifyCode 6 格动态高亮 | Task 3 Step 1 |
| VerifyCode 倒计时 59s | Task 3 Step 1 |
| VerifyCode 按钮禁用/激活 | Task 3 Step 1 |
| VerifyCode accessibilityIdentifier 全覆盖 | Task 3 Step 1 |

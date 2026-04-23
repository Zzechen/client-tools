# iOS Demo App — Implementation Plan

**日期**：2026-04-24  
**范围**：iOS Demo App 开发

---

## Phase 1：项目初始化

### Task 1.1：创建项目结构

**文件**：`packages/ios/demo/project.yml`

```yaml
name: ClientToolsDemo
options:
  bundleIdPrefix: com.clienttools
  deploymentTarget:
    iOS: "14.0"
targets:
  ClientToolsDemo:
    type: application
    platform: iOS
    sources:
      - Sources
    settings:
      base:
        INFOPLIST_FILE: Sources/Info.plist
        PRODUCT_BUNDLE_IDENTIFIER: com.clienttools.demo
        DEVELOPMENT_TEAM: ""
        CODE_SIGN_IDENTITY: ""
        CODE_SIGNING_REQUIRED: NO
        CODE_SIGNING_ALLOWED: NO
```

---

### Task 1.2：创建 Podfile

**文件**：`packages/ios/demo/Podfile`

```ruby
platform :ios, '14.0'
use_frameworks!

target 'ClientToolsDemo' do
  pod 'SnapKit', '~> 5.6'
  pod 'ClientToolsSDK', :path => '../sdk'
end
```

---

### Task 1.3：创建 Info.plist

**文件**：`packages/ios/demo/Sources/Info.plist`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "...">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>$(DEVELOPMENT_LANGUAGE)</string>
    <key>CFBundleExecutable</key>
    <string>$(EXECUTABLE_NAME)</string>
    <key>CFBundleIdentifier</key>
    <string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
    <key>UIApplicationSceneManifest</key>
    <dict>
        <key>UIApplicationSupportsMultipleScenes</key>
        <false/>
        <key>UISceneConfigurations</key>
        <dict>
            <key>UIWindowSceneSessionRoleApplication</key>
            <array>
                <dict>
                    <key>UISceneConfigurationName</key>
                    <string>Default Configuration</string>
                    <key>UISceneDelegateClassName</key>
                    <string>$(PRODUCT_MODULE_NAME).SceneDelegate</string>
                </dict>
            </array>
        </dict>
    </dict>
    <key>UILaunchStoryboardName</key>
    <string>LaunchScreen</string>
    <key>UIRequiredDeviceCapabilities</key>
    <array>
        <string>armv7</string>
    </array>
    <key>UISupportedInterfaceOrientations</key>
    <array>
        <string>UIInterfaceOrientationPortrait</string>
    </array>
    <key>NSAppTransportSecurity</key>
    <dict>
        <key>NSAllowsArbitraryLoads</key>
        <true/>
    </dict>
</dict>
</plist>
```

---

### Task 1.4：创建 AppDelegate

**文件**：`packages/ios/demo/Sources/ClientToolsDemo/AppDelegate.swift`

```swift
import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        #if DEBUG
        ClientToolsSDK.shared.init()
        #endif
        return true
    }

    // MARK: UISceneSession Lifecycle

    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }

    func application(_ application: UIApplication, didDiscardSceneSessions sceneSessions: Set<UISceneSession>) {
    }
}
```

---

### Task 1.5：创建 SceneDelegate

**文件**：`packages/ios/demo/Sources/ClientToolsDemo/SceneDelegate.swift`

```swift
import UIKit

class SceneDelegate: UIResponder, UIWindowSceneDelegate {

    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = (scene as? UIWindowScene) else { return }

        window = UIWindow(windowScene: windowScene)
        let homeVC = HomeViewController()
        let navController = UINavigationController(rootViewController: homeVC)
        window?.rootViewController = navController
        window?.makeKeyAndVisible()
    }
}
```

---

## Phase 2：首页列表

### Task 2.1：HomeViewController

**文件**：`packages/ios/demo/Sources/ClientToolsDemo/Home/HomeViewController.swift`

```swift
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
        cell.accessibilityIdentifier = "home_cell_\(page.vcClass.lowercased().replacingOccurrences(of: "viewcontroller", with: ""))"
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
```

---

### Task 2.2：HomeCell

**文件**：`packages/ios/demo/Sources/ClientToolsDemo/Home/HomeCell.swift`

```swift
import UIKit
import SnapKit

class HomeCell: UITableViewCell {

    static let identifier = "HomeCell"

    private let iconLabel: UILabel = {
        let label = UILabel()
        label.font = .systemFont(ofSize: 24)
        return label
    }()

    private let titleLabel: UILabel = {
        let label = UILabel()
        label.font = .boldSystemFont(ofSize: 16)
        return label
    }()

    private let subtitleLabel: UILabel = {
        let label = UILabel()
        label.font = .systemFont(ofSize: 14)
        label.textColor = .secondaryLabel
        return label
    }()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        accessoryType = .disclosureIndicator

        contentView.addSubview(iconLabel)
        contentView.addSubview(titleLabel)
        contentView.addSubview(subtitleLabel)

        iconLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.centerY.equalToSuperview()
            make.width.height.equalTo(32)
        }

        titleLabel.snp.makeConstraints { make in
            make.leading.equalTo(iconLabel.snp.trailing).offset(12)
            make.top.equalToSuperview().offset(12)
            make.trailing.equalToSuperview().offset(-16)
        }

        subtitleLabel.snp.makeConstraints { make in
            make.leading.equalTo(titleLabel)
            make.top.equalTo(titleLabel.snp.bottom).offset(4)
            make.trailing.equalToSuperview().offset(-16)
            make.bottom.equalToSuperview().offset(-12)
        }
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(title: String, subtitle: String, icon: String) {
        titleLabel.text = title
        subtitleLabel.text = subtitle
        iconLabel.text = icon
    }
}
```

---

## Phase 3：Login 页面

### Task 3.1：LoginViewController

**文件**：`packages/ios/demo/Sources/ClientToolsDemo/Login/LoginViewController.swift`

```swift
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
```

---

## Phase 4：Profile 页面

### Task 4.1：ProfileViewController

**文件**：`packages/ios/demo/Sources/ClientToolsDemo/Profile/ProfileViewController.swift`

```swift
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
```

---

## Phase 5：Settings 页面

### Task 5.1：SettingsViewController

**文件**：`packages/ios/demo/Sources/ClientToolsDemo/Settings/SettingsViewController.swift`

```swift
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
```

### Task 5.2：SettingsSwitchCell

**文件**：`packages/ios/demo/Sources/ClientToolsDemo/Settings/SettingsSwitchCell.swift`

```swift
import UIKit
import SnapKit

class SettingsSwitchCell: UITableViewCell {

    static let identifier = "SettingsSwitchCell"

    private let titleLabel = UILabel()
    private let switchControl = UISwitch()

    private var rowId: String = ""

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none

        contentView.addSubview(titleLabel)
        contentView.addSubview(switchControl)

        titleLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.centerY.equalToSuperview()
        }

        switchControl.snp.makeConstraints { make in
            make.trailing.equalToSuperview().offset(-16)
            make.centerY.equalToSuperview()
        }
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(id: String, title: String, isOn: Bool) {
        rowId = id
        accessibilityIdentifier = id
        titleLabel.text = title
        switchControl.isOn = isOn
    }
}
```

### Task 5.3：SettingsDetailCell

**文件**：`packages/ios/demo/Sources/ClientToolsDemo/Settings/SettingsDetailCell.swift`

```swift
import UIKit
import SnapKit

class SettingsDetailCell: UITableViewCell {

    static let identifier = "SettingsDetailCell"

    private let titleLabel = UILabel()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: .default, reuseIdentifier: reuseIdentifier)
        accessoryType = .disclosureIndicator

        contentView.addSubview(titleLabel)
        titleLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.centerY.equalToSuperview()
        }
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(id: String, title: String) {
        accessibilityIdentifier = id
        titleLabel.text = title
    }
}
```

---

## Phase 6：List 页面

### Task 6.1：ListViewController

**文件**：`packages/ios/demo/Sources/ClientToolsDemo/List/ListViewController.swift`

```swift
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
```

### Task 6.2：ListCell

**文件**：`packages/ios/demo/Sources/ClientToolsDemo/List/ListCell.swift`

```swift
import UIKit
import SnapKit

class ListCell: UITableViewCell {

    static let identifier = "ListCell"

    private let iconImageView: UIImageView = {
        let iv = UIImageView()
        iv.backgroundColor = .systemGray5
        iv.layer.cornerRadius = 20
        iv.clipsToBounds = true
        return iv
    }()

    private let titleLabel: UILabel = {
        let label = UILabel()
        label.font = .systemFont(ofSize: 16)
        return label
    }()

    private let subtitleLabel: UILabel = {
        let label = UILabel()
        label.text = "副标题"
        label.font = .systemFont(ofSize: 14)
        label.textColor = .secondaryLabel
        return label
    }()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        accessoryType = .disclosureIndicator

        contentView.addSubview(iconImageView)
        contentView.addSubview(titleLabel)
        contentView.addSubview(subtitleLabel)

        iconImageView.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.centerY.equalToSuperview()
            make.width.height.equalTo(40)
        }

        titleLabel.snp.makeConstraints { make in
            make.leading.equalTo(iconImageView.snp.trailing).offset(12)
            make.top.equalToSuperview().offset(12)
            make.trailing.equalToSuperview().offset(-16)
        }

        subtitleLabel.snp.makeConstraints { make in
            make.leading.equalTo(titleLabel)
            make.top.equalTo(titleLabel.snp.bottom).offset(4)
            make.trailing.equalToSuperview().offset(-16)
            make.bottom.equalToSuperview().offset(-12)
        }
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(title: String) {
        titleLabel.text = title
    }
}
```

---

## Phase 7：资源文件

### Task 7.1：LaunchScreen.storyboard

**文件**：`packages/ios/demo/Resources/LaunchScreen.storyboard`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<document type="com.apple.InterfaceBuilder3.CocoaTouch.Storyboard.XIB" version="3.0" toolsVersion="21701" targetRuntime="iOS.CocoaTouch" propertyAccessControl="none" useAutolayout="YES" launchScreen="YES" useTraitCollections="YES" useSafeAreas="YES" colorMatched="YES" initialViewController="01J-lp-oVM">
    <device id="retina6_12" orientation="portrait" appearance="light"/>
    <dependencies>
        <plugIn identifier="com.apple.InterfaceBuilder.IBCocoaTouchPlugin" version="21678"/>
        <capability name="Safe area layout guides" minToolsVersion="9.0"/>
    </dependencies>
    <scenes>
        <scene sceneID="EHf-IW-A2E">
            <objects>
                <viewController id="01J-lp-oVM" sceneMemberID="viewController">
                    <view key="view" contentMode="scaleToFill" id="Ze5-6b-2t3">
                        <rect key="frame" x="0.0" y="0.0" width="393" height="852"/>
                        <autoresizingMask key="autoresizingMask" widthSizable="YES" heightSizable="YES"/>
                        <label opaque="NO" userInteractionEnabled="NO" text="ClientTools Demo" textAlignment="center" lineBreakMode="tailTruncation" baselineAdjustment="alignBaselines" adjustsFontSizeToFit="NO" translatesAutoresizingMaskIntoConstraints="NO" id="GJd-Yh-RWb">
                            <rect key="frame" x="97" y="416" width="199" height="20"/>
                            <fontDescription key="fontDescription" type="boldSystem" pointSize="17"/>
                            <nil key="textColor"/>
                            <nil key="highlightedColor"/>
                        </label>
                        <viewLayoutGuide key="safeArea" id="6Tk-OE-BBY"/>
                    </view>
                </viewController>
                <placeholder placeholderIdentifier="IBFirstResponder" id="iYj-Kq-Ea1" userLabel="First Responder" sceneMemberID="firstResponder"/>
            </objects>
            <point key="canvasLocation" x="52" y="374"/>
        </scene>
    </scenes>
</document>
```

### Task 7.2：Assets.xcassets

```bash
mkdir -p packages/ios/demo/Resources/Assets.xcassets/AppIcon.appiconset
```

**文件**：`packages/ios/demo/Resources/Assets.xcassets/Contents.json`

```json
{
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

---

## 执行顺序

```
Phase 1：项目初始化
  Task 1.1 → 1.2 → 1.3 → 1.4 → 1.5
        ↓
Phase 2：首页列表
  Task 2.1 → 2.2
        ↓
Phase 3：Login 页面
  Task 3.1
        ↓
Phase 4：Profile 页面
  Task 4.1
        ↓
Phase 5：Settings 页面
  Task 5.1 → 5.2 → 5.3
        ↓
Phase 6：List 页面
  Task 6.1 → 6.2
        ↓
Phase 7：资源文件
  Task 7.1 → 7.2
```

---

## 验证方式

1. `cd packages/ios/demo && pod install`
2. `open ClientToolsDemo.xcworkspace`
3. Xcode 中选择模拟器，运行
4. 验证首页列表显示 4 个页面入口
5. 点击每个入口，验证页面正常跳转
6. 验证 `accessibilityIdentifier` 是否正确设置

---

## 预计工作量

| Phase | 任务数 | 复杂度 |
|-------|--------|--------|
| 项目初始化 | 5 | 低 |
| 首页列表 | 2 | 低 |
| Login 页面 | 1 | 低 |
| Profile 页面 | 1 | 低 |
| Settings 页面 | 3 | 中 |
| List 页面 | 2 | 低 |
| 资源文件 | 2 | 低 |

**总计**：约 **2-3 小时**

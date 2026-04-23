# iOS Demo App 技术方案

**日期**：2026-04-24  
**范围**：iOS Demo App，用于验证 ClientToolsSDK

---

## 一、目标

创建一个 iOS Demo App，用于：
1. 验证 ClientToolsSDK 的各项能力
2. 方便后续增加新的测试页面
3. 模拟真实 App 的使用场景

---

## 二、技术栈

| 组件 | 技术 |
|------|------|
| 框架 | UIKit + Swift |
| 最低版本 | iOS 14.0 |
| SDK 集成 | 本地 CocoaPod（开发时） |
| 布局 | SnapKit（Masonry 风格） |

---

## 三、页面结构

```
┌─────────────────────────────────────┐
│           DemoNavigationController     │
│  ┌───────────────────────────────┐   │
│  │         HomeViewController     │   │  ← 首页列表
│  │  ┌─────────────────────────┐ │   │
│  │  │ Cell: Login Demo       │ │   │
│  │  ├─────────────────────────┤ │   │
│  │  │ Cell: Profile Demo     │ │   │
│  │  ├─────────────────────────┤ │   │
│  │  │ Cell: Settings Demo     │ │   │
│  │  ├─────────────────────────┤ │   │
│  │  │ Cell: List Demo       │ │   │
│  │  └─────────────────────────┘ │   │
│  └───────────────────────────────┘   │
└─────────────────────────────────────┘
```

---

## 四、首页列表设计

### 4.1 布局

UITableView + UITableViewCell，展示所有可用测试页面。

### 4.2 Cell 样式

```
┌─────────────────────────────────────┐
│  📄  Login Demo                   → │
│      登录页面测试                    │
├─────────────────────────────────────┤
│  📱  Profile Demo                 → │
│      个人资料页面测试                 │
├─────────────────────────────────────┤
│  ⚙️  Settings Demo               → │
│      设置页面测试                    │
├─────────────────────────────────────┤
│  📋  List Demo                   → │
│      列表页面测试                    │
└─────────────────────────────────────┘
```

### 4.3 accessibilityIdentifier 命名规范

所有可操控的 View 必须设置 `accessibilityIdentifier`：

| 页面 | View | identifier |
|------|------|-------------|
| 首页 | NavigationBar | `home_nav_bar` |
| 首页 | TableView | `home_list` |
| 首页 | Cell-Login | `home_cell_login` |
| 首页 | Cell-Profile | `home_cell_profile` |
| 首页 | Cell-Settings | `home_cell_settings` |
| 首页 | Cell-List | `home_cell_list` |

---

## 五、测试页面

### 5.1 LoginViewController

模拟登录页面，用于测试基础 View 查询和修改：

```
┌─────────────────────────────────────┐
│  ← 返回              登录        [Nav] │
├─────────────────────────────────────┤
│                                     │
│         ┌───────────────┐           │
│         │   Logo Image  │           │
│         │   (100x100)    │           │
│         └───────────────┘           │
│                                     │
│         ┌───────────────┐           │
│         │  用户名输入框  │ ← username_input │
│         └───────────────┘           │
│                                     │
│         ┌───────────────┐           │
│         │  密码输入框    │ ← password_input │
│         └───────────────┘           │
│                                     │
│         ┌───────────────┐           │
│         │    登录按钮    │ ← login_btn │
│         └───────────────┘           │
│                                     │
│         ┌───────────────┐           │
│         │   注册按钮     │ ← register_btn │
│         └───────────────┘           │
│                                     │
└─────────────────────────────────────┘
```

**accessibilityIdentifier**：
- `login_nav_bar`
- `login_logo` (UIImageView)
- `login_username_input` (UITextField)
- `login_password_input` (UITextField, secure)
- `login_btn` (UIButton)
- `login_register_btn` (UIButton)

### 5.2 ProfileViewController

模拟个人资料页面，用于测试多种 View 类型：

```
┌─────────────────────────────────────┐
│  ← 返回            个人资料      [Nav] │
├─────────────────────────────────────┤
│         ┌───────────────┐           │
│         │   头像图片     │ ← profile_avatar │
│         └───────────────┘           │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  昵称: John Doe      [Label]│   │
│  ├─────────────────────────────┤   │
│  │  简介: iOS Developer [Label]│   │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │       头像设置按钮           │ ← profile_avatar_btn │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │       编辑资料按钮           │ ← profile_edit_btn │
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘
```

### 5.3 SettingsViewController

模拟设置页面，用于测试列表和开关：

```
┌─────────────────────────────────────┐
│  ← 返回            设置          [Nav] │
├─────────────────────────────────────┤
│  通知设置                        │
│  ├─────────────────────────────┤   │
│  │ 🔔 推送通知        [Switch]│ ← settings_notify_switch │
│  ├─────────────────────────────┤   │
│  │ 🔒 隐私保护        [Switch]│ ← settings_privacy_switch │
├─────────────────────────────────────┤
│  常规设置                        │
│  ├─────────────────────────────┤   │
│  │ 🌐 语言           [Label→]│ ← settings_language │
│  ├─────────────────────────────┤   │
│  │ 📱 清除缓存       [Label→]│ ← settings_clear_cache │
│  ├─────────────────────────────┤   │
│  │ ℹ️  关于          [Label→]│ ← settings_about │
└─────────────────────────────────────┘
```

### 5.4 ListViewController

模拟列表页面，用于测试 scroll：

```
┌─────────────────────────────────────┐
│  ← 返回            列表          [Nav] │
├─────────────────────────────────────┤
│  ┌─────────────────────────────┐   │
│  │ Cell 1                     │   │
│  │ 图片 + 标题 + 副标题         │   │
│  ├─────────────────────────────┤   │
│  │ Cell 2                     │   │
│  │ 图片 + 标题 + 副标题         │   │
│  ├─────────────────────────────┤   │
│  │ Cell 3                     │   │ ← 滚动测试用
│  │ 图片 + 标题 + 副标题         │   │
│  ├─────────────────────────────┤   │
│  │ Cell 4                     │   │
│  │ 图片 + 标题 + 副标题         │   │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │       滚动到底部按钮        │ ← list_scroll_bottom_btn │
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘
        ↓ scroll 滚动
```

**accessibilityIdentifier**：
- `list_table_view` (UITableView)
- `list_scroll_bottom_btn` (UIButton)
- `list_cell_{index}` (UITableViewCell，动态生成)

---

## 六、目录结构

```
packages/
└── ios/
    └── demo/
        ├── project.yml              # XcodeGen 配置
        ├── Podfile                 # CocoaPods 依赖
        ├── Sources/
        │   └── ClientToolsDemo/
        │       ├── AppDelegate.swift
        │       ├── SceneDelegate.swift
        │       ├── Info.plist
        │       │
        │       ├── Home/
        │       │   ├── HomeViewController.swift
        │       │   └── HomeCell.swift
        │       │
        │       ├── Login/
        │       │   └── LoginViewController.swift
        │       │
        │       ├── Profile/
        │       │   └── ProfileViewController.swift
        │       │
        │       ├── Settings/
        │       │   └── SettingsViewController.swift
        │       │
        │       └── List/
        │           ├── ListViewController.swift
        │           └── ListCell.swift
        │
        └── Resources/
            ├── Assets.xcassets
            └── LaunchScreen.storyboard
```

---

## 七、增加新测试页面的流程

### 7.1 创建页面

1. 在 `Sources/ClientToolsDemo/` 下新建目录，如 `NewPage/`
2. 创建 `NewPageViewController.swift`

### 7.2 设置 accessibilityIdentifier

```swift
class NewPageViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()

        let titleLabel = UILabel()
        titleLabel.accessibilityIdentifier = "newpage_title"
        view.addSubview(titleLabel)

        let actionBtn = UIButton()
        actionBtn.accessibilityIdentifier = "newpage_action_btn"
        view.addSubview(actionBtn)
    }
}
```

### 7.3 注册到首页列表

在 `HomeViewController.swift` 中添加：

```swift
let pages: [(title: String, identifier: String, vcClass: String)] = [
    ("Login Demo", "home_cell_login", "LoginViewController"),
    ("New Page Demo", "home_cell_newpage", "NewPageViewController"),
    // ...
]
```

---

## 八、依赖

### 8.1 SnapKit

用于 Auto Layout：

```ruby
pod 'SnapKit', '~> 5.6'
```

### 8.2 ClientToolsSDK

本地开发时引用：

```ruby
pod 'ClientToolsSDK', :path => '../sdk'
```

---

## 九、验证清单

| 测试项 | 页面 | 验证方式 |
|--------|------|---------|
| View 查询 | Login | `GET /api/nodes/all` |
| View ID | 所有 | 检查 accessibilityIdentifier |
| View 修改 | Login | `POST /api/modify` |
| click | Login | `POST /api/click` |
| scroll | List | `POST /api/scroll` |
| WebView 叠加 | 所有 | `POST /api/overlay/show` |
| 页面切换 | 所有 | `GET /api/page/current` |

# iOS InspectorPanel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 iOS SDK 中实现与 Android 视觉对齐的 InspectorPanel 浮动控制面板，全局悬浮于所有页面之上。

**Architecture:** `InspectorPanelView`（纯 UIKit 视图）+ `InspectorPanel`（逻辑层）挂载到 `OverlayManager` 管理的 UIWindow 上，共享同一个 `InspectorViewModel`。`HtmlFileStore` 补充 `getAllFiles()` 方法供面板列表使用。`ClientToolsSDK` 存储 port 供状态面板显示。

**Tech Stack:** UIKit（纯代码，无 Storyboard）、UIPanGestureRecognizer、UIAlertController、UISlider

---

## 文件清单

| 操作 | 文件 | 说明 |
|------|------|------|
| Modify | `clients/ios/sdk/Sources/Overlay/HtmlFileStore.swift` | 新增 `getAllFiles() -> [FileInfo]` |
| Modify | `clients/ios/sdk/Sources/ClientToolsSDK.swift` | 新增 `port` 属性 |
| Modify | `clients/ios/sdk/Sources/Inspector/InspectorViewModel.swift` | 新增 `.status` tab + `onActiveTabChanged` 回调 |
| Create | `clients/ios/sdk/Sources/Inspector/InspectorPanelView.swift` | 纯 UIKit 视图层 |
| Create | `clients/ios/sdk/Sources/Inspector/InspectorPanel.swift` | 逻辑层，订阅 ViewModel，处理交互 |
| Modify | `clients/ios/sdk/Sources/Overlay/OverlayManager.swift` | `ensureWindow()` 末尾创建并持有 InspectorPanel |

**执行顺序：Task 1 → 2 → 3 → 4 → 5 → 6 → 7**（Task 5 依赖 Task 3 中定义的 `ActiveTab.status` 和 `onActiveTabChanged`）

---

## Task 1: HtmlFileStore 补充 getAllFiles

**Files:**
- Modify: `clients/ios/sdk/Sources/Overlay/HtmlFileStore.swift`

- [ ] **Step 1: 在 `findFile` 方法之后插入 `getAllFiles()` 方法**

```swift
public func getAllFiles() -> [FileInfo] {
    guard let files = try? fileManager.contentsOfDirectory(at: baseDir, includingPropertiesForKeys: nil) else { return [] }
    return files
        .filter { $0.pathExtension.lowercased() == "html" }
        .compactMap { url -> FileInfo? in
            let name = url.deletingPathExtension().lastPathComponent
            let parts = name.split(separator: "_", maxSplits: 1)
            guard parts.count == 2 else { return nil }
            return FileInfo(tag: String(parts[0]), timestamp: String(parts[1]), filePath: url.path)
        }
        .sorted { $0.timestamp > $1.timestamp }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' \
  build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
```

Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 3: 提交**

```bash
git add clients/ios/sdk/Sources/Overlay/HtmlFileStore.swift
git commit -m "feat(ios-sdk): add getAllFiles to HtmlFileStore"
```

---

## Task 2: ClientToolsSDK 存储 port

**Files:**
- Modify: `clients/ios/sdk/Sources/ClientToolsSDK.swift`

- [ ] **Step 1: 添加 `port` 公开只读属性，在 `start()` 中赋值**

将 `ClientToolsSDK.swift` 改为如下（仅展示需变动部分，其余不变）：

在 `public let imageFileStore = ImageFileStore()` 后添加：
```swift
public private(set) var port: Int = 8080
```

在 `start(port:)` 的 `guard !isRunning` 通过后、`startHttpServer(port:)` 之前添加：
```swift
self.port = port
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' \
  build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
```

Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 3: 提交**

```bash
git add clients/ios/sdk/Sources/ClientToolsSDK.swift
git commit -m "feat(ios-sdk): expose port property on ClientToolsSDK"
```

---

## Task 3: InspectorViewModel 新增 status tab + activeTab 回调

**Files:**
- Modify: `clients/ios/sdk/Sources/Inspector/InspectorViewModel.swift`

此任务必须在 Task 5 之前完成，因为 `InspectorPanel` 依赖 `ActiveTab.status` 和 `onActiveTabChanged`。

- [ ] **Step 1: 修改 `InspectorViewModel.swift`，替换为以下完整内容**

```swift
import Foundation

public struct FileInfo {
    public let tag: String
    public let timestamp: String
    public let filePath: String

    public init(tag: String, timestamp: String, filePath: String) {
        self.tag = tag; self.timestamp = timestamp; self.filePath = filePath
    }
}

public struct ImageInfo {
    public let tag: String
    public let timestamp: String
    public let filePath: String
    public let ext: String

    public init(tag: String, timestamp: String, filePath: String, ext: String) {
        self.tag = tag; self.timestamp = timestamp; self.filePath = filePath; self.ext = ext
    }
}

public struct WebViewState {
    public var currentFile: FileInfo? = nil
    public var isVisible: Bool = false
    public var offsetX: Float = 0
    public var offsetY: Float = 0
    public var opacity: Float = 0.5

    public init(currentFile: FileInfo? = nil, isVisible: Bool = false, offsetX: Float = 0, offsetY: Float = 0, opacity: Float = 0.5) {
        self.currentFile = currentFile; self.isVisible = isVisible
        self.offsetX = offsetX; self.offsetY = offsetY; self.opacity = opacity
    }
}

public struct ImageState {
    public var currentImage: ImageInfo? = nil
    public var isVisible: Bool = false
    public var offsetX: Float = 0
    public var offsetY: Float = 0
    public var opacity: Float = 0.5

    public init(currentImage: ImageInfo? = nil, isVisible: Bool = false, offsetX: Float = 0, offsetY: Float = 0, opacity: Float = 0.5) {
        self.currentImage = currentImage; self.isVisible = isVisible
        self.offsetX = offsetX; self.offsetY = offsetY; self.opacity = opacity
    }
}

public enum ActiveTab { case webview, image, status }

public class InspectorViewModel {
    public var webViewState: WebViewState = WebViewState() { didSet { onWebViewStateChanged?(webViewState) } }
    public var imageState: ImageState = ImageState()       { didSet { onImageStateChanged?(imageState) } }
    public var activeTab: ActiveTab = .webview             { didSet { onActiveTabChanged?(activeTab) } }

    public var onWebViewStateChanged: ((WebViewState) -> Void)?
    public var onImageStateChanged: ((ImageState) -> Void)?
    public var onActiveTabChanged: ((ActiveTab) -> Void)?

    public init() {}
}
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' \
  build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
```

Expected: `** BUILD SUCCEEDED **`（`InspectorApiHandler` 中的 `.image` 判断不受影响，新增 `.status` case 无破坏性）

- [ ] **Step 3: 提交**

```bash
git add clients/ios/sdk/Sources/Inspector/InspectorViewModel.swift
git commit -m "feat(ios-inspector): add status tab and onActiveTabChanged to ViewModel"
```

---

## Task 4: InspectorPanelView — 视图层

**Files:**
- Create: `clients/ios/sdk/Sources/Inspector/InspectorPanelView.swift`

视图层只负责创建并暴露 UI 控件，不含业务逻辑。

- [ ] **Step 1: 创建文件，写入完整实现**

```swift
import UIKit

class InspectorPanelView: UIView {

    // MARK: - 顶栏
    let dragHandle   = UIView()
    let titleLabel   = UILabel()
    let closeButton  = UIButton(type: .system)

    // MARK: - Tab 行
    let tabWebview   = UIButton(type: .system)
    let tabImage     = UIButton(type: .system)
    let tabStatus    = UIButton(type: .system)

    // MARK: - WebView Section
    let sectionWebviewHeader  = UIButton(type: .system)
    let sectionWebviewContent = UIView()
    let currentFileLabel      = UILabel()
    let btnSelectFile         = UIButton(type: .system)

    // MARK: - 图片 Section
    let sectionImageHeader    = UIButton(type: .system)
    let sectionImageContent   = UIView()
    let currentImageLabel     = UILabel()
    let btnSelectImage        = UIButton(type: .system)

    // MARK: - 状态 Section
    let sectionStatusContent  = UIView()
    let statusServerLabel     = UILabel()
    let statusPageLabel       = UILabel()
    let statusIproxyLabel     = UILabel()
    let statusIproxyCmd       = UILabel()

    // MARK: - 调整 Section
    let sectionAdjustHeader   = UIButton(type: .system)
    let sectionAdjustContent  = UIView()
    let btnStep1              = UIButton(type: .system)
    let btnStep10             = UIButton(type: .system)
    let btnStep50             = UIButton(type: .system)
    let btnLeft               = UIButton(type: .system)
    let btnUp                 = UIButton(type: .system)
    let btnDown               = UIButton(type: .system)
    let btnRight              = UIButton(type: .system)
    let opacityLabel          = UILabel()
    let opacitySlider         = UISlider()
    let offsetLabel           = UILabel()

    // MARK: - 控制 Section
    let sectionControlHeader  = UIButton(type: .system)
    let sectionControlContent = UIView()
    let btnShow               = UIButton(type: .system)
    let btnHide               = UIButton(type: .system)

    // MARK: - Colors
    static let purple      = UIColor(hex: "#6200EE")
    static let darkBg      = UIColor(hex: "#1E1E3A")
    static let panelBg     = UIColor(hex: "#12122A")
    static let sectionBg   = UIColor(hex: "#1A1A30")
    static let contentBg   = UIColor(hex: "#0D0D1A")
    static let lightPurple = UIColor(hex: "#BB86FC")
    static let green       = UIColor(hex: "#4CAF50")
    static let gray        = UIColor(hex: "#9E9E9E")

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) { fatalError() }

    private func setup() {
        backgroundColor = InspectorPanelView.panelBg
        layer.cornerRadius = 12
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.5
        layer.shadowRadius = 8
        layer.masksToBounds = false

        let stack = UIStackView()
        stack.axis = .vertical
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        stack.addArrangedSubview(makeHeader())
        stack.addArrangedSubview(makeTabRow())
        stack.addArrangedSubview(sectionWebviewHeader)
        stack.addArrangedSubview(sectionWebviewContent)
        stack.addArrangedSubview(sectionImageHeader)
        stack.addArrangedSubview(sectionImageContent)
        stack.addArrangedSubview(sectionStatusContent)
        stack.addArrangedSubview(sectionAdjustHeader)
        stack.addArrangedSubview(sectionAdjustContent)
        stack.addArrangedSubview(sectionControlHeader)
        stack.addArrangedSubview(sectionControlContent)

        setupSectionWebview()
        setupSectionImage()
        setupSectionStatus()
        setupSectionAdjust()
        setupSectionControl()
    }

    private func makeHeader() -> UIView {
        dragHandle.backgroundColor = InspectorPanelView.purple
        dragHandle.heightAnchor.constraint(equalToConstant: 44).isActive = true

        titleLabel.text = "⬡  Inspector"
        titleLabel.textColor = .white
        titleLabel.font = .systemFont(ofSize: 13, weight: .bold)

        closeButton.setTitle("✕", for: .normal)
        closeButton.setTitleColor(UIColor.white.withAlphaComponent(0.8), for: .normal)
        closeButton.titleLabel?.font = .systemFont(ofSize: 14)
        closeButton.widthAnchor.constraint(equalToConstant: 28).isActive = true
        closeButton.heightAnchor.constraint(equalToConstant: 28).isActive = true

        let row = UIStackView(arrangedSubviews: [titleLabel, closeButton])
        row.axis = .horizontal
        row.layoutMargins = UIEdgeInsets(top: 0, left: 14, bottom: 0, right: 14)
        row.isLayoutMarginsRelativeArrangement = true
        row.translatesAutoresizingMaskIntoConstraints = false
        dragHandle.addSubview(row)
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: dragHandle.topAnchor),
            row.bottomAnchor.constraint(equalTo: dragHandle.bottomAnchor),
            row.leadingAnchor.constraint(equalTo: dragHandle.leadingAnchor),
            row.trailingAnchor.constraint(equalTo: dragHandle.trailingAnchor),
        ])
        return dragHandle
    }

    private func makeTabRow() -> UIView {
        let tabs = [tabWebview, tabImage, tabStatus]
        let titles = ["WebView", "图片", "状态"]
        for (tab, title) in zip(tabs, titles) {
            tab.setTitle(title, for: .normal)
            tab.titleLabel?.font = .systemFont(ofSize: 11)
        }
        let row = UIStackView(arrangedSubviews: tabs)
        row.axis = .horizontal
        row.distribution = .fillEqually
        row.backgroundColor = UIColor(hex: "#0D0D1A")
        row.heightAnchor.constraint(equalToConstant: 36).isActive = true
        return row
    }

    private func setupSectionWebview() {
        styleCollapsibleHeader(sectionWebviewHeader, title: "▶  WebView")
        currentFileLabel.text = "当前：无"
        currentFileLabel.textColor = InspectorPanelView.gray
        currentFileLabel.font = .systemFont(ofSize: 11)
        styleButton(btnSelectFile, title: "选择本地文件", bg: InspectorPanelView.purple, fg: .white)
        btnSelectFile.heightAnchor.constraint(equalToConstant: 34).isActive = true
        let inner = UIStackView(arrangedSubviews: [currentFileLabel, btnSelectFile])
        inner.axis = .vertical; inner.spacing = 6
        inner.layoutMargins = UIEdgeInsets(top: 8, left: 12, bottom: 8, right: 12)
        inner.isLayoutMarginsRelativeArrangement = true
        sectionWebviewContent.backgroundColor = InspectorPanelView.contentBg
        embedStack(inner, in: sectionWebviewContent)
        sectionWebviewContent.isHidden = true
    }

    private func setupSectionImage() {
        styleCollapsibleHeader(sectionImageHeader, title: "▶  图片文件")
        currentImageLabel.text = "当前：无"
        currentImageLabel.textColor = InspectorPanelView.gray
        currentImageLabel.font = .systemFont(ofSize: 11)
        styleButton(btnSelectImage, title: "选择本地图片", bg: InspectorPanelView.purple, fg: .white)
        btnSelectImage.heightAnchor.constraint(equalToConstant: 34).isActive = true
        let inner = UIStackView(arrangedSubviews: [currentImageLabel, btnSelectImage])
        inner.axis = .vertical; inner.spacing = 6
        inner.layoutMargins = UIEdgeInsets(top: 8, left: 12, bottom: 8, right: 12)
        inner.isLayoutMarginsRelativeArrangement = true
        sectionImageContent.backgroundColor = InspectorPanelView.contentBg
        embedStack(inner, in: sectionImageContent)
        sectionImageContent.isHidden = true
    }

    private func setupSectionStatus() {
        statusServerLabel.textColor = InspectorPanelView.green
        statusServerLabel.font = .monospacedSystemFont(ofSize: 12, weight: .regular)
        statusPageLabel.text = "当前页面：—"
        statusPageLabel.textColor = InspectorPanelView.gray
        statusPageLabel.font = .monospacedSystemFont(ofSize: 11, weight: .regular)
        statusIproxyLabel.text = "iproxy 转发："
        statusIproxyLabel.textColor = InspectorPanelView.lightPurple
        statusIproxyLabel.font = .systemFont(ofSize: 11)
        statusIproxyCmd.textColor = UIColor(hex: "#E0E0E0")
        statusIproxyCmd.font = .monospacedSystemFont(ofSize: 10, weight: .regular)
        statusIproxyCmd.backgroundColor = InspectorPanelView.darkBg
        statusIproxyCmd.layer.cornerRadius = 4
        statusIproxyCmd.layer.masksToBounds = true
        statusIproxyCmd.numberOfLines = 0
        let inner = UIStackView(arrangedSubviews: [statusServerLabel, statusPageLabel, statusIproxyLabel, statusIproxyCmd])
        inner.axis = .vertical; inner.spacing = 8
        inner.layoutMargins = UIEdgeInsets(top: 12, left: 14, bottom: 12, right: 14)
        inner.isLayoutMarginsRelativeArrangement = true
        sectionStatusContent.backgroundColor = InspectorPanelView.contentBg
        embedStack(inner, in: sectionStatusContent)
        sectionStatusContent.isHidden = true
    }

    private func setupSectionAdjust() {
        styleCollapsibleHeader(sectionAdjustHeader, title: "▶  调整")
        styleButton(btnStep1,  title: "1pt",  bg: InspectorPanelView.darkBg, fg: InspectorPanelView.lightPurple)
        styleButton(btnStep10, title: "10pt", bg: InspectorPanelView.purple, fg: .white)
        styleButton(btnStep50, title: "50pt", bg: InspectorPanelView.darkBg, fg: InspectorPanelView.lightPurple)
        let stepRow = UIStackView(arrangedSubviews: [btnStep1, btnStep10, btnStep50])
        stepRow.axis = .horizontal; stepRow.distribution = .fillEqually; stepRow.spacing = 4

        styleButton(btnLeft,  title: "◀", bg: InspectorPanelView.darkBg, fg: InspectorPanelView.lightPurple)
        styleButton(btnUp,    title: "△", bg: InspectorPanelView.darkBg, fg: InspectorPanelView.lightPurple)
        styleButton(btnDown,  title: "▽", bg: InspectorPanelView.darkBg, fg: InspectorPanelView.lightPurple)
        styleButton(btnRight, title: "▶", bg: InspectorPanelView.darkBg, fg: InspectorPanelView.lightPurple)
        for b in [btnLeft, btnUp, btnDown, btnRight] {
            b.heightAnchor.constraint(equalToConstant: 40).isActive = true
        }
        let dirRow = UIStackView(arrangedSubviews: [btnLeft, btnUp, btnDown, btnRight])
        dirRow.axis = .horizontal; dirRow.distribution = .fillEqually; dirRow.spacing = 3

        opacityLabel.text = "透明度：50%"
        opacityLabel.textColor = InspectorPanelView.gray
        opacityLabel.font = .systemFont(ofSize: 11)
        opacitySlider.minimumValue = 0; opacitySlider.maximumValue = 1; opacitySlider.value = 0.5
        opacitySlider.minimumTrackTintColor = InspectorPanelView.lightPurple
        opacitySlider.thumbTintColor = InspectorPanelView.purple

        offsetLabel.text = "偏移：X: 0pt  Y: 0pt"
        offsetLabel.textColor = InspectorPanelView.gray
        offsetLabel.font = .systemFont(ofSize: 11)

        let inner = UIStackView(arrangedSubviews: [stepRow, dirRow, opacityLabel, opacitySlider, offsetLabel])
        inner.axis = .vertical; inner.spacing = 6
        inner.layoutMargins = UIEdgeInsets(top: 8, left: 12, bottom: 8, right: 12)
        inner.isLayoutMarginsRelativeArrangement = true
        sectionAdjustContent.backgroundColor = InspectorPanelView.contentBg
        embedStack(inner, in: sectionAdjustContent)
        sectionAdjustContent.isHidden = true
    }

    private func setupSectionControl() {
        styleCollapsibleHeader(sectionControlHeader, title: "▶  控制")
        styleButton(btnShow, title: "显示", bg: InspectorPanelView.purple, fg: .white)
        styleButton(btnHide, title: "隐藏", bg: InspectorPanelView.darkBg, fg: InspectorPanelView.lightPurple)
        for b in [btnShow, btnHide] { b.heightAnchor.constraint(equalToConstant: 38).isActive = true }
        let inner = UIStackView(arrangedSubviews: [btnShow, btnHide])
        inner.axis = .horizontal; inner.distribution = .fillEqually; inner.spacing = 6
        inner.layoutMargins = UIEdgeInsets(top: 8, left: 12, bottom: 12, right: 12)
        inner.isLayoutMarginsRelativeArrangement = true
        sectionControlContent.backgroundColor = InspectorPanelView.contentBg
        embedStack(inner, in: sectionControlContent)
        sectionControlContent.isHidden = true
    }

    // MARK: - Helpers

    func styleCollapsibleHeader(_ btn: UIButton, title: String) {
        btn.setTitle(title, for: .normal)
        btn.setTitleColor(InspectorPanelView.lightPurple, for: .normal)
        btn.titleLabel?.font = .systemFont(ofSize: 12, weight: .bold)
        btn.backgroundColor = InspectorPanelView.sectionBg
        btn.contentHorizontalAlignment = .left
        btn.contentEdgeInsets = UIEdgeInsets(top: 0, left: 14, bottom: 0, right: 14)
        btn.heightAnchor.constraint(equalToConstant: 40).isActive = true
    }

    func styleButton(_ btn: UIButton, title: String, bg: UIColor, fg: UIColor) {
        btn.setTitle(title, for: .normal)
        btn.setTitleColor(fg, for: .normal)
        btn.titleLabel?.font = .systemFont(ofSize: 12)
        btn.backgroundColor = bg
        btn.layer.cornerRadius = 4
        btn.layer.masksToBounds = true
    }

    private func embedStack(_ stack: UIStackView, in container: UIView) {
        stack.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: container.topAnchor),
            stack.bottomAnchor.constraint(equalTo: container.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: container.trailingAnchor),
        ])
    }
}

extension UIColor {
    convenience init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let r = CGFloat((int >> 16) & 0xFF) / 255
        let g = CGFloat((int >> 8)  & 0xFF) / 255
        let b = CGFloat(int         & 0xFF) / 255
        self.init(red: r, green: g, blue: b, alpha: 1)
    }
}
```

- [ ] **Step 2: 编译验证（需先 pod install，因为新增了文件）**

```bash
cd clients/ios/demo && pod install 2>&1 | tail -3 && \
xcodebuild -workspace ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' \
  build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
```

Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 3: 提交**

```bash
git add clients/ios/sdk/Sources/Inspector/InspectorPanelView.swift
git commit -m "feat(ios-inspector): add InspectorPanelView UIKit layout"
```

---

## Task 5: InspectorPanel — 逻辑层

**Files:**
- Create: `clients/ios/sdk/Sources/Inspector/InspectorPanel.swift`

依赖 Task 3（`ActiveTab.status`、`onActiveTabChanged`）和 Task 4（`InspectorPanelView`）。

- [ ] **Step 1: 创建文件，写入完整实现**

```swift
import UIKit

class InspectorPanel {

    private let panelView = InspectorPanelView()
    private let floatBtn  = UIButton(type: .system)
    private weak var container: UIView?

    private let viewModel: InspectorViewModel
    private let imageFileStore: ImageFileStore
    private let htmlFileStore: HtmlFileStore
    private let port: Int

    private var stepPt: Float = 10
    private var isPanelVisible = false
    private var panStartCenter: CGPoint = .zero
    private var panMoved = false

    init(viewModel: InspectorViewModel,
         imageFileStore: ImageFileStore,
         htmlFileStore: HtmlFileStore,
         port: Int) {
        self.viewModel      = viewModel
        self.imageFileStore = imageFileStore
        self.htmlFileStore  = htmlFileStore
        self.port           = port
        setupFloatBtn()
        setupInteractions()
        setupObservers()
    }

    // MARK: - 挂载

    func attach(to view: UIView) {
        container = view

        floatBtn.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(floatBtn)
        NSLayoutConstraint.activate([
            floatBtn.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            floatBtn.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -16),
            floatBtn.widthAnchor.constraint(equalToConstant: 40),
            floatBtn.heightAnchor.constraint(equalToConstant: 40),
        ])

        panelView.translatesAutoresizingMaskIntoConstraints = false
        panelView.isHidden = true
        view.addSubview(panelView)
        NSLayoutConstraint.activate([
            panelView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            panelView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            panelView.widthAnchor.constraint(equalToConstant: 288),
        ])

        applyTab(viewModel.activeTab)
        updateStatusSection()
    }

    // MARK: - 悬浮按钮

    private func setupFloatBtn() {
        floatBtn.setTitle("⚙", for: .normal)
        floatBtn.setTitleColor(.white, for: .normal)
        floatBtn.titleLabel?.font = .systemFont(ofSize: 18)
        floatBtn.backgroundColor = InspectorPanelView.purple
        floatBtn.layer.cornerRadius = 20
        floatBtn.layer.masksToBounds = true

        let pan = UIPanGestureRecognizer(target: self, action: #selector(handleFloatBtnPan(_:)))
        floatBtn.addGestureRecognizer(pan)
        floatBtn.addTarget(self, action: #selector(floatBtnTapped), for: .touchUpInside)
    }

    @objc private func floatBtnTapped() {
        isPanelVisible.toggle()
        panelView.isHidden = !isPanelVisible
        if isPanelVisible { updateStatusSection() }
    }

    @objc private func handleFloatBtnPan(_ g: UIPanGestureRecognizer) {
        guard let view = container else { return }
        switch g.state {
        case .began:
            panStartCenter = floatBtn.center
            panMoved = false
        case .changed:
            let t = g.translation(in: view)
            if !panMoved && (abs(t.x) > 8 || abs(t.y) > 8) { panMoved = true }
            if panMoved {
                let newX = (panStartCenter.x + t.x).clamped(to: 20...(view.bounds.width - 20))
                let newY = (panStartCenter.y + t.y).clamped(to: 20...(view.bounds.height - 20))
                floatBtn.center = CGPoint(x: newX, y: newY)
            }
        default: break
        }
    }

    // MARK: - 面板拖拽

    @objc private func handlePanelPan(_ g: UIPanGestureRecognizer) {
        guard let view = container else { return }
        let t = g.translation(in: view)
        panelView.center = CGPoint(
            x: (panelView.center.x + t.x).clamped(to: 144...(view.bounds.width - 144)),
            y: (panelView.center.y + t.y).clamped(to: 0...view.bounds.height)
        )
        g.setTranslation(.zero, in: view)
    }

    // MARK: - 交互绑定

    private func setupInteractions() {
        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePanelPan(_:)))
        panelView.dragHandle.addGestureRecognizer(pan)

        panelView.closeButton.addTarget(self, action: #selector(closePanel), for: .touchUpInside)
        panelView.tabWebview.addTarget(self, action: #selector(tabWebviewTapped), for: .touchUpInside)
        panelView.tabImage.addTarget(self,   action: #selector(tabImageTapped),   for: .touchUpInside)
        panelView.tabStatus.addTarget(self,  action: #selector(tabStatusTapped),  for: .touchUpInside)

        panelView.sectionWebviewHeader.addTarget(self, action: #selector(toggleWebviewSection), for: .touchUpInside)
        panelView.sectionImageHeader.addTarget(self,   action: #selector(toggleImageSection),   for: .touchUpInside)
        panelView.sectionAdjustHeader.addTarget(self,  action: #selector(toggleAdjustSection),  for: .touchUpInside)
        panelView.sectionControlHeader.addTarget(self, action: #selector(toggleControlSection), for: .touchUpInside)

        panelView.btnSelectFile.addTarget(self,  action: #selector(selectFileTapped),  for: .touchUpInside)
        panelView.btnSelectImage.addTarget(self, action: #selector(selectImageTapped), for: .touchUpInside)

        panelView.btnStep1.addTarget(self,  action: #selector(step1Tapped),  for: .touchUpInside)
        panelView.btnStep10.addTarget(self, action: #selector(step10Tapped), for: .touchUpInside)
        panelView.btnStep50.addTarget(self, action: #selector(step50Tapped), for: .touchUpInside)

        panelView.btnLeft.addTarget(self,  action: #selector(leftTapped),  for: .touchUpInside)
        panelView.btnUp.addTarget(self,    action: #selector(upTapped),    for: .touchUpInside)
        panelView.btnDown.addTarget(self,  action: #selector(downTapped),  for: .touchUpInside)
        panelView.btnRight.addTarget(self, action: #selector(rightTapped), for: .touchUpInside)

        panelView.opacitySlider.addTarget(self, action: #selector(opacityChanged(_:)), for: .valueChanged)
        panelView.btnShow.addTarget(self, action: #selector(showTapped), for: .touchUpInside)
        panelView.btnHide.addTarget(self, action: #selector(hideTapped), for: .touchUpInside)
    }

    @objc private func closePanel()       { isPanelVisible = false; panelView.isHidden = true }
    @objc private func tabWebviewTapped() { viewModel.activeTab = .webview }
    @objc private func tabImageTapped()   { viewModel.activeTab = .image }
    @objc private func tabStatusTapped()  { viewModel.activeTab = .status }

    @objc private func toggleWebviewSection() { toggleSection(panelView.sectionWebviewHeader, content: panelView.sectionWebviewContent) }
    @objc private func toggleImageSection()   { toggleSection(panelView.sectionImageHeader,   content: panelView.sectionImageContent) }
    @objc private func toggleAdjustSection()  { toggleSection(panelView.sectionAdjustHeader,  content: panelView.sectionAdjustContent) }
    @objc private func toggleControlSection() { toggleSection(panelView.sectionControlHeader, content: panelView.sectionControlContent) }

    @objc private func step1Tapped()  { selectStep(1) }
    @objc private func step10Tapped() { selectStep(10) }
    @objc private func step50Tapped() { selectStep(50) }

    @objc private func leftTapped()  { applyOffset(dx: -stepPt, dy: 0) }
    @objc private func upTapped()    { applyOffset(dx: 0, dy: -stepPt) }
    @objc private func downTapped()  { applyOffset(dx: 0, dy: stepPt) }
    @objc private func rightTapped() { applyOffset(dx: stepPt, dy: 0) }

    @objc private func opacityChanged(_ slider: UISlider) {
        let v = slider.value
        switch viewModel.activeTab {
        case .webview: viewModel.webViewState.opacity = v
        case .image:   viewModel.imageState.opacity = v
        case .status:  break
        }
        panelView.opacityLabel.text = "透明度：\(Int(v * 100))%"
    }

    @objc private func showTapped() {
        switch viewModel.activeTab {
        case .webview:
            if viewModel.webViewState.currentFile != nil { viewModel.webViewState.isVisible = true }
        case .image:
            if viewModel.imageState.currentImage != nil { viewModel.imageState.isVisible = true }
        case .status: break
        }
    }

    @objc private func hideTapped() {
        switch viewModel.activeTab {
        case .webview: viewModel.webViewState.isVisible = false
        case .image:   viewModel.imageState.isVisible = false
        case .status:  break
        }
    }

    @objc private func selectFileTapped() {
        let files = htmlFileStore.getAllFiles()
        guard !files.isEmpty else { return }
        let current = viewModel.webViewState.currentFile
        let alert = UIAlertController(title: "选择 HTML 文件", message: nil, preferredStyle: .actionSheet)
        for f in files {
            let star = (f.tag == current?.tag && f.timestamp == current?.timestamp) ? " ★" : ""
            alert.addAction(UIAlertAction(title: "\(f.tag)  \(f.timestamp)\(star)", style: .default) { [weak self] _ in
                guard let self = self else { return }
                var s = self.viewModel.webViewState
                s.currentFile = f; s.isVisible = true
                self.viewModel.webViewState = s
            })
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentAlert(alert)
    }

    @objc private func selectImageTapped() {
        let images = imageFileStore.getAllImages()
        guard !images.isEmpty else { return }
        let current = viewModel.imageState.currentImage
        let alert = UIAlertController(title: "选择图片", message: nil, preferredStyle: .actionSheet)
        for img in images {
            let star = (img.tag == current?.tag && img.timestamp == current?.timestamp) ? " ★" : ""
            alert.addAction(UIAlertAction(title: "\(img.tag)  \(img.timestamp) (\(img.ext))\(star)", style: .default) { [weak self] _ in
                guard let self = self else { return }
                var s = self.viewModel.imageState
                s.currentImage = img; s.isVisible = true
                self.viewModel.imageState = s
            })
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentAlert(alert)
    }

    // MARK: - ViewModel 观察

    private func setupObservers() {
        viewModel.onWebViewStateChanged = { [weak self] state in
            DispatchQueue.main.async { self?.syncWebViewUI(state) }
        }
        viewModel.onImageStateChanged = { [weak self] state in
            DispatchQueue.main.async { self?.syncImageUI(state) }
        }
        viewModel.onActiveTabChanged = { [weak self] tab in
            DispatchQueue.main.async { self?.applyTab(tab) }
        }
    }

    private func syncWebViewUI(_ state: WebViewState) {
        let file = state.currentFile
        panelView.currentFileLabel.text = file != nil ? "当前：\(file!.tag)  \(file!.timestamp)" : "当前：无"
        if viewModel.activeTab == .webview {
            panelView.opacitySlider.value = state.opacity
            panelView.opacityLabel.text = "透明度：\(Int(state.opacity * 100))%"
            panelView.offsetLabel.text = "偏移：X: \(Int(state.offsetX))pt  Y: \(Int(state.offsetY))pt"
        }
    }

    private func syncImageUI(_ state: ImageState) {
        let img = state.currentImage
        panelView.currentImageLabel.text = img != nil ? "当前：\(img!.tag)  \(img!.timestamp)" : "当前：无"
        if viewModel.activeTab == .image {
            panelView.opacitySlider.value = state.opacity
            panelView.opacityLabel.text = "透明度：\(Int(state.opacity * 100))%"
            panelView.offsetLabel.text = "偏移：X: \(Int(state.offsetX))pt  Y: \(Int(state.offsetY))pt"
        }
    }

    // MARK: - Tab 切换

    private func applyTab(_ tab: ActiveTab) {
        let isWebview = tab == .webview
        let isImage   = tab == .image
        let isStatus  = tab == .status

        styleTab(panelView.tabWebview, active: isWebview)
        styleTab(panelView.tabImage,   active: isImage)
        styleTab(panelView.tabStatus,  active: isStatus)

        panelView.sectionWebviewHeader.isHidden  = !isWebview
        panelView.sectionWebviewContent.isHidden = true
        panelView.sectionImageHeader.isHidden    = !isImage
        panelView.sectionImageContent.isHidden   = true
        panelView.sectionStatusContent.isHidden  = !isStatus
        panelView.sectionAdjustHeader.isHidden   = isStatus
        panelView.sectionAdjustContent.isHidden  = true
        panelView.sectionControlHeader.isHidden  = isStatus
        panelView.sectionControlContent.isHidden = true

        switch tab {
        case .webview: syncWebViewUI(viewModel.webViewState)
        case .image:   syncImageUI(viewModel.imageState)
        case .status:  updateStatusSection()
        }
    }

    private func styleTab(_ btn: UIButton, active: Bool) {
        btn.backgroundColor = active ? InspectorPanelView.purple : InspectorPanelView.darkBg
        btn.setTitleColor(active ? .white : InspectorPanelView.lightPurple, for: .normal)
    }

    // MARK: - Helpers

    private func applyOffset(dx: Float, dy: Float) {
        switch viewModel.activeTab {
        case .webview:
            viewModel.webViewState.offsetX += dx
            viewModel.webViewState.offsetY += dy
        case .image:
            viewModel.imageState.offsetX += dx
            viewModel.imageState.offsetY += dy
        case .status: break
        }
    }

    private func selectStep(_ pt: Int) {
        stepPt = Float(pt)
        for (btn, val) in [(panelView.btnStep1, 1), (panelView.btnStep10, 10), (panelView.btnStep50, 50)] {
            let active = val == pt
            btn.backgroundColor = active ? InspectorPanelView.purple : InspectorPanelView.darkBg
            btn.setTitleColor(active ? .white : InspectorPanelView.lightPurple, for: .normal)
        }
    }

    private func toggleSection(_ header: UIButton, content: UIView) {
        let willShow = content.isHidden
        content.isHidden = !willShow
        let title = header.title(for: .normal) ?? ""
        header.setTitle(
            willShow ? title.replacingOccurrences(of: "▶", with: "▼")
                     : title.replacingOccurrences(of: "▼", with: "▶"),
            for: .normal
        )
    }

    private func updateStatusSection() {
        panelView.statusServerLabel.text = "● HTTP Server: \(port)"
        let page = ClientToolsSDK.shared.getCurrentPage()
        panelView.statusPageLabel.text = "当前页面：\(page.pageName.isEmpty ? "—" : page.pageName)"
        panelView.statusIproxyCmd.text = "  iproxy \(port) \(port)  "
    }

    private func presentAlert(_ alert: UIAlertController) {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else { return }
        var top = root
        while let presented = top.presentedViewController { top = presented }
        top.present(alert, animated: true)
    }
}

extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' \
  build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
```

Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 3: 提交**

```bash
git add clients/ios/sdk/Sources/Inspector/InspectorPanel.swift
git commit -m "feat(ios-inspector): add InspectorPanel logic layer"
```

---

## Task 6: OverlayManager 挂载 InspectorPanel

**Files:**
- Modify: `clients/ios/sdk/Sources/Overlay/OverlayManager.swift`

- [ ] **Step 1: 添加 `inspectorPanel` 属性，在 `ensureWindow()` 末尾创建面板**

在 `private var imageView: UIImageView?` 之后添加：

```swift
private var inspectorPanel: InspectorPanel?
```

在 `ensureWindow()` 函数末尾（`imageView = iv` 之后，函数闭括号之前）添加：

```swift
let panel = InspectorPanel(
    viewModel: viewModel,
    imageFileStore: ClientToolsSDK.shared.imageFileStore,
    htmlFileStore: fileStore,
    port: ClientToolsSDK.shared.port
)
panel.attach(to: vc.view)
inspectorPanel = panel
```

- [ ] **Step 2: 编译验证**

```bash
cd clients/ios/demo && xcodebuild -workspace ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo -destination 'generic/platform=iOS Simulator' \
  build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
```

Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 3: 提交**

```bash
git add clients/ios/sdk/Sources/Overlay/OverlayManager.swift
git commit -m "feat(ios-inspector): mount InspectorPanel in OverlayManager window"
```

---

## Task 7: 端到端验证 + 完成分支

- [ ] **Step 1: 在模拟器上 build & run**

```bash
xcrun simctl list devices available | grep "iPhone 1" | head -3
```

在 Xcode 选任意 iPhone 模拟器运行 `ClientToolsDemo`。

- [ ] **Step 2: 验证悬浮按钮**

- 右下角出现紫色 ⚙ 圆形按钮
- 可拖拽移位
- 点击切换面板显示/隐藏

- [ ] **Step 3: 验证三个 Tab**

- WebView Tab：WebView section + 调整/控制 section 可见，图片/状态 section 隐藏
- 图片 Tab：图片 section + 调整/控制 section 可见，WebView/状态 section 隐藏
- 状态 Tab：显示端口、当前页面、iproxy 命令；调整/控制 section 隐藏

- [ ] **Step 4: 验证调整功能**

- 方向键移动覆盖层（需先有覆盖层内容）
- 透明度滑条实时更新
- 偏移 label 实时刷新

- [ ] **Step 5: 使用 finishing-a-development-branch 完成分支**

调用 `superpowers:finishing-a-development-branch` 技能完成合并。

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

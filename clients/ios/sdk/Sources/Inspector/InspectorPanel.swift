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
    private var panelCenterX: NSLayoutConstraint?
    private var panelCenterY: NSLayoutConstraint?

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
        let cx = panelView.centerXAnchor.constraint(equalTo: view.centerXAnchor)
        let cy = panelView.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        panelCenterX = cx
        panelCenterY = cy
        NSLayoutConstraint.activate([
            cx,
            cy,
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
        guard let view = container,
              let cx = panelCenterX, let cy = panelCenterY else { return }
        let t = g.translation(in: view)
        let newCx = (cx.constant + t.x).clamped(to: (144 - view.bounds.midX)...(view.bounds.midX - 144))
        let newCy = (cy.constant + t.y).clamped(to: -view.bounds.midY...view.bounds.midY)
        cx.constant = newCx
        cy.constant = newCy
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
        viewModel.addWebViewStateObserver { [weak self] state in
            DispatchQueue.main.async { self?.syncWebViewUI(state) }
        }
        viewModel.addImageStateObserver { [weak self] state in
            DispatchQueue.main.async { self?.syncImageUI(state) }
        }
        viewModel.addActiveTabObserver { [weak self] tab in
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

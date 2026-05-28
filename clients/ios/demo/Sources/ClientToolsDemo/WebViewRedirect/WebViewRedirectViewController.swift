import UIKit
import WebKit
import SnapKit
import ClientToolsSDK

class WebViewRedirectViewController: UIViewController {

    private let remoteOriginalUrl = "https://example.com"
    private var localOriginalUrl: String {
        Bundle.main.url(forResource: "test_local", withExtension: "html")?.absoluteString ?? ""
    }

    private lazy var remoteUrlLabel: UILabel = {
        let l = UILabel()
        l.font = .systemFont(ofSize: 11)
        l.textColor = UIColor(red: 0, green: 0.83, blue: 0.67, alpha: 1)
        l.numberOfLines = 2
        l.accessibilityIdentifier = "webview_redirect_url_remote"
        return l
    }()

    private lazy var localUrlLabel: UILabel = {
        let l = UILabel()
        l.font = .systemFont(ofSize: 11)
        l.textColor = UIColor(red: 0, green: 0.83, blue: 0.67, alpha: 1)
        l.numberOfLines = 2
        l.accessibilityIdentifier = "webview_redirect_url_local"
        return l
    }()

    private lazy var remoteWebView: WKWebView = {
        let wv = WKWebView()
        wv.accessibilityIdentifier = "webview_redirect_remote"
        return wv
    }()

    private lazy var localWebView: WKWebView = {
        let wv = WKWebView()
        wv.accessibilityIdentifier = "webview_redirect_local"
        return wv
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "WebView 重定向测试"
        view.backgroundColor = .systemBackground
        view.accessibilityIdentifier = "webview_redirect_root"

        navigationItem.rightBarButtonItem = UIBarButtonItem(
            title: "重新加载",
            style: .plain,
            target: self,
            action: #selector(loadAll)
        )

        let remoteSectionLabel = makeLabel("远程 URL WebView")
        let localSectionLabel = makeLabel("本地文件 WebView")
        let divider = UIView()
        divider.backgroundColor = .separator

        [remoteSectionLabel, remoteUrlLabel, remoteWebView,
         divider, localSectionLabel, localUrlLabel, localWebView].forEach { view.addSubview($0) }

        remoteSectionLabel.snp.makeConstraints { make in
            make.top.equalTo(view.safeAreaLayoutGuide).offset(8)
            make.leading.trailing.equalToSuperview().inset(16)
        }
        remoteUrlLabel.snp.makeConstraints { make in
            make.top.equalTo(remoteSectionLabel.snp.bottom).offset(2)
            make.leading.trailing.equalToSuperview().inset(16)
        }
        remoteWebView.snp.makeConstraints { make in
            make.top.equalTo(remoteUrlLabel.snp.bottom).offset(4)
            make.leading.trailing.equalToSuperview()
            make.height.equalToSuperview().multipliedBy(0.35)
        }
        divider.snp.makeConstraints { make in
            make.top.equalTo(remoteWebView.snp.bottom)
            make.leading.trailing.equalToSuperview()
            make.height.equalTo(1)
        }
        localSectionLabel.snp.makeConstraints { make in
            make.top.equalTo(divider.snp.bottom).offset(8)
            make.leading.trailing.equalToSuperview().inset(16)
        }
        localUrlLabel.snp.makeConstraints { make in
            make.top.equalTo(localSectionLabel.snp.bottom).offset(2)
            make.leading.trailing.equalToSuperview().inset(16)
        }
        localWebView.snp.makeConstraints { make in
            make.top.equalTo(localUrlLabel.snp.bottom).offset(4)
            make.leading.trailing.equalToSuperview()
            make.bottom.equalTo(view.safeAreaLayoutGuide)
        }

        loadAll()
    }

    @objc private func loadAll() {
        let resolvedRemote = ClientToolsSDK.shared.resolveRedirect(remoteOriginalUrl)
        let resolvedLocal = ClientToolsSDK.shared.resolveRedirect(localOriginalUrl)

        remoteUrlLabel.text = resolvedRemote
        localUrlLabel.text = resolvedLocal

        if let url = URL(string: resolvedRemote) {
            remoteWebView.load(URLRequest(url: url))
        }
        if let url = URL(string: resolvedLocal) {
            localWebView.load(URLRequest(url: url))
        }
    }

    private func makeLabel(_ text: String) -> UILabel {
        let l = UILabel()
        l.text = text
        l.font = .systemFont(ofSize: 12)
        l.textColor = .secondaryLabel
        return l
    }
}

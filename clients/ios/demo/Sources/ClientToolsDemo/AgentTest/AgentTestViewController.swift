import UIKit
import SnapKit

class AgentTestViewController: UIViewController {

    // MARK: - Status
    private lazy var statusLabel: UILabel = {
        let l = UILabel()
        l.accessibilityIdentifier = "test_status"
        l.text = "status: idle"
        l.textColor = .white
        l.font = .systemFont(ofSize: 14)
        l.numberOfLines = 0
        l.backgroundColor = UIColor.white.withAlphaComponent(0.1)
        l.layer.cornerRadius = 8
        l.clipsToBounds = true
        l.textAlignment = .left
        return l
    }()

    // MARK: - Input
    private lazy var inputField: UITextField = {
        let tf = UITextField()
        tf.accessibilityIdentifier = "test_input"
        tf.placeholder = "输入框（test_input）"
        tf.textColor = .white
        tf.borderStyle = .roundedRect
        tf.backgroundColor = UIColor.white.withAlphaComponent(0.1)
        return tf
    }()

    private lazy var clearBtn: UIButton = {
        let b = UIButton(type: .system)
        b.accessibilityIdentifier = "test_btn_clear"
        b.setTitle("清空输入框", for: .normal)
        b.addTarget(self, action: #selector(onClear), for: .touchUpInside)
        return b
    }()

    // MARK: - Gesture buttons
    private lazy var longPressBtn: UIButton = {
        let b = UIButton(type: .system)
        b.accessibilityIdentifier = "test_btn_long_press"
        b.setTitle("长按我（test_btn_long_press）", for: .normal)
        b.backgroundColor = UIColor.systemBlue.withAlphaComponent(0.3)
        b.layer.cornerRadius = 8
        let lp = UILongPressGestureRecognizer(target: self, action: #selector(onLongPress(_:)))
        b.addGestureRecognizer(lp)
        return b
    }()

    private lazy var doubleTapBtn: UIButton = {
        let b = UIButton(type: .system)
        b.accessibilityIdentifier = "test_btn_double_tap"
        b.setTitle("双击我（test_btn_double_tap）", for: .normal)
        b.backgroundColor = UIColor.systemGreen.withAlphaComponent(0.3)
        b.layer.cornerRadius = 8
        let dt = UITapGestureRecognizer(target: self, action: #selector(onDoubleTap))
        dt.numberOfTapsRequired = 2
        b.addGestureRecognizer(dt)
        return b
    }()

    // MARK: - Scroll
    private lazy var scrollView: UIScrollView = {
        let sv = UIScrollView()
        sv.accessibilityIdentifier = "test_scroll"
        return sv
    }()

    private lazy var scrollContent: UIStackView = {
        let sv = UIStackView()
        sv.axis = .vertical
        sv.spacing = 0
        return sv
    }()

    // MARK: - wait_for
    private lazy var triggerDelayBtn: UIButton = {
        let b = UIButton(type: .system)
        b.accessibilityIdentifier = "test_btn_trigger_delay"
        b.setTitle("触发延迟出现（2秒后）", for: .normal)
        b.backgroundColor = UIColor.systemOrange.withAlphaComponent(0.3)
        b.layer.cornerRadius = 8
        b.addTarget(self, action: #selector(onTriggerDelay), for: .touchUpInside)
        return b
    }()

    private lazy var delayedView: UIView = {
        let v = UIView()
        v.accessibilityIdentifier = "test_delayed_view"
        v.backgroundColor = UIColor(red: 0.3, green: 0.3, blue: 1.0, alpha: 0.6)
        v.isHidden = true
        let l = UILabel()
        l.text = "我是延迟出现的 View"
        l.textColor = .white
        l.textAlignment = .center
        v.addSubview(l)
        l.snp.makeConstraints { $0.edges.equalToSuperview() }
        return v
    }()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Agent Test"
        view.backgroundColor = UIColor(red: 0.1, green: 0.1, blue: 0.15, alpha: 1)
        setupUI()
        setupScrollItems()
    }

    private func setupUI() {
        let stack = UIStackView(arrangedSubviews: [
            statusLabel, inputField, clearBtn,
            longPressBtn, doubleTapBtn,
            scrollView,
            triggerDelayBtn, delayedView
        ])
        stack.axis = .vertical
        stack.spacing = 12
        stack.setCustomSpacing(24, after: clearBtn)
        stack.setCustomSpacing(24, after: doubleTapBtn)
        stack.setCustomSpacing(24, after: scrollView)

        let outer = UIScrollView()
        outer.addSubview(stack)
        view.addSubview(outer)

        outer.snp.makeConstraints { $0.edges.equalTo(view.safeAreaLayoutGuide) }
        stack.snp.makeConstraints {
            $0.edges.equalToSuperview().inset(UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16))
            $0.width.equalTo(outer).offset(-32)
        }

        statusLabel.snp.makeConstraints { $0.height.greaterThanOrEqualTo(44) }
        longPressBtn.snp.makeConstraints { $0.height.equalTo(56) }
        doubleTapBtn.snp.makeConstraints { $0.height.equalTo(56) }
        scrollView.snp.makeConstraints { $0.height.equalTo(200) }
        triggerDelayBtn.snp.makeConstraints { $0.height.equalTo(56) }
        delayedView.snp.makeConstraints { $0.height.equalTo(56) }

        scrollView.addSubview(scrollContent)
        scrollContent.snp.makeConstraints {
            $0.edges.equalToSuperview()
            $0.width.equalTo(scrollView)
        }
    }

    private func setupScrollItems() {
        for i in 0..<20 {
            let label = UILabel()
            label.accessibilityIdentifier = "test_item_\(i)"
            label.text = "Item \(i)"
            label.textColor = UIColor.white.withAlphaComponent(0.8)
            label.font = .systemFont(ofSize: 14)
            label.textAlignment = .left
            let container = UIView()
            container.addSubview(label)
            label.snp.makeConstraints {
                $0.edges.equalToSuperview().inset(UIEdgeInsets(top: 12, left: 16, bottom: 12, right: 16))
            }
            scrollContent.addArrangedSubview(container)
        }
    }

    // MARK: - Actions

    @objc private func onClear() {
        inputField.text = ""
        updateStatus("clear_input")
    }

    @objc private func onLongPress(_ gr: UILongPressGestureRecognizer) {
        if gr.state == .began { updateStatus("long_press") }
    }

    @objc private func onDoubleTap() {
        updateStatus("double_tap")
    }

    @objc private func onTriggerDelay() {
        updateStatus("trigger_delay")
        delayedView.isHidden = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { [weak self] in
            self?.delayedView.isHidden = false
            self?.updateStatus("delayed_view_visible")
        }
    }

    private func updateStatus(_ action: String) {
        statusLabel.text = "status: \(action)"
    }
}

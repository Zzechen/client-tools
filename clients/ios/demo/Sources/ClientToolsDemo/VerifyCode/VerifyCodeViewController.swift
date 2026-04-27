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
        btn.titleLabel?.font = .boldSystemFont(ofSize: 16)
        btn.backgroundColor = UIColor(hex: "#1A3040")
        btn.layer.cornerRadius = 8
        btn.isEnabled = false
        btn.accessibilityIdentifier = "verify_btn_confirm"
        btn.setTitleColor(UIColor(hex: "#4A6070"), for: .disabled)
        btn.setTitleColor(.black, for: .normal)
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
        updateCodeDisplay()
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

import Foundation

enum LoginUiState {
    case idle
    case loading
    case success(user: UserInfo, token: String)
    case error(message: String)
}

class LoginViewModel {
    var onStateChange: ((LoginUiState) -> Void)?
    var onCountdownChange: ((Int) -> Void)?

    private var state: LoginUiState = .idle {
        didSet { onStateChange?(state) }
    }
    private var smsCountdown: Int = 0 {
        didSet { onCountdownChange?(smsCountdown) }
    }

    private var countdownTimer: Timer?
    private let authService: AuthService

    init(authService: AuthService = AuthService()) {
        self.authService = authService
    }

    func sendSmsCode(phone: String) {
        Task {
            do {
                let resp = try await authService.sendSmsCode(phone: phone)
                await MainActor.run {
                    if resp.code == 0 { startCountdown() }
                    else { state = .error(message: resp.message) }
                }
            } catch {
                await MainActor.run { state = .error(message: error.localizedDescription) }
            }
        }
    }

    func loginSms(phone: String, code: String) {
        state = .loading
        Task {
            do {
                let resp = try await authService.loginSms(phone: phone, code: code)
                await MainActor.run { handleLoginResponse(resp) }
            } catch {
                await MainActor.run { state = .error(message: error.localizedDescription) }
            }
        }
    }

    func loginPassword(phone: String, password: String) {
        state = .loading
        Task {
            do {
                let resp = try await authService.loginPassword(phone: phone, password: password)
                await MainActor.run { handleLoginResponse(resp) }
            } catch {
                await MainActor.run { state = .error(message: error.localizedDescription) }
            }
        }
    }

    func loginEmail(email: String, password: String) {
        state = .loading
        Task {
            do {
                let resp = try await authService.loginEmail(email: email, password: password)
                await MainActor.run { handleLoginResponse(resp) }
            } catch {
                await MainActor.run { state = .error(message: error.localizedDescription) }
            }
        }
    }

    func resetState() {
        state = .idle
    }

    private func handleLoginResponse(_ resp: LoginResponse) {
        if resp.code == 0, let data = resp.data {
            state = .success(user: data.user, token: data.token)
        } else {
            state = .error(message: resp.message)
        }
    }

    private func startCountdown() {
        countdownTimer?.invalidate()
        smsCountdown = 60
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            self.smsCountdown -= 1
            if self.smsCountdown <= 0 {
                self.countdownTimer?.invalidate()
                self.countdownTimer = nil
                self.smsCountdown = 0
            }
        }
    }

    deinit {
        countdownTimer?.invalidate()
    }
}

import Foundation
import ClientToolsSDK

class AuthService {
    static let baseURL = "http://api.pulse.app/"

    private let session: URLSession

    init() {
        session = ClientToolsSDK.shared.makeMockSession()
    }

    func sendSmsCode(phone: String) async throws -> BaseResponse {
        try await post("auth/sms/send", body: SendSmsRequest(phone: phone))
    }

    func loginSms(phone: String, code: String) async throws -> LoginResponse {
        try await post("auth/login/sms", body: SmsLoginRequest(phone: phone, code: code))
    }

    func loginPassword(phone: String, password: String) async throws -> LoginResponse {
        try await post("auth/login/password", body: PasswordLoginRequest(phone: phone, password: password))
    }

    func loginEmail(email: String, password: String) async throws -> LoginResponse {
        try await post("auth/login/email", body: EmailLoginRequest(email: email, password: password))
    }

    private func post<Req: Encodable, Resp: Decodable>(_ path: String, body: Req) async throws -> Resp {
        var request = URLRequest(url: URL(string: AuthService.baseURL + path)!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        let (data, _) = try await session.data(for: request)
        return try JSONDecoder().decode(Resp.self, from: data)
    }
}

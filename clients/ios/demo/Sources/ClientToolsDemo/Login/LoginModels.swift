import Foundation

// MARK: - Requests

struct SendSmsRequest: Encodable {
    let phone: String
}

struct SmsLoginRequest: Encodable {
    let phone: String
    let code: String
}

struct PasswordLoginRequest: Encodable {
    let phone: String
    let password: String
}

struct EmailLoginRequest: Encodable {
    let email: String
    let password: String
}

// MARK: - Responses

struct BaseResponse: Decodable {
    let code: Int
    let message: String
}

struct LoginResponse: Decodable {
    let code: Int
    let message: String
    let data: LoginData?
}

struct LoginData: Decodable {
    let token: String
    let user: UserInfo
}

struct UserInfo: Codable {
    let id: String
    let name: String
    let phone: String
    let email: String
    let avatar_url: String
}

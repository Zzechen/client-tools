import UIKit
import ClientToolsSDK

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    // 当前登录用户，登录后由 ViewController 写入
    static var currentUser: [String: String]? = nil
    static var currentToken: String? = nil

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        ClientToolsSDK.shared.start(
            customRoutes: [
                // 正常：返回当前登录用户信息
                CustomRoute(
                    path: "user/profile",
                    method: .get,
                    description: "获取当前登录用户信息",
                    handler: { _ in
                        if let user = AppDelegate.currentUser {
                            func esc(_ s: String) -> String {
                                s.replacingOccurrences(of: "\\", with: "\\\\")
                                 .replacingOccurrences(of: "\"", with: "\\\"")
                            }
                            let json = "{\"id\":\"\(esc(user["id"] ?? ""))\",\"name\":\"\(esc(user["name"] ?? ""))\",\"phone\":\"\(esc(user["phone"] ?? ""))\",\"email\":\"\(esc(user["email"] ?? ""))\"}"
                            return .ok(json)
                        } else {
                            return .error("未登录", code: 401)
                        }
                    }
                ),
                // 业务错误：模拟账户被禁用
                CustomRoute(
                    path: "user/settings",
                    method: .post,
                    description: "更新用户设置（模拟业务错误：账户已被禁用）",
                    params: ["settings": "JSON 格式的设置项"],
                    handler: { _ in
                        return .error("账户已被禁用，无法修改设置", code: 403)
                    }
                ),
                // 超时：延迟 6000ms，超过 handler 默认超时 4500ms
                CustomRoute(
                    path: "debug/slow",
                    method: .get,
                    description: "慢接口，延迟 6000ms，用于触发 handler 超时",
                    handler: { _ in
                        try await Task.sleep(nanoseconds: 6_000_000_000)
                        return .ok("should not reach here")
                    }
                ),
                // 崩溃：抛出异常，SDK 捕获并包装为 error 响应
                CustomRoute(
                    path: "debug/crash",
                    method: .post,
                    description: "故意抛出异常，验证 SDK 异常捕获",
                    handler: { _ in
                        struct DemoError: Error { let msg: String }
                        throw DemoError(msg: "demo crash: intentional exception")
                    }
                ),
            ]
        )
        return true
    }

    // MARK: UISceneSession Lifecycle

    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }

    func application(_ application: UIApplication, didDiscardSceneSessions sceneSessions: Set<UISceneSession>) {
    }
}

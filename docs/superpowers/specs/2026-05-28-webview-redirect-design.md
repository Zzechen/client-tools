# WebView URL 重定向 — 设计文档

## 背景

本地 Web 开发时，开发者需要将 App 内 WebView 加载的指定远程 URL 替换为本地开发地址（如 `http://192.168.1.x:3000`），以便在真机 App 环境中验证本地页面效果。

SDK 当前无此能力。本设计新增 WebView 重定向规则系统，并提供 debug/release 包分离方案。

---

## 设计目标

1. AI 通过 MCP 工具管理重定向规则
2. App 在加载 WebView URL 时调用 `resolveRedirect()` 获取最终 URL
3. release 包引入 noop 实现，`resolveRedirect()` 直接返回原始 URL，零运行时开销
4. App 生产代码可无条件调用 SDK 方法，无需 `if (BuildConfig.DEBUG)` 判断

---

## 规则结构

```json
{
  "id": "auto-generated-uuid",
  "urlPattern": "https://example\\.com/page.*",
  "targetUrl": "http://192.168.1.100:3000/page"
}
```

- `urlPattern`：正则表达式，匹配原始 URL（不含 query 部分）
- `targetUrl`：命中后跳转的目标地址
- 匹配策略：**第一条命中生效**（按添加顺序）
- Query 合并：将原始 URL 的 query 参数追加到 `targetUrl` 上；若 `targetUrl` 已有同名参数，以原始 URL 的值为准

---

## HTTP 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/webview/redirects` | 添加规则 |
| GET | `/webview/redirects` | 列出所有规则 |
| DELETE | `/webview/redirects/{id}` | 删除指定规则 |
| DELETE | `/webview/redirects` | 清空所有规则 |

风格与 `/mock/rules` 完全一致。

---

## Proto 定义

在 `proto/` 新增 `webview_redirect.proto`（或在已有文件扩展），定义：

```protobuf
message WebViewRedirectRule {
  string id         = 1;
  string urlPattern = 2;
  string targetUrl  = 3;
}

message AddWebViewRedirectRequest {
  string urlPattern = 1;
  string targetUrl  = 2;
}

message WebViewRedirectResponse {
  WebViewRedirectRule data = 1;
}

message WebViewRedirectListResponse {
  repeated WebViewRedirectRule rules = 1;
}
```

Android proto 文件同步更新至 `clients/android/sdk/src/main/proto/`。

---

## SDK 公开方法

### Android

```kotlin
object ClientToolsSDK {
    /**
     * 匹配重定向规则，返回最终 URL。
     * 无规则命中时返回原始 url 不变。
     * 原始 URL 的 query 参数会追加到目标 URL 上。
     */
    fun resolveRedirect(url: String): String
}
```

### iOS

```swift
public class ClientToolsSDK {
    /**
     * 匹配重定向规则，返回最终 URL。
     * 无规则命中时返回原始 url 不变。
     * 原始 URL 的 query 参数会追加到目标 URL 上。
     */
    public func resolveRedirect(_ url: String) -> String
}
```

### 使用方式

```kotlin
// Android — WebView 加载前调用
val finalUrl = ClientToolsSDK.resolveRedirect(originalUrl)
webView.loadUrl(finalUrl)
```

```swift
// iOS — WebView 加载前调用
let finalUrl = ClientToolsSDK.shared.resolveRedirect(originalUrl)
webView.load(URLRequest(url: URL(string: finalUrl)!))
```

---

## MCP 工具

新增 4 个工具，在 `mcp/src/tools/webview.ts` 中扩展：

| 工具名 | 参数 | 说明 |
|--------|------|------|
| `webview_redirect_add` | `urlPattern`, `targetUrl` | 添加重定向规则，返回规则 id |
| `webview_redirect_list` | — | 列出所有规则 |
| `webview_redirect_delete` | `id` | 删除指定规则 |
| `webview_redirect_clear` | — | 清空所有规则 |

同步更新 `docs/mcp-tools.md`。

---

## debug / release 包分离

### Android

新增 `clients/android/noop/` Gradle 模块，包含所有公开 SDK API 的空实现：

```kotlin
// noop 实现
object ClientToolsSDK {
    fun init(context: Context) {}
    fun resolveRedirect(url: String): String = url
    // 其余所有公开方法均为空实现
}
```

App 接入：
```gradle
debugImplementation   'com.github.Zzechen:client-tools:v1.x.x'
releaseImplementation 'com.github.Zzechen:client-tools-noop:v1.x.x'
```

noop 模块单独发布为 JitPack artifact（与 sdk 模块同仓库）。

### iOS

podspec 新增 `ClientToolsSDK-Noop` pod（在同一仓库），包含与 Android noop 对等的空实现：

```ruby
Pod::Spec.new do |s|
  s.name    = 'ClientToolsSDK-Noop'
  s.version = '1.0.0'
  # ...
  s.source_files = 'clients/ios/noop/Sources/**/*.swift'
end
```

App 接入：
```ruby
pod 'ClientToolsSDK',      :configurations => ['Debug']
pod 'ClientToolsSDK-Noop', :configurations => ['Release']
```

---

## Demo 测试页

Android (`clients/android/demo/`) 和 iOS (`clients/ios/demo/`) 各新增一个 **WebView 测试页**，用于完整验证重定向功能。

### 页面入口
Demo 主页新增入口「WebView 重定向测试」，跳转到测试页。

### 测试页内容
页面包含两个 WebView：

| WebView | 加载的 URL | 说明 |
|---------|-----------|------|
| 远程 URL WebView | `https://example.com`（可配置） | 模拟生产远程页面 |
| 本地文件 WebView | assets/`test_local.html` | 模拟已有本地 HTML 资源 |

页面上方显示每个 WebView 当前实际加载的 URL（调用 `resolveRedirect()` 后的结果），方便验证规则是否生效。

### 测试 HTML 文件
`assets/test_local.html`（Android）/ `Resources/test_local.html`（iOS）：一个简单 HTML 页面，显示"本地测试页 - 原始"，作为被替换的原始内容。

### 验证流程
1. 打开测试页，两个 WebView 正常加载原始 URL
2. AI 调用 `webview_redirect_add` 添加规则，将某 URL 指向本地地址
3. 点击页面上的「重新加载」按钮，触发 `resolveRedirect()` 重新执行
4. 验证对应 WebView 已加载目标地址，query 参数正确透传

---

## 实现范围

| 模块 | 变更 |
|------|------|
| `proto/` | 新增 `WebViewRedirectRule` 及相关消息 |
| `clients/android/sdk/` | 新增 `WebViewRedirectStore`、`resolveRedirect()` 实现、HTTP 路由 |
| `clients/android/noop/` | 新建模块，所有公开 API 空实现，JitPack 发布配置 |
| `clients/android/demo/` | 新增 WebView 重定向测试页、`test_local.html` |
| `clients/ios/sdk/` | 新增 `WebViewRedirectStore`、`resolveRedirect()` 实现、HTTP 路由 |
| `clients/ios/noop/` | 新建目录，所有公开 API 空实现，podspec |
| `clients/ios/demo/` | 新增 WebView 重定向测试页、`test_local.html` |
| `mcp/src/tools/webview.ts` | 新增 4 个 redirect 工具 |
| `tests/local-server/` | 新增静态文件服务器，用于验证重定向目标 |
| `docs/mcp-tools.md` | 同步更新工具列表 |
| `docs/sdk-http-api.md` | 同步更新接口文档 |

---

## 本地静态文件服务器

路径：`tests/local-server/`

用途：提供本地 Web 页面供 WebView 重定向测试时加载，运行在开发者电脑上，设备通过局域网 IP 访问。

```
tests/local-server/
  server.js          — Node.js 静态文件服务器（无依赖或仅用 http 模块）
  public/
    index.html       — 默认首页，显示"本地服务器 - 已替换"
    test.html        — 带 query 参数展示的测试页
```

启动方式：
```bash
cd tests/local-server
node server.js         # 默认监听 0.0.0.0:3000
node server.js 8888    # 自定义端口
```

启动后输出局域网访问地址，供设备填入重定向规则的 `targetUrl`。

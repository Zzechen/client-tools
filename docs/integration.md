# Client Tools SDK 接入指南

> 供 Claude Code 读取，由 AI 指导工程师完成接入配置。

---

## 一、工程依赖

### Android

最低 API：26（Android 8.0）

**添加 JitPack 仓库（`settings.gradle.kts` 项目级）：**

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**SDK 依赖（`app/build.gradle.kts` 模块级）：**

```kotlin
// debug 包：完整 SDK，暴露 HTTP 接口供 AI 调用
debugImplementation("com.github.Zzechen:client-tools:v1.0.1")
// release 包：noop 桩，所有接口空实现，零运行时开销
releaseImplementation("com.github.Zzechen:client-tools-noop:v1.0.1")
```

将版本号替换为最新 tag（参见 [Releases](https://github.com/Zzechen/client-tools/releases)）。

> `client-tools-noop` 与 `client-tools` 实现同一接口，release 包无需修改业务代码。

参考实现：`clients/android/demo/`

---

### iOS

最低版本：iOS 14

**Podfile：**

```ruby
platform :ios, '14.0'
use_frameworks!

target 'YourTarget' do
  pod 'SwiftProtobuf', '~> 1.28'
  pod 'ClientToolsSDK', :git => 'https://github.com/Zzechen/client-tools.git', :tag => 'ios/1.0.1'
end

post_install do |installer|
  installer.pods_project.targets.each do |target|
    target.build_configurations.each do |config|
      config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '14.0'
    end
  end
end
```

安装：`pod install`

参考实现：`clients/ios/demo/`

---

## 二、MCP 安装

### 前置条件

- Node.js 18+
- adb（Android Debug Bridge）— Android 调试必需
- iproxy（libimobiledevice）— iOS USB 连接必需

```bash
# 安装 iproxy（macOS）
brew install libimobiledevice
```

### 安装步骤

```bash
# 安装依赖并构建
cd <repo>/mcp
npm install && npm run build
```

> **连接说明：**
> - **Android**：MCP 在每次请求前自动执行 `adb forward tcp:8081 tcp:8081`，无需手动操作。
> - **iOS（USB）**：MCP 在每次请求前自动检测端口并按需启动 `iproxy 8080 8080`，无需手动操作。需确保设备已通过 USB 连接且 iproxy 已安装。

### Claude Code 配置

在项目 `.claude/settings.json` 中添加（或在 Claude Desktop config 中配置）：

```json
{
  "mcpServers": {
    "client-tools": {
      "command": "node",
      "args": ["<repo>/mcp/dist/index.js"]
    }
  }
}
```

将 `<repo>` 替换为本仓库的绝对路径。

### 验证

连接设备后，在 Claude Code 中调用：
- `get_current_page` — 返回当前 Activity/页面名，确认链路正常

---

## 三、Skill 安装

当前只需安装 `client-tools-inspect`（运行时视觉校正协议）。

```bash
cp -r <repo>/skill/client-tools-inspect ~/.claude/skills/
```

验证：在 Claude Code 中输入 `/client-tools-inspect`，确认 skill 正常加载。

---

## 四、View 标识约束

**这是 SDK 能力的硬性前提，缺失则 MCP 工具无法定位 View。**

| 平台 | 要求 | 说明 |
|------|------|------|
| Android | 每个 View 必须设置 `android:id` | 含中间容器层，不可省略 |
| iOS | 每个 View 必须设置 `accessibilityIdentifier` | 含容器层 |

**命名规则：** `<页面前缀>_<语义名>`

示例：
- `login_text_title`
- `login_btn_submit`
- `login_container_form`
- `verify_code_digit_1`

参考实现：`clients/android/demo/src/main/res/layout/`、`clients/ios/demo/`

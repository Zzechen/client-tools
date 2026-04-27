# Client Tools SDK 接入指南

> 供 Claude Code 读取，由 AI 指导工程师完成接入配置。

---

## 一、工程依赖

### Android

最低 API：26（Android 8.0）

**Gradle Plugin（`build.gradle.kts` 项目级）：**

```kotlin
plugins {
    id("com.google.protobuf") version "0.9.4" apply false
}
```

**SDK 依赖（`build.gradle.kts` 模块级）：**

```kotlin
// TODO: Maven 坐标待 SDK 发布后填入，目前使用本地 .aar
implementation(files("libs/client-tools-sdk.aar"))

// protobuf-kotlin（SDK 传递依赖，需显式声明）
implementation("com.google.protobuf:protobuf-kotlin:4.26.1")

// SDK 内部依赖（若未打包进 .aar 则需添加）
implementation("org.nanohttpd:nanohttpd:2.3.1")
implementation("androidx.recyclerview:recyclerview:1.3.2")
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
```

**protobuf 配置（`build.gradle.kts` 模块级）：**

```kotlin
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.26.1"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("kotlin")
            }
        }
    }
}
```

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
  # TODO: CocoaPod 名称待 SDK 发布后填入，目前使用本地路径
  pod 'ClientToolsSDK', :path => '../sdk'
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
- adb（Android Debug Bridge）
- Python 3.11+（`extract_view_layout` 工具依赖）

### 安装步骤

```bash
# 1. 安装依赖并构建
cd <repo>/mcp
npm install && npm run build

# 2. 初始化 preprocess 脚本环境（extract_view_layout 工具依赖）
cd <repo>/mcp/scripts/preprocess
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt

# 3. adb forward（每次连接设备后执行）
adb forward tcp:8080 tcp:8080
```

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
- `extract_view_layout` — 传入一个 HTML 文件路径和 viewport，确认节点数据返回正常

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

# client-tools

让 AI 真正"看懂"并"操作"你的移动端 App。

SDK 嵌入 App 后暴露一套 HTTP 接口，MCP Server 将这些接口封装为 AI 可直接调用的工具。AI 不仅能检查界面、对比设计稿、修改布局，还能通过自定义路由调用 App 的私有能力——页面跳转、获取用户信息、触发业务逻辑，一切均可扩展。

支持平台：Android · iOS

## 功能

**界面检查**
- 截图、获取视图节点树、查询节点属性
- DOM 查询（WebView 内容）

**界面操作**
- 运行时修改布局属性（位置、尺寸、边距、文字样式等）
- 模拟点击、滚动

**设计稿对比**
- 推送 HTML 设计稿叠加到 App 界面
- 自动计算偏移对齐，逐节点视觉校正

**自定义扩展**
- App 自行注册任意 HTTP 路由，暴露私有能力给 AI
- 典型用途：页面跳转、获取当前用户信息、查询 App 状态、触发业务操作

**WebView 重定向**
- 将 App 内 WebView 加载的指定远程 URL 替换为本地开发地址
- 支持正则匹配、query 参数透传，debug/release 包分离（noop 实现）

**其他**
- Mock：拦截和模拟网络请求
- 图片管理：推送本地图片到设备展示
- WebView 覆层：显示/隐藏 HTML 叠加层

## 架构

```
App (Android / iOS)
  └── SDK（HTTP :8080）
        └── MCP Server
              └── AI (Claude)
```

App 内嵌 SDK，SDK 暴露 HTTP 接口；MCP Server 将接口封装为 MCP 工具，供 AI 直接调用。

## 安装

**Android SDK**（via JitPack）

在 `settings.gradle.kts` 中添加 JitPack 仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        // ...
        maven { url = uri("https://jitpack.io") }
    }
}
```

在 `app/build.gradle.kts` 中按 debug/release 分别依赖：

```kotlin
// debug 包：完整 SDK，暴露 HTTP 接口
debugImplementation("com.github.Zzechen:client-tools:v1.1.0")
// release 包：noop 桩，所有接口空实现，零运行时开销
releaseImplementation("com.github.Zzechen:client-tools-noop:v1.1.0")
```

> `client-tools-noop` 与 `client-tools` 实现同一接口，release 包无需改代码，直接替换即可。

**iOS SDK**（via CocoaPods）

```ruby
pod 'ClientToolsSDK', :git => 'https://github.com/Zzechen/client-tools.git', :tag => 'ios/1.1.0'
```

**MCP Server**（本地构建）

```bash
cd mcp && npm install && npm run build
# adb forward（每次连接 Android 设备后执行）
adb forward tcp:8080 tcp:8080
```

Claude Code 配置（`.mcp.json`）：

```json
{
  "mcpServers": {
    "client-tools": {
      "command": "node",
      "args": ["/path/to/client-tools/mcp/dist/index.js"]
    }
  }
}
```

## 快速开始

### App 开发者

在 App 里接入 SDK，让 AI 能看到和操作你的界面，并注册自定义路由暴露 App 私有能力。

→ 查看 [接入指南](docs/integration.md)

### AI / MCP 用户

通过 MCP 工具控制移动端界面、做视觉核对。

→ 查看 [MCP 工具列表](docs/mcp-tools.md)（27 个工具）

→ 查看 [SDK HTTP API](docs/sdk-http-api.md)

## 文档

| 文档 | 内容 |
|------|------|
| [MCP Tools](docs/mcp-tools.md) | 23 个 MCP 工具的参数与返回值，AI 调用参考 |
| [SDK HTTP API](docs/sdk-http-api.md) | SDK HTTP 接口完整参考，含 Android/iOS 对比 |
| [接入指南](docs/integration.md) | App 集成 SDK 的步骤 |

## 目录结构

```
clients/
  android/sdk/     — Android SDK（.aar）
  android/demo/    — Android 接入示例
  ios/sdk/         — iOS SDK（CocoaPod）
  ios/demo/        — iOS 接入示例
mcp/               — MCP Server（TypeScript）
proto/             — Protocol Buffer 定义
docs/              — 文档
skill/             — client-tools-inspect 技能
tests/             — 运行时 E2E 测试脚本
```

## License

[MIT](LICENSE)

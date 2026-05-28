# client-tools

AI Coding 客户端页面开发增强套件。

将 AI 编码能力延伸到移动端运行时：SDK 嵌入 App 暴露 HTTP 接口，MCP Server 将接口封装为 AI 可直接调用的工具，实现「设计稿 → 运行时」的视觉核对与循环修正。

支持平台：Android · iOS

## 架构

```
App (Android / iOS)
  └── SDK（HTTP :8080）
        └── MCP Server
              └── AI (Claude)
```

App 内嵌 SDK，SDK 暴露 HTTP 接口；MCP Server 将接口封装为 MCP 工具，供 AI 直接调用。

## 快速开始

### 我是 App 开发者

想在自己的 App 里接入 SDK，让 AI 能看到和操作你的界面？

→ 查看 [接入指南](docs/integration.md)

### 我是 AI / MCP 用户

想通过 MCP 工具控制移动端界面、做视觉核对？

→ 查看 [MCP 工具列表](docs/mcp-tools.md)（23 个工具）

→ 查看 [SDK HTTP API](docs/sdk-http-api.md)

## 文档导航

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

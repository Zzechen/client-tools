# client-tools

AI Coding 客户端页面开发增强套件，目标是让 AI 高质量完成「设计稿 → 运行时」的实现，并提供运行时视觉核对与循环修正能力。

## 架构

```
App (Android / iOS)
  └── SDK（HTTP :8080）
        └── MCP Server
              └── AI (Claude)
```

App 内嵌 SDK，SDK 暴露 HTTP 接口；MCP Server 将接口封装为 MCP 工具，供 AI 直接调用。

## 文档导航

| 文档 | 内容 |
|------|------|
| [MCP Tools](docs/mcp-tools.md) | 22 个 MCP 工具的参数与返回值，AI 调用参考 |
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
tests/             — 测试
```

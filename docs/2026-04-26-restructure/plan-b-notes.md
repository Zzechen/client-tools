# Plan B：SDK ↔ MCP 通信迁移至 Protocol Buffers（设计备忘）

> 实施前置条件：Plan A（目录重组）完成

## 目标

将 SDK ↔ MCP 之间的 HTTP Body 从 JSON 改为 protobuf binary，各端从同一份 `.proto` schema 生成代码，彻底消除字段漂移。

## 决策记录

| 问题 | 决策 |
|------|------|
| KMP 是否保留 | 否，完全移除（Plan A 已完成） |
| proto 协议范围 | 完整覆盖所有跨端 HTTP Body（Node、ViewProps、ModifyViewRequest、ApiResponse 等） |
| Android 库 | `protobuf-kotlin`（com.google.protobuf:protobuf-kotlin） |
| iOS 库 | `SwiftProtobuf` |
| MCP（Python/Node） | Python: `protobuf`（grpcio-tools 生成）；或切换 MCP 到 Node.js 用 `@bufbuild/protobuf` |
| Python preprocess 是否迁移 | 否，Python preprocess 后续会整体删除，不做 pb 迁移 |

## .proto 文件结构（草稿）

```
proto/
├── node.proto          # Node, NodeType, NodeAttrs, TextAttrs, ImageAttrs…
├── modify.proto        # ModifyViewRequest, ViewProps
├── api.proto           # ApiResponse<T>（通用响应封装）
└── page.proto          # PageChangedEvent, DeviceInfo
```

## 各端代码生成

- **Android**：Gradle protobuf plugin（`com.google.protobuf:protobuf-gradle-plugin`），生成到 `android/sdk/build/generated/`
- **iOS**：`swift-protobuf` CLI，生成 `.pb.swift` 放到 `ios/sdk/Sources/Generated/`
- **MCP**：`protoc --python_out` 或 `buf generate`，生成到 `mcp/generated/`

## HTTP 协议变更

- Content-Type: `application/x-protobuf`（替换 `application/json`）
- 响应 Body 改为 protobuf binary
- 版本协商：header `X-CT-Proto-Version: 1`

## 影响范围

- `android/sdk/` HttpServer、所有 Request/Response 解析
- `ios/sdk/` HttpServer、所有 Codable 替换为 pb 生成类
- `mcp/` 所有工具的请求构造和响应解析
- CI：增加 protoc 编译步骤

## 实施顺序（建议）

1. 写好 `.proto` 文件并各端生成代码（验证 schema 一致性）
2. Android SDK 切换（有完整测试覆盖）
3. MCP 同步切换（Android+MCP 联调）
4. iOS SDK 切换
5. 端到端验证

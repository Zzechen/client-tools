# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

AI Coding 客户端页面开发增强套件，目标是让 AI 高质量完成「设计稿 → 安卓运行时」的实现，并提供运行时视觉核对与循环修正能力。

详细需求见 `docs/requirements-list.md`。

## 目录结构

- `clients/` — 各端客户端实现
  - `clients/android/sdk/` — Android SDK，打包为 `.aar`
  - `clients/android/demo/` — Android 接入示例
  - `clients/ios/sdk/` — iOS SDK（CocoaPod）
  - `clients/ios/demo/` — iOS 接入示例
  - `clients/harmony/sdk/` — HarmonyOS SDK（骨架，待实现）
  - `clients/harmony/demo/` — HarmonyOS 接入示例（骨架）
- `mcp/` — MCP Server，封装 SDK HTTP 接口供 AI 调用
- `skill/` — 仅含 `client-tools-inspect`（运行时视觉校正协议）
- `tests/` — 所有测试，按功能子目录划分
- `docs/` — 文档
- `settings.gradle.kts` — Gradle 多模块根配置（项目根）
- `tech-plan.md` — 整体技术规划

## 开发约定

- 所有文档存放在 `docs/` 目录
- 所有测试存放在 `tests/` 目录，按功能模块细分子目录
- 与用户交流使用中文
- 结构化数据格式优先使用 JSON

## 技术约定

- **Android 布局**：统一使用 XML（不使用 Jetpack Compose）
- **数据模型**：跨端 HTTP 通信使用 Protocol Buffers，schema 在 `proto/`，由 `buf generate` 生成各端代码；Inspector/DOM 接口（`/inspector/*`、`/dom/*`）仍使用 JSON
- **Android 最低版本**：API 26（Android 8.0）
- **iOS 最低版本**：iOS 14
- **View 标识**：
  - Android：每个 View（含中间容器层）必须设置 `android:id`，这是 `get_node`/`get_all_nodes` MCP 工具的硬性前提，缺失则运行时无法定位
  - iOS：每个 View 必须设置 `accessibilityIdentifier`，命名规则与 Android 一致（页面前缀 + 语义名，如 `login_text_title`）
- **Gradle 工程**：根目录在 `clients/android/`（`settings.gradle.kts` 在此），使用 Java 17
- **Gradle Java 路径**：`gradle.properties` 不包含 `org.gradle.java.home`，Java 路径由各开发者在 IDE（Android Studio）的 Gradle JDK 设置中本地配置，不提交到仓库

## 运行测试

```bash
# Android SDK（在 clients/android/ 目录下执行）
cd clients/android && ./gradlew :sdk:assembleDebug
```

## Proto 代码生成（修改 .proto 文件后执行）

```bash
cd proto && buf generate
```

生成目标：
- iOS Swift → `clients/ios/sdk/Sources/Generated/`
- MCP TypeScript → `mcp/src/generated/`
- Android 由 Gradle 自动生成（`clients/android/sdk/src/main/proto/` → build 目录）

**注意：** Android proto 文件需同步更新 `clients/android/sdk/src/main/proto/`（与 `proto/` 保持一致）。

## Superpowers 文档路径

- Spec 文件：`docs/YYYY-MM-DD-<topic>/spec.md`
- Plan 文件：`docs/YYYY-MM-DD-<topic>/plan.md`

## 文档同步约定

修改以下代码时，必须同步更新对应文档：

- 修改 `mcp/src/tools/` 下任何工具（新增/删除/改参数）→ 同步更新 `docs/mcp-tools.md`
- 修改 Android/iOS HttpServer 路由（新增/删除/改接口）→ 同步更新 `docs/sdk-http-api.md`
- 修改项目整体结构或新增模块 → 同步更新 `README.md`

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
- `skill/` — AI 工作流 Skill + 设计稿预处理脚本（Python/Playwright）
- `tests/` — 所有测试，按功能子目录划分（如 `tests/preprocess/`）
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
- **数据模型**：各端独立维护，Android 模型在 `android/sdk/src/main/kotlin/com/clienttools/sdk/models/`，无 KMP 共享层
- **Android 最低版本**：API 26（Android 8.0）
- **iOS 最低版本**：iOS 14
- **Gradle 工程**：根目录在 `clients/android/`（`settings.gradle.kts` 在此），使用 Java 17
- **Gradle Java 路径**：`gradle.properties` 不包含 `org.gradle.java.home`，Java 路径由各开发者在 IDE（Android Studio）的 Gradle JDK 设置中本地配置，不提交到仓库

## 运行测试

```bash
# Python（preprocess）
.claude/skills/client-tools-preprocess/scripts/.venv/bin/pytest tests/preprocess/ -q

# Android SDK（在 clients/android/ 目录下执行）
cd clients/android && ./gradlew :sdk:assembleDebug
```

## Superpowers 文档路径

- Spec 文件：`docs/YYYY-MM-DD-<topic>/spec.md`
- Plan 文件：`docs/YYYY-MM-DD-<topic>/plan.md`

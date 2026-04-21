# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

AI Coding 客户端页面开发增强套件，目标是让 AI 高质量完成「设计稿 → 安卓运行时」的实现，并提供运行时视觉核对与循环修正能力。

详细需求见 `docs/requirements-list.md`。

## 目录结构

- `packages/` — Gradle 多平台工程根目录（settings.gradle.kts 在此）
  - `shared/` — KMP 共享模块（data class + 序列化逻辑，commonMain，Android/iOS 唯一源）
  - `android/sdk/` — Android SDK，打包为 `.aar`（依赖 shared）
  - `android/demo/` — Android 接入示例
  - `ios/sdk/` — iOS SDK，打包为 `.xcframework`（依赖 shared KMP framework）
  - `ios/demo/` — iOS 接入示例
- `mcp/` — MCP Server，封装 SDK HTTP 接口供 AI 调用
- `skill/` — AI 工作流 Skill + 设计稿预处理脚本（Python/Playwright）
- `tests/` — 所有测试，按功能子目录划分（如 `tests/preprocess/`）
- `docs/` — 文档
- `tech-plan.md` — 整体技术规划

## 开发约定

- 所有文档存放在 `docs/` 目录
- 所有测试存放在 `tests/` 目录，按功能模块细分子目录
- 与用户交流使用中文
- 结构化数据格式优先使用 JSON

## 技术约定

- **Android 布局**：统一使用 XML（不使用 Jetpack Compose）
- **KMP 共享模块**：仅包含纯 Kotlin 数据结构和序列化逻辑，不依赖任何平台 API
- **Android 最低版本**：API 26（Android 8.0）
- **iOS 最低版本**：iOS 14
- **Gradle 工程**：根目录在 `packages/`，使用 Java 17（`gradle.properties` 中指定）

## 运行测试

```bash
# Python（preprocess）
.claude/skills/client-tools-preprocess/scripts/.venv/bin/pytest tests/preprocess/ -q

# Kotlin（KMP shared）
cd packages && ./gradlew :shared:jvmTest
```

## Superpowers 文档路径

- Spec 文件：`docs/YYYY-MM-DD-<topic>/spec.md`
- Plan 文件：`docs/YYYY-MM-DD-<topic>/plan.md`

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

AI Coding 客户端页面开发增强套件，目标是让 AI 高质量完成「设计稿 → 安卓运行时」的实现，并提供运行时视觉核对与循环修正能力。

详细需求见 `docs/requirements-list.md`。

## 目录结构

- `android/sdk/` — Android SDK（View 树获取、WebView 叠加、运行时属性修改）
- `android/demo/` — Android 接入示例
- `mcp/` — MCP Server，封装 SDK HTTP 接口供 AI 调用
- `skill/` — AI 工作流 Skill，描述校正循环策略；含设计稿预处理脚本（Python/Playwright）
- `docs/` — 文档
- `tech-plan.md` — 整体技术规划（详细设计见此文档）

## 开发约定

- 所有文档存放在 `docs/` 目录
- 与用户交流使用中文
- 结构化数据格式优先使用 JSON

## Superpowers 文档路径

- Spec 文件：`docs/YYYY-MM-DD-<topic>/spec.md`
- Plan 文件：`docs/YYYY-MM-DD-<topic>/plan.md`

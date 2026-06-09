# Design: iOS P0 Fixes

Date: 2026-06-10

## Overview

修复两个阻塞 iOS 端正常工作的 P0 问题：

1. **P0-1**：`modify_view` 约束修改逻辑改用 Transform 方案
2. **P0-3**：MCP 补齐 iOS iproxy 自动连接支持

---

## P0-1：iOS modify_view 改用 Transform 方案

### 背景

当前 `FrameModifier.swift` 通过查找并停用 `NSLayoutConstraint` 来修改 View 尺寸和位置，存在两个根本缺陷：

- 只能找到 `secondItem == nil` 的绝对约束，无法处理兄弟 View 相对约束
- 替换约束时硬编码 `multiplier: 1`，破坏原有比例关系

### 方案

放弃约束修改路线，改为直接操作 `view.transform`，与 Android 的 `scaleX/scaleY/translationX/translationY` 方案对齐。

Transform 在 AutoLayout 布局完成后叠加，完全绕过约束系统，不影响原有约束，布局刷新后修改仍然保留。

### 属性映射

| 请求参数 | iOS 实现 |
|---|---|
| `scaleX` | `CGAffineTransform(scaleX: sx, y: 1)` |
| `scaleY` | `CGAffineTransform(scaleX: 1, y: sy)` |
| `scaleX + scaleY` | `CGAffineTransform(scaleX: sx, y: sy)` |
| `translationX` | `.translatedBy(x: tx, y: 0)` 叠加到 scale |
| `translationY` | `.translatedBy(x: 0, y: ty)` 叠加到 scale |
| `alpha` | `view.alpha = value`（不变） |

组合时：`view.transform = CGAffineTransform(scaleX: sx, y: sy).translatedBy(x: tx, y: ty)`

### 改动范围

- **文件**：`clients/ios/sdk/Sources/ViewModify/FrameModifier.swift`
- **删除**：`setFixedDimension` 中的约束查找/停用/重建逻辑
- **新增**：基于 `CGAffineTransform` 的 transform 组合逻辑
- **保留**：`alpha` 属性修改逻辑不变

### 重置行为

当请求将 scale 恢复为 1、translation 恢复为 0 时，设置 `view.transform = .identity` 完全重置。

---

## P0-3：MCP iOS iproxy 自动连接支持

### 背景

`mcp/src/sdk-client.ts` 的 `ensureAdbForward` 函数只处理 Android，iOS 分支为空。所有请求方法（`sdkGet/sdkPost/sdkDelete`）都依赖此函数，导致 USB 连接的 iOS 设备无法建立端口转发。

### 方案

新增 `ensureIosProxy` 函数，在每次 iOS 请求前调用，与 `ensureAdbForward` 对称。

### 连接逻辑

```
1. 用 `nc -z localhost <ios_port>` 检测端口是否已监听
2. 已监听 → 直接发请求
3. 未监听 → 执行 `iproxy <port> <port>`（detached spawn）
   3a. iproxy 不存在（ENOENT）→ 抛出异常，错误信息：
       "iproxy not found. Please install libimobiledevice: brew install libimobiledevice"
   3b. 启动成功 → 保存子进程引用，继续请求
```

### 进程管理

- iproxy 子进程引用保存在模块级变量，避免重复 spawn
- 注册 `process.on('exit')`、`process.on('SIGINT')`、`process.on('SIGTERM')` 清理 iproxy 子进程
- iproxy 子进程使用 `detached: true` + `unref()`，不阻塞 MCP 进程退出

### 改动范围

- **文件**：`mcp/src/sdk-client.ts`
- **新增**：`ensureIosProxy()` 函数
- **修改**：`sdkGet/sdkPost/sdkDelete` 中 platform 为 `ios` 时调用 `ensureIosProxy`
- **新增**：进程退出钩子（`process.on('exit'/'SIGINT'/'SIGTERM')`）

### 错误处理

| 场景 | 行为 |
|---|---|
| iproxy 未安装 | 抛出异常，明确告知 `brew install libimobiledevice` |
| 端口已被占用（iproxy 已运行） | 检测到端口监听，跳过 spawn，直接请求 |
| iproxy 启动后进程崩溃 | 下次请求重新检测并尝试重启 |

---

## 不在本次范围内

- iOS WebView files 接口（已确认实现完整，非 P0）
- HarmonyOS SDK
- Protocol Buffers 迁移

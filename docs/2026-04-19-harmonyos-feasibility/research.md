# 鸿蒙 SDK 迁移可行性调研

日期：2026-04-19

## 背景

评估将现有 Android SDK 能力迁移到鸿蒙（HarmonyOS NEXT）的可行性。Android SDK 核心能力包括：内嵌 HTTP 服务器（NanoHTTPD）、运行时 View 树遍历与属性修改、Inspector 叠加层（WebView/图片）。

---

## 问题一：内嵌 HTTP 服务器

### 调研内容

| 方案 | 可行性 | 说明 |
|---|---|---|
| `@ohos.net.socket` TCP Server | ✅ 可行 | 原生支持 TCP 服务端，事件驱动 |
| NAPI 封装 cpp-httplib / mongoose | ✅ 可行 | 需配置 C/C++ 编译工具链，复杂度较高 |
| `@ohos.net.webSocket`（服务端） | ❌ 不支持 | 仅客户端 API |
| 第三方鸿蒙 HTTP Server 库 | ❌ 暂无 | 三方库中心无现成方案 |

### 结论

**可行，需自实现。** 推荐基于 `@ohos.net.socket` 手写轻量级 HTTP 协议解析（解析 method/uri/headers/body，返回 JSON response），复杂度中等，相当于实现一个迷你 NanoHTTPD。

---

## 问题二：UI 组件树遍历与动态修改

### 调研内容

| 能力 | 鸿蒙支持情况 | 说明 |
|---|---|---|
| 遍历完整组件子树 | ❌ 不支持 | 无 `getChildAt()` 等 API |
| 通过 ID 点查询组件位置 | ⚠️ 部分支持 | `UIContext.getComponentUtils().getRectangleById()`，仅返回位置信息 |
| 运行时动态修改组件属性 | ❌ 不支持 | ArkUI 声明式范式，无反射机制 |
| Accessibility API（app 内） | ❌ 不支持 | 仅限测试环境（@ohos.uitest） |
| ArkUI Inspector | ❌ 不支持 | 仅开发时调试工具 |

### 结论

**能力严重受限，是最大的架构差异。** 鸿蒙声明式框架决定「运行时通过 ID 找组件并直接修改」在 app 内无法实现。唯一路径是让宿主 app 主动暴露 `@State` 状态变量，SDK 通过修改状态间接驱动 UI 刷新——这意味着鸿蒙 SDK 接入模式是**侵入式**的，和 Android 非侵入式有根本差异。

---

## 整体能力对照

| 能力 | Android | 鸿蒙 |
|---|---|---|
| HTTP 服务器 | NanoHTTPD（开箱即用） | 需基于 TCP Socket 自实现 |
| View 查询 | 完整子树遍历 | 仅 ID 点查询（位置信息） |
| 动态修改属性 | 运行时直接修改 | ❌ 不支持，只能状态驱动 |
| Inspector 叠加层 | View 系统直接叠加 | ArkUI 组件叠加（可行） |
| 图片/WebView 叠加 | ✅ | ✅（Image/Web 组件） |
| 生命周期管理 | `ActivityLifecycleCallbacks` | `AbilityLifecycleCallback`（有对应） |
| 状态管理 | ViewModel + StateFlow | @State / @Observed（思路一致） |

---

## KMP 可行性

鸿蒙不在 Kotlin 官方 KMP 支持的目标平台列表中（无鸿蒙 target），Kotlin/Native → NAPI 桥接无成熟方案。

**结论：KMP 无法用于鸿蒙移植。** 推荐策略为协议复用：

```
shared/（纯 Kotlin 数据结构）→ Android SDK
                              → iOS SDK（KMP/Native）
                              → 鸿蒙 SDK（ArkTS 独立实现，接口协议保持一致）
```

---

## 综合结论

迁移整体可行，但存在两个根本性差异需提前决策：

1. **HTTP 服务器**：需自实现，工作量可控（1-2周）
2. **View 树遍历/修改**：鸿蒙架构限制，需将接入模式改为侵入式（宿主 app 需配合注册状态），这会影响 SDK 的接入体验和文档设计

建议在全面开发前，先用鸿蒙模拟器做两个 PoC 验证：
- PoC 1：`@ohos.net.socket` 实现 HTTP Server，验证能否稳定监听并处理请求
- PoC 2：宿主 app 暴露 @State，SDK 通过接口修改，验证端到端流程

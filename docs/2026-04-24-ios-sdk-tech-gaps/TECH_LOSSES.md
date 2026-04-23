# iOS SDK 技术差距分析

**日期**：2026-04-24  
**来源**：brainstorming  
**范围**：iOS SDK vs Android SDK 功能对比

---

## 一、背景

iOS 和 Android 是两个完全不同的 UI 框架。iOS SDK 实现时会在某些能力上存在差距，需要在设计阶段明确。

---

## 二、能力对比

### 2.1 HTTP Server

| 能力 | Android | iOS |
|------|---------|-----|
| HTTP Server | Nanohttpd（嵌入式） | GCDWebServer |
| 端口 | 8080 | 8080 |
| REST 端点 | ✅ | ✅ |
| SSE 推送 | ❌ 已删除 | ❌ 不做 |

**结论**：无差距。

---

### 2.2 WebView 叠加

| 能力 | Android | iOS |
|------|---------|-----|
| HTML 加载 | WebView | WKWebView |
| 透明度 | ✅ | ✅ |
| 偏移调整 | ✅ | ✅ |
| 本地文件加载 | ✅ | ✅ |
| DOM 查询 | JS 注入 | JS 注入 |

**结论**：无差距。

---

### 2.3 View 查询

| 能力 | Android | iOS |
|------|---------|-----|
| 查询方式 | 遍历 DecorView | Runtime Hash |
| ID 生成 | `android:id`（原生） | 类名+地址+层级路径（自动生成） |
| 侵入性 | 无 | 无 |
| 稳定性 | 永久唯一 | 页面存活内稳定 |
| 样式属性获取 | ✅ 支持 | ❌ 不支持（UIKit 无运行时样式查询） |

**结论**：基本可替代，但样式属性获取能力缺失。

---

### 2.4 View 修改

| 修改项 | Android | iOS | 差距 |
|--------|---------|-----|------|
| 位置 (x, y) | ✅ LayoutParams | ✅ frame | 无 |
| 尺寸 (w, h) | ✅ LayoutParams | ✅ frame | 无 |
| margin | ✅ LayoutParams | ⚠️ 需改约束 | **无法直接修改** |
| padding | ✅ setPadding() | ⚠️ 仅部分控件支持 | **仅部分控件可改** |
| 背景色 | ✅ | ✅ | 无 |
| 透明度 | ✅ | ✅ | 无 |
| 显隐 | ✅ | ✅ | 无 |
| transform | ✅ | ✅ | 无 |

**iOS margin 无法直接修改的原因**：

iOS 没有 Android 那套 LayoutParams 系统。margin 是通过以下方式实现的：

```
1. Stack View spacing — margin 靠约束 constant 实现
2. Masonry/SnapKit — constraint.constant
3. 原生 Auto Layout — NSLayoutConstraint.constant
```

SDK 想改 margin：
- ✅ 可以改 `constraint.constant`（如果能找到这个约束）
- ❌ 但 UIKit 没有提供遍历 View 约束的公开 API
- ❌ 改 frame 会和 Auto Layout 冲突

**iOS padding 实现限制**：

| 控件 | padding 属性 | SDK 可改 |
|------|-------------|---------|
| UITextField | `contentEdgeInsets` | ✅ |
| UIButton | `contentEdgeInsets` | ✅ |
| UILabel | 无内置 | ❌ |
| UIView | 无内置 | ❌ |

---

## 三、总结

| 能力 | Android | iOS | 差距 |
|------|---------|-----|------|
| HTTP Server | ✅ | ✅ | 无 |
| WebView 叠加 | ✅ | ✅ | 无 |
| View 查询（位置/尺寸）| ✅ | ✅ | 无 |
| View 查询（样式属性）| ✅ | ❌ | **缺失** |
| View 修改（frame）| ✅ | ✅ | 无 |
| View 修改（margin）| ✅ | ⚠️ 约束修改 | **受限** |
| View 修改（padding）| ✅ | ⚠️ 仅部分控件 | **受限** |

---

## 四、iOS SDK 技术决策

| 问题 | 决策 |
|------|------|
| View ID 生成 | Runtime Hash（类名+地址+层级路径），无侵入 |
| margin 修改 | 通过 `constraint.constant` 修改，需遍历约束 |
| padding 修改 | 仅支持 UITextField、UIButton 等有 inset API 的控件 |
| 样式属性查询 | 不支持 |

---

## 五、后续需要确认的问题

- [ ] iOS 约束遍历方案是否可行（是否有私有 API 或遍历方式）
- [ ] 是否接受 margin/padding 修改能力受限
- [ ] 样式属性查询缺失是否影响核心流程

---

## 六、iOS margin/padding 修改 — 可行方案

### 6.1 参考：FLEX 调试工具

[FLEX](https://github.com/Flipboard/FLEX)（Flipboard Explorer）是 iOS 著名的交互式调试工具，已实现：

- View 层级的完整遍历
- 约束（constraints）的查看和修改
- Runtime 属性修改

FLEX 使用 **Objective-C Runtime + 私有 API** 实现，源码开源（BSD License）。

### 6.2 iOS 约束存储的私有路径

UIView 的约束存储在以下私有属性中（通过 KVC 可访问）：

```swift
// 获取 view 的所有约束（自己的 + 加到父视图的）
view.value(forKey: "_constraints")

// 获取 layout guide 中的约束
view.value(forKey: "_viewConstraintLayoutGuide")
```

### 6.3 SDK 非侵入式改 margin 的可行路径

```
1. 遍历 targetView.superview.constraints
   → 找到涉及 targetView 属性的约束（如 .top、.left、.right、.bottom）

2. 遍历 targetView.constraints
   → 找到 targetView 自己持有的约束（如 height、width）

3. 改 constraint.constant
```

**限制**：需要定位到「哪个方向的 margin」。AI 需要知道要改 topMargin 还是 leftMargin。

### 6.4 SDK 设计决策

**SDK 只提供能力，AI 来决策。**

API 设计示例：
```json
POST /api/constraint/modify
{
  "id": "UIButton_0x7f8a3b2c",
  "attribute": "top",      // top | left | right | bottom | leading | trailing
  "constant": -8           // 调整量（dp），正值扩大，负值缩小
}
```

### 6.5 参考 FLEX 的注意事项

| 可以参考 | 不可以直接集成 |
|---------|--------------|
| Runtime 遍历约束的技巧 | FLEX 的 UI 和交互逻辑 |
| 私有 API 路径（`_constraints`） | FLEX 作为库直接依赖 |
| KVC 访问私有属性 | FLEX 的窗口管理 |

FLEX 使用私有 API，但作为开发调试 SDK 可以接受（不打包进生产包、不上架 App Store）。

### 6.6 最终技术决策

| 问题 | 决策 |
|------|------|
| margin 修改 | 通过遍历 `superview.constraints` 找到对应约束，改 `constant` |
| padding 修改 | 同上，或仅支持有 `contentEdgeInsets` 的控件 |
| 样式属性查询 | 不支持 |
| 参考实现 | FLEX 源码（Runtime + 私有 API） |
| SDK 定位 | 只提供能力，AI 决策 |

# Client-Tools SDK 能力基线

**日期**：2026-04-23  
**平台**：Android  
**版本**：v1.0 (模块3)

---

## 一、能力总览

| 能力分类 | 子能力 | 优先级 | 状态 |
|----------|--------|--------|------|
| HTTP Server | 启动服务、端口8080、REST端点、SSE事件 | P0 | ✅ 已实现 |
| View 查询 | 按ID查询、遍历DecorView、样式属性提取 | P0 | ✅ 已实现 |
| View 修改 | margin/padding/宽高修改、多属性同时修改 | P0 | ✅ 已实现 |
| WebView 叠加 | 本地HTML加载、透明度调整、拖拽控制 | P1 | ✅ 已实现 |
| Inspector 叠加 | 图片叠加层、DOM查询对比 | P1 | ✅ 已实现 |
| 页面切换事件 | SSE推送Activity切换事件 | P1 | ✅ 已实现 |
| 自动初始化 | ContentProvider零侵入接入 | P0 | ✅ 已实现 |

---

## 二、HTTP Server

### 2.1 能力清单

| 端点 | 方法 | 功能 | 测试状态 |
|------|------|------|---------|
| `/api/nodes/{id}` | GET | 查询指定ID的视图结构化信息 | ✅ |
| `/api/modify` | POST | 修改视图属性（margin/padding/宽高） | ✅ |
| `/api/overlay/show` | POST | 显示WebView叠加层 | ⚠️ 未测试 |
| `/api/overlay/hide` | POST | 隐藏WebView叠加层 | ⚠️ 未测试 |
| `/api/overlay/opacity` | POST | 调整叠加层透明度 | ⚠️ 未测试 |
| `/api/events` | GET | SSE事件流（页面切换事件） | ✅ |
| `/api/page/current` | GET | 获取当前页面信息 | ⚠️ 文档有但未测试 |
| `/api/inspector/screenshot` | GET | 截图并叠加标注 | ⚠️ 未测试 |

### 2.2 技术实现

```
Nanohttpd (端口8080)
    ↓
ApiHandler (REST路由)
    ↓
┌─────────────────────────────────────────┐
│ /api/nodes/*     → ViewQueryService     │
│ /api/modify      → ViewModifier         │
│ /api/overlay/*   → OverlayManager       │
│ /api/inspector/* → InspectorOverlay     │
│ /api/events      → EventManager (SSE)   │
└─────────────────────────────────────────┘
```

### 2.3 HarmonyOS 评估

| 子能力 | HarmonyOS 可行性 | 方案 |
|--------|-----------------|------|
| HTTP Server | ✅ 可行 | 基于 `@ohos.net.socket` 自实现 |
| REST 端点 | ✅ 可行 | 同上 |
| SSE 事件 | ✅ 可行 | `@ohos.net.socket` + 手动实现 SSE 格式 |
| 端口8080 | ✅ 可行 | 端口号可配置 |

### 2.4 推荐实现方案

**方案：`@ohos.net.socket` TCP Server（自实现简化版 Nanohttpd）**

推荐理由：
- NAPI 封装 C++ HTTP 库（cpp-httplib/mongoose）复杂度高，需配置 C/C++ 编译工具链
- `@ohos.net.socket` 是 HarmonyOS 原生 API，门槛低

**可砍掉的功能（轻量实现）：**
- ❌ Chunked transfer
- ❌ Keep-alive
- ❌ Gzip 压缩
- ❌ 多线程/连接池

**需要实现的功能：**
- ✅ 解析 HTTP 请求行（GET/POST + URI + Version）
- ✅ 解析请求 Headers
- ✅ 解析 POST Body（JSON）
- ✅ 构造 HTTP Response（状态码 + Headers + JSON Body）
- ✅ SSE 事件推送（`text/event-stream` 格式）

**工作量估算**：200-300 行 ArkTS 代码，约 1-2 周

**结论**：HTTP Server 核心能力可完整迁移。

---

## 三、View 查询

### 3.1 能力清单

| 能力 | 说明 | 测试状态 |
|------|------|---------|
| 按ID查找 | `findViewById` 遍历DecorView | ✅ |
| 全量遍历 | 获取所有带ID的View | ✅ |
| 基础属性 | screenX/screenY/widthDp/heightDp/visibility | ✅ |
| 样式属性 | 字体大小/颜色/字重/缩放模式等 | ✅ |
| 节点类型 | TEXT/IMAGE/LIST/CONTAINER | ✅ |

### 3.2 数据结构

```kotlin
data class ViewInfo(
    val id: String,
    val type: String,          // TEXT/IMAGE/LIST/CONTAINER
    val screenX: Float,         // dp，相对屏幕左上角
    val screenY: Float,         // dp
    val widthDp: Float,         // dp
    val heightDp: Float,        // dp
    val visibility: Int,        // VISIBLE/INVISIBLE/GONE
    val isEnabled: Boolean,
    val attrs: NodeAttrs?       // TextAttrs/ImageAttrs/ListAttrs/ContainerAttrs
)
```

### 3.3 HarmonyOS 评估

| 子能力 | HarmonyOS 可行性 | 说明 |
|--------|-----------------|------|
| 按ID查找 | ⚠️ 有限支持 | `UIContext.getComponentUtils().getRectangleById()` 仅返回位置，无属性 |
| 全量遍历 | ❌ 不支持 | ArkUI无子节点遍历API |
| 基础属性 | ⚠️ 有限支持 | 仅位置信息（x/y/width/height），无visibility/isEnabled |
| 样式属性 | ❌ 不支持 | 无法运行时获取组件样式 |
| 节点类型 | ⚠️ 部分 | ArkUI组件类型需人工映射 |

**结论**：View查询能力在HarmonyOS严重受限，**无法完整实现**。

---

## 四、View 修改

### 4.1 能力清单

| 修改类型 | 属性 | 测试状态 |
|----------|------|---------|
| Margin调整 | marginTopDiffDp/marginBottomDiffDp/marginLeftDiffDp/marginRightDiffDp | ✅ |
| Padding调整 | paddingTopDiffDiffDp等 | ✅ |
| 尺寸设置 | widthDp/heightDp | ✅ |
| 批量修改 | 多属性同时修改 | ✅ |

**不支持的属性**（文档提到但未测试）：
- 背景色、字体颜色、字体大小
- wrap_content 特殊值

### 4.2 HarmonyOS 评估

| 修改类型 | HarmonyOS 可行性 | 说明 |
|----------|-----------------|------|
| Margin调整 | ❌ 不支持 | ArkUI无LayoutParams概念 |
| Padding调整 | ❌ 不支持 | ArkUI padding通过组件属性设置 |
| 尺寸设置 | ⚠️ 部分支持 | 需宿主app暴露@State，SDK修改状态间接实现 |

**结论**：View修改在HarmonyOS无法实现，**只能通过状态驱动间接实现（侵入式）**。

---

## 五、WebView 叠加

### 5.1 能力清单

| 能力 | 说明 | 测试状态 |
|------|------|---------|
| 本地HTML加载 | 从本地文件加载HTML | ⚠️ 未测试 |
| 远程URL加载 | 从URL加载 | ⚠️ 未测试 |
| 透明度调整 | 0.0-1.0 | ⚠️ 未测试 |
| 位置调整 | 上下左右偏移 | ⚠️ 未测试 |
| 显示/隐藏 | 按需显示隐藏 | ⚠️ 未测试 |
| 多文件管理 | 按tag+timestamp保存切换 | ⚠️ 未测试 |

### 5.2 技术实现

```
OverlayManager
├── WebViewFileStore (本地HTML存储)
├── WebViewApiHandler (REST控制)
└── WebViewManager (WindowManager浮窗)
```

### 5.3 HarmonyOS 评估

| 子能力 | HarmonyOS 可行性 | 说明 |
|--------|-----------------|------|
| HTML组件加载 | ✅ 可行 | ArkUI的Web组件支持加载HTML |
| 透明度调整 | ✅ 可行 | opacity属性 |
| 位置调整 | ✅ 可行 | translate/position |
| 显示/隐藏 | ✅ 可行 | visibility属性 |
| 文件存储 | ✅ 可行 | @ohos.file.fs |

**结论**：WebView叠加能力可完整迁移。

---

## 六、Inspector 叠加（图片对比）

### 6.1 能力清单

| 能力 | 说明 | 状态 |
|------|------|------|
| 图片文件存储 | InspectorFileStore | ✅ |
| 图片渲染 | ImageRenderer + FitWidthImageView | ✅ |
| DOM节点信息 | DomNodeInfo + DomQueryService | ✅ |
| Inspector面板 | InspectorPanel + InspectorViewModel | ✅ |
| Inspector页面 | InspectorPage (SSE事件订阅) | ✅ |
| 标注标签解析 | InspectorApiHandler | ✅ |
| 截图上报 | screenshot端点 | ❌ 未实现 |

### 6.2 技术实现

```
InspectorOverlay
├── InspectorFileStore (图片存储)
├── ImageRenderer (图片渲染组件)
├── DomQueryService (DOM查询服务)
├── InspectorPanel (悬浮面板)
├── InspectorViewModel (状态管理)
└── InspectorApiHandler (REST + SSE)
```

### 6.3 HarmonyOS 评估

| 子能力 | HarmonyOS 可行性 | 说明 |
|--------|-----------------|------|
| 图片存储 | ✅ 可行 | @ohos.file.fs |
| 图片渲染 | ✅ 可行 | Image组件 |
| DOM查询 | ⚠️ 需重建 | HarmonyOS无DOM概念，需新建映射层 |
| Inspector面板 | ✅ 可行 | Stack容器 + 悬浮按钮 |
| SSE事件 | ✅ 可行 | 同HTTP Server方案 |
| 截图上报 | ✅ 可行 | @ohos.multimedia.media |

**结论**：Inspector叠加能力可完整迁移，DOM查询需重建。

---

## 七、页面切换事件

### 7.1 能力清单

| 能力 | 说明 | 测试状态 |
|------|------|---------|
| Activity监听 | ActivityLifecycleCallbacks | ✅ |
| 事件推送 | SSE格式 | ✅ |
| 事件格式 | `{"event":"page_changed","pageName":"...","timestamp":"..."}` | ✅ |

### 7.2 HarmonyOS 评估

| 子能力 | HarmonyOS 可行性 | 说明 |
|--------|-----------------|------|
| 页面监听 | ✅ 可行 | UIAbilityLifecycleCallback |
| 事件推送 | ✅ 可行 | 同HTTP Server方案 |

**结论**：页面切换事件可完整迁移。

---

## 八、自动初始化

### 8.1 能力清单

| 能力 | 说明 | 测试状态 |
|------|------|---------|
| ContentProvider | 自动启动HTTP Server | ✅ |
| 零侵入接入 | 只需添加依赖 | ✅ |

### 8.2 HarmonyOS 评估

| 子能力 | HarmonyOS 可行性 | 说明 |
|--------|-----------------|------|
| 自动初始化 | ✅ 可行 | 在EntryAbility onCreate()中初始化 |
| 零侵入接入 | ⚠️ 部分可行 | 需宿主app导入SDK并初始化 |

**结论**：自动初始化可迁移，但需宿主app显式调用初始化方法。

---

## 九、总结：HarmonyOS 能力映射表

### 9.1 完整可迁移

| Android能力 | HarmonyOS方案 | 工作量 |
|-------------|--------------|--------|
| HTTP Server (REST + SSE) | @ohos.net.socket 自实现 | 1-2周 |
| WebView叠加 | ArkUI Web组件 | 1周 |
| Inspector图片叠加 | Image组件 + Stack | 1周 |
| 页面切换事件 | UIAbilityLifecycleCallback | 1-2天 |
| 文件存储 | @ohos.file.fs | 2-3天 |
| 自动初始化 | EntryAbility onCreate() | 1天 |

### 9.2 部分可迁移（有限支持）

| Android能力 | HarmonyOS限制 | 需改的地方 |
|-------------|--------------|-----------|
| View查询 | 仅ID查位置，无属性遍历 | 只能按ID查位置，无法遍历和获取样式 |
| Inspector DOM查询 | 无DOM概念 | 需重建DOM映射层 |

### 9.3 无法迁移（非侵入式）

| Android能力 | HarmonyOS障碍 | 替代方案 |
|-------------|--------------|---------|
| View属性修改 | ArkUI声明式，无法直接修改 | **侵入式方案**：宿主app暴露@State变量，SDK通过修改状态间接驱动UI |

### 9.4 结论

| 能力维度 | Android | HarmonyOS | 差距 |
|----------|---------|----------|------|
| HTTP Server | ✅ | ✅ | 无 |
| View查询 | ✅ 完整 | ⚠️ 有限 | 无遍历、无属性获取 |
| View修改 | ✅ 非侵入 | ❌ 侵入式 | 需改接入模式 |
| WebView叠加 | ✅ | ✅ | 无 |
| Inspector叠加 | ✅ | ✅ | DOM映射层需重建 |
| 页面事件 | ✅ | ✅ | 无 |
| 接入模式 | 非侵入 | 侵入式 | 根本性差异 |

**最终结论**：HarmonyOS SDK **技术上可实现**，但存在一个**根本性架构差异**：

> Android SDK 是**非侵入式**的（通过 DecorView 遍历直接操作任意 View），而 HarmonyOS 因 ArkUI 声明式框架限制，必须改为**侵入式**接入（宿主 app 需主动暴露 @State 变量给 SDK）。

这对 SDK 的使用方式和文档设计有重大影响，建议在全面开发前先做 PoC 验证。

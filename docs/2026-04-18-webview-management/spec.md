# WebView 管理系统 Spec

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:writing-plans to create implementation plan based on this spec.

**Goal:** 为 Android SDK 实现完整的 WebView 管理系统，支持 HTML 推送、本地存储、手动调整面板等功能，用于设计稿与运行时的视觉对比。

**Architecture:** 分层架构，包括 HTTP 接口层、本地存储层和 UI 层。HTTP 接口接收并保存设计稿 HTML，UI 层提供悬浮窗和可折叠面板用于手动调整（位移、透明度），支持完整的拖动和文件管理功能。

**Tech Stack:** Kotlin, Android SDK, Nanohttpd, Storage Framework

---

## 1. 系统架构

### 1.1 分层设计

```
┌─────────────────────────────────────────────┐
│         UI 层（悬浮窗 + 面板）               │
│  ┌─ 悬浮按钮（可拖动）                      │
│  └─ 展开面板（可拖动，3 个可折叠模块）     │
├─────────────────────────────────────────────┤
│         HTTP 接口层（Nanohttpd）             │
│  POST /webview/push-html                    │
│  POST /webview/show                         │
│  POST /webview/hide                         │
│  POST /webview/adjust                       │
│  GET  /webview/files                        │
├─────────────────────────────────────────────┤
│         本地存储层（App Cache）              │
│  <cache>/webview/<tag>/<tag>_<timestamp>.html
├─────────────────────────────────────────────┤
│         OverlayManager（WebView 容器）       │
│  show(url, opacity)                         │
│  hide()                                     │
│  setOpacity(opacity)                        │
│  setOffset(offsetX, offsetY)                │
└─────────────────────────────────────────────┘
```

### 1.2 主要组件

| 组件 | 职责 | 实现语言 |
|-----|------|--------|
| **WebViewManager** | 统一管理 WebView 生命周期、存储、HTTP 接口 | Kotlin |
| **FloatingControlPanel** | 悬浮按钮和展开面板 UI | Kotlin (自定义 View) |
| **WebViewFileStore** | 本地存储管理 | Kotlin |
| **WebViewApiHandler** | HTTP 接口处理 | Kotlin |
| **OverlayManager** | 增强已有 OverlayManager（添加位移控制） | Kotlin |

---

## 2. HTTP 接口

### 2.1 推送 HTML

```
POST /webview/push-html

请求头：
Content-Type: application/json

请求体：
{
  "tag": "login",
  "html": "<html>...</html>",
  "timestamp": "0418-1430"  // MMdd-HHmm 格式，可省略，服务端自动生成
}

响应：
{
  "code": 0,
  "message": "success",
  "data": {
    "tag": "login",
    "timestamp": "0418-1430",
    "filePath": "/data/user/0/com.clienttools.demo/cache/webview/login/login_0418-1430.html",
    "fileSize": 5120
  }
}

错误响应（400）：
{
  "code": 400,
  "message": "Invalid HTML content or tag"
}
```

### 2.2 显示 WebView

```
POST /webview/show

请求体：
{
  "tag": "login",
  "timestamp": "0418-1430"  // 指定加载哪个文件
}

响应：
{
  "code": 0,
  "message": "success",
  "data": {
    "tag": "login",
    "timestamp": "0418-1430",
    "opacity": 1.0,
    "offsetX": 0,
    "offsetY": 0
  }
}
```

### 2.3 隐藏 WebView

```
POST /webview/hide

响应：
{
  "code": 0,
  "message": "success"
}
```

### 2.4 调整位移和透明度

```
POST /webview/adjust

请求体：
{
  "offsetX": 10,        // 相对当前位置的增量（dp），可正可负
  "offsetY": -20,
  "opacity": 0.7,       // 绝对值 0.0-1.0，可省略（只调整位移）
  "step": "10dp"        // 档位，用于 UI 记录当前设置，可省略
}

响应：
{
  "code": 0,
  "data": {
    "offsetX": 10,      // 当前累计位移
    "offsetY": -20,
    "opacity": 0.7
  }
}
```

### 2.5 获取已保存文件列表

```
GET /webview/files

响应：
{
  "code": 0,
  "data": {
    "files": [
      {
        "tag": "login",
        "timestamp": "0417-1400",
        "size": 4096,
        "isCurrent": false
      },
      {
        "tag": "login",
        "timestamp": "0418-1430",
        "size": 5120,
        "isCurrent": true
      }
    ]
  }
}
```

---

## 3. 本地存储

### 3.1 目录结构

```
<app-cache>/webview/
├─ login/
│  ├─ login_0417-1400.html
│  ├─ login_0417-1410.html
│  └─ login_0418-1430.html        (当前加载的，标记为 current)
├─ home/
│  └─ home_0418-1500.html
└─ profile/
   └─ profile_0418-1600.html
```

### 3.2 存储管理

**路径规则：**
```
<tag>_<timestamp>.html
- <tag>: 设计稿标签（如 login、home、profile），由推送端指定
- <timestamp>: MMdd-HHmm 格式（如 0418-1430），由推送端指定或服务端自动生成
```

**当前文件标记：**
- 每个 tag 只有一个「当前」文件
- 加载新文件时，自动更新当前文件指针
- 可通过 `isCurrent` 字段查询

**清理策略：**
- 手动清理：提供 HTTP 接口删除指定文件或某个 tag 下的所有文件（后续可扩展）
- 自动清理：超过 7 天的文件自动删除（可配置）

---

## 4. UI 层：悬浮窗和面板

### 4.1 悬浮按钮

**外观：**
- 大小：10×10 dp
- 形状：圆形
- 颜色：蓝色（#6200EE）
- 初始位置：屏幕右下角（距边 10dp）

**交互：**
- 点击：展开面板
- 长按/拖动：移动悬浮按钮（MotionEvent.ACTION_MOVE）

### 4.2 展开面板

**外观：**
- 宽度：280 dp
- 高度：动态（初始 400dp，随模块展开/隐藏调整）
- 背景：白色，圆角 8dp，阴影
- 位置：浮动，默认右下，可拖动

**拖动：**
- 顶部拖动条（高 40dp）用于拖动整个面板
- 拖动时保持面板在屏幕内（边界检测）

### 4.3 模块设计

#### 4.3.1 WebView 模块（可折叠）

```
┌─────────────────────────────┐
│ ▼ WebView                   │  (点击折叠/展开)
├─────────────────────────────┤
│ 当前：login (0418-1430)     │
│                             │
│ 已保存文件：                 │
│ ○ login_0417-1400          │
│ ◐ login_0418-1430 ★ 当前   │
│ ○ home_0418-1500           │
│                             │
└─────────────────────────────┘
```

**功能：**
- 显示当前加载的 HTML 标签和时间戳
- 列出所有已保存的 HTML 文件
- 点击文件可快速加载（调用 `/webview/show` 接口）
- 当前文件用 ★ 和半圆符号标记

#### 4.3.2 调整模块（可折叠）

```
┌─────────────────────────────┐
│ ▼ 调整                      │  (点击折叠/展开)
├─────────────────────────────┤
│ 位移（档位: 1dp 10dp 50dp） │
│          △                  │
│       ◀︎ ◆ ▶︎                │
│          ▽                  │
│ [1dp]  [10dp]  [50dp]      │
│                             │
│ 透明度：                     │
│ [==========●===] 70%       │
│                             │
│ 当前偏移：                   │
│ X: +20dp   Y: -15dp        │
└─────────────────────────────┘
```

**功能：**
- **位移控制：**
  - 4 个方向按钮（上下左右）
  - 3 个档位选择（1dp、10dp、50dp），默认 10dp
  - 点击按钮调用 `/webview/adjust` 接口
  - 显示当前累计位移

- **透明度滑块：**
  - 范围 0.0 - 1.0
  - 默认 0.5
  - 实时调整，调用 `/webview/adjust` 接口

#### 4.3.3 控制模块（始终可见，不可折叠）

```
┌─────────────────────────────┐
│ 控制                        │
├─────────────────────────────┤
│ [显示 WebView] [隐藏]       │
│                             │
│          [关闭面板]         │
└─────────────────────────────┘
```

**功能：**
- **显示 / 隐藏 WebView：** 切换按钮，调用 `/webview/show` 或 `/webview/hide` 接口
- **关闭面板：** 隐藏展开的面板，保留悬浮按钮

### 4.4 交互流程

```
1. 启动 App
   ↓
2. SDK 自动显示悬浮按钮（右下角）
   ↓
3. 点击悬浮按钮 → 展开面板（动画展开，科技感效果）
   ↓
4. 用户操作（2 选 1）
   A) AI 推送 HTML → 自动加载到 WebView（后台），显示在【已保存文件】列表中
   B) 加载已保存文件 → 点击列表中文件 → 调用 /webview/show → WebView 显示
   ↓
5. WebView 在屏幕上叠加显示
   ↓
6. 调整 WebView
   - 位移：点击方向按钮，选择档位 → 调用 /webview/adjust
   - 透明度：拖动滑块 → 调用 /webview/adjust
   ↓
7. 隐藏 WebView
   - 点击【隐藏】按钮 → 调用 /webview/hide
   ↓
8. 关闭面板
   - 点击【关闭面板】按钮
   - 悬浮按钮保留在屏幕上
```

---

## 5. UI 设计指南（科技感）

### 5.1 悬浮按钮

**样式：**
- 背景：渐变色（#6200EE → #3700B3）
- 大小：48×48 dp（易点击）
- 形状：圆形，带阴影（elevation 8dp）
- 图标：白色，简约设计（如 ⚙️ 或 🎛️），使用 Material Icons

**动画：**
- 点击时：按压效果（缩小 10%，回弹）
- 拖动时：实时跟随手指，边界检测

### 5.2 展开面板

**样式：**
- 背景：深色背景（#1F1F1F）+ 毛玻璃效果（iOS style）
- 文字：白色或浅灰色
- 边框：微弱的发光边框（#6200EE 20% 透明度）
- 圆角：16 dp
- 阴影：elevation 16dp + 外发光效果

**动画：**
- 展开：从悬浮按钮位置弹出，缩放动画（0.8 → 1.0）+ 透明度渐变（0 → 1）
- 折叠模块：滑动收起，高度动画
- 拖动面板：跟随手指，松手时物理弹性动画

### 5.3 模块设计

**WebView 模块：**
- 标题：字号 14sp，粗体
- 文件列表：每项 44dp 高度，hover 时背景高亮（#333333）
- 当前文件：半圆符号 ◐ + ★，颜色突出（#6200EE）

**调整模块：**
- 方向按钮：48×48 dp，圆形，带 ripple 效果
- 档位选择：3 个 toggle button，选中状态加强色
- 透明度滑块：Material Design 风格，拖动时显示百分比气泡

**控制模块：**
- 按钮：全宽，高 48dp，带 ripple 动画
- 按钮间距：8 dp

---

## 6. Activity 重启兼容性

### 6.1 状态持久化

WebView 的显示状态需要在 Activity 销毁/重启（如屏幕旋转）时恢复。

**持久化的状态：**
1. 当前加载的 HTML 文件（tag + timestamp）
2. WebView 的显示/隐藏状态
3. 位移值（offsetX, offsetY）
4. 透明度（opacity）

**实现方式：**

使用 `SavedStateHandle` 或 `ViewModel` 保存状态：

```kotlin
// ViewModel 方式（推荐）
class WebViewViewModel : ViewModel() {
    // 当前加载的文件
    val currentFile = MutableLiveData<Pair<String, String>>()  // (tag, timestamp)
    
    // WebView 是否显示
    val isWebViewVisible = MutableLiveData<Boolean>(false)
    
    // 位移和透明度
    val offsetX = MutableLiveData<Int>(0)
    val offsetY = MutableLiveData<Int>(0)
    val opacity = MutableLiveData<Float>(1.0f)
    
    // 当 Activity 销毁时，这些数据被保留
    // Activity 重启时自动恢复
}
```

### 6.2 恢复流程

```
Activity.onCreate()
  ↓
检查 ViewModel.currentFile
  ↓ 如果存在且显示状态为 true
  ↓
调用 /webview/show(tag, timestamp)
  ↓
调用 /webview/adjust(offsetX, offsetY, opacity)
  ↓
WebView 显示位置和透明度恢复完成
```

### 6.3 注意事项

- ✅ 不保存 HTML 文件内容，只保存标识（tag + timestamp）
- ✅ 文件列表从本地存储动态读取，无需特殊恢复
- ✅ 悬浮按钮和面板位置：屏幕旋转后重置到默认位置（右下角），用户可重新拖动
- ✅ 所有 HTTP 调用幂等，重复调用不会产生副作用

---

## 7. OverlayManager 增强

现有 OverlayManager 基础上添加：

```kotlin
object OverlayManager {
    // 已有方法
    fun show(url: String, opacity: Float = 1.0f): Boolean
    fun hide(): Boolean
    fun setOpacity(opacity: Float): Boolean
    
    // 新增方法
    fun setOffset(offsetX: Int, offsetY: Int): Boolean  // dp 为单位
    fun getOffset(): Pair<Int, Int>  // 返回 (offsetX, offsetY)
}
```

**实现细节：**
- 使用 `WindowManager.LayoutParams` 的 `x` 和 `y` 字段存储位移
- 每次调整时重新调用 `windowManager.updateViewLayout()`

---

## 8. 验收标准

| 功能 | 验收条件 |
|-----|--------|
| **HTML 推送** | 接收 POST /webview/push-html，正确保存文件，返回文件路径 |
| **WebView 显示** | 加载 HTML 文件，正确显示在屏幕上，透明度正确 |
| **WebView 隐藏** | 隐藏 WebView，释放资源 |
| **位移调整** | 支持 4 个方向，3 个档位，累计位移正确显示 |
| **透明度调整** | 滑块范围 0-100%，实时调整 |
| **文件列表** | 列出所有已保存文件，标记当前文件 |
| **快速切换** | 点击列表文件，正确加载对应 HTML |
| **悬浮窗拖动** | 悬浮按钮和面板都支持拖动，不超出屏幕边界 |
| **模块折叠** | 模块标题可点击展开/隐藏，面板高度动态调整 |
| **HTTP 接口** | 所有接口返回正确的 JSON 响应 |

---

## 9. 实现清单

- [ ] WebViewManager 类（统一管理）
- [ ] WebViewFileStore 类（本地存储）
- [ ] WebViewApiHandler 类（HTTP 接口）
- [ ] FloatingControlPanel 类（UI 悬浮窗）
  - [ ] 悬浮按钮
  - [ ] 展开面板
  - [ ] WebView 模块
  - [ ] 调整模块
  - [ ] 控制模块
- [ ] OverlayManager 增强（位移控制）
- [ ] 集成到 SDK 初始化流程
- [ ] 单元测试和集成测试

---

## 10. 依赖关系

- **依赖于：** 已有的 OverlayManager、HTTP Server 框架、SDK 初始化机制
- **被依赖于：** 模块 4&5（差异计算和 AI 校正循环）

---

## 11. 参考资源

- tech-plan.md 第 3.1 和 3.4 节
- OverlayManager.kt（已有实现）
- ApiHandler.kt（已有框架）

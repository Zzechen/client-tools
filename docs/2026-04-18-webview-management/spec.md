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
│         WebViewRenderer（WebView 渲染）       │
│  collect isVisible → visibility             │
│  collect currentFile → loadUrl              │
│  collect opacity → alpha                    │
│  collect offsetX/Y → translationX/Y         │
└─────────────────────────────────────────────┘
```

### 1.2 主要组件

| 组件 | 职责 | 实现语言 |
|-----|------|--------|
| **InspectorPage** | 每个 Activity 的管理单元，封装 View + ViewModel，注册到 SDK 栈 | Kotlin |
| **InspectorViewModel** | 单页状态中心（currentFile、isVisible、offsetX/Y、opacity） | Kotlin (AndroidViewModel) |
| **InspectorPanel** | 悬浮按钮和展开面板 UI，观察 ViewModel 驱动渲染 | Kotlin (自定义 View) |
| **WebViewRenderer** | 持有 WebView 引用，collect InspectorViewModel → 执行 loadUrl/visibility/alpha/translationX/Y | Kotlin |
| **InspectorFileStore** | 本地存储管理 | Kotlin |
| **InspectorApiHandler** | HTTP 接口处理，写入当前页 ViewModel | Kotlin |
| **ClientToolsSDK** | 全局入口，维护 InspectorPage 栈，提供 getTop() | Kotlin (object) |

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

**处理逻辑：**
1. 从 `InspectorFileStore` 查文件路径，文件不存在则返回 404
2. `viewModel.currentFile.value = FileInfo(tag, timestamp, fileUrl)`
3. `viewModel.isVisible.value = true`
4. `WebViewRenderer` collect 到变化后自动执行 `loadUrl` + `visibility = VISIBLE`

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
  "offsetX": 10,        // 增量（dp），可正可负，服务端执行 viewModel.offsetX += offsetX
  "offsetY": -20,
  "opacity": 0.7        // 绝对值 0.0-1.0，可省略（只调整位移）
}

响应：
{
  "code": 0,
  "data": {
    "offsetX": 30,      // 累计绝对位移（执行增量后的 ViewModel 当前值）
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
- `isCurrent` 不持久化，完全由内存状态动态计算
- `GET /webview/files` 响应时，`InspectorApiHandler` 从 `InspectorFileStore` 取文件列表，再与 `ClientToolsSDK.getTop()?.viewModel?.currentFile?.value` 对比，匹配则 `isCurrent = true`
- `getTop()` 为 null 时（无前台 Activity），所有文件 `isCurrent = false`

**清理策略：**
- 手动清理：提供 HTTP 接口删除指定文件或某个 tag 下的所有文件（后续可扩展）
- 自动清理：超过 7 天的文件自动删除（可配置）

---

## 4. UI 层：悬浮窗和面板

### 4.1 悬浮按钮

**外观：**
- 大小：40×40 dp
- 形状：圆形
- 颜色：蓝色（#6200EE）
- 初始位置：屏幕右下角（距边 10dp）

**交互：**
- 点击：展开面板
- 长按/拖动：移动悬浮按钮（MotionEvent.ACTION_MOVE）

### 4.2 展开面板

**外观：**
- 宽度：280 dp
- 高度：动态（初始 200dp，随模块展开/隐藏调整）
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
│ [1dp]  [10dp]  [50dp]      │
│ [◀︎]    [△]    [▽]   [▶︎]   │
│                             │
│ 透明度：                     │
│ [==========●===] 50%       │
│ 偏移：X: +20dp  Y: -15dp   │
└─────────────────────────────┘
```

**功能：**
- **位移控制：**
  - 4 个方向按钮（上下左右）排成一行：[◀︎] [△] [▽] [▶︎]
  - 3 个档位选择（1dp、10dp、50dp），默认 10dp
  - 点击按钮：`viewModel.offsetX.value += step`（或 offsetY），直接写 ViewModel，增量累加
  - 显示当前累计绝对位移（来自 ViewModel）

- **透明度滑块：**
  - 范围 0.0 - 1.0
  - 默认 0.5
  - 拖动时直接写 `viewModel.opacity.value = progress`，不走 HTTP

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
6. 调整 WebView（手动操作直接写 ViewModel，不走 HTTP）
   - 位移：选择档位 → 点击方向按钮 → viewModel.offsetX/Y += step
   - 透明度：拖动滑块 → viewModel.opacity = value
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
- 大小：40×40 dp
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
- 档位选择：3 个 toggle button，选中状态加强色，排在方向按钮上方一行
- 方向按钮：[◀︎] [△] [▽] [▶︎] 四个按钮排成一行，均匀分布，每个 48×48 dp，圆形，带 ripple 效果
- 透明度滑块：Material Design 风格，默认值 50%，拖动时显示百分比气泡
- 偏移显示：紧接滑块下方，单行显示 X/Y 值

**控制模块：**
- 按钮：全宽，高 48dp，带 ripple 动画
- 按钮间距：8 dp

---

## 6. Activity 重建兼容性

`InspectorViewModel` 继承 `AndroidViewModel`，Activity 旋转重建时 ViewModel 实例不销毁，所有 StateFlow 状态（currentFile、isVisible、offsetX/Y、opacity）天然保留。

`InspectorPage.attach()` 在重建后重新执行，`WebViewRenderer` 和 `InspectorPanel` 重新 collect ViewModel，自动恢复到重建前的状态，无需额外逻辑。

- 悬浮按钮和面板的屏幕位置（x/y 坐标）重置到默认位置（右下角），不做持久化。

---

## 7. InspectorPage 与数据流

### 7.1 XML 布局结构

三者（WebView、悬浮按钮、看板）统一收到一个 XML 文件 `inspector_overlay.xml` 中，通过 FrameLayout 层叠，层级由 view 顺序决定（后者在上）：

```xml
<!-- res/layout/inspector_overlay.xml -->
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- 层级 1（最底）：WebView，全屏，透明背景 -->
    <WebView
        android:id="@+id/overlay_webview"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@android:color/transparent"
        android:visibility="gone" />

    <!-- 层级 2：看板面板，默认隐藏，展开后浮于 WebView 上 -->
    <include
        layout="@layout/inspector_panel"
        android:visibility="gone" />

    <!-- 层级 3（最顶）：悬浮按钮，始终可见，可拖动 -->
    <TextView
        android:id="@+id/float_btn"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:layout_gravity="bottom|end"
        android:layout_margin="16dp" />

</FrameLayout>
```

看板单独拆成 `inspector_panel.xml`（可折叠模块），通过 `<include>` 引入，保持各自文件职责清晰。

### 7.2 InspectorPage

每个 Activity 对应一个 `InspectorPage` 实例，是该页面的管理单元：

```kotlin
class InspectorPage(val activity: Activity) {
    val viewModel: InspectorViewModel =
        ViewModelProvider(activity)[InspectorViewModel::class.java]

    // inflate 统一布局，包含 WebView + 悬浮按钮 + 看板
    val rootView: View = LayoutInflater.from(activity)
        .inflate(R.layout.inspector_overlay, null)
    val panel: InspectorPanel = InspectorPanel(rootView, viewModel)
    val renderer: WebViewRenderer = WebViewRenderer(rootView, viewModel)

    fun attach() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content.addView(rootView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val scope = (activity as LifecycleOwner).lifecycleScope
        panel.startObserving(scope)
        renderer.startObserving(scope)
    }

    fun detach() {
        panel.stopObserving()
        renderer.stopObserving()
    }
}
```

### 7.3 InspectorViewModel

每个 Activity 独立实例，状态互不共享：

```kotlin
data class FileInfo(
    val tag: String,
    val timestamp: String,
    val fileUrl: String   // file:// 绝对路径，供 WebView.loadUrl() 使用
)

class InspectorViewModel(app: Application) : AndroidViewModel(app) {
    val currentFile = MutableStateFlow<FileInfo?>(null)
    val isVisible   = MutableStateFlow(false)
    val offsetX     = MutableStateFlow(0)     // dp，累计绝对值
    val offsetY     = MutableStateFlow(0)     // dp，累计绝对值
    val opacity     = MutableStateFlow(0.5f)  // 0.0-1.0，默认 0.5
}
```

### 7.4 ClientToolsSDK 栈

```kotlin
object ClientToolsSDK {
    private val pageStack = mutableListOf<InspectorPage>()  // 有序，栈顶=当前前台

    fun getTop(): InspectorPage? = pageStack.lastOrNull()

    // 内部由 ActivityLifecycleCallbacks 调用
    internal fun push(page: InspectorPage)   { pageStack.add(page) }
    internal fun remove(page: InspectorPage) { pageStack.remove(page) }
}
```

注册逻辑（`ClientToolsSDK.init(application)` 时）：

```kotlin
application.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
    val pages = WeakHashMap<Activity, InspectorPage>()

    override fun onActivityResumed(activity: Activity) {
        if (pages[activity] == null) {
            val page = InspectorPage(activity)
            page.attach()
            pages[activity] = page
            ClientToolsSDK.push(page)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        pages.remove(activity)?.let {
            it.detach()
            ClientToolsSDK.remove(it)
        }
    }
})
```

### 7.5 数据流

```
HTTP 请求（AI 推送 / 接口调用）
  ↓
InspectorApiHandler
  → ClientToolsSDK.getTop().viewModel.xxx.value = ...
  ↓
┌─────────────────────────────────────┐
│  InspectorViewModel (StateFlow)        │
│  currentFile / isVisible / offset / opacity │
└─────────────────────────────────────┘
  ↓ collect                    ↓ collect
InspectorPanel           WebViewRenderer
  UI 自动刷新                  WebView 显隐/位移/透明度自动同步

手动操作（面板按钮 / 滑块）
  ↓
InspectorPanel → viewModel.xxx.value = ...
  ↓（同上，WebViewRenderer 响应）
```

### 7.6 原则说明

- **零侵入**：Demo/宿主 App 无需任何 SDK 代码，完全由 `ActivityLifecycleCallbacks` 自动管理
- **只注入一次**：首次 `onActivityResumed` 时创建 `InspectorPage` 并挂载，此后始终存在
- **独立状态**：各页面 ViewModel 独立，切换 Activity 不共享状态
- **单一写入点**：无论 HTTP 还是手动操作，都只写 ViewModel；WebViewRenderer 和 InspectorPanel 均为观察者

---

## 8. WebViewRenderer

轻量的渲染执行者，职责单一：持有 `inspector_overlay.xml` 中的 WebView 引用，collect `InspectorViewModel` 驱动渲染，不持有任何状态。

```kotlin
// scope 由 InspectorPage 传入（使用 activity.lifecycleScope），与 Activity 生命周期绑定
class WebViewRenderer(rootView: View, private val viewModel: InspectorViewModel) {
    private val webView: WebView = rootView.findViewById(R.id.overlay_webview)
    private var job: Job? = null

    fun startObserving(scope: CoroutineScope) {
        job = scope.launch {
            launch { viewModel.isVisible.collect { webView.visibility = if (it) View.VISIBLE else View.GONE } }
            launch { viewModel.currentFile.filterNotNull().collect { webView.loadUrl(it.fileUrl) } }
            launch { viewModel.opacity.collect { webView.alpha = it } }
            launch {
                combine(viewModel.offsetX, viewModel.offsetY) { x, y -> x to y }
                    .collect { (x, y) ->
                        webView.translationX = x.dpToPx(webView.context)
                        webView.translationY = y.dpToPx(webView.context)
                    }
            }
        }
    }

    fun stopObserving() { job?.cancel() }
}
```

**WebView 初始化配置**（在 `InspectorPage.attach()` 中，inflate 后立即配置）：
```kotlin
webView.settings.javaScriptEnabled = true
webView.settings.domStorageEnabled = true
webView.settings.allowFileAccess = true
@Suppress("DEPRECATION")
webView.settings.allowFileAccessFromFileURLs = true
webView.setBackgroundColor(Color.TRANSPARENT)
```

---

## 9. 验收标准

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

## 10. 实现清单

- [ ] `InspectorViewModel`（StateFlow 状态中心）
- [ ] `InspectorPage`（Activity 管理单元，封装 View + ViewModel）
- [ ] `ClientToolsSDK` 栈管理（push / remove / getTop）
- [ ] `ActivityLifecycleCallbacks` 注册与自动注入
- [ ] `res/layout/inspector_overlay.xml`（统一根布局：WebView + 悬浮按钮 + 看板 `<include>`）
- [ ] `res/layout/inspector_panel.xml`（看板面板，含 3 个可折叠模块）
- [ ] `InspectorPanel`（悬浮按钮 + 看板逻辑，观察 ViewModel）
  - [ ] 悬浮按钮（可拖动，点击展开/收起看板）
  - [ ] 看板面板（可拖动）
  - [ ] WebView 模块（文件列表 + 当前文件）
  - [ ] 调整模块（方向按钮一行 + 档位 + 透明度滑块）
  - [ ] 控制模块（显示/隐藏/关闭）
- [ ] `WebViewRenderer`（持有 rootView 中的 WebView，collect ViewModel 驱动显隐/位移/透明度/loadUrl）
- [ ] `InspectorFileStore`（本地存储）
- [ ] `InspectorApiHandler`（HTTP 接口，写入 getTop().viewModel）
- [ ] 单元测试和集成测试

---

## 11. 依赖关系

- **依赖于：** 已有的 HTTP Server 框架（NanoHTTPD）、SDK 初始化机制（ClientToolsSDK.init）
- **被依赖于：** 模块 4&5（差异计算和 AI 校正循环）

---

## 12. 参考资源

- tech-plan.md 第 3.1 和 3.4 节
- ApiHandler.kt（已有 HTTP 路由框架，InspectorApiHandler 接入此处）
- ApiHandler.kt（已有框架）

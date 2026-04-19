# Inspector 图片功能设计 Spec

## 背景

在现有 Inspector 系统（WebView 叠加层）基础上，增加设计稿图片（png/jpg）加载功能。逻辑和流程与 WebView 一致：推送/保存/自动选中；支持偏移、透明度调整。通过面板顶部 Tab（WebView / 图片）区分两种模式，状态各自独立。

---

## 架构总览

```
inspector/
├── InspectorViewModel.kt     // 重构：WebViewState + ImageState + activeTab
├── InspectorPage.kt          // 新增 ImageRenderer，传新 ViewModel
├── InspectorPanel.kt         // 新增 Tab 行，复用调整/控制 section
├── WebViewRenderer.kt        // 适配新 ViewModel 结构（vm.webView.xxx）
├── ImageRenderer.kt          // 新增：订阅 vm.image，驱动 ImageView
├── InspectorFileStore.kt     // 不变（HTML 文件存储）
├── ImageFileStore.kt         // 新增：图片文件存储（png/jpg）
├── InspectorApiHandler.kt    // 扩展：新增 push-image/show-image/images 路由
└── HttpServer.kt             // 新增路由注册
```

**数据流**：
- HTTP → `ImageFileStore.saveImage()` → 更新 `vm.image`
- `ImageRenderer` 订阅 `vm.image`，驱动 `ImageView` 显示/隐藏/位移/透明度
- `InspectorPanel` 订阅 `vm.activeTab`，切换面板内容区域

---

## ViewModel 结构

```kotlin
data class WebViewState(
    val currentFile: FileInfo? = null,
    val isVisible: Boolean = false,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val opacity: Float = 0.5f
)

data class ImageState(
    val currentImage: ImageInfo? = null,
    val isVisible: Boolean = false,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val opacity: Float = 0.5f
)

enum class ActiveTab { WEBVIEW, IMAGE }

class InspectorViewModel(app: Application) : AndroidViewModel(app) {
    val activeTab = MutableStateFlow(ActiveTab.WEBVIEW)
    val webView   = MutableStateFlow(WebViewState())
    val image     = MutableStateFlow(ImageState())
}
```

`WebViewState` 和 `ImageState` 状态完全独立，切换 Tab 不会相互影响。

---

## ImageInfo 数据类

```kotlin
data class ImageInfo(
    val tag: String,
    val timestamp: String,
    val filePath: String,   // 绝对路径，供 BitmapFactory 直接使用
    val ext: String         // "png" 或 "jpg"
)
```

---

## ImageView 渲染

### 自定义 ScaleType：FitWidthImageView

宽度铺满屏幕，高度按比例缩放。需自定义 `ImageView` 子类 `FitWidthImageView`：

- 覆写 `onMeasure`：`measuredHeight = imageHeight * (measuredWidth / imageWidth)`
- `setImageBitmap` 后触发重新 measure

### 采样加载（防 OOM）

```
1. BitmapFactory.Options.inJustDecodeBounds = true，读取原始尺寸
2. inSampleSize = ceil(imageWidth / screenWidth)，确保解码宽度 ≤ 屏幕宽
3. inJustDecodeBounds = false，正式解码
```

### overlay 中的位置

`inspector_overlay.xml` 中与 `WebView` 并列，默认 `visibility=GONE`。`ImageRenderer` 订阅 `vm.image`，控制可见性、透明度（`alpha`）、位移（`translationX/Y`，dp→px 换算与 WebViewRenderer 一致）。

---

## 面板 UI

### Tab 行

位置：`drag_handle` 下方，`section` 内容上方。

```
[ WebView ]  [ 图片 ]
```

- 两个等宽按钮，激活态背景 `#6200EE` 文字 `#FFFFFF`，非激活 `#1E1E3A` 文字 `#BB86FC`
- 点击切换 `vm.activeTab`

### Section 联动

| Section | WebView Tab | 图片 Tab |
|---------|------------|---------|
| WebView 文件（当前文件标签 + 选择按钮） | 显示 | 隐藏 |
| 图片文件（当前图片标签 + 选择按钮） | 隐藏 | 显示 |
| 调整（偏移/透明度） | 操作 `vm.webView` | 操作 `vm.image` |
| 控制（显示/隐藏） | 操作 `vm.webView.isVisible` | 操作 `vm.image.isVisible` |

调整和控制 section 的 UI 复用，操作目标随 `activeTab` 切换。

---

## ImageFileStore

存储路径：`cacheDir/inspector-images/{tag}/{tag}_{timestamp}.{ext}`

```kotlin
class ImageFileStore(context: Context) {
    fun saveImage(tag: String, timestamp: String, bytes: ByteArray, ext: String): ImageInfo?
    fun getAllImages(): List<ImageInfo>
    fun getFilePath(tag: String, timestamp: String): String?
    fun deleteAll(): Boolean
    fun generateTimestamp(): String
}
```

---

## HTTP 接口

### POST `/inspector/push-image`

Request body（JSON）：
```json
{
  "tag": "login",
  "timestamp": "0419-1430",   // 可选，缺省自动生成
  "image": "<base64>",
  "ext": "png"                // 可选，缺省 "png"
}
```

行为：保存文件，自动选中（`vm.image = ImageState(currentImage=saved, isVisible=true, ...)`）。

Response：
```json
{"code": 0, "data": {"tag": "login", "timestamp": "0419-1430", "filePath": "/...", "fileSize": 12345}}
```

### POST `/inspector/show-image`

Request body：
```json
{"tag": "login", "timestamp": "0419-1430"}
```

行为：查找文件，更新 `vm.image.currentImage` 并 `isVisible=true`。

### POST `/inspector/hide`

Request body：
```json
{"type": "webview"}   // 或 "image"；缺省则隐藏当前 activeTab 对应的
```

### POST `/inspector/adjust`

Request body：
```json
{"type": "image", "offsetX": 10, "offsetY": -5, "opacity": 0.7}
```

`type` 缺省则操作当前 `activeTab`。`offsetX/Y` 为增量（累加），`opacity` 为绝对值。

### GET `/inspector/images`

Response：
```json
{
  "code": 0,
  "data": {
    "images": [
      {"tag": "login", "timestamp": "0419-1430", "ext": "png", "size": 12345, "isCurrent": true}
    ]
  }
}
```

---

## 不在本次范围内

- 图片缩放手势（双指缩放）
- 图片旋转
- 多图同时叠加显示

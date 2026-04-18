# 模块 2：KMP 共享数据结构 — Spec

> 创建时间：2026-04-17

---

## 目标

定义 Android SDK 与 iOS SDK 之间的数据结构唯一源，包含节点数据、HTTP 响应结构、设备信息等，通过 KMP 编译为 Android（`.aar`）和 iOS（`.xcframework`）各自可用的代码。

---

## 模块位置

`packages/shared/`，作为独立 Gradle 模块，被 `packages/android/sdk/` 和 `packages/ios/sdk/` 依赖。

---

## 数据结构定义

### NodeType

```kotlin
enum class NodeType {
    TEXT, IMAGE, LIST, CONTAINER
}
```

### NodeAttrs（sealed class）

```kotlin
sealed class NodeAttrs

data class TextAttrs(
    val fontSize: Float,
    val color: String,        // ARGB 格式，如 "#FF333333"
    val fontWeight: String
) : NodeAttrs()

data class ImageAttrs(
    val scaleType: String = "fitCenter"
) : NodeAttrs()

data class ListAttrs(
    val itemSpacing: Float,       // dp，必选
    val orientation: String       // "VERTICAL" | "HORIZONTAL"
) : NodeAttrs()

data class ContainerAttrs(
    val paddingTop: Float,
    val paddingBottom: Float,
    val paddingLeft: Float,
    val paddingRight: Float
) : NodeAttrs()
```

### Node

```kotlin
data class Node(
    val id: String,
    val type: NodeType,
    val screenX: Float,           // 相对屏幕左上角，dp
    val screenY: Float,           // 相对屏幕左上角，dp
    val widthDp: Float,           // dp
    val heightDp: Float,          // dp
    val attrs: NodeAttrs? = null
)
```

### DeviceInfo

```kotlin
data class DeviceInfo(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val density: Float,           // dpi / 160
    val orientation: String       // "portrait" | "landscape"
)
```

### 通用 HTTP Response

```kotlin
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val sdkVersion: Int,
    val device: DeviceInfo,
    val data: T? = null
)
```

### View 修改请求

```kotlin
data class ViewProps(
    val marginTopDiffDp: Float? = null,
    val marginBottomDiffDp: Float? = null,
    val marginLeftDiffDp: Float? = null,
    val marginRightDiffDp: Float? = null,
    val paddingTopDiffDp: Float? = null,
    val paddingBottomDiffDp: Float? = null,
    val paddingLeftDiffDp: Float? = null,
    val paddingRightDiffDp: Float? = null,
    val widthDp: Float? = null,       // 绝对值，直接设置目标宽度
    val heightDp: Float? = null        // 绝对值，直接设置目标高度
)

data class ModifyViewRequest(
    val id: String,
    val props: ViewProps
)
```

`null` 表示不修改该属性，SDK 只处理非 null 字段。

### 页面切换事件

```kotlin
data class PageChangedEvent(
    val event: String = "page_changed",
    val activityName: String,
    val timestamp: String          // 格式 "MMdd-HHmm"
)
```

---

## 序列化

使用 `kotlinx.serialization`，所有数据类标注 `@Serializable`。

`NodeAttrs` 为 sealed class，使用 `@SerialName` 区分子类型：

```kotlin
@Serializable
sealed class NodeAttrs

@Serializable
@SerialName("text")
data class TextAttrs(...) : NodeAttrs()

@Serializable
@SerialName("image")
data class ImageAttrs(...) : NodeAttrs()
```

JSON 序列化示例：

```json
{
  "type": "text",
  "fontSize": 16.0,
  "color": "#FF333333",
  "fontWeight": "700"
}
```

---

## Gradle 配置

```kotlin
// packages/shared/build.gradle.kts
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
```

---

## 测试要求

- 所有 data class 可正确序列化为 JSON
- `NodeAttrs` sealed class 反序列化时能正确还原子类型
- `ApiResponse<List<Node>>` 完整序列化/反序列化

---

## 约束

- `commonMain` 中不引入任何 Android 或 iOS 平台 API
- 不包含业务逻辑，只有数据结构和序列化
- 所有字段名与 HTTP 协议 JSON 字段名保持一致（camelCase）

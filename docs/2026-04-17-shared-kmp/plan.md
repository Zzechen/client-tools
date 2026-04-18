# KMP 共享数据结构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 `packages/shared/` KMP 模块，定义所有平台共享的数据结构和序列化逻辑，作为 Android/iOS SDK 的唯一数据源。

**Architecture:** 纯 `commonMain` Kotlin 代码，不含任何平台 API。使用 `kotlinx.serialization` 处理 JSON 序列化，`NodeAttrs` 使用 sealed class + `@SerialName` 多态序列化。Gradle 多平台配置支持 Android 和三个 iOS target。

**Tech Stack:** Kotlin Multiplatform、kotlinx.serialization 1.7.3、Gradle 8.x、Kotlin 2.x

---

## 文件结构

```
packages/                                     # Gradle 工程根目录（实际偏差：原计划在项目根目录）
  settings.gradle.kts                         # 包含 shared 模块声明
  build.gradle.kts                            # 根 Gradle 配置
  gradle.properties                           # 指定 Java 17 路径
  gradle/
    libs.versions.toml                        # 版本目录（含 AGP）
    wrapper/
      gradle-wrapper.properties
      gradle-wrapper.jar
  shared/
    build.gradle.kts                          # KMP 模块配置（含 AGP + jvm target）
    src/
      commonMain/kotlin/com/clienttools/shared/
        models/
          NodeType.kt                         # NodeType enum
          NodeAttrs.kt                        # sealed class + 4 个子类
          Node.kt                             # Node data class
          DeviceInfo.kt                       # DeviceInfo data class
          ApiResponse.kt                      # ApiResponse<T> generic class
          ModifyViewRequest.kt                # ViewProps + ModifyViewRequest
          PageChangedEvent.kt                 # PageChangedEvent data class
      commonTest/kotlin/com/clienttools/shared/
        SerializationTest.kt                  # 所有序列化/反序列化测试
```

---

### Task 1: Gradle 项目初始化

**Files:**
- Create: `packages/settings.gradle.kts`
- Create: `packages/build.gradle.kts`
- Create: `packages/shared/build.gradle.kts`
- Create: `packages/gradle/libs.versions.toml`

> **实际偏差：**
> - 所有 Gradle 文件放在 `packages/` 下，不污染项目根目录
> - `settings.gradle.kts` 使用 `include(":shared")`（非 `:packages:shared`）
> - `libs.versions.toml` 增加了 `agp = "8.7.3"` 和 `android-library` plugin
> - `shared/build.gradle.kts` 增加了 AGP plugin、`android {}` 块、`jvm()` target、`compileOptions` JVM 17
> - 新增 `packages/gradle.properties` 指定 `org.gradle.java.home` 为 Java 17
> - 测试命令改为 `cd packages && ./gradlew :shared:jvmTest`（无 jvm target 则无法在本地运行）
> - `Json { encodeDefaults = true }` 需开启，否则默认值字段（如 `ImageAttrs.scaleType`）不序列化

- [x] **Step 1: 创建 settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "client-tools"

include(":shared")
```

- [x] **Step 2: 创建根 build.gradle.kts**

```kotlin
plugins {
    kotlin("multiplatform") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
    id("com.android.library") version "8.7.3" apply false
}
```

- [x] **Step 3: 创建 gradle/libs.versions.toml**

```toml
[versions]
kotlin = "2.1.0"
serialization = "1.7.3"
agp = "8.7.3"

[libraries]
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
android-library = { id = "com.android.library", version.ref = "agp" }
```

- [x] **Step 4: 创建 packages/shared/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.clienttools.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvm()
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
```

- [x] **Step 5: 创建源码目录**

```bash
mkdir -p packages/shared/src/commonMain/kotlin/com/clienttools/shared/models
mkdir -p packages/shared/src/commonTest/kotlin/com/clienttools/shared
```

- [x] **Step 6: 验证 Gradle 同步**

```bash
cd packages && ./gradlew :shared:tasks --quiet
```

Expected: 输出任务列表，无报错

- [x] **Step 7: Commit**

```bash
git add packages/settings.gradle.kts packages/build.gradle.kts packages/gradle/ packages/shared/build.gradle.kts packages/gradle.properties
git commit -m "feat: init shared KMP module gradle config"
```

---

### Task 2: NodeType 与 NodeAttrs

**Files:**
- Create: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/NodeType.kt`
- Create: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/NodeAttrs.kt`
- Create: `packages/shared/src/commonTest/kotlin/com/clienttools/shared/SerializationTest.kt`

- [x] **Step 1: 写失败测试**

写入 `packages/shared/src/commonTest/kotlin/com/clienttools/shared/SerializationTest.kt`：

```kotlin
package com.clienttools.shared

import com.clienttools.shared.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun testTextAttrsSerialize() {
        val attrs: NodeAttrs = TextAttrs(
            fontSize = 16f,
            color = "#FF333333",
            fontWeight = "700"
        )
        val encoded = json.encodeToString(attrs)
        assert(encoded.contains("\"type\":\"text\""))
        assert(encoded.contains("\"fontSize\":16.0"))
        assert(encoded.contains("\"color\":\"#FF333333\""))
    }

    @Test
    fun testTextAttrsDeserialize() {
        val jsonStr = """{"type":"text","fontSize":16.0,"color":"#FF333333","fontWeight":"700"}"""
        val attrs: NodeAttrs = json.decodeFromString(jsonStr)
        assertIs<TextAttrs>(attrs)
        assertEquals(16f, attrs.fontSize)
        assertEquals("#FF333333", attrs.color)
    }

    @Test
    fun testImageAttrsSerialize() {
        val attrs: NodeAttrs = ImageAttrs()
        val encoded = json.encodeToString(attrs)
        assert(encoded.contains("\"type\":\"image\""))
        assert(encoded.contains("\"scaleType\":\"fitCenter\""))
    }

    @Test
    fun testListAttrsDeserialize() {
        val jsonStr = """{"type":"list","itemSpacing":8.0,"orientation":"VERTICAL"}"""
        val attrs: NodeAttrs = json.decodeFromString(jsonStr)
        assertIs<ListAttrs>(attrs)
        assertEquals(8f, attrs.itemSpacing)
    }

    @Test
    fun testContainerAttrsDeserialize() {
        val jsonStr = """{"type":"container","paddingTop":12.0,"paddingBottom":12.0,"paddingLeft":16.0,"paddingRight":16.0}"""
        val attrs: NodeAttrs = json.decodeFromString(jsonStr)
        assertIs<ContainerAttrs>(attrs)
        assertEquals(12f, attrs.paddingTop)
    }
}
```

- [x] **Step 2: 运行测试确认失败**

```bash
cd packages && ./gradlew :shared:jvmTest
```

Expected: FAIL with `Unresolved reference: NodeAttrs`

- [x] **Step 3: 实现 NodeType.kt**

```kotlin
package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class NodeType {
    TEXT, IMAGE, LIST, CONTAINER
}
```

- [x] **Step 4: 实现 NodeAttrs.kt**

```kotlin
package com.clienttools.shared.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class NodeAttrs

@Serializable
@SerialName("text")
data class TextAttrs(
    val fontSize: Float,
    val color: String,
    val fontWeight: String
) : NodeAttrs()

@Serializable
@SerialName("image")
data class ImageAttrs(
    val scaleType: String = "fitCenter"
) : NodeAttrs()

@Serializable
@SerialName("list")
data class ListAttrs(
    val itemSpacing: Float,
    val orientation: String
) : NodeAttrs()

@Serializable
@SerialName("container")
data class ContainerAttrs(
    val paddingTop: Float,
    val paddingBottom: Float,
    val paddingLeft: Float,
    val paddingRight: Float
) : NodeAttrs()
```

- [x] **Step 5: 运行测试确认通过**

```bash
cd packages && ./gradlew :shared:jvmTest
```

Expected: 5 tests PASS

- [x] **Step 6: Commit**

```bash
git add packages/shared/src/
git commit -m "feat: add NodeType and NodeAttrs with serialization"
```

---

### Task 3: Node、DeviceInfo、ApiResponse

**Files:**
- Create: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/Node.kt`
- Create: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/DeviceInfo.kt`
- Create: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/ApiResponse.kt`
- Modify: `packages/shared/src/commonTest/kotlin/com/clienttools/shared/SerializationTest.kt`

- [x] **Step 1: 追加测试**

在 `SerializationTest.kt` 的 `SerializationTest` class 内追加：

```kotlin
    @Test
    fun testNodeSerialize() {
        val node = Node(
            id = "text_1",
            type = NodeType.TEXT,
            screenX = 16f,
            screenY = 48f,
            widthDp = 200f,
            heightDp = 24f,
            attrs = TextAttrs(fontSize = 16f, color = "#FF333333", fontWeight = "700")
        )
        val encoded = json.encodeToString(node)
        assert(encoded.contains("\"id\":\"text_1\""))
        assert(encoded.contains("\"type\":\"TEXT\""))
        assert(encoded.contains("\"screenX\":16.0"))
    }

    @Test
    fun testApiResponseWithNodeList() {
        val device = DeviceInfo(
            screenWidthDp = 375,
            screenHeightDp = 812,
            density = 3f,
            orientation = "portrait"
        )
        val response = ApiResponse(
            code = 0,
            message = "success",
            sdkVersion = 1,
            device = device,
            data = listOf(
                Node("text_1", NodeType.TEXT, 16f, 48f, 200f, 24f, null)
            )
        )
        val encoded = json.encodeToString(response)
        val decoded: ApiResponse<List<Node>> = json.decodeFromString(encoded)
        assertEquals(0, decoded.code)
        assertEquals(1, decoded.data?.size)
        assertEquals("text_1", decoded.data?.first()?.id)
    }
```

- [x] **Step 2: 运行测试确认失败**

```bash
cd packages && ./gradlew :shared:jvmTest
```

Expected: FAIL with `Unresolved reference: Node`

- [x] **Step 3: 实现 Node.kt**

```kotlin
package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class Node(
    val id: String,
    val type: NodeType,
    val screenX: Float,
    val screenY: Float,
    val widthDp: Float,
    val heightDp: Float,
    val attrs: NodeAttrs? = null
)
```

- [x] **Step 4: 实现 DeviceInfo.kt**

```kotlin
package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val density: Float,
    val orientation: String
)
```

- [x] **Step 5: 实现 ApiResponse.kt**

```kotlin
package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val sdkVersion: Int,
    val device: DeviceInfo,
    val data: T? = null
)
```

- [x] **Step 6: 运行测试确认通过**

```bash
cd packages && ./gradlew :shared:jvmTest
```

Expected: 7 tests PASS

- [x] **Step 7: Commit**

```bash
git add packages/shared/src/
git commit -m "feat: add Node, DeviceInfo, ApiResponse models"
```

---

### Task 4: ModifyViewRequest 与 PageChangedEvent

**Files:**
- Create: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/ModifyViewRequest.kt`
- Create: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/PageChangedEvent.kt`
- Modify: `packages/shared/src/commonTest/kotlin/com/clienttools/shared/SerializationTest.kt`

- [x] **Step 1: 追加测试**

在 `SerializationTest.kt` 的 class 内追加：

```kotlin
    @Test
    fun testModifyViewRequestSerialize() {
        val request = ModifyViewRequest(
            id = "login_text_1",
            props = ViewProps(marginTopDiffDp = 4f, paddingLeftDiffDp = 8f)
        )
        val encoded = json.encodeToString(request)
        val decoded: ModifyViewRequest = json.decodeFromString(encoded)
        assertEquals("login_text_1", decoded.id)
        assertEquals(4f, decoded.props.marginTopDiffDp)
        assertEquals(null, decoded.props.marginBottomDiffDp)
    }

    @Test
    fun testPageChangedEventSerialize() {
        val event = PageChangedEvent(
            activityName = "com.example.LoginActivity",
            timestamp = "0417-1423"
        )
        val encoded = json.encodeToString(event)
        val decoded: PageChangedEvent = json.decodeFromString(encoded)
        assertEquals("page_changed", decoded.event)
        assertEquals("com.example.LoginActivity", decoded.activityName)
    }
```

- [x] **Step 2: 运行测试确认失败**

```bash
cd packages && ./gradlew :shared:jvmTest
```

Expected: FAIL with `Unresolved reference: ModifyViewRequest`

- [x] **Step 3: 实现 ModifyViewRequest.kt**

```kotlin
package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class ViewProps(
    val marginTopDiffDp: Float? = null,
    val marginBottomDiffDp: Float? = null,
    val marginLeftDiffDp: Float? = null,
    val marginRightDiffDp: Float? = null,
    val paddingTopDiffDp: Float? = null,
    val paddingBottomDiffDp: Float? = null,
    val paddingLeftDiffDp: Float? = null,
    val paddingRightDiffDp: Float? = null,
    val widthDp: Float? = null,
    val heightDp: Float? = null
)

@Serializable
data class ModifyViewRequest(
    val id: String,
    val props: ViewProps
)
```

- [x] **Step 4: 实现 PageChangedEvent.kt**

```kotlin
package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class PageChangedEvent(
    val event: String = "page_changed",
    val activityName: String,
    val timestamp: String
)
```

- [x] **Step 5: 运行全部测试确认通过**

```bash
cd packages && ./gradlew :shared:jvmTest
```

Expected: 9 tests PASS

- [x] **Step 6: Commit**

```bash
git add packages/shared/src/
git commit -m "feat: add ModifyViewRequest and PageChangedEvent models"
```

---

### Task 5: 验证 Android 编译

**Files:**
- 无新增文件

> **实际偏差：**
> - Android target 任务名为 `compileDebugKotlinAndroid`（非 `compileKotlinAndroid`）
> - iOS target 编译首次运行会自动下载 Kotlin/Native toolchain（约 2-3 分钟）

- [x] **Step 1: 编译 Android target**

```bash
cd packages && ./gradlew :shared:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL

- [x] **Step 2: 编译 iOS target**

```bash
cd packages && ./gradlew :shared:compileKotlinIosArm64
```

Expected: BUILD SUCCESSFUL

- [x] **Step 3: Commit**

```bash
git add .
git commit -m "feat: shared KMP module complete"
```

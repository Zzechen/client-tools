# 目录重组：移除 KMP，各端独立 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `packages/android/` 和 `packages/ios/` 移至项目根目录，删除 KMP `shared` 模块，各端维护自己的数据模型，并创建 `harmony/` 骨架目录。

**Architecture:** 移除 KMP 多平台共享层，每端完全独立。Android SDK 内联原来 shared 中的数据类（保持 kotlinx.serialization）。iOS 数据类已在 iOS SDK 中独立存在，无需迁移。Gradle 根目录从 `packages/` 变为项目根目录。

**Tech Stack:** Kotlin/Android Gradle、CocoaPods、Swift

---

## 文件变更地图

| 操作 | 路径 |
|------|------|
| 移动 | `packages/android/` → `android/` |
| 移动 | `packages/ios/` → `ios/` |
| 创建 | `harmony/demo/`、`harmony/sdk/` 骨架 |
| 删除 | `packages/shared/` |
| 删除 | `packages/`（其余残留） |
| 新建 | `settings.gradle.kts`（根目录） |
| 新建 | `build.gradle.kts`（根目录） |
| 新建 | `gradle/`、`gradlew`、`gradlew.bat`（根目录） |
| 修改 | `android/sdk/build.gradle.kts`（移除 :shared 依赖） |
| 新建 | `android/sdk/src/main/kotlin/…/models/`（内联 shared 模型） |
| 修改 | `android/sdk/` 所有文件中 `com.clienttools.shared.models` import → `com.clienttools.sdk.models` |
| 修改 | `ios/demo/Podfile`（路径 `../sdk` 保持相对，移动后仍正确） |
| 修改 | `.gitignore`（更新路径） |
| 修改 | `CLAUDE.md`（更新目录结构说明） |

---

### Task 1: 将 shared 模型内联到 android/sdk

**Files:**
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/Node.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/NodeType.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/NodeAttrs.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/ModifyViewRequest.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/ApiResponse.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/PageChangedEvent.kt`
- Create: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/DeviceInfo.kt`
- Modify: `packages/android/sdk/build.gradle.kts`（移除 :shared 依赖）

- [ ] **Step 1: 创建 models 目录并内联 NodeType**

```bash
mkdir -p packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models
```

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/NodeType.kt`：

```kotlin
package com.clienttools.sdk.models

import kotlinx.serialization.Serializable

@Serializable
enum class NodeType {
    TEXT, IMAGE, LIST, CONTAINER
}
```

- [ ] **Step 2: 内联 NodeAttrs**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/NodeAttrs.kt`：

```kotlin
package com.clienttools.sdk.models

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

- [ ] **Step 3: 内联 Node**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/Node.kt`：

```kotlin
package com.clienttools.sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class Node(
    val id: String,
    val type: NodeType,
    val screenX: Float,
    val screenY: Float,
    val widthDp: Float,
    val heightDp: Float,
    val attrs: NodeAttrs? = null,
    val customAttrs: Map<String, String> = emptyMap()
)
```

- [ ] **Step 4: 内联 ModifyViewRequest（含 ViewProps）**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/ModifyViewRequest.kt`：

```kotlin
package com.clienttools.sdk.models

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
    val widthDp: String? = null,
    val heightDp: String? = null,
    val letterSpacingEm: Float? = null,
    val lineSpacingExtraDp: Float? = null,
    val includeFontPadding: Boolean? = null
)

@Serializable
data class ModifyViewRequest(
    val id: String,
    val props: ViewProps
)
```

- [ ] **Step 5: 内联 DeviceInfo**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/DeviceInfo.kt`：

```kotlin
package com.clienttools.sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val screenWidthDp: Float,
    val screenHeightDp: Float,
    val density: Float
)
```

- [ ] **Step 6: 内联 ApiResponse**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/ApiResponse.kt`：

```kotlin
package com.clienttools.sdk.models

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

- [ ] **Step 7: 内联 PageChangedEvent**

创建 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/PageChangedEvent.kt`：

```kotlin
package com.clienttools.sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class PageChangedEvent(
    val pageName: String,
    val timestamp: Long
)
```

- [ ] **Step 8: 移除 android/sdk/build.gradle.kts 中的 :shared 依赖**

编辑 `packages/android/sdk/build.gradle.kts`，删除这一行：

```kotlin
    implementation(project(":shared"))
```

- [ ] **Step 9: 批量替换 android/sdk 源码中的 import 包名**

```bash
# 将所有 shared 模型 import 替换为 sdk 内联模型 import
find packages/android/sdk/src/main/kotlin -name "*.kt" -exec \
  sed -i '' 's/import com\.clienttools\.shared\.models\./import com.clienttools.sdk.models./g' {} \;
```

- [ ] **Step 10: 验证 Android 编译通过**

```bash
cd packages && ./gradlew :android:sdk:assembleDebug
```

预期输出：`BUILD SUCCESSFUL`

- [ ] **Step 11: 提交**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/models/
git add packages/android/sdk/build.gradle.kts
git add packages/android/sdk/src/main/kotlin/
git commit -m "feat(android): inline shared models into android/sdk, remove :shared dependency"
```

---

### Task 2: 将 Gradle 根目录从 packages/ 迁移到项目根目录

**Files:**
- Create: `settings.gradle.kts`（项目根）
- Create: `build.gradle.kts`（项目根）
- 移动: `packages/gradle/` → `gradle/`
- 移动: `packages/gradlew` → `gradlew`
- 移动: `packages/gradlew.bat` → `gradlew.bat`
- 移动: `packages/gradle.properties` → `gradle.properties`
- 移动: `packages/local.properties` → `local.properties`（若存在）
- Create: `android/sdk/build.gradle.kts`（新路径，内容不变）
- Create: `android/demo/build.gradle.kts`（新路径，内容不变）
- Modify: `android/sdk/build.gradle.kts` 中 `libs` 版本目录路径

> **注意**：本 Task 执行顺序：先复制 Gradle wrapper 到根目录 → 移动 android/ 目录 → 创建新 settings.gradle.kts → 验证编译 → 删除 packages/

- [ ] **Step 1: 复制 Gradle wrapper 到项目根目录**

```bash
cd /Users/zzc/Desktop/works/client-tools
cp -r packages/gradle ./gradle
cp packages/gradlew ./gradlew
cp packages/gradlew.bat ./gradlew.bat
cp packages/gradle.properties ./gradle.properties
chmod +x gradlew
```

- [ ] **Step 2: 将 packages/android/ 移至项目根 android/**

```bash
cp -r packages/android ./android
```

（保留 packages/android 备用，Task 4 统一删除）

- [ ] **Step 3: 将 packages/ios/ 移至项目根 ios/**

```bash
cp -r packages/ios ./ios
```

- [ ] **Step 4: 创建根目录 settings.gradle.kts**

创建 `/Users/zzc/Desktop/works/client-tools/settings.gradle.kts`：

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

include(":android:sdk")
include(":android:demo")
```

- [ ] **Step 5: 创建根目录 build.gradle.kts**

创建 `/Users/zzc/Desktop/works/client-tools/build.gradle.kts`：

```kotlin
plugins {
    id("com.android.library") version "8.2.0" apply false
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
}
```

> 版本号从 `packages/build.gradle.kts` 确认后填入。先执行下一步查看原始版本号。

- [ ] **Step 5a: 查看原始 build.gradle.kts 中的插件版本**

```bash
cat packages/build.gradle.kts
```

将输出的版本号填入上一步的文件。

- [ ] **Step 6: 将 libs.versions.toml 复制到根 gradle 目录**

```bash
ls packages/gradle/libs.versions.toml && \
cp packages/gradle/libs.versions.toml gradle/libs.versions.toml
```

- [ ] **Step 7: 验证根目录 Gradle 编译**

```bash
cd /Users/zzc/Desktop/works/client-tools
./gradlew :android:sdk:assembleDebug
```

预期输出：`BUILD SUCCESSFUL`

如果报 "local.properties" 找不到，执行：

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

- [ ] **Step 8: 提交**

```bash
git add settings.gradle.kts build.gradle.kts gradle/ gradlew gradlew.bat gradle.properties
git add android/
git add ios/
git commit -m "feat: move android/ and ios/ to project root, add root Gradle configuration"
```

---

### Task 3: 创建 harmony/ 骨架目录

**Files:**
- Create: `harmony/sdk/.gitkeep`
- Create: `harmony/demo/.gitkeep`
- Create: `harmony/README.md`

- [ ] **Step 1: 创建骨架目录**

```bash
mkdir -p harmony/sdk harmony/demo
touch harmony/sdk/.gitkeep harmony/demo/.gitkeep
```

- [ ] **Step 2: 创建 README**

创建 `harmony/README.md`：

```markdown
# harmony

HarmonyOS SDK 和 Demo（待实现）

## 目录结构

- `sdk/` — HarmonyOS SDK（ArkTS）
- `demo/` — 接入示例
```

- [ ] **Step 3: 提交**

```bash
git add harmony/
git commit -m "feat: add harmony/ skeleton directory for future HarmonyOS support"
```

---

### Task 4: 删除 packages/ 目录和旧 Gradle 配置

> **前置**：Task 2 验证编译通过后才执行本 Task。

**Files:**
- 删除: `packages/`（整个目录）

- [ ] **Step 1: 再次验证根目录编译正常**

```bash
cd /Users/zzc/Desktop/works/client-tools
./gradlew :android:sdk:assembleDebug
```

确认 `BUILD SUCCESSFUL` 后才继续。

- [ ] **Step 2: 删除 packages/ 目录**

```bash
rm -rf packages/
```

- [ ] **Step 3: 验证删除后编译仍然通过**

```bash
./gradlew :android:sdk:assembleDebug
```

预期输出：`BUILD SUCCESSFUL`

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "chore: remove packages/ directory after migration to project root"
```

---

### Task 5: 更新 iOS Podfile 路径

> iOS 文件从 `packages/ios/` 移至 `ios/`，Podfile 中的相对路径 `../sdk` 需要核实（ios/demo/Podfile 相对 ios/ 目录本身是 `../sdk`，移动后变为 `../../ios/sdk` 的情况需检查）。

**Files:**
- Modify: `ios/demo/Podfile`

- [ ] **Step 1: 检查新位置的 Podfile 中路径**

```bash
cat ios/demo/Podfile
```

查看 `pod 'ClientToolsSDK', :path =>` 后面的路径。

- [ ] **Step 2: 确认路径正确性**

`ios/demo/Podfile` 中引用的是 `'../sdk'`，即 `ios/sdk/`。移动后目录结构为：

```
ios/
├── demo/Podfile   ← 在这里
└── sdk/           ← 目标
```

路径 `'../sdk'` 相对于 `ios/demo/` 指向 `ios/sdk/`，**路径正确，无需修改**。

若路径不同，更新为 `'../sdk'`。

- [ ] **Step 3: 验证 iOS Pod install**

```bash
cd ios/demo && pod install
```

预期输出：Pod installation complete，无报错。

- [ ] **Step 4: 验证 iOS 编译**

```bash
cd ios/demo
xcodebuild -workspace ClientToolsDemo.xcworkspace \
  -scheme ClientToolsDemo \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

预期：`BUILD SUCCEEDED`

- [ ] **Step 5: 提交（若 Podfile 有改动）**

```bash
git add ios/demo/Podfile ios/demo/Podfile.lock 2>/dev/null
git commit -m "fix(ios): update Podfile path after ios/ directory relocation"
```

若无改动跳过此步。

---

### Task 6: 更新 .gitignore 和 CLAUDE.md

**Files:**
- Modify: `.gitignore`
- Modify: `CLAUDE.md`

- [ ] **Step 1: 更新 .gitignore**

检查 .gitignore 中与 `packages/` 相关的条目：

```bash
grep -n "packages" .gitignore
```

将 `packages/build/`、`packages/.gradle/` 等路径改为根目录对应路径（`build/`、`.gradle/`）。典型更新：

```
# 旧
packages/.gradle/
packages/build/
packages/android/demo/.gradle/
packages/android/demo/build/
packages/android/sdk/build/
packages/local.properties

# 新
.gradle/
build/
android/demo/.gradle/
android/demo/build/
android/sdk/build/
local.properties
ios/demo/Pods/
ios/demo/Podfile.lock
```

- [ ] **Step 2: 更新 CLAUDE.md 目录结构说明**

将 CLAUDE.md 中的目录结构章节更新为：

```markdown
## 目录结构

- `android/` — Android 工程根目录（settings.gradle.kts 在项目根）
  - `android/sdk/` — Android SDK，打包为 `.aar`
  - `android/demo/` — Android 接入示例
- `ios/` — iOS 工程
  - `ios/sdk/` — iOS SDK（CocoaPod）
  - `ios/demo/` — iOS 接入示例
- `harmony/` — HarmonyOS（骨架，待实现）
  - `harmony/sdk/`
  - `harmony/demo/`
- `mcp/` — MCP Server，封装 SDK HTTP 接口供 AI 调用
- `skill/` — AI 工作流 Skill + 设计稿预处理脚本（Python/Playwright）
- `tests/` — 所有测试，按功能子目录划分
- `docs/` — 文档
- `settings.gradle.kts` — Gradle 多模块根配置（项目根）
- `tech-plan.md` — 整体技术规划
```

同时更新"运行测试"章节中的 Python 命令路径（若有 `packages/` 前缀，去掉）。

- [ ] **Step 3: 提交**

```bash
git add .gitignore CLAUDE.md
git commit -m "docs: update .gitignore and CLAUDE.md for new directory structure"
```

---

## 实施验收标准

1. `./gradlew :android:sdk:assembleDebug` 在项目根执行，BUILD SUCCESSFUL
2. `./gradlew :android:demo:assembleDebug` 在项目根执行，BUILD SUCCESSFUL
3. `cd ios/demo && pod install && xcodebuild … build`，BUILD SUCCEEDED
4. `packages/` 目录不存在
5. `android/`、`ios/`、`harmony/` 在项目根存在
6. `shared/` 模块不存在，android/sdk/models/ 包含所有内联模型

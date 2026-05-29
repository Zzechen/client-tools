# JitPack 发布 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `sdk` 和 `noop` 两个模块都发布到 JitPack，版本号集中在 `gradle.properties` 统一维护。

**Architecture:** 在 `clients/android/gradle.properties` 新增 `CLIENT_TOOLS_VERSION` 属性；`sdk` 和 `noop` 的 `build.gradle.kts` 读取该属性替换硬编码版本号；`jitpack.yml` 增加 noop 的构建和发布命令。

**Tech Stack:** Gradle `maven-publish` plugin，JitPack CI，Kotlin DSL

---

## 文件改动清单

| 文件 | 操作 |
|---|---|
| `clients/android/gradle.properties` | 修改：追加 `CLIENT_TOOLS_VERSION=1.0.1` |
| `clients/android/sdk/build.gradle.kts` | 修改：`version` 读属性（第 48 行） |
| `clients/android/noop/build.gradle.kts` | 修改：`version` 读属性（第 32 行） |
| `jitpack.yml` | 修改：加入 noop 构建和发布命令 |

---

### Task 1: 在 gradle.properties 中添加集中版本号

**Files:**
- Modify: `clients/android/gradle.properties`

- [ ] **Step 1: 追加版本属性**

在 `clients/android/gradle.properties` 末尾追加：

```properties
CLIENT_TOOLS_VERSION=1.0.1
```

文件最终内容：

```properties
android.useAndroidX=true
android.enableJetifier=true
CLIENT_TOOLS_VERSION=1.0.1
```

- [ ] **Step 2: 验证 Gradle 能读到该属性**

```bash
cd clients/android
./gradlew properties | grep CLIENT_TOOLS_VERSION
```

期望输出包含：
```
CLIENT_TOOLS_VERSION: 1.0.1
```

- [ ] **Step 3: Commit**

```bash
git add clients/android/gradle.properties
git commit -m "build: add CLIENT_TOOLS_VERSION to gradle.properties"
```

---

### Task 2: sdk 模块读取集中版本号

**Files:**
- Modify: `clients/android/sdk/build.gradle.kts`（`afterEvaluate` 块，约第 48 行）

- [ ] **Step 1: 替换硬编码版本**

将 `sdk/build.gradle.kts` 中 `afterEvaluate` 块内的 `version` 行：

```kotlin
                version = "1.0.0"
```

改为：

```kotlin
                version = project.properties["CLIENT_TOOLS_VERSION"] as String
```

完整 `afterEvaluate` 块如下：

```kotlin
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.Zzechen"
                artifactId = "client-tools"
                version = project.properties["CLIENT_TOOLS_VERSION"] as String
            }
        }
    }
}
```

- [ ] **Step 2: 本地发布验证版本号正确**

```bash
cd clients/android
./gradlew :sdk:publishReleasePublicationToMavenLocal
```

期望输出末尾：
```
BUILD SUCCESSFUL
```

- [ ] **Step 3: 检查本地 Maven 仓库中的版本**

```bash
ls ~/.m2/repository/com/github/Zzechen/client-tools/
```

期望输出包含目录 `1.0.1/`（不含 `1.0.0/`）。

- [ ] **Step 4: Commit**

```bash
git add clients/android/sdk/build.gradle.kts
git commit -m "build(sdk): read version from CLIENT_TOOLS_VERSION property"
```

---

### Task 3: noop 模块读取集中版本号

**Files:**
- Modify: `clients/android/noop/build.gradle.kts`（`afterEvaluate` 块，约第 32 行）

- [ ] **Step 1: 替换硬编码版本**

将 `noop/build.gradle.kts` 中 `afterEvaluate` 块内的 `version` 行：

```kotlin
                version = "1.0.0"
```

改为：

```kotlin
                version = project.properties["CLIENT_TOOLS_VERSION"] as String
```

完整 `afterEvaluate` 块如下：

```kotlin
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.Zzechen"
                artifactId = "client-tools-noop"
                version = project.properties["CLIENT_TOOLS_VERSION"] as String
            }
        }
    }
}
```

- [ ] **Step 2: 本地发布验证版本号正确**

```bash
cd clients/android
./gradlew :noop:publishReleasePublicationToMavenLocal
```

期望输出末尾：
```
BUILD SUCCESSFUL
```

- [ ] **Step 3: 检查本地 Maven 仓库中的版本**

```bash
ls ~/.m2/repository/com/github/Zzechen/client-tools-noop/
```

期望输出包含目录 `1.0.1/`（不含 `1.0.0/`）。

- [ ] **Step 4: Commit**

```bash
git add clients/android/noop/build.gradle.kts
git commit -m "build(noop): read version from CLIENT_TOOLS_VERSION property"
```

---

### Task 4: 更新 jitpack.yml 发布两个模块

**Files:**
- Modify: `jitpack.yml`（repo 根目录）

- [ ] **Step 1: 更新 install 命令**

将 `jitpack.yml` 完整内容替换为：

```yaml
jdk:
  - openjdk17
install:
  - cd clients/android && ./gradlew :sdk:assembleRelease :noop:assembleRelease :sdk:publishReleasePublicationToMavenLocal :noop:publishReleasePublicationToMavenLocal
```

- [ ] **Step 2: 本地模拟 JitPack 全量构建**

```bash
cd clients/android
./gradlew :sdk:assembleRelease :noop:assembleRelease \
  :sdk:publishReleasePublicationToMavenLocal \
  :noop:publishReleasePublicationToMavenLocal
```

期望输出末尾：
```
BUILD SUCCESSFUL
```

- [ ] **Step 3: 验证两个 artifact 均已发布到本地 Maven**

```bash
ls ~/.m2/repository/com/github/Zzechen/client-tools/1.0.1/
ls ~/.m2/repository/com/github/Zzechen/client-tools-noop/1.0.1/
```

两个目录都应包含 `.aar` 和 `.pom` 文件，例如：
```
client-tools-1.0.1.aar
client-tools-1.0.1.pom
client-tools-noop-1.0.1.aar
client-tools-noop-1.0.1.pom
```

- [ ] **Step 4: Commit**

```bash
git add jitpack.yml
git commit -m "ci: publish sdk and noop modules to JitPack"
```

# JitPack 发布设计

**日期：** 2026-05-30  
**状态：** 已批准

## 目标

将 Android SDK（`sdk`）和 Noop SDK（`noop`）两个模块都发布到 JitPack，版本号集中管理，避免两个 artifact 版本漂移。

## 发布产物

| artifact | 用途 |
|---|---|
| `com.github.Zzechen:client-tools:<version>` | 调试/开发包，含完整 HTTP server |
| `com.github.Zzechen:client-tools-noop:<version>` | 生产包，所有接口为 noop |

## 改动范围

### 1. `clients/android/gradle.properties`

新增版本号属性：

```properties
CLIENT_TOOLS_VERSION=1.0.1
```

升级时只改这一行，sdk 和 noop 自动同步。

### 2. `clients/android/sdk/build.gradle.kts`

`publishing` 块中的 `version` 从硬编码改为读属性：

```kotlin
version = project.properties["CLIENT_TOOLS_VERSION"] as String
```

### 3. `clients/android/noop/build.gradle.kts`

同上：

```kotlin
version = project.properties["CLIENT_TOOLS_VERSION"] as String
```

### 4. `jitpack.yml`（repo 根目录）

同时构建并发布两个模块：

```yaml
jdk:
  - openjdk17
install:
  - cd clients/android && ./gradlew :sdk:assembleRelease :noop:assembleRelease :sdk:publishReleasePublicationToMavenLocal :noop:publishReleasePublicationToMavenLocal
```

## 接入方使用方式

```kotlin
// app/build.gradle.kts
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // 调试变体（开发期）
    debugImplementation("com.github.Zzechen:client-tools:1.0.1")
    // 生产变体（发布期）
    releaseImplementation("com.github.Zzechen:client-tools-noop:1.0.1")
}
```

## 版本升级流程

1. 修改 `clients/android/gradle.properties` 中的 `CLIENT_TOOLS_VERSION`
2. 提交并推送
3. 在 GitHub 打对应版本的 tag（如 `android/1.0.1`）
4. JitPack 自动触发构建，发布两个 artifact

## 不在范围内

- iOS CocoaPod 发布（独立流程）
- 自动化版本 bump（手动维护）
- 发布到 Maven Central

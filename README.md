# Client Tools

AI Coding 客户端页面开发增强套件。让 AI 高质量完成「设计稿 → 安卓/iOS 运行时」的实现，并提供运行时视觉核对与循环修正能力。

## 核心特性

- **跨平台共享数据结构**：Kotlin Multiplatform (KMP) 定义统一的协议数据类，编译为 Android `.aar` 和 iOS `.xcframework`
- **设计稿自动化提取**：Playwright 渲染 HTML/CSS 设计稿，自动提取节点树结构和样式信息
- **运行时视觉比对**：WebView 叠加设计参考，支持透明度调整和拖拽控制
- **渐进式修正循环**：AI 局部调整 → 局部校对 → 全屏验收的迭代流程
- **MCP 工具接口**：标准 HTTP 协议封装所有 SDK 能力，MCP Server 供 AI 调用

## 项目结构

```
client-tools/
├── packages/                    # Gradle 多平台工程根目录
│   ├── settings.gradle.kts      # 工程配置
│   ├── shared/                  # KMP 共享模块（所有数据结构）
│   ├── android/sdk/             # Android SDK 实现
│   ├── android/demo/            # Android 接入示例
│   ├── ios/sdk/                 # iOS SDK 实现（Swift）
│   └── ios/demo/                # iOS 接入示例
├── mcp/                         # MCP Server（HTTP 接口 + 工具定义）
├── skill/preprocess/            # 设计稿预处理脚本（Playwright）
├── tests/                       # 所有测试（按功能模块细分）
│   └── preprocess/              # 预处理工具测试
├── docs/                        # 文档目录
│   ├── requirements-list.md     # 原始需求列表
│   ├── 2026-04-17-preprocess/   # 模块 1：设计稿预处理（已完成）
│   └── 2026-04-17-shared-kmp/   # 模块 2：KMP 共享模块（已完成）
├── CLAUDE.md                    # Claude Code 项目指南
├── tech-plan.md                 # 整体技术规划文档
└── README.md                    # 本文件
```

## 已完成模块

### 模块 1：设计稿预处理工具 ✅

**目标**：将 HTML/CSS 设计稿渲染后提取所有节点结构化信息。

**技术栈**：Python 3.10+、Playwright、asyncio

**关键特性**：
- Playwright 无头 Chromium 渲染设计稿
- 自动提取节点树：id、type（TEXT/IMAGE/LIST/CONTAINER）、屏幕坐标、尺寸
- 提取样式信息：TEXT 节点的字体、颜色、字重；IMAGE 的缩放模式；LIST 的 item 间距；CONTAINER 的内边距
- 锚点相对坐标：以指定节点的上/下边缘为基准，计算所有其他节点的 dx/dy
- JSON 结构化输出

**执行**：
```bash
# 列举设计稿中的所有节点（供 AI 选择锚点）
skill/preprocess/.venv/bin/python skill/preprocess/preprocess.py \
  --input design.html \
  --viewport 375 \
  --list-only

# 完整处理（指定锚点）
skill/preprocess/.venv/bin/python skill/preprocess/preprocess.py \
  --input design.html \
  --viewport 375 \
  --anchor-id text_1 \
  --anchor-edge top \
  --output design.json
```

**测试**：14 个单元 + 集成测试全部通过
```bash
skill/preprocess/.venv/bin/pytest tests/preprocess/ -q
```

**文档**：[设计稿预处理 Spec & Plan](docs/2026-04-17-preprocess/)

---

### 模块 2：KMP 共享数据结构 ✅

**目标**：定义 Android/iOS SDK 之间的数据结构唯一源。

**技术栈**：Kotlin Multiplatform、kotlinx.serialization 1.7.3、Gradle 8.x

**关键特性**：
- 纯 `commonMain` Kotlin 代码，不依赖任何平台 API
- 7 个 data class + 1 个 sealed class：
  - `Node` / `NodeType` / `NodeAttrs`（4 个子类：TextAttrs、ImageAttrs、ListAttrs、ContainerAttrs）
  - `DeviceInfo`（设备屏幕信息）
  - `ApiResponse<T>`（通用 HTTP 响应包装）
  - `ViewProps` / `ModifyViewRequest`（运行时修改请求）
  - `PageChangedEvent`（页面切换事件）
- 使用 `@SerialName` 支持 sealed class 多态序列化
- 编译为：
  - Android `.aar`（`compileDebugKotlinAndroid` 成功）
  - iOS `.xcframework`（`compileKotlinIosArm64` 成功）

**编译与测试**：
```bash
cd packages

# 运行 JVM 测试（9 个序列化测试全部通过）
./gradlew :shared:jvmTest

# 编译 Android target
./gradlew :shared:compileDebugKotlinAndroid

# 编译 iOS target（首次约 3 分钟，自动下载 Kotlin/Native toolchain）
./gradlew :shared:compileKotlinIosArm64
```

**文档**：[KMP 共享模块 Spec & Plan](docs/2026-04-17-shared-kmp/)

---

## 后续模块（规划中）

- **模块 3**：Android SDK 实现（packages/android/sdk/）
- **模块 4**：iOS SDK 实现（packages/ios/sdk/）
- **模块 5**：MCP Server（mcp/）
- **模块 6**：AI Skill 集成工作流

详见 [tech-plan.md](tech-plan.md)

---

## 快速开始

### 环境要求

- **Java 17+**（Gradle 和 Android 编译）
- **Python 3.10+**（设计稿预处理）
- **Node.js 16+**（MCP Server，后续）

### 设置

1. **克隆并进入项目**
   ```bash
   git clone git@gitee.com:zzcm1259/client-tools.git
   cd client-tools
   ```

2. **配置 Python 环境**（preprocess 工具）
   ```bash
   cd skill/preprocess
   python3 -m venv .venv
   .venv/bin/pip install -r requirements.txt
   .venv/bin/playwright install chromium
   ```

3. **验证 Gradle 工程**（KMP 模块）
   ```bash
   cd packages
   ./gradlew :shared:tasks --quiet
   ```

### 运行测试

```bash
# 预处理工具测试
skill/preprocess/.venv/bin/pytest tests/preprocess/ -q

# KMP 模块测试
cd packages && ./gradlew :shared:jvmTest
```

---

## 技术约定

- **Android 布局**：统一使用 XML（不用 Jetpack Compose）
- **最低版本**：Android API 26（8.0）、iOS 14
- **KMP 原则**：commonMain 中仅纯 Kotlin，不含平台 API
- **数据序列化**：JSON 格式，通过 kotlinx.serialization
- **测试组织**：所有测试放在 `tests/` 目录，按功能模块细分子目录
- **Gradle 环境**：Java 17（在 `packages/gradle.properties` 中指定）

详见 [CLAUDE.md](CLAUDE.md)

---

## 文档导航

- [CLAUDE.md](CLAUDE.md) — Claude Code 开发指南（开发者必读）
- [tech-plan.md](tech-plan.md) — 完整技术规划（架构 + 协议设计 + 集成流程）
- [docs/requirements-list.md](docs/requirements-list.md) — 原始需求列表
- [docs/2026-04-17-preprocess/](docs/2026-04-17-preprocess/) — 模块 1 规格与实施计划
- [docs/2026-04-17-shared-kmp/](docs/2026-04-17-shared-kmp/) — 模块 2 规格与实施计划

---

## 协议与许可

本项目遵循 MIT 许可证。详见 LICENSE 文件。

---

## 联系与反馈

如有问题或建议，欢迎提交 Issue 或 Pull Request 至 [Gitee](https://gitee.com/zzcm1259/client-tools)。

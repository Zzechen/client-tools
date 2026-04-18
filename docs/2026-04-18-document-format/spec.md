# 模块 2：结构化文档格式设计 Spec

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:writing-plans to create implementation plan based on this spec.

**Goal:** 定义一个标准的设计稿结构化文档格式，连接预处理工具（模块 1）的输出和 AI 校正循环（模块 4&5）的输入。

**Architecture:** 单锚点模式的扁平节点列表，用 DesignDocument 包装器管理元数据。节点采用强类型属性定义（TextAttrs、ImageAttrs 等）加可选 customAttrs 扩展，完整保留设计信息供后续差异计算和属性修改。

**Tech Stack:** Kotlin Multiplatform (KMP), kotlinx.serialization, JSON

---

## 1. 核心数据结构

### 1.1 DesignDocument

结构化文档的顶层容器，包含锚点信息、元数据和扁平节点列表。

```kotlin
@Serializable
data class DesignDocument(
    val version: String = "1.0",
    val metadata: DocumentMetadata,
    val anchorNodeId: String,
    val nodes: List<Node>
)
```

**字段说明：**
- `version`: 文档格式版本，便于向后兼容
- `metadata`: 设计稿元信息（名称、尺寸、创建时间等）
- `anchorNodeId`: 锚点节点的 ID，所有其他节点的坐标都相对此节点
- `nodes`: 扁平节点列表，按 DOM 顺序排列

### 1.2 DocumentMetadata

设计稿元信息。

```kotlin
@Serializable
data class DocumentMetadata(
    val name: String,
    val description: String? = null,
    val designerName: String? = null,
    val createdAt: String,
    val modifiedAt: String,
    val screenWidthDp: Float,
    val screenHeightDp: Float,
    val tags: List<String> = emptyList()
)
```

**字段说明：**
- `name`: 设计稿名称（如 "Login Screen v1.0"）
- `description`: 可选的设计稿描述
- `designerName`: 设计者名称（可选，便于溯源）
- `createdAt`, `modifiedAt`: ISO 8601 时间戳
- `screenWidthDp`, `screenHeightDp`: 设计稿的目标屏幕尺寸（DP 单位）
- `tags`: 标签数组，便于分类和检索

### 1.3 Node（已有，确认无变化）

单个 UI 节点，存储相对锚点的坐标和尺寸，以及类型特定的属性。

```kotlin
@Serializable
data class Node(
    val id: String,
    val type: NodeType,
    val screenX: Float,          // 相对锚点的 X 坐标（DP）
    val screenY: Float,          // 相对锚点的 Y 坐标（DP）
    val widthDp: Float,
    val heightDp: Float,
    val attrs: NodeAttrs? = null,
    val customAttrs: Map<String, String> = emptyMap()  // 新增：扩展属性
)
```

**字段说明：**
- `id`: 唯一标识符，预处理工具自动注入
- `type`: 节点类型（TEXT、IMAGE、LIST、CONTAINER）
- `screenX`, `screenY`: **相对于 anchorNodeId 的坐标**，这是核心
- `widthDp`, `heightDp`: 节点尺寸
- `attrs`: 类型特定的属性（sealed class）
  - TextAttrs: fontSize, color, fontWeight
  - ImageAttrs: scaleType
  - ListAttrs: itemSpacing, orientation
  - ContainerAttrs: paddingTop, paddingBottom, paddingLeft, paddingRight
- `customAttrs`: 设计稿特有的属性（key-value），用于扩展不在标准属性中的信息

### 1.4 NodeType（已有，确认无变化）

```kotlin
@Serializable
enum class NodeType {
    TEXT, IMAGE, LIST, CONTAINER
}
```

### 1.5 NodeAttrs（已有，确认无变化）

sealed class，支持不同节点类型的属性。

```kotlin
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

---

## 2. 坐标系统和锚点机制

### 2.1 相对坐标定义

所有节点的 `screenX` 和 `screenY` 都是**相对锚点节点的屏幕坐标**。

**示例：**
```
设计稿布局：
┌─────────────────────────┐
│  Header (锚点)          │  screenY=0
│  ┌───────────────────┐  │
│  │ Avatar            │  │  screenY=100, screenX=50
│  └───────────────────┘  │
│  ┌───────────────────┐  │
│  │ Login Button      │  │  screenY=180, screenX=50
│  └───────────────────┘  │
└─────────────────────────┘

相对坐标：
- Header (锚点): screenX=0, screenY=0
- Avatar: screenX=50, screenY=100
- Login Button: screenX=50, screenY=180
```

### 2.2 锚点选择原则

锚点通常是：
- 设计稿中**最稳定的参考元素**（如页面的主容器或顶部导航栏）
- 坐标为 (0, 0) 或接近屏幕边缘的元素
- 预处理工具自动选择，或由设计者指定

### 2.3 坐标验证

在序列化/反序列化时，需要：
1. 验证 anchorNodeId 对应的节点存在
2. 验证锚点节点的坐标为 (0, 0)（或至少是相对位置的起点）

---

## 3. 设计稿与运行时视图的对应关系

### 3.1 设计稿侧

DesignDocument 包含的是**设计意图**：
- 每个 UI 元素应该在什么位置
- 应该有多大尺寸
- 应该有什么颜色、字体等属性

### 3.2 运行时侧

Android View 树通过 SDK 的 `/api/nodes/{id}` 端点返回，数据结构**相同**（也是 Node 列表）：
- 实际运行时的 UI 元素位置和尺寸
- 实际的视觉属性

### 3.3 差异计算

模块 4&5 的校正循环会：
1. 获取 DesignDocument 的节点列表（设计意图）
2. 通过 SDK 获取运行时 View 树（实际状态）
3. 逐个对比 `screenX/Y`、`widthDp/heightDp`、属性值
4. 计算差异，调用 `/api/modify` 进行修正

---

## 4. 序列化格式

### 4.1 JSON 序列化示例

```json
{
  "version": "1.0",
  "metadata": {
    "name": "Login Screen v1.0",
    "description": "User login page with email and password fields",
    "designerName": "Alice",
    "createdAt": "2026-04-18T10:00:00Z",
    "modifiedAt": "2026-04-18T14:30:00Z",
    "screenWidthDp": 360,
    "screenHeightDp": 800,
    "tags": ["authentication", "mobile"]
  },
  "anchorNodeId": "header",
  "nodes": [
    {
      "id": "header",
      "type": "CONTAINER",
      "screenX": 0.0,
      "screenY": 0.0,
      "widthDp": 360.0,
      "heightDp": 100.0,
      "attrs": {
        "type": "container",
        "paddingTop": 16.0,
        "paddingBottom": 16.0,
        "paddingLeft": 16.0,
        "paddingRight": 16.0
      },
      "customAttrs": {
        "backgroundColor": "#FF6200EE"
      }
    },
    {
      "id": "avatar",
      "type": "IMAGE",
      "screenX": 150.0,
      "screenY": 20.0,
      "widthDp": 60.0,
      "heightDp": 60.0,
      "attrs": {
        "type": "image",
        "scaleType": "centerCrop"
      },
      "customAttrs": {
        "borderRadius": "30"
      }
    },
    {
      "id": "username_label",
      "type": "TEXT",
      "screenX": 16.0,
      "screenY": 120.0,
      "widthDp": 100.0,
      "heightDp": 24.0,
      "attrs": {
        "type": "text",
        "fontSize": 14.0,
        "color": "#FF333333",
        "fontWeight": "normal"
      },
      "customAttrs": {}
    },
    {
      "id": "username_input",
      "type": "CONTAINER",
      "screenX": 16.0,
      "screenY": 150.0,
      "widthDp": 328.0,
      "heightDp": 48.0,
      "attrs": {
        "type": "container",
        "paddingTop": 8.0,
        "paddingBottom": 8.0,
        "paddingLeft": 12.0,
        "paddingRight": 12.0
      },
      "customAttrs": {
        "borderColor": "#FFCCCCCC",
        "borderWidth": "1"
      }
    },
    {
      "id": "login_button",
      "type": "CONTAINER",
      "screenX": 16.0,
      "screenY": 280.0,
      "widthDp": 328.0,
      "heightDp": 48.0,
      "attrs": {
        "type": "container",
        "paddingTop": 0.0,
        "paddingBottom": 0.0,
        "paddingLeft": 0.0,
        "paddingRight": 0.0
      },
      "customAttrs": {
        "backgroundColor": "#FF6200EE"
      }
    }
  ]
}
```

### 4.2 YAML 替代方案（可选）

如果设计者倾向 YAML 格式，可以使用完全等价的 YAML 序列化。关键是内容结构一致。

---

## 5. 验收标准和约束

### 5.1 文档约束

1. **唯一性**：`nodes` 数组中的 `id` 必须全局唯一
2. **锚点存在性**：`anchorNodeId` 必须对应 `nodes` 中的某个节点
3. **锚点坐标**：锚点节点的 `screenX=0, screenY=0`
4. **正尺寸**：所有 `widthDp` 和 `heightDp` 必须为正数
5. **屏幕约束**：所有节点的 `screenX + widthDp` 应不超过 `metadata.screenWidthDp`；`screenY + heightDp` 应不超过 `metadata.screenHeightDp`（允许溢出但需标记警告）

### 5.2 属性完整性

- `attrs` 字段：如果 `type=TEXT`，必须有 `TextAttrs`；如果 `type=IMAGE`，必须有 `ImageAttrs`；以此类推
- `customAttrs`：可选，但如果存在，值必须是字符串类型

---

## 6. 使用场景

### 场景 1：设计稿导入

设计者通过预处理工具生成 DesignDocument JSON，上传到系统。

### 场景 2：AI 代码生成

AI 读取 DesignDocument，根据节点信息生成对应的 Android 布局代码。

### 场景 3：运行时对比

```
1. 获取 DesignDocument（设计意图）
2. 通过 SDK /api/nodes 获取实时 View 树
3. 逐个对比节点的 screenX/Y、widthDp/heightDp、attrs
4. 记录差异，驱动 modify API 调整
```

### 场景 4：差异可视化

在 WebView 叠加层中，显示设计稿的节点轮廓，与原生 View 树对比。

---

## 7. 扩展性和向后兼容

### 7.1 版本管理

`version` 字段用于版本控制：
- `1.0`: 当前版本（2026-04-18）
- 如果后续添加新的 NodeType 或 NodeAttrs，增加 minor 版本
- 重大结构变化时增加 major 版本

### 7.2 customAttrs 扩展

特殊设计稿属性（如自定义颜色、动画参数等）通过 `customAttrs` Map 承载，不破坏核心结构。

---

## 8. 实现清单

- [ ] 在 shared 模块中新增 `DesignDocument` 和 `DocumentMetadata` 类
- [ ] 为 `Node` 类添加 `customAttrs` 字段
- [ ] 编写序列化/反序列化单元测试
- [ ] 创建示例文档（login_screen.json、form_screen.json）
- [ ] 编写文档约束验证器
- [ ] 更新 shared 模块的导出清单

---

## 9. 依赖关系

- **模块 1（设计稿预处理）** → 输出 DesignDocument
- **模块 2（本模块）** ← 定义格式
- **模块 4&5（AI 校正循环）** → 读取和使用 DesignDocument

---

## 10. 参考资源

- 预处理工具输出示例：待模块 1 完成集成测试后补充
- 运行时 View 树格式：与 DesignDocument 保持一致（相同的 Node 结构）

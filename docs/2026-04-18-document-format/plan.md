# 模块 2：结构化文档格式 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 KMP shared 模块中实现 DesignDocument 数据模型及其序列化，支持设计稿结构化表示和预处理工具集成。

**Architecture:** 在既有 Node/NodeType/NodeAttrs 基础上，添加 DesignDocument 容器和 DocumentMetadata。Node 增强 customAttrs 字段支持扩展属性。所有类型使用 kotlinx.serialization 以支持跨平台序列化。

**Tech Stack:** Kotlin, KMP, kotlinx.serialization

---

### Task 1: DocumentMetadata 数据模型

**Files:**
- Create: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/DocumentMetadata.kt`
- Modify: None
- Test: `packages/shared/src/commonTest/kotlin/com/clienttools/shared/DesignDocumentTest.kt`

- [ ] **Step 1: 创建 DocumentMetadata.kt 文件**

```kotlin
package com.clienttools.shared.models

import kotlinx.serialization.Serializable

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

- [ ] **Step 2: 创建单元测试文件 DesignDocumentTest.kt（先创建空壳）**

```kotlin
package com.clienttools.shared

import com.clienttools.shared.models.DocumentMetadata
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DesignDocumentTest {
    
    @Test
    fun testDocumentMetadataSerializationDeserialization() {
        val metadata = DocumentMetadata(
            name = "Login Screen v1.0",
            description = "User login page",
            designerName = "Alice",
            createdAt = "2026-04-18T10:00:00Z",
            modifiedAt = "2026-04-18T14:30:00Z",
            screenWidthDp = 360f,
            screenHeightDp = 800f,
            tags = listOf("authentication", "mobile")
        )
        
        val json = Json.encodeToString(DocumentMetadata.serializer(), metadata)
        val decoded = Json.decodeFromString(DocumentMetadata.serializer(), json)
        
        assertEquals(metadata, decoded)
    }
}
```

- [ ] **Step 3: 运行测试验证通过**

```bash
cd packages && ./gradlew :shared:jvmTest -k DocumentMetadata
```

Expected: PASS

- [ ] **Step 4: 提交**

```bash
git add packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/DocumentMetadata.kt \
        packages/shared/src/commonTest/kotlin/com/clienttools/shared/DesignDocumentTest.kt && \
git commit -m "feat: add DocumentMetadata data class for design document metadata"
```

---

### Task 2: DesignDocument 容器类

**Files:**
- Create: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/DesignDocument.kt`
- Modify: None
- Test: `packages/shared/src/commonTest/kotlin/com/clienttools/shared/DesignDocumentTest.kt`

- [ ] **Step 1: 创建 DesignDocument.kt 文件**

```kotlin
package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class DesignDocument(
    val version: String = "1.0",
    val metadata: DocumentMetadata,
    val anchorNodeId: String,
    val nodes: List<Node>
)
```

- [ ] **Step 2: 在 DesignDocumentTest.kt 中添加 DesignDocument 序列化测试**

在 `DesignDocumentTest` 类中添加：

```kotlin
import com.clienttools.shared.models.DesignDocument
import com.clienttools.shared.models.Node
import com.clienttools.shared.models.NodeType
import com.clienttools.shared.models.TextAttrs

@Test
fun testDesignDocumentSerializationDeserialization() {
    val metadata = DocumentMetadata(
        name = "Login Screen v1.0",
        description = "User login page",
        designerName = "Alice",
        createdAt = "2026-04-18T10:00:00Z",
        modifiedAt = "2026-04-18T14:30:00Z",
        screenWidthDp = 360f,
        screenHeightDp = 800f,
        tags = listOf("authentication", "mobile")
    )
    
    val nodes = listOf(
        Node(
            id = "header",
            type = NodeType.CONTAINER,
            screenX = 0f,
            screenY = 0f,
            widthDp = 360f,
            heightDp = 100f,
            attrs = null,
            customAttrs = mapOf("backgroundColor" to "#FF6200EE")
        ),
        Node(
            id = "title",
            type = NodeType.TEXT,
            screenX = 16f,
            screenY = 20f,
            widthDp = 100f,
            heightDp = 24f,
            attrs = TextAttrs(
                fontSize = 24f,
                color = "#FFFFFF",
                fontWeight = "bold"
            ),
            customAttrs = emptyMap()
        )
    )
    
    val document = DesignDocument(
        version = "1.0",
        metadata = metadata,
        anchorNodeId = "header",
        nodes = nodes
    )
    
    val json = Json.encodeToString(DesignDocument.serializer(), document)
    val decoded = Json.decodeFromString(DesignDocument.serializer(), json)
    
    assertEquals(document, decoded)
    assertEquals("header", decoded.anchorNodeId)
    assertEquals(2, decoded.nodes.size)
}
```

- [ ] **Step 3: 运行测试验证通过**

```bash
cd packages && ./gradlew :shared:jvmTest -k DesignDocument
```

Expected: PASS (两个测试都通过)

- [ ] **Step 4: 提交**

```bash
git add packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/DesignDocument.kt && \
git commit -m "feat: add DesignDocument container for design document structure"
```

---

### Task 3: Node 类增强 customAttrs 字段

**Files:**
- Modify: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/Node.kt`
- Test: `packages/shared/src/commonTest/kotlin/com/clienttools/shared/DesignDocumentTest.kt`

- [ ] **Step 1: 读取当前 Node.kt 内容**

```bash
cat packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/Node.kt
```

Expected: 看到现有 Node 数据类定义

- [ ] **Step 2: 修改 Node.kt 添加 customAttrs 字段**

将：
```kotlin
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

改为：
```kotlin
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

- [ ] **Step 3: 运行现有测试验证不破坏**

```bash
cd packages && ./gradlew :shared:jvmTest
```

Expected: 所有测试通过（包括已有的 SerializationTest）

- [ ] **Step 4: 在 DesignDocumentTest 中添加 customAttrs 验证测试**

在 `DesignDocumentTest` 类中添加：

```kotlin
@Test
fun testNodeCustomAttrsDeserialization() {
    val node = Node(
        id = "button",
        type = NodeType.CONTAINER,
        screenX = 10f,
        screenY = 20f,
        widthDp = 100f,
        heightDp = 50f,
        attrs = null,
        customAttrs = mapOf(
            "backgroundColor" to "#FF6200EE",
            "borderRadius" to "8"
        )
    )
    
    val json = Json.encodeToString(Node.serializer(), node)
    val decoded = Json.decodeFromString(Node.serializer(), json)
    
    assertEquals(node, decoded)
    assertEquals(mapOf("backgroundColor" to "#FF6200EE", "borderRadius" to "8"), decoded.customAttrs)
}
```

- [ ] **Step 5: 运行全部测试验证通过**

```bash
cd packages && ./gradlew :shared:jvmTest
```

Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/Node.kt \
        packages/shared/src/commonTest/kotlin/com/clienttools/shared/DesignDocumentTest.kt && \
git commit -m "feat: add customAttrs field to Node for design-specific extensions"
```

---

### Task 4: 文档约束验证器

**Files:**
- Create: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/validation/DesignDocumentValidator.kt`
- Test: `packages/shared/src/commonTest/kotlin/com/clienttools/shared/DesignDocumentValidatorTest.kt`

- [ ] **Step 1: 创建 DesignDocumentValidator.kt**

```kotlin
package com.clienttools.shared.validation

import com.clienttools.shared.models.DesignDocument
import com.clienttools.shared.models.NodeType
import com.clienttools.shared.models.TextAttrs
import com.clienttools.shared.models.ImageAttrs
import com.clienttools.shared.models.ListAttrs
import com.clienttools.shared.models.ContainerAttrs

data class ValidationError(
    val field: String,
    val message: String
)

object DesignDocumentValidator {
    
    fun validate(document: DesignDocument): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        // 检查锚点存在性
        val anchorNode = document.nodes.find { it.id == document.anchorNodeId }
        if (anchorNode == null) {
            errors.add(ValidationError("anchorNodeId", "Anchor node with id '${document.anchorNodeId}' not found"))
        } else {
            // 检查锚点坐标
            if (anchorNode.screenX != 0f || anchorNode.screenY != 0f) {
                errors.add(ValidationError("anchorNodeId", "Anchor node must have screenX=0 and screenY=0, but got screenX=${anchorNode.screenX}, screenY=${anchorNode.screenY}"))
            }
        }
        
        // 检查节点 ID 唯一性
        val nodeIds = document.nodes.map { it.id }
        val duplicates = nodeIds.groupingBy { it }.eachCount().filter { it.value > 1 }
        duplicates.forEach { (id, count) ->
            errors.add(ValidationError("nodes", "Duplicate node id '$id' found $count times"))
        }
        
        // 检查每个节点的有效性
        document.nodes.forEach { node ->
            if (node.widthDp <= 0) {
                errors.add(ValidationError("nodes[${node.id}].widthDp", "Width must be positive, but got ${node.widthDp}"))
            }
            if (node.heightDp <= 0) {
                errors.add(ValidationError("nodes[${node.id}].heightDp", "Height must be positive, but got ${node.heightDp}"))
            }
            
            // 检查类型与属性匹配
            when (node.type) {
                NodeType.TEXT -> {
                    if (node.attrs !is TextAttrs && node.attrs != null) {
                        errors.add(ValidationError("nodes[${node.id}].attrs", "TEXT node should have TextAttrs"))
                    }
                }
                NodeType.IMAGE -> {
                    if (node.attrs !is ImageAttrs && node.attrs != null) {
                        errors.add(ValidationError("nodes[${node.id}].attrs", "IMAGE node should have ImageAttrs"))
                    }
                }
                NodeType.LIST -> {
                    if (node.attrs !is ListAttrs && node.attrs != null) {
                        errors.add(ValidationError("nodes[${node.id}].attrs", "LIST node should have ListAttrs"))
                    }
                }
                NodeType.CONTAINER -> {
                    if (node.attrs !is ContainerAttrs && node.attrs != null) {
                        errors.add(ValidationError("nodes[${node.id}].attrs", "CONTAINER node should have ContainerAttrs"))
                    }
                }
            }
        }
        
        return errors
    }
    
    fun isValid(document: DesignDocument): Boolean = validate(document).isEmpty()
}
```

- [ ] **Step 2: 创建验证器测试文件**

```kotlin
package com.clienttools.shared

import com.clienttools.shared.models.DesignDocument
import com.clienttools.shared.models.DocumentMetadata
import com.clienttools.shared.models.Node
import com.clienttools.shared.models.NodeType
import com.clienttools.shared.models.TextAttrs
import com.clienttools.shared.validation.DesignDocumentValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DesignDocumentValidatorTest {
    
    private fun createValidDocument(): DesignDocument {
        val metadata = DocumentMetadata(
            name = "Test",
            createdAt = "2026-04-18T10:00:00Z",
            modifiedAt = "2026-04-18T10:00:00Z",
            screenWidthDp = 360f,
            screenHeightDp = 800f
        )
        return DesignDocument(
            metadata = metadata,
            anchorNodeId = "header",
            nodes = listOf(
                Node(
                    id = "header",
                    type = NodeType.CONTAINER,
                    screenX = 0f,
                    screenY = 0f,
                    widthDp = 360f,
                    heightDp = 100f
                )
            )
        )
    }
    
    @Test
    fun testValidDocumentPasses() {
        val doc = createValidDocument()
        assertTrue(DesignDocumentValidator.isValid(doc))
        assertEquals(0, DesignDocumentValidator.validate(doc).size)
    }
    
    @Test
    fun testMissingAnchorNodeFails() {
        val doc = createValidDocument().copy(anchorNodeId = "nonexistent")
        assertFalse(DesignDocumentValidator.isValid(doc))
        val errors = DesignDocumentValidator.validate(doc)
        assertEquals(1, errors.size)
        assertEquals("anchorNodeId", errors[0].field)
    }
    
    @Test
    fun testAnchorNodeWrongCoordinatesFails() {
        val doc = createValidDocument().copy(
            nodes = listOf(
                Node(
                    id = "header",
                    type = NodeType.CONTAINER,
                    screenX = 10f,
                    screenY = 20f,
                    widthDp = 360f,
                    heightDp = 100f
                )
            )
        )
        assertFalse(DesignDocumentValidator.isValid(doc))
        val errors = DesignDocumentValidator.validate(doc)
        assertTrue(errors.any { it.field == "anchorNodeId" })
    }
    
    @Test
    fun testDuplicateNodeIdsFails() {
        val doc = createValidDocument().copy(
            nodes = listOf(
                Node(id = "header", type = NodeType.CONTAINER, screenX = 0f, screenY = 0f, widthDp = 360f, heightDp = 100f),
                Node(id = "header", type = NodeType.TEXT, screenX = 10f, screenY = 10f, widthDp = 100f, heightDp = 24f)
            )
        )
        assertFalse(DesignDocumentValidator.isValid(doc))
        val errors = DesignDocumentValidator.validate(doc)
        assertTrue(errors.any { it.message.contains("Duplicate") })
    }
    
    @Test
    fun testNegativeWidthFails() {
        val doc = createValidDocument().copy(
            nodes = listOf(
                Node(id = "header", type = NodeType.CONTAINER, screenX = 0f, screenY = 0f, widthDp = -10f, heightDp = 100f)
            )
        )
        assertFalse(DesignDocumentValidator.isValid(doc))
        val errors = DesignDocumentValidator.validate(doc)
        assertTrue(errors.any { it.message.contains("Width") })
    }
}
```

- [ ] **Step 3: 运行验证器测试**

```bash
cd packages && ./gradlew :shared:jvmTest -k DesignDocumentValidator
```

Expected: 所有测试通过

- [ ] **Step 4: 提交**

```bash
git add packages/shared/src/commonMain/kotlin/com/clienttools/shared/validation/DesignDocumentValidator.kt \
        packages/shared/src/commonTest/kotlin/com/clienttools/shared/DesignDocumentValidatorTest.kt && \
git commit -m "feat: add DesignDocumentValidator for document constraint checking"
```

---

### Task 5: 示例文档和集成测试

**Files:**
- Create: `docs/examples/design-document-example.json`
- Create: `docs/2026-04-18-document-format/implementation-notes.md`
- Test: `packages/shared/src/commonTest/kotlin/com/clienttools/shared/DesignDocumentIntegrationTest.kt`

- [ ] **Step 1: 创建示例 JSON 文档**

```bash
mkdir -p docs/examples
cat > docs/examples/design-document-example.json << 'EOF'
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
      "id": "title",
      "type": "TEXT",
      "screenX": 16.0,
      "screenY": 20.0,
      "widthDp": 328.0,
      "heightDp": 60.0,
      "attrs": {
        "type": "text",
        "fontSize": 24.0,
        "color": "#FFFFFFFF",
        "fontWeight": "bold"
      },
      "customAttrs": {}
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
      "id": "password_label",
      "type": "TEXT",
      "screenX": 16.0,
      "screenY": 210.0,
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
      "id": "password_input",
      "type": "CONTAINER",
      "screenX": 16.0,
      "screenY": 240.0,
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
      "screenY": 320.0,
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
EOF
```

- [ ] **Step 2: 创建集成测试（从 JSON 文件读取并验证）**

```kotlin
package com.clienttools.shared

import com.clienttools.shared.models.DesignDocument
import com.clienttools.shared.validation.DesignDocumentValidator
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class DesignDocumentIntegrationTest {
    
    @Test
    fun testLoadAndValidateExampleDocument() {
        // 这个测试验证示例 JSON 可以被正确反序列化和验证
        val exampleJson = """
        {
          "version": "1.0",
          "metadata": {
            "name": "Login Screen v1.0",
            "description": "User login page",
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
              "customAttrs": {"backgroundColor": "#FF6200EE"}
            },
            {
              "id": "title",
              "type": "TEXT",
              "screenX": 16.0,
              "screenY": 20.0,
              "widthDp": 328.0,
              "heightDp": 60.0,
              "attrs": {
                "type": "text",
                "fontSize": 24.0,
                "color": "#FFFFFFFF",
                "fontWeight": "bold"
              },
              "customAttrs": {}
            }
          ]
        }
        """.trimIndent()
        
        val document = Json.decodeFromString(DesignDocument.serializer(), exampleJson)
        
        assertEquals("1.0", document.version)
        assertEquals("Login Screen v1.0", document.metadata.name)
        assertEquals("header", document.anchorNodeId)
        assertEquals(2, document.nodes.size)
        
        assertTrue(DesignDocumentValidator.isValid(document))
    }
}
```

- [ ] **Step 3: 运行集成测试**

```bash
cd packages && ./gradlew :shared:jvmTest -k DesignDocumentIntegration
```

Expected: PASS

- [ ] **Step 4: 创建实现笔记文档**

```bash
cat > docs/2026-04-18-document-format/implementation-notes.md << 'EOF'
# 模块 2 实现笔记

## 完成清单

- [x] DesignDocument 和 DocumentMetadata 类实现
- [x] Node 类扩展 customAttrs 字段
- [x] DesignDocumentValidator 约束检查
- [x] 序列化/反序列化测试
- [x] 示例文档

## 关键设计决策

### 1. 相对坐标系统

所有节点的 `screenX` 和 `screenY` 都是相对于 `anchorNodeId` 的坐标。锚点本身的坐标为 (0, 0)。

验证：DesignDocumentValidator.validate() 检查锚点坐标是否为 (0, 0)

### 2. customAttrs 扩展

为了支持设计稿特有的属性（如 borderRadius、backgroundColor 等），使用 `Map<String, String>` 类型。

特点：
- 灵活性强，可容纳任意属性
- 值类型为 String，便于序列化和配置
- 不影响类型安全的核心属性

### 3. 验证层次

分为两层验证：
- **编译时**：Kotlin 类型系统保证 node.id、node.type 等核心字段存在
- **运行时**：DesignDocumentValidator 检查约束（ID 唯一性、锚点存在等）

### 4. 版本管理

`version` 字段（默认 "1.0"）用于文档格式版本控制，便于向后兼容。

## 后续集成

### 模块 1 集成

预处理工具（Python）应输出符合本格式的 JSON：

```python
{
    "version": "1.0",
    "metadata": {...},
    "anchorNodeId": "...",
    "nodes": [...]
}
```

### 模块 4&5 集成

AI 校正循环在读取 DesignDocument 时：

1. 调用 DesignDocumentValidator.validate() 检查文档合法性
2. 获取运行时 View 树（也是 Node 列表格式）
3. 逐个对比节点属性，计算差异

## API 使用示例

```kotlin
// 读取 JSON
val json = File("design-document.json").readText()
val document = Json.decodeFromString(DesignDocument.serializer(), json)

// 验证
val errors = DesignDocumentValidator.validate(document)
if (errors.isNotEmpty()) {
    errors.forEach { println("${it.field}: ${it.message}") }
}

// 访问节点
val anchorNode = document.nodes.find { it.id == document.anchorNodeId }
document.nodes.forEach { node ->
    println("${node.id} at (${node.screenX}, ${node.screenY})")
}
```

## 测试覆盖

- ✅ DocumentMetadata 序列化/反序列化
- ✅ DesignDocument 序列化/反序列化
- ✅ Node customAttrs 序列化/反序列化
- ✅ 锚点存在性验证
- ✅ 锚点坐标验证
- ✅ ID 唯一性验证
- ✅ 尺寸有效性验证
- ✅ 类型与属性匹配验证

## 已知限制

1. customAttrs 值仅支持 String 类型（便于配置和扩展）
2. 暂不支持多锚点模式（设计时决定采用单锚点）
3. 验证器不检查节点是否超出屏幕边界（允许溢出）

## 文件清单

- `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/DesignDocument.kt`
- `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/DocumentMetadata.kt`
- `packages/shared/src/commonMain/kotlin/com/clienttools/shared/validation/DesignDocumentValidator.kt`
- `packages/shared/src/commonTest/kotlin/com/clienttools/shared/DesignDocumentTest.kt`
- `packages/shared/src/commonTest/kotlin/com/clienttools/shared/DesignDocumentValidatorTest.kt`
- `packages/shared/src/commonTest/kotlin/com/clienttools/shared/DesignDocumentIntegrationTest.kt`
- `docs/examples/design-document-example.json`
EOF
```

- [ ] **Step 5: 运行所有 shared 模块测试验证无破坏**

```bash
cd packages && ./gradlew :shared:jvmTest
```

Expected: 所有测试通过，包括旧的 SerializationTest 和新添加的各项测试

- [ ] **Step 6: 提交**

```bash
git add docs/examples/design-document-example.json \
        docs/2026-04-18-document-format/implementation-notes.md \
        packages/shared/src/commonTest/kotlin/com/clienttools/shared/DesignDocumentIntegrationTest.kt && \
git commit -m "docs: add example document and integration tests for DesignDocument"
```

---

## 总结

模块 2 完成后的交付物：

✅ **KMP Shared 模块**
- DesignDocument（容器类）
- DocumentMetadata（元数据）
- Node 增强（customAttrs 字段）
- DesignDocumentValidator（约束验证）

✅ **测试覆盖**
- 序列化/反序列化测试
- 约束验证测试
- 集成测试

✅ **文档**
- 示例 JSON 文档
- 实现笔记和 API 使用指南

✅ **可集成性**
- 与模块 1 预处理工具对接（JSON 输出格式）
- 与模块 4&5 AI 校正循环对接（验证和数据访问）

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

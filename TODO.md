# Inspect 工作流改进 TODO

来源：2026-04-19 登录页视觉核对实测总结

## 问题与改进项

### 1. 核对阶段不修改代码
**问题：** 核对过程中直接改 XML，导致逻辑混乱，难以追踪累计改动。  
**改进：** 核对阶段只用 `modify_view` 做运行时调整，将所有差异记录到核对清单文档（checklist.md），待用户确认后再集中写回 XML。

### 2. 关联视图联动问题
**问题：** 调整视图 A 后，依赖 A 位置的视图 B 跟着偏移，导致反复调整。  
**改进：** 
- 核对顺序严格从上到下，每个节点调整后立即重新查询其下方直接依赖节点
- 记录每次调整对下方节点的影响，批量评估后再统一应用
- 优先调整父容器，再调整子节点


### 5. 解除 HTML id 与 Android View id 的强绑定
**问题：** 当前要求设计稿 HTML 元素 id 与 Android XML View id 一一对应，流程约束多，HTML 或 Android 任意一侧重命名都会导致匹配失效。  
**改进：** 核对时不依赖 id 对应，改为基于坐标的自动匹配：
1. `dom_all()` 获取所有 DOM 节点坐标
2. AI 根据 layout XML 推断待核对的 View id 列表
3. 对每个 View，在 DOM 中找坐标最近的节点（结合尺寸 + 节点类型加权匹配）
4. 匹配成功则比对位置差异；匹配不上则标记为"新增/缺失"

好处：HTML 与 Android 完全解耦，各自独立命名。  
风险：坐标差异较大时可能误匹配，需结合节点类型（text/image/container）降低误匹配率。  
**约束：** Android XML 中每个 View 必须设置 `android:id`，这是 `get_node` 的硬性要求，不可省略。

### 4. 核对阈值收严为 < 1dp
**问题：** 当前阈值 2dp 导致部分偏差被忽略，累计误差明显。  
**改进：** 将位置和尺寸验收阈值统一改为 **< 1dp**，≥ 1dp 即需调整。  
需同步更新 `client-tools-inspect` skill 文档中的阈值表。

### 7. modify_view 支持 wrap_content
**问题：** 当前 `modify_view` 的 `widthDp`/`heightDp` 只接受数值，无法将 View 尺寸还原为 `wrap_content`，校对时无法修正被硬编码宽高的节点。  
**改进：** `widthDp`/`heightDp` 支持特殊值 `"wrap_content"`，SDK 侧映射为 `ViewGroup.LayoutParams.WRAP_CONTENT`。

### 6. 跨端数据结构迁移至 Protocol Buffers
**问题：** 当前 `shared/` KMP 模块用 Kotlin data class + kotlinx.serialization，多端字段名/类型靠人工保持一致，容易漂移。  
**改进：** 将所有跨端数据结构（Node、NodeType、TextAttrs、ImageAttrs 等）迁移到 `.proto` 定义，各端从同一份 schema 生成代码：
- Android：`protobuf-kotlin` 插件生成 Kotlin 类
- iOS：`Swift Protobuf` 生成 Swift 类
- Python（preprocess/MCP）：`grpcio-tools` 生成 Python 类

**影响范围：**
- `packages/shared/` 模块重构，移除 kotlinx.serialization，改为 protoc 生成产物
- `skill/preprocess/models.py` 替换为 proto 生成的 Python 类
- `mcp/` 通信协议同步切换
- 构建链需增加 protoc 编译步骤（Gradle task + CI）

**约束：** KMP 仅保留构建胶水层，不再包含手写数据类。

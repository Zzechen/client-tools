# Design: SDK & MCP 说明文档

**日期：** 2026-05-20  
**状态：** 已批准

## 背景

项目目前缺少一份面向开发者（App 接入方 + AI/Claude）的一目了然参考文档。查阅接口、工具参数需要翻阅源代码，效率低且易过期。需要建立结构清晰、易于维护的文档体系，并通过 CLAUDE.md 规则确保代码更新时文档同步跟进。

## 目标

1. 提供 MCP 工具完整参考（22 个工具），无需看源码即可调用
2. 提供 SDK HTTP 接口参考，包含 Android/iOS 对比，无需看源码即可集成
3. README.md 作为项目导航入口
4. CLAUDE.md 规则保障文档与代码长期同步

## 不在范围内

- 不重写已有的 `docs/integration.md`（接入指南保持现状）
- 不新增教程或 How-to 文章
- 不引入自动化文档生成工具

## 文件结构

```
README.md                    # 项目概览 + 导航入口（新建/覆盖）
docs/
  mcp-tools.md               # MCP 工具完整参考（新建）
  sdk-http-api.md            # SDK HTTP 接口参考（新建）
  integration.md             # 已有，不改动
```

## 各文件详细设计

### README.md

内容顺序：
1. 一句话项目描述（中文）
2. 架构说明：`App SDK ←HTTP:8080→ MCP Server ←MCP→ AI (Claude)`
3. 快速导航表：

| 文档 | 内容 |
|------|------|
| [MCP Tools](docs/mcp-tools.md) | 22 个 MCP 工具参数与返回值 |
| [SDK HTTP API](docs/sdk-http-api.md) | SDK HTTP 接口，Android/iOS 对比 |
| [接入指南](docs/integration.md) | SDK 集成步骤 |

4. 目录结构说明（简版）

### docs/mcp-tools.md

结构：
- **概览表**：按分组（7 组）列出所有工具名 + 一句话用途
- **详情**：每个工具独立小节，包含：
  - 描述（一句话）
  - 参数表（名称 / 类型 / 必填 / 说明）
  - 典型返回值 JSON 示例

工具分组：
| 分组 | 工具 |
|------|------|
| 页面/节点 | get_current_page, get_node, get_all_nodes, capture_view |
| 交互 | click_view, scroll_view |
| 视图修改 | modify_view_android, modify_view_ios |
| WebView 覆层 | push_html, show_webview, hide_overlay, adjust_overlay, list_files |
| 图片覆层 | push_image, show_image, list_images |
| DOM 查询 | dom_all, dom_by_id |
| Mock | mock_add, mock_list, mock_delete, mock_clear |

### docs/sdk-http-api.md

结构：
- **通用说明**：端口 8080、数据格式 protobuf、通用响应字段（ResponseMeta）
- **接口概览表**：路径 / 方法 / Android / iOS / 说明（✓ / — 标记平台支持）
- **接口详情**：每个接口独立小节，包含：
  - 请求字段（来自 proto 定义）
  - 响应字段
  - 平台差异说明（如有）
- **数据模型**：核心 proto message 字段说明（Node、ResponseMeta、MockRule 等）

### CLAUDE.md 新增规则

在 `## 开发约定` 章节下追加：

```markdown
## 文档同步约定

修改以下代码时，必须同步更新对应文档：

- 修改 `mcp/src/tools/` 下任何工具（新增/删除/改参数）→ 同步更新 `docs/mcp-tools.md`
- 修改 Android/iOS HttpServer 路由（新增/删除/改接口）→ 同步更新 `docs/sdk-http-api.md`
- 修改项目整体结构或新增模块 → 同步更新 `README.md`
```

## 实现步骤

1. 更新 `CLAUDE.md`，追加文档同步规则
2. 新建 `docs/mcp-tools.md`，填充 22 个工具详情（参数来源：`mcp/src/tools/`）
3. 新建 `docs/sdk-http-api.md`，填充接口详情（来源：Android/iOS HttpServer + proto 定义）
4. 新建/覆盖 `README.md`，写概览和导航
5. 提交

## 成功标准

- 不看源码，仅凭 `docs/mcp-tools.md` 能调用任意 MCP 工具
- 不看源码，仅凭 `docs/sdk-http-api.md` 能构造任意 SDK HTTP 请求
- README.md 三分钟内能找到任意入口
- AI 修改 MCP 工具或 SDK 接口后，对应文档自动跟进更新

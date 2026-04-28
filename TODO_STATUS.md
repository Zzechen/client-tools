# Client-Tools 项目状态清单

> 更新时间：2026-04-27

---

## ✅ 已完成

| 模块 | 状态 | 文档 |
|------|------|------|
| 模块1：设计稿预处理（Python+Playwright） | ✅ 完成 | `docs/2026-04-17-preprocess/` |
| 模块2：KMP共享数据结构 | ✅ 完成 | `docs/2026-04-17-shared-kmp/` |
| 模块3：Android SDK + Demo | ✅ 完成 | `docs/2026-04-18-android-sdk/` |
| 模块4：WebView叠加 | ✅ 完成 | `docs/2026-04-18-webview-management/` |
| 模块5：DOM查询 | ✅ 完成 | `docs/2026-04-19-dom-query/` |
| 模块6：Inspector v2 | ✅ 完成 | `docs/2026-04-20-inspect-v2/` |
| Skill：client-tools 三个skill | ✅ 完成 | `docs/2026-04-19-client-tools-skills/` |
| HarmonyOS可行性调研 | ✅ 完成（结论：不做） | `docs/2026-04-19-harmonyos-feasibility/research.md` |
| iOS SDK（SwiftProtobuf，基础接口） | ✅ 完成（11/19 接口） | `clients/ios/sdk/` |
| MCP Server | ✅ 完成 | `mcp/` |
| 跨端数据迁移至Protocol Buffers | ✅ 完成 | `proto/`，三端均已迁移 |
| iOS Demo 重构 + Login/VerifyCode 实现 | ✅ 完成 | `clients/ios/demo/`，删除多余页面，按设计稿实现两个页面 |

### TODO.md 改进项完成情况

| 改进项 | 状态 | 说明 |
|--------|------|------|
| #1 核对阶段不修改代码 | ✅ 已实现 | inspect skill 使用 `modify_view` 运行时调整 |
| #5 解除HTML id与Android View id强绑定 | ✅ 已实现 | 改为坐标自动匹配 |
| #2 关联视图联动问题 | ✅ 已实现 | 从上到下逐个核对 |
| #4 核对阈值收严为<1dp | ✅ 已实现 | inspect skill 已更新 |
| #7 modify_view支持wrap_content | ❌ 未做 | — |
| #6 跨端数据迁移至Protocol Buffers | ✅ 已实现 | proto/ 三端迁移完成 |

---

## ❌ 未完成（待办）

| 优先级 | 任务 | 说明 |
|--------|------|------|
| ~~P0~~ | ~~iOS SDK 补齐：View 截图~~ | ✅ 已完成，`GET /api/capture/{id}`，`layer.render` 截图 |
| ~~P0~~ | ~~iOS SDK 补齐：Inspector 图片覆盖层~~ | ✅ 已完成，push-image / show-image / images / hide / adjust + InspectorPanel |
| P0 | MCP 适配 iOS 连接 | 当前 MCP 走 `adb forward` + `127.0.0.1`，仅适用 Android；iOS 模拟器需直连 `localhost`，iOS 真机需 `iproxy`；需在 MCP 中增加 iOS 连接模式 |
| P0 | iOS SDK 补齐：WebView 文件列表 | `GET /webview/files` |
| P0 | iOS SDK 补齐：DOM 查询 | `GET /dom/all` + `GET /dom/{id}`，WKWebView JavaScriptBridge |
| ~~P0~~ | ~~iOS Demo 重构：删除多余页面~~ | ✅ 已完成 |
| P1 | TODO #7：modify_view支持wrap_content | SDK侧改造 |
| P1 | TODO #8：TextView行高对齐 | implement阶段自动计算lineHeight |
| P2 | HarmonyOS SDK | 参考Kuikly方案，KMP通过ohosArm64Main支持 |

---

## 📊 HarmonyOS 能力差距分析

详见 `docs/2026-04-23-capability-baseline/CAPABILITY_BASELINE.md`

| 能力 | Android | HarmonyOS | 差距 |
|------|---------|----------|------|
| HTTP Server | ✅ | ✅ 可迁移 | 无 |
| View查询 | ✅ 完整 | ⚠️ 有限 | 无遍历、无属性获取 |
| View修改 | ✅ 非侵入 | ❌ 侵入式 | 根本性架构差异 |
| WebView叠加 | ✅ | ✅ 可迁移 | 无 |
| Inspector叠加 | ✅ | ✅ 需重建DOM层 | DOM映射层需新建 |
| 接入模式 | 非侵入 | 侵入式 | 需宿主app配合暴露@State |
| P2 | SVG → Vector Drawable自动转换 | orchestrate spec已定义 |

---

## 🚫 不做（已重新评估）

| 项目 | 原因 |
|------|------|
| ~~HarmonyOS SDK~~ | 重新调研后：KMP 可通过 ohosArm64Main 支持 HarmonyOS（参考腾讯 Kuikly 方案），ROI 可接受 |
| ~~Skill集成工作流（orchestrate）~~ | 方向调整：弱化 skill，框架只提供能力，工作流交给 AI 自主判断 |

---

## 📝 技术决策记录

| 日期 | 决策 | 原因 |
|------|------|------|
| 2026-04-23 | TODO #6 PB迁移优先级从P2→P1 | 考虑支持HarmonyOS+Web多平台，KMP无法覆盖 |
| 2026-04-23 | HarmonyOS SDK 从"不做"改为"规划中" | 参考腾讯Kuikly框架，KMP可支持HarmonyOS（ohosArm64Main→.so→Bridge） |

---

## 📋 新工作目录结构（规划中）

```
<project-root>/
└── design/
    └── <timestamp>-<bizname>/
        ├── state.json           # 状态机文件
        ├── design.html          # 原始设计稿
        ├── design.json          # preprocess产出
        └── drawables/          # SVG → Vector Drawable
```

---

## 📝 技术决策记录

| 日期 | 决策 | 原因 |
|------|------|------|
| 2026-04-23 | TODO #6 PB迁移优先级从P2→P1 | 考虑支持HarmonyOS+Web多平台，KMP无法覆盖 |

---

## 下一步建议

1. **优先完成 iOS SDK** — 补齐 Android 外的另一端
2. **优先完成 MCP Server** — Skill 需要 MCP 工具才能工作
3. 等显示器接好后，**用真机跑通 Android 端完整流程**


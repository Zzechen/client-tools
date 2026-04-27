# iOS SDK: GET /api/capture/{id}

## 背景

Android SDK 已实现 `GET /api/capture/{id}`，接受 view id，返回该 View 的 PNG 截图（protobuf `CaptureResponse`）。iOS SDK 缺少该接口，本次补齐。

## 目标

在 iOS SDK 的 HTTP Server 中新增 `GET /api/capture/{id}`，语义与 Android 端一致。

## 设计

### 接口

```
GET /api/capture/{id}
```

- **成功**：HTTP 200，Content-Type: application/x-protobuf，body 为 `Clienttools_CaptureResponse`，`image_png` 字段为 PNG bytes
- **view 不存在或尺寸为 0**：HTTP 404，`SimpleResponse` 含错误信息

### 截图实现

使用 `UIGraphicsImageRenderer` + `view.layer.render(in:)` 方式：

- 必须在主线程执行，用 `DispatchSemaphore` 等待结果（对齐 Android 的 `CountDownLatch` 模式）
- 对 WKWebView、Metal 渲染层等无法完整截图的 View，尽力而为，不特殊处理

### 改动范围

**`ViewQueryService.swift`** — 新增方法：

```swift
func captureView(id: String) -> Data?
```

- 调用已有的 `findView(byId:)` 定位 View
- 尺寸为 0 或找不到 View 返回 `nil`
- 主线程截图，返回 PNG `Data`

**`HttpServer.swift`** — 新增路由和 handler：

- 在 `processRequest` 追加 `GET /api/capture/` 前缀匹配（同 `/api/nodes/` 的做法）
- handler 调用 `viewQueryService.captureView(id:)`，结果写入 `Clienttools_CaptureResponse.imagePng` 返回

### 数据流

```
GET /api/capture/{id}
  → HttpServer.processRequest
  → ViewQueryService.captureView(id)
      → findView(byId:)
      → layer.render(in:)   // 主线程
  → Clienttools_CaptureResponse
  → protobuf bytes 响应
```

### 错误处理

| 情况 | HTTP 状态 | 错误信息 |
|------|-----------|---------|
| view 不存在 | 404 | "View not found" |
| view 尺寸为 0 | 404 | "View has no size" |

## 不在范围内

- WKWebView、Metal 等特殊 View 的截图增强
- 截图格式参数（固定 PNG）
- 压缩质量参数

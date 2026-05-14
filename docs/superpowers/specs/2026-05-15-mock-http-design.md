# HTTP Mock 能力设计文档

**日期**：2026-05-15  
**范围**：Android（iOS 后续扩展）  
**目标**：为 AI 黑盒测试提供网络层 mock 能力，使 AI 可在无真实后端的情况下完整验证 UI 交互流程。

---

## 背景

现有 client-tools 具备 UI 检查、元素点击、截图等运行时能力，但网络请求无法干预，导致黑盒测试依赖真实后端，场景覆盖受限。引入 HTTP mock 后，AI 可自行编排接口返回，独立完成「注册 mock → 操作 UI → 验证结果」的完整测试闭环。

---

## 整体架构

```
MCP Server (TypeScript)
  └── mock.ts
        ├── mock_add    读取本地 JSON 文件 → 序列化为 protobuf → POST /mock/rules
        ├── mock_delete                                          → DELETE /mock/rules/:id
        ├── mock_list                                           → GET /mock/rules
        └── mock_clear                                          → DELETE /mock/rules

Android SDK (Kotlin)
  ├── MockRuleStore    内存规则表（ConcurrentHashMap），线程安全
  ├── MockInterceptor  OkHttp Interceptor，匹配规则后返回 mock response 或抛出异常
  └── HttpServer       新增 /mock/* 路由，读写 MockRuleStore，protobuf 收发

App（接入方）
  └── 构建 OkHttpClient 时注册 MockInterceptor()   ← 唯一改动
```

数据流：AI 写好规则文件 → 调 `mock_add(file)` → MCP 读文件并 protobuf 序列化 → POST SDK → 规则入内存 → App 发请求 → Interceptor 命中规则 → 返回 mock response，不走网络。

---

## Proto 定义

新增 `proto/mock.proto`：

```proto
syntax = "proto3";
package clienttools;

message AddMockRuleRequest {
  string url       = 1;  // 正则表达式，匹配请求 URL
  string method    = 2;  // 大写，如 POST
  int64  delay_ms  = 3;  // 延迟毫秒，默认 0
  string error     = 4;  // "timeout" / "connection_refused" / 空=正常返回
  int32  status    = 5;  // HTTP 状态码，默认 200
  map<string, string> headers = 6;
  string body      = 7;
}

message MockRule {
  string id        = 1;  // UUID，添加时由 SDK 生成
  string url       = 2;
  string method    = 3;
  int64  delay_ms  = 4;
  string error     = 5;
  int32  status    = 6;
  map<string, string> headers = 7;
  string body      = 8;
}

message MockRuleListResponse {
  repeated MockRule rules = 1;
}

message DeleteMockRuleResponse {
  bool success = 1;
}

message ClearMockRulesResponse {
  int32 cleared_count = 1;
}
```

生成目标：
- Android Kotlin → `clients/android/sdk/src/main/proto/mock.proto`（Gradle 自动生成）
- MCP TypeScript → `mcp/src/generated/`（`buf generate`）

---

## Mock 规则文件格式（JSON）

规则文件由 AI 在测试前写到本地，MCP 读取后转为 protobuf 发给 SDK。每条规则一个文件。

**正常返回：**
```json
{
  "url": "/api/login",
  "method": "POST",
  "delay_ms": 500,
  "status": 200,
  "headers": { "Content-Type": "application/json" },
  "body": "{\"token\": \"abc123\", \"userId\": 1}"
}
```

**错误场景：**
```json
{
  "url": "/api/login",
  "method": "POST",
  "delay_ms": 3000,
  "error": "timeout"
}
```

**字段说明：**

| 字段 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `url` | 是 | — | 正则表达式，用 `containsMatchIn` 匹配完整请求 URL |
| `method` | 是 | — | HTTP 方法，大写 |
| `delay_ms` | 否 | 0 | 延迟毫秒，error 场景同样生效 |
| `status` | 否 | 200 | HTTP 状态码，`error` 存在时忽略 |
| `headers` | 否 | {} | 响应头 |
| `body` | 否 | "" | 响应体字符串，`error` 存在时忽略 |
| `error` | 否 | — | `"timeout"` / `"connection_refused"`，有此字段则抛出对应异常 |

**URL 匹配规则：**
- 使用 `Regex(rule.url).containsMatchIn(requestUrl)`
- 写 `/api/login` 可匹配任意 host 下该路径
- 需精确匹配时加 `^...$`，如 `"^https://api\\.example\\.com/api/login$"`
- 同一 URL+Method 有多条规则时，最后添加的生效（last-win）

**支持的 error 类型：**
- `"timeout"` → `SocketTimeoutException`
- `"connection_refused"` → `ConnectException`
- 其他任意字符串 → `IOException("mock error: <value>")`

---

## Android SDK 实现

### 新增依赖

```kotlin
// clients/android/sdk/build.gradle.kts
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

### MockRuleStore

```kotlin
// com.clienttools.sdk.mock.MockRuleStore
object MockRuleStore {
    private val rules = ConcurrentHashMap<String, MockRule>()
    private val insertOrder = CopyOnWriteArrayList<String>()

    fun add(rule: MockRule): MockRule
    fun delete(id: String): Boolean
    fun list(): List<MockRule>
    fun clear(): Int                                           // 返回清除数量
    fun findMatch(url: String, method: String): MockRule?     // last-win
}
```

### MockInterceptor

```kotlin
// com.clienttools.sdk.mock.MockInterceptor
class MockInterceptor : Interceptor {
    override fun intercept(chain: Chain): Response {
        val req = chain.request()
        val rule = MockRuleStore.findMatch(req.url.toString(), req.method)
            ?: return chain.proceed(req)

        if (rule.delayMs > 0) Thread.sleep(rule.delayMs)

        if (rule.error.isNotEmpty()) throw when (rule.error) {
            "timeout"            -> SocketTimeoutException("mock timeout")
            "connection_refused" -> ConnectException("mock connection refused")
            else                 -> IOException("mock error: ${rule.error}")
        }

        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(rule.status)
            .message("Mock")
            .apply { rule.headersMap.forEach { (k, v) -> header(k, v) } }
            .body(rule.body.toResponseBody(
                rule.headersMap["Content-Type"]?.toMediaTypeOrNull()
            ))
            .build()
    }
}
```

### HttpServer 新增路由

```
POST   /mock/rules        body: AddMockRuleRequest (protobuf)  → MockRule (protobuf)
GET    /mock/rules                                             → MockRuleListResponse (protobuf)
DELETE /mock/rules/:id                                        → DeleteMockRuleResponse (protobuf)
DELETE /mock/rules                                            → ClearMockRulesResponse (protobuf)
```

---

## MCP 工具（mock.ts）

```typescript
mock_add(file: string)
// 读取 JSON 文件 → 构造 AddMockRuleRequest → POST /mock/rules
// 返回生成的规则 id 及完整规则内容

mock_delete(id: string)
// DELETE /mock/rules/:id

mock_list()
// GET /mock/rules，返回当前所有规则列表

mock_clear()
// DELETE /mock/rules，返回清除数量
```

---

## App 接入

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(MockInterceptor())
    .build()
```

仅此一处改动。`MockInterceptor` 在无匹配规则时透传请求，不影响生产行为（建议仅 debug build 注册）。

---

## 测试场景示例

```
1. mock_clear()                          // 清空旧规则
2. mock_add("mocks/login_success.json")  // 注册正常登录 mock
3. 输入账号密码，点击登录
4. android layout → 验证跳转到首页
5. mock_add("mocks/login_401.json")      // 切换为错误场景
6. 再次点击登录
7. android layout → 验证错误提示文案出现
```

---

## 不在范围内

- iOS 支持（后续扩展）
- request body / header 匹配
- 请求日志记录（验证接口是否被调用）
- 规则持久化（进程重启后清空）
- 并发规则（同一请求触发多条规则）

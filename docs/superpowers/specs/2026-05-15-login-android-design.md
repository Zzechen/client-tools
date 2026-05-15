# Android 登录页逻辑实现设计

**日期**：2026-05-15  
**平台**：Android Demo  
**目标**：为现有登录页 UI 骨架补充完整的业务逻辑、网络层和接口，使用 MCP mock 工具完成端到端测试并输出验证报告。

---

## 1. 现状

- `LoginActivity.kt`：仅处理系统状态栏，无业务逻辑
- `activity_login.xml`：已有完整 UI，包含 SMS / 密码 / 邮箱三个 Tab 及对应输入控件
- 无网络库、无 API 调用
- `UserInfoActivity` 不存在，登录后无跳转目标

---

## 2. 架构

```
LoginActivity
  └── LoginViewModel (StateFlow<LoginUiState>)
        └── RetrofitClient.authService
              └── OkHttpClient (含 MockInterceptor)
                    ↓ mock 拦截
              MockRuleStore（SDK 内置）

登录成功 → startActivity(UserInfoActivity) + finish()
UserInfoActivity → Intent 接收 UserInfo (Parcelable)
退出登录 → startActivity(LoginActivity) + finishAffinity()
```

**页面流程**：

```
LoginActivity
  ├── Tab SMS   → 手机号 + 验证码  → POST /auth/login/sms
  ├── Tab PWD   → 手机号 + 密码    → POST /auth/login/password
  └── Tab EMAIL → 邮箱 + 密码      → POST /auth/login/email
         ↓ 成功
    UserInfoActivity（展示 UserInfo）
```

---

## 3. 网络层

### 3.1 新增依赖（`clients/android/demo/build.gradle.kts`）

```kotlin
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-gson:2.11.0")
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
```

OkHttp 已有（MockInterceptor 在用），无需重复添加。

### 3.2 数据模型

```kotlin
// 请求
data class SendSmsRequest(val phone: String)
data class SmsLoginRequest(val phone: String, val code: String)
data class PasswordLoginRequest(val phone: String, val password: String)
data class EmailLoginRequest(val email: String, val password: String)

// 响应
data class BaseResponse(val code: Int, val message: String)
data class LoginResponse(val code: Int, val message: String, val data: LoginData?)
data class LoginData(val token: String, val user: UserInfo)
data class UserInfo(
    val id: String,
    val name: String,
    val phone: String,
    val email: String,
    val avatar_url: String
) : Parcelable
```

### 3.3 AuthService 接口

```kotlin
interface AuthService {
    @POST("auth/sms/send")
    suspend fun sendSmsCode(@Body req: SendSmsRequest): BaseResponse

    @POST("auth/login/sms")
    suspend fun loginSms(@Body req: SmsLoginRequest): LoginResponse

    @POST("auth/login/password")
    suspend fun loginPassword(@Body req: PasswordLoginRequest): LoginResponse

    @POST("auth/login/email")
    suspend fun loginEmail(@Body req: EmailLoginRequest): LoginResponse
}
```

### 3.4 RetrofitClient（单例）

```kotlin
object RetrofitClient {
    val authService: AuthService by lazy {
        Retrofit.Builder()
            .baseUrl("http://localhost:${SdkConfig.PORT}/")
            .client(SdkHttpClient.instance)   // 复用 SDK 的 OkHttpClient（含 MockInterceptor）
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthService::class.java)
    }
}
```

---

## 4. LoginViewModel

```kotlin
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val user: UserInfo) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

// 短信倒计时状态
data class SmsCountdownState(val seconds: Int = 0) // 0 = 未倒计时

class LoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _countdown = MutableStateFlow(SmsCountdownState())
    val countdown: StateFlow<SmsCountdownState> = _countdown.asStateFlow()

    fun sendSmsCode(phone: String) { ... }         // 调用 authService.sendSmsCode，成功后启动 60s 倒计时
    fun loginSms(phone: String, code: String) { ... }
    fun loginPassword(phone: String, password: String) { ... }
    fun loginEmail(email: String, password: String) { ... }
}
```

---

## 5. LoginActivity 职责

- `ViewModelProvider` 获取 `LoginViewModel`
- Tab 点击切换输入区域显示/隐藏
- `repeatOnLifecycle(STARTED)` 收集 `uiState`：
  - `Loading` → 禁用提交按钮 + 显示 ProgressBar
  - `Success` → `startActivity<UserInfoActivity>(userInfo)` + `finish()`
  - `Error` → Toast 显示错误信息
  - `Idle` → 恢复按钮状态
- 收集 `countdown`：更新发送验证码按钮文字与可用状态

### 5.1 客户端输入校验（提交前）

| Tab | 校验规则 |
|-----|---------|
| SMS | 手机号 11 位数字 + 验证码 6 位非空 |
| PWD | 手机号 11 位数字 + 密码非空 |
| EMAIL | 邮箱格式（含 `@`）+ 密码非空 |

---

## 6. UserInfoActivity

### 6.1 布局 `activity_user_info.xml`

| View id | 类型 | 内容 |
|---------|------|------|
| `user_info_root` | ScrollView | 根容器 |
| `user_info_avatar` | TextView | 首字母圆形头像（无真实图片） |
| `user_info_name` | TextView | 昵称 |
| `user_info_phone` | TextView | 手机号（脱敏显示） |
| `user_info_email` | TextView | 邮箱 |
| `user_info_token` | TextView | Token（截断前 20 字符，调试用） |
| `user_info_btn_logout` | Button | 退出登录 |

### 6.2 逻辑

- `onCreate`：从 `intent.getParcelableExtra<UserInfo>(KEY_USER)` 取数据，直接绑定到 View
- 退出登录按钮：`startActivity<LoginActivity>()` + `finishAffinity()`

---

## 7. Mock 规则

规则文件放在 `clients/android/demo/mock/` 目录。

### 7.1 正常场景

**`auth_sms_send.json`**
```json
{
  "url": "http://localhost:8080/auth/sms/send",
  "method": "POST",
  "status": 200,
  "headers": {"Content-Type": "application/json"},
  "body": "{\"code\":0,\"message\":\"ok\"}"
}
```

**`auth_login_sms.json`**
```json
{
  "url": "http://localhost:8080/auth/login/sms",
  "method": "POST",
  "status": 200,
  "headers": {"Content-Type": "application/json"},
  "body": "{\"code\":0,\"message\":\"ok\",\"data\":{\"token\":\"tok_sms_abc123\",\"user\":{\"id\":\"u001\",\"name\":\"Alice\",\"phone\":\"138****8888\",\"email\":\"alice@example.com\",\"avatar_url\":\"\"}}}"
}
```

**`auth_login_password.json`**
```json
{
  "url": "http://localhost:8080/auth/login/password",
  "method": "POST",
  "status": 200,
  "headers": {"Content-Type": "application/json"},
  "body": "{\"code\":0,\"message\":\"ok\",\"data\":{\"token\":\"tok_pwd_def456\",\"user\":{\"id\":\"u002\",\"name\":\"Bob\",\"phone\":\"139****9999\",\"email\":\"bob@example.com\",\"avatar_url\":\"\"}}}"
}
```

**`auth_login_email.json`**
```json
{
  "url": "http://localhost:8080/auth/login/email",
  "method": "POST",
  "status": 200,
  "headers": {"Content-Type": "application/json"},
  "body": "{\"code\":0,\"message\":\"ok\",\"data\":{\"token\":\"tok_email_ghi789\",\"user\":{\"id\":\"u003\",\"name\":\"Carol\",\"phone\":\"\",\"email\":\"carol@example.com\",\"avatar_url\":\"\"}}}"
}
```

### 7.2 异常场景

**`auth_login_sms_error.json`**（验证码错误）
```json
{
  "url": "http://localhost:8080/auth/login/sms",
  "method": "POST",
  "status": 200,
  "headers": {"Content-Type": "application/json"},
  "body": "{\"code\":1001,\"message\":\"验证码错误或已过期\"}"
}
```

**`auth_login_password_error.json`**（密码错误）
```json
{
  "url": "http://localhost:8080/auth/login/password",
  "method": "POST",
  "status": 200,
  "headers": {"Content-Type": "application/json"},
  "body": "{\"code\":1002,\"message\":\"手机号或密码错误\"}"
}
```

---

## 8. 测试策略

### 8.1 MCP 工具序列

```
# 准备
mock_clear
mock_add auth_sms_send.json
mock_add auth_login_sms.json

# 场景 1：SMS 登录成功
get_current_page                       → 确认在 LoginActivity
get_node login_tab_code                → 确认 Tab 存在
click_view login_tab_code              → 切换到短信 Tab
click_view login_btn_verify_text       → 点击发送验证码
capture_view login_btn_verify_text     → 截图验证倒计时文字
click_view login_btn_submit            → 提交登录
get_current_page                       → 确认跳转到 UserInfoActivity
get_node user_info_name                → 确认昵称 = "Alice"
get_node user_info_phone               → 确认手机号 = "138****8888"
capture_view user_info_root            → 截图用户信息页

# 场景 2：SMS 登录失败
mock_delete <sms_success_id>
mock_add auth_login_sms_error.json
click_view login_btn_submit
get_current_page                       → 确认仍在 LoginActivity（未跳转）
capture_view login_root                → 截图 Toast 错误提示

# 场景 3：密码登录成功
mock_clear
mock_add auth_login_password.json
click_view login_tab_password
click_view login_btn_submit
get_current_page                       → 确认跳转到 UserInfoActivity
get_node user_info_name                → 确认昵称 = "Bob"
capture_view user_info_root

# 场景 4：邮箱登录成功
mock_clear
mock_add auth_login_email.json
click_view login_tab_email
click_view login_btn_submit
get_current_page                       → 确认跳转到 UserInfoActivity
get_node user_info_name                → 确认昵称 = "Carol"
capture_view user_info_root

# 场景 5：退出登录
click_view user_info_btn_logout
get_current_page                       → 确认返回 LoginActivity
```

### 8.2 测试报告格式

```markdown
| 场景 | 步骤 | 预期 | 实际 | 截图 | 结论 |
|------|------|------|------|------|------|
| SMS 登录成功 | 提交后 get_current_page | UserInfoActivity | ... | [截图] | PASS/FAIL |
...
```

---

## 9. 文件变更清单

| 操作 | 路径 |
|------|------|
| 修改 | `clients/android/demo/build.gradle.kts` |
| 修改 | `clients/android/demo/src/main/kotlin/.../LoginActivity.kt` |
| 新增 | `clients/android/demo/src/main/kotlin/.../LoginViewModel.kt` |
| 新增 | `clients/android/demo/src/main/kotlin/.../RetrofitClient.kt` |
| 新增 | `clients/android/demo/src/main/kotlin/.../AuthService.kt` |
| 新增 | `clients/android/demo/src/main/kotlin/.../model/LoginModels.kt` |
| 新增 | `clients/android/demo/src/main/kotlin/.../UserInfoActivity.kt` |
| 新增 | `clients/android/demo/src/main/res/layout/activity_user_info.xml` |
| 新增 | `clients/android/demo/mock/auth_sms_send.json` |
| 新增 | `clients/android/demo/mock/auth_login_sms.json` |
| 新增 | `clients/android/demo/mock/auth_login_sms_error.json` |
| 新增 | `clients/android/demo/mock/auth_login_password.json` |
| 新增 | `clients/android/demo/mock/auth_login_password_error.json` |
| 新增 | `clients/android/demo/mock/auth_login_email.json` |

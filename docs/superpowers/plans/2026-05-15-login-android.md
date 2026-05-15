# Android 登录页逻辑实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Android Demo 登录页补充完整业务逻辑，含 Retrofit 网络层、LoginViewModel、三种登录方式（SMS/密码/邮箱）、UserInfoActivity，并通过 MCP mock 工具验证端到端流程，输出测试报告。

**Architecture:** LoginActivity 持有 LoginViewModel，通过 StateFlow 驱动 UI 状态；网络层使用 Retrofit + Gson，复用 DemoApplication.httpClient（已含 MockInterceptor）；登录成功后跳转 UserInfoActivity 展示用户基本信息；退出登录返回 LoginActivity。

**Tech Stack:** Retrofit 2.11.0, converter-gson 2.11.0, lifecycle-viewmodel-ktx 2.8.3, lifecycle-runtime-ktx 2.8.3, OkHttp 4.12.0（已有）, kotlin-parcelize

---

## File Structure

```
clients/android/demo/
├── build.gradle.kts                                            MODIFY – 添加 Retrofit/ViewModel/Parcelize 依赖
├── src/main/
│   ├── AndroidManifest.xml                                     MODIFY – 注册 UserInfoActivity
│   ├── kotlin/com/clienttools/demo/
│   │   ├── LoginActivity.kt                                    MODIFY – 完整登录逻辑（Tab 切换 + ViewModel 观察）
│   │   ├── LoginViewModel.kt                                   NEW    – StateFlow 状态管理 + 倒计时
│   │   ├── UserInfoActivity.kt                                 NEW    – 用户信息展示 + 退出登录
│   │   ├── MainActivity.kt                                     MODIFY – 添加 UserInfoActivity Demo 入口
│   │   ├── model/
│   │   │   └── LoginModels.kt                                  NEW    – 请求/响应 data class，UserInfo Parcelable
│   │   └── network/
│   │       ├── AuthService.kt                                  NEW    – Retrofit 接口定义
│   │       └── RetrofitClient.kt                               NEW    – Retrofit 单例（复用 DemoApplication.httpClient）
│   └── res/layout/
│       ├── activity_login.xml                                  MODIFY – 重构输入区域，新增 SMS/PWD/EMAIL 字段
│       └── activity_user_info.xml                              NEW    – 用户信息页布局
└── mock/
    ├── auth_sms_send.json                                      NEW
    ├── auth_login_sms.json                                     NEW
    ├── auth_login_sms_error.json                               NEW
    ├── auth_login_password.json                                NEW
    ├── auth_login_password_error.json                          NEW
    └── auth_login_email.json                                   NEW
```

**关键约定：**
- Retrofit baseUrl = `http://api.pulse.app/`（虚假域名，MockInterceptor 在网络层之前拦截，不发出真实请求）
- MockRuleStore 使用 `Regex(entry.url).containsMatchIn(requestUrl)` 匹配，mock 规则 URL 写路径片段即可（如 `auth/login/sms`）
- `login_field_phone` 从 ConstraintLayout 直接子 View 迁移到 `login_input_area` LinearLayout 内部
- `login_btn_verify_text`（原在按钮内）迁移到 `login_section_sms` 区段，成为独立的"发送验证码"按钮
- `login_btn_submit` 内改用 `login_btn_submit_label`（TextView）+ `login_btn_submit_progress`（ProgressBar）

---

### Task 1: 添加依赖

**Files:**
- Modify: `clients/android/demo/build.gradle.kts`

- [ ] **Step 1: 完整替换 build.gradle.kts**

  ```kotlin
  plugins {
      id("com.android.application")
      id("org.jetbrains.kotlin.android")
      id("kotlin-parcelize")
  }

  kotlin {
      compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
      }
  }

  android {
      namespace = "com.clienttools.demo"
      compileSdk = 35
      defaultConfig {
          applicationId = "com.clienttools.demo"
          minSdk = 26
          targetSdk = 35
          versionCode = 1
          versionName = "1.0"
      }
      compileOptions {
          sourceCompatibility = JavaVersion.VERSION_21
          targetCompatibility = JavaVersion.VERSION_21
      }
  }

  dependencies {
      implementation(project(":sdk"))
      implementation(libs.kotlin.stdlib)
      implementation(libs.androidx.appcompat)
      implementation(libs.androidx.core)
      implementation("androidx.recyclerview:recyclerview:1.3.2")
      implementation("androidx.constraintlayout:constraintlayout:2.1.4")
      implementation("com.squareup.okhttp3:okhttp:4.12.0")
      implementation("com.squareup.retrofit2:retrofit:2.11.0")
      implementation("com.squareup.retrofit2:converter-gson:2.11.0")
      implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
      implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

      testImplementation(kotlin("test"))
      androidTestImplementation(libs.androidx.test.espresso)
  }
  ```

- [ ] **Step 2: 验证编译**

  ```bash
  cd /Users/zzc/Desktop/works/client-tools/clients/android && ./gradlew :demo:assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

  ```bash
  git add clients/android/demo/build.gradle.kts
  git commit -m "feat(demo): add Retrofit, ViewModel, and kotlin-parcelize dependencies"
  ```

---

### Task 2: 创建数据模型

**Files:**
- Create: `clients/android/demo/src/main/kotlin/com/clienttools/demo/model/LoginModels.kt`

- [ ] **Step 1: 创建 LoginModels.kt**

  ```kotlin
  package com.clienttools.demo.model

  import android.os.Parcelable
  import kotlinx.parcelize.Parcelize

  // ── 请求体 ──────────────────────────────────────────────────────────────────

  data class SendSmsRequest(val phone: String)
  data class SmsLoginRequest(val phone: String, val code: String)
  data class PasswordLoginRequest(val phone: String, val password: String)
  data class EmailLoginRequest(val email: String, val password: String)

  // ── 响应体 ──────────────────────────────────────────────────────────────────

  data class BaseResponse(val code: Int, val message: String)

  data class LoginResponse(
      val code: Int,
      val message: String,
      val data: LoginData?
  )

  data class LoginData(
      val token: String,
      val user: UserInfo
  )

  @Parcelize
  data class UserInfo(
      val id: String,
      val name: String,
      val phone: String,
      val email: String,
      val avatar_url: String
  ) : Parcelable
  ```

- [ ] **Step 2: 验证编译**

  ```bash
  cd /Users/zzc/Desktop/works/client-tools/clients/android && ./gradlew :demo:assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

  ```bash
  git add clients/android/demo/src/main/kotlin/com/clienttools/demo/model/LoginModels.kt
  git commit -m "feat(demo): add login request/response models and Parcelable UserInfo"
  ```

---

### Task 3: 创建网络层

**Files:**
- Create: `clients/android/demo/src/main/kotlin/com/clienttools/demo/network/AuthService.kt`
- Create: `clients/android/demo/src/main/kotlin/com/clienttools/demo/network/RetrofitClient.kt`

- [ ] **Step 1: 创建 AuthService.kt**

  ```kotlin
  package com.clienttools.demo.network

  import com.clienttools.demo.model.BaseResponse
  import com.clienttools.demo.model.EmailLoginRequest
  import com.clienttools.demo.model.LoginResponse
  import com.clienttools.demo.model.PasswordLoginRequest
  import com.clienttools.demo.model.SendSmsRequest
  import com.clienttools.demo.model.SmsLoginRequest
  import retrofit2.http.Body
  import retrofit2.http.POST

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

- [ ] **Step 2: 创建 RetrofitClient.kt**

  ```kotlin
  package com.clienttools.demo.network

  import com.clienttools.demo.DemoApplication
  import retrofit2.Retrofit
  import retrofit2.converter.gson.GsonConverterFactory

  object RetrofitClient {
      val authService: AuthService by lazy {
          Retrofit.Builder()
              .baseUrl("http://api.pulse.app/")
              .client(DemoApplication.httpClient)
              .addConverterFactory(GsonConverterFactory.create())
              .build()
              .create(AuthService::class.java)
      }
  }
  ```

  > `http://api.pulse.app/` 是虚假域名。`DemoApplication.httpClient` 已挂载 `MockInterceptor`，请求不会到达网络。

- [ ] **Step 3: 验证编译**

  ```bash
  cd /Users/zzc/Desktop/works/client-tools/clients/android && ./gradlew :demo:assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

  ```bash
  git add clients/android/demo/src/main/kotlin/com/clienttools/demo/network/
  git commit -m "feat(demo): add AuthService Retrofit interface and RetrofitClient singleton"
  ```

---

### Task 4: 创建 LoginViewModel

**Files:**
- Create: `clients/android/demo/src/main/kotlin/com/clienttools/demo/LoginViewModel.kt`

- [ ] **Step 1: 创建 LoginViewModel.kt**

  ```kotlin
  package com.clienttools.demo

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import com.clienttools.demo.model.EmailLoginRequest
  import com.clienttools.demo.model.LoginResponse
  import com.clienttools.demo.model.PasswordLoginRequest
  import com.clienttools.demo.model.SendSmsRequest
  import com.clienttools.demo.model.SmsLoginRequest
  import com.clienttools.demo.model.UserInfo
  import com.clienttools.demo.network.RetrofitClient
  import kotlinx.coroutines.Job
  import kotlinx.coroutines.delay
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.launch

  sealed class LoginUiState {
      object Idle : LoginUiState()
      object Loading : LoginUiState()
      data class Success(val user: UserInfo, val token: String) : LoginUiState()
      data class Error(val message: String) : LoginUiState()
  }

  class LoginViewModel : ViewModel() {

      private val authService = RetrofitClient.authService

      private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
      val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

      private val _smsCountdown = MutableStateFlow(0)
      val smsCountdown: StateFlow<Int> = _smsCountdown.asStateFlow()

      private var countdownJob: Job? = null

      fun sendSmsCode(phone: String) {
          viewModelScope.launch {
              try {
                  val resp = authService.sendSmsCode(SendSmsRequest(phone))
                  if (resp.code == 0) startCountdown()
                  else _uiState.value = LoginUiState.Error(resp.message)
              } catch (e: Exception) {
                  _uiState.value = LoginUiState.Error(e.message ?: "网络错误")
              }
          }
      }

      fun loginSms(phone: String, code: String) {
          _uiState.value = LoginUiState.Loading
          viewModelScope.launch {
              try {
                  handleLoginResponse(authService.loginSms(SmsLoginRequest(phone, code)))
              } catch (e: Exception) {
                  _uiState.value = LoginUiState.Error(e.message ?: "网络错误")
              }
          }
      }

      fun loginPassword(phone: String, password: String) {
          _uiState.value = LoginUiState.Loading
          viewModelScope.launch {
              try {
                  handleLoginResponse(authService.loginPassword(PasswordLoginRequest(phone, password)))
              } catch (e: Exception) {
                  _uiState.value = LoginUiState.Error(e.message ?: "网络错误")
              }
          }
      }

      fun loginEmail(email: String, password: String) {
          _uiState.value = LoginUiState.Loading
          viewModelScope.launch {
              try {
                  handleLoginResponse(authService.loginEmail(EmailLoginRequest(email, password)))
              } catch (e: Exception) {
                  _uiState.value = LoginUiState.Error(e.message ?: "网络错误")
              }
          }
      }

      fun resetState() {
          _uiState.value = LoginUiState.Idle
      }

      private fun handleLoginResponse(resp: LoginResponse) {
          if (resp.code == 0 && resp.data != null) {
              _uiState.value = LoginUiState.Success(resp.data.user, resp.data.token)
          } else {
              _uiState.value = LoginUiState.Error(resp.message)
          }
      }

      private fun startCountdown() {
          countdownJob?.cancel()
          countdownJob = viewModelScope.launch {
              for (i in 60 downTo 1) {
                  _smsCountdown.value = i
                  delay(1000L)
              }
              _smsCountdown.value = 0
          }
      }
  }
  ```

- [ ] **Step 2: 验证编译**

  ```bash
  cd /Users/zzc/Desktop/works/client-tools/clients/android && ./gradlew :demo:assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

  ```bash
  git add clients/android/demo/src/main/kotlin/com/clienttools/demo/LoginViewModel.kt
  git commit -m "feat(demo): add LoginViewModel with SMS/password/email login states and countdown"
  ```

---

### Task 5: 重构 activity_login.xml 输入区域

**Files:**
- Modify: `clients/android/demo/src/main/res/layout/activity_login.xml`

**背景：** 当前布局中 `login_field_phone` 和 `login_btn_submit` 是 ConstraintLayout 直接子 View，`login_btn_verify_text` 是 `login_btn_submit` 内的文字标签。需要将输入字段包进 `login_input_area` LinearLayout，将 `login_btn_verify_text` 迁移为 SMS 区段的独立发送按钮，并在 `login_btn_submit` 内新增 label + ProgressBar。

- [ ] **Step 1: 替换手机号和按钮两个区块**

  在 `activity_login.xml` 中，将以下两段内容整体替换（从 `<!-- ===== 手机号输入框 ===== -->` 到 `login_btn_submit` FrameLayout 的结束标签）：

  **旧内容（lines 192–275）要替换的两个块：**
  ```xml
  <!-- ===== 手机号输入框 ===== -->
  <LinearLayout android:id="@+id/login_field_phone" ...（整个块）... </LinearLayout>

  <!-- ===== 获取验证码按钮（实心青色） ===== -->
  <FrameLayout android:id="@+id/login_btn_submit" ...（整个块）... </FrameLayout>
  ```

  **新内容（替换为）：**

  ```xml
          <!-- ===== 输入区域（手机号 + Tab 对应字段） ===== -->
          <LinearLayout
              android:id="@+id/login_input_area"
              android:layout_width="0dp"
              android:layout_height="wrap_content"
              android:orientation="vertical"
              android:layout_marginStart="24dp"
              android:layout_marginEnd="24dp"
              android:layout_marginTop="16dp"
              app:layout_constraintTop_toBottomOf="@+id/login_tabs"
              app:layout_constraintStart_toStartOf="parent"
              app:layout_constraintEnd_toEndOf="parent">

              <!-- 手机号（SMS / PWD Tab 共用） -->
              <LinearLayout
                  android:id="@+id/login_field_phone"
                  android:layout_width="match_parent"
                  android:layout_height="52dp"
                  android:orientation="horizontal"
                  android:background="@drawable/login_bg_input"
                  android:gravity="center_vertical"
                  android:paddingStart="14dp"
                  android:paddingEnd="14dp">

                  <View
                      android:id="@+id/login_phone_flag"
                      android:layout_width="20dp"
                      android:layout_height="14dp"
                      android:background="#CC3333" />

                  <TextView
                      android:id="@+id/login_phone_code"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:text="+86"
                      android:textColor="@color/text_primary"
                      android:textSize="15sp"
                      android:layout_marginStart="6dp" />

                  <TextView
                      android:id="@+id/login_phone_chevron"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:text=" ∨"
                      android:textColor="@color/login_text_hint"
                      android:textSize="10sp" />

                  <View
                      android:id="@+id/login_phone_divider"
                      android:layout_width="1dp"
                      android:layout_height="20dp"
                      android:background="@color/login_divider"
                      android:layout_marginStart="10dp"
                      android:layout_marginEnd="12dp" />

                  <EditText
                      android:id="@+id/login_phone_number"
                      android:layout_width="0dp"
                      android:layout_height="wrap_content"
                      android:layout_weight="1"
                      android:hint="138 0013 8000"
                      android:textColor="@color/text_primary"
                      android:textColorHint="@color/login_text_hint"
                      android:textSize="16sp"
                      android:inputType="phone"
                      android:background="@null"
                      android:maxLines="1"
                      android:imeOptions="actionNext" />
              </LinearLayout>

              <!-- SMS：验证码输入 + 发送按钮 -->
              <LinearLayout
                  android:id="@+id/login_section_sms"
                  android:layout_width="match_parent"
                  android:layout_height="52dp"
                  android:orientation="horizontal"
                  android:background="@drawable/login_bg_input"
                  android:gravity="center_vertical"
                  android:paddingStart="14dp"
                  android:paddingEnd="14dp"
                  android:layout_marginTop="12dp"
                  android:visibility="visible">

                  <EditText
                      android:id="@+id/login_input_sms_code"
                      android:layout_width="0dp"
                      android:layout_height="wrap_content"
                      android:layout_weight="1"
                      android:hint="6位验证码"
                      android:textColor="@color/text_primary"
                      android:textColorHint="@color/login_text_hint"
                      android:textSize="16sp"
                      android:inputType="number"
                      android:maxLength="6"
                      android:background="@null"
                      android:imeOptions="actionDone" />

                  <TextView
                      android:id="@+id/login_btn_verify_text"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:text="发送验证码"
                      android:textColor="@color/login_cyan"
                      android:textSize="13sp"
                      android:paddingStart="8dp" />
              </LinearLayout>

              <!-- PWD：密码输入 -->
              <LinearLayout
                  android:id="@+id/login_section_pwd"
                  android:layout_width="match_parent"
                  android:layout_height="52dp"
                  android:orientation="horizontal"
                  android:background="@drawable/login_bg_input"
                  android:gravity="center_vertical"
                  android:paddingStart="14dp"
                  android:paddingEnd="14dp"
                  android:layout_marginTop="12dp"
                  android:visibility="gone">

                  <EditText
                      android:id="@+id/login_input_password"
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:hint="请输入密码"
                      android:textColor="@color/text_primary"
                      android:textColorHint="@color/login_text_hint"
                      android:textSize="16sp"
                      android:inputType="textPassword"
                      android:background="@null"
                      android:imeOptions="actionDone" />
              </LinearLayout>

              <!-- EMAIL：邮箱 + 密码 -->
              <LinearLayout
                  android:id="@+id/login_section_email"
                  android:layout_width="match_parent"
                  android:layout_height="wrap_content"
                  android:orientation="vertical"
                  android:layout_marginTop="12dp"
                  android:visibility="gone">

                  <LinearLayout
                      android:layout_width="match_parent"
                      android:layout_height="52dp"
                      android:orientation="horizontal"
                      android:background="@drawable/login_bg_input"
                      android:gravity="center_vertical"
                      android:paddingStart="14dp"
                      android:paddingEnd="14dp">

                      <EditText
                          android:id="@+id/login_input_email"
                          android:layout_width="match_parent"
                          android:layout_height="wrap_content"
                          android:hint="请输入邮箱"
                          android:textColor="@color/text_primary"
                          android:textColorHint="@color/login_text_hint"
                          android:textSize="16sp"
                          android:inputType="textEmailAddress"
                          android:background="@null"
                          android:imeOptions="actionNext" />
                  </LinearLayout>

                  <LinearLayout
                      android:layout_width="match_parent"
                      android:layout_height="52dp"
                      android:orientation="horizontal"
                      android:background="@drawable/login_bg_input"
                      android:gravity="center_vertical"
                      android:paddingStart="14dp"
                      android:paddingEnd="14dp"
                      android:layout_marginTop="12dp">

                      <EditText
                          android:id="@+id/login_input_email_password"
                          android:layout_width="match_parent"
                          android:layout_height="wrap_content"
                          android:hint="请输入密码"
                          android:textColor="@color/text_primary"
                          android:textColorHint="@color/login_text_hint"
                          android:textSize="16sp"
                          android:inputType="textPassword"
                          android:background="@null"
                          android:imeOptions="actionDone" />
                  </LinearLayout>
              </LinearLayout>

          </LinearLayout>

          <!-- ===== 登录按钮 ===== -->
          <FrameLayout
              android:id="@+id/login_btn_submit"
              android:layout_width="0dp"
              android:layout_height="52dp"
              android:layout_marginStart="24dp"
              android:layout_marginEnd="24dp"
              android:layout_marginTop="22dp"
              android:background="@drawable/login_bg_btn_cyan"
              app:layout_constraintTop_toBottomOf="@+id/login_input_area"
              app:layout_constraintStart_toStartOf="parent"
              app:layout_constraintEnd_toEndOf="parent">

              <TextView
                  android:id="@+id/login_btn_submit_label"
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:layout_gravity="center"
                  android:text="验证并登录  →"
                  android:textColor="@color/login_bg"
                  android:textSize="16sp"
                  android:textStyle="bold" />

              <ProgressBar
                  android:id="@+id/login_btn_submit_progress"
                  android:layout_width="24dp"
                  android:layout_height="24dp"
                  android:layout_gravity="center"
                  android:visibility="gone"
                  android:indeterminateTint="@color/login_bg" />

          </FrameLayout>
  ```

- [ ] **Step 2: 验证编译**

  ```bash
  cd /Users/zzc/Desktop/works/client-tools/clients/android && ./gradlew :demo:assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

  ```bash
  git add clients/android/demo/src/main/res/layout/activity_login.xml
  git commit -m "feat(demo): restructure login input area with SMS/PWD/EMAIL sections and ProgressBar"
  ```

---

### Task 6: 实现 LoginActivity 完整业务逻辑

**Files:**
- Modify: `clients/android/demo/src/main/kotlin/com/clienttools/demo/LoginActivity.kt`

- [ ] **Step 1: 完整替换 LoginActivity.kt**

  ```kotlin
  package com.clienttools.demo

  import android.content.Intent
  import android.os.Bundle
  import android.view.View
  import android.widget.EditText
  import android.widget.FrameLayout
  import android.widget.ProgressBar
  import android.widget.TextView
  import android.widget.Toast
  import androidx.appcompat.app.AppCompatActivity
  import androidx.constraintlayout.widget.ConstraintLayout
  import androidx.core.view.ViewCompat
  import androidx.core.view.WindowCompat
  import androidx.core.view.WindowInsetsCompat
  import androidx.lifecycle.Lifecycle
  import androidx.lifecycle.ViewModelProvider
  import androidx.lifecycle.lifecycleScope
  import androidx.lifecycle.repeatOnLifecycle
  import com.clienttools.demo.model.UserInfo
  import kotlinx.coroutines.launch

  class LoginActivity : AppCompatActivity() {

      private lateinit var viewModel: LoginViewModel

      // Tabs
      private lateinit var tabCode: TextView
      private lateinit var tabPassword: TextView
      private lateinit var tabEmail: TextView

      // Input area
      private lateinit var fieldPhone: View
      private lateinit var phoneNumber: EditText
      private lateinit var sectionSms: View
      private lateinit var inputSmsCode: EditText
      private lateinit var btnSendCode: TextView
      private lateinit var sectionPwd: View
      private lateinit var inputPassword: EditText
      private lateinit var sectionEmail: View
      private lateinit var inputEmail: EditText
      private lateinit var inputEmailPassword: EditText

      // Submit button
      private lateinit var btnSubmit: FrameLayout
      private lateinit var btnSubmitLabel: TextView
      private lateinit var btnSubmitProgress: ProgressBar

      private var currentTab = Tab.SMS

      enum class Tab { SMS, PWD, EMAIL }

      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          WindowCompat.setDecorFitsSystemWindows(window, false)
          window.statusBarColor = android.graphics.Color.TRANSPARENT
          setContentView(R.layout.activity_login)

          val navBar = findViewById<ConstraintLayout>(R.id.login_nav_bar)
          ViewCompat.setOnApplyWindowInsetsListener(navBar) { view, insets ->
              val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
              view.setPadding(0, statusBarHeight, 0, 0)
              insets
          }

          viewModel = ViewModelProvider(this)[LoginViewModel::class.java]
          bindViews()
          switchTab(Tab.SMS)
          setupClickListeners()
          observeViewModel()
      }

      private fun bindViews() {
          tabCode = findViewById(R.id.login_tab_code)
          tabPassword = findViewById(R.id.login_tab_password)
          tabEmail = findViewById(R.id.login_tab_email)

          fieldPhone = findViewById(R.id.login_field_phone)
          phoneNumber = findViewById(R.id.login_phone_number)
          sectionSms = findViewById(R.id.login_section_sms)
          inputSmsCode = findViewById(R.id.login_input_sms_code)
          btnSendCode = findViewById(R.id.login_btn_verify_text)
          sectionPwd = findViewById(R.id.login_section_pwd)
          inputPassword = findViewById(R.id.login_input_password)
          sectionEmail = findViewById(R.id.login_section_email)
          inputEmail = findViewById(R.id.login_input_email)
          inputEmailPassword = findViewById(R.id.login_input_email_password)

          btnSubmit = findViewById(R.id.login_btn_submit)
          btnSubmitLabel = findViewById(R.id.login_btn_submit_label)
          btnSubmitProgress = findViewById(R.id.login_btn_submit_progress)
      }

      private fun switchTab(tab: Tab) {
          currentTab = tab

          // Tab 样式
          listOf(tabCode to (tab == Tab.SMS), tabPassword to (tab == Tab.PWD), tabEmail to (tab == Tab.EMAIL))
              .forEach { (tv, selected) ->
                  if (selected) {
                      tv.setBackgroundResource(R.drawable.login_bg_tab_selected)
                      tv.setTextColor(getColor(R.color.login_bg))
                  } else {
                      tv.background = null
                      tv.setTextColor(getColor(R.color.login_text_hint))
                  }
              }

          // 输入区域显隐
          fieldPhone.visibility = if (tab == Tab.EMAIL) View.GONE else View.VISIBLE
          sectionSms.visibility = if (tab == Tab.SMS) View.VISIBLE else View.GONE
          sectionPwd.visibility = if (tab == Tab.PWD) View.VISIBLE else View.GONE
          sectionEmail.visibility = if (tab == Tab.EMAIL) View.VISIBLE else View.GONE

          // 按钮文字
          btnSubmitLabel.text = when (tab) {
              Tab.SMS -> "验证并登录  →"
              Tab.PWD -> "密码登录  →"
              Tab.EMAIL -> "邮箱登录  →"
          }
      }

      private fun setupClickListeners() {
          tabCode.setOnClickListener { switchTab(Tab.SMS) }
          tabPassword.setOnClickListener { switchTab(Tab.PWD) }
          tabEmail.setOnClickListener { switchTab(Tab.EMAIL) }

          btnSendCode.setOnClickListener {
              val phone = phoneNumber.text.toString().trim()
              if (phone.length != 11) { toast("请输入11位手机号"); return@setOnClickListener }
              viewModel.sendSmsCode(phone)
          }

          btnSubmit.setOnClickListener {
              when (currentTab) {
                  Tab.SMS -> {
                      val phone = phoneNumber.text.toString().trim()
                      val code = inputSmsCode.text.toString().trim()
                      if (phone.length != 11) { toast("请输入11位手机号"); return@setOnClickListener }
                      if (code.length != 6) { toast("请输入6位验证码"); return@setOnClickListener }
                      viewModel.loginSms(phone, code)
                  }
                  Tab.PWD -> {
                      val phone = phoneNumber.text.toString().trim()
                      val pwd = inputPassword.text.toString()
                      if (phone.length != 11) { toast("请输入11位手机号"); return@setOnClickListener }
                      if (pwd.isEmpty()) { toast("请输入密码"); return@setOnClickListener }
                      viewModel.loginPassword(phone, pwd)
                  }
                  Tab.EMAIL -> {
                      val email = inputEmail.text.toString().trim()
                      val pwd = inputEmailPassword.text.toString()
                      if (!email.contains("@")) { toast("请输入有效邮箱"); return@setOnClickListener }
                      if (pwd.isEmpty()) { toast("请输入密码"); return@setOnClickListener }
                      viewModel.loginEmail(email, pwd)
                  }
              }
          }
      }

      private fun observeViewModel() {
          lifecycleScope.launch {
              repeatOnLifecycle(Lifecycle.State.STARTED) {
                  launch {
                      viewModel.uiState.collect { state ->
                          when (state) {
                              is LoginUiState.Loading -> setSubmitLoading(true)
                              is LoginUiState.Success -> navigateToUserInfo(state.user, state.token)
                              is LoginUiState.Error -> {
                                  setSubmitLoading(false)
                                  toast(state.message)
                                  viewModel.resetState()
                              }
                              is LoginUiState.Idle -> setSubmitLoading(false)
                          }
                      }
                  }
                  launch {
                      viewModel.smsCountdown.collect { seconds ->
                          if (seconds > 0) {
                              btnSendCode.isEnabled = false
                              btnSendCode.text = "${seconds}s 后重发"
                          } else {
                              btnSendCode.isEnabled = true
                              btnSendCode.text = "发送验证码"
                          }
                      }
                  }
              }
          }
      }

      private fun setSubmitLoading(loading: Boolean) {
          btnSubmit.isEnabled = !loading
          btnSubmitLabel.visibility = if (loading) View.GONE else View.VISIBLE
          btnSubmitProgress.visibility = if (loading) View.VISIBLE else View.GONE
          if (!loading) switchTab(currentTab)
      }

      private fun navigateToUserInfo(user: UserInfo, token: String) {
          startActivity(Intent(this, UserInfoActivity::class.java).apply {
              putExtra(UserInfoActivity.KEY_USER, user)
              putExtra(UserInfoActivity.KEY_TOKEN, token)
          })
          finish()
      }

      private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
  }
  ```

- [ ] **Step 2: 验证编译**

  `UserInfoActivity` 会在 Task 7 完整实现，若此步骤报 `Unresolved reference`，先创建占位文件：

  ```kotlin
  // clients/android/demo/src/main/kotlin/com/clienttools/demo/UserInfoActivity.kt
  package com.clienttools.demo
  import androidx.appcompat.app.AppCompatActivity
  class UserInfoActivity : AppCompatActivity() {
      companion object {
          const val KEY_USER = "user_info"
          const val KEY_TOKEN = "token"
      }
  }
  ```

  ```bash
  cd /Users/zzc/Desktop/works/client-tools/clients/android && ./gradlew :demo:assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

  ```bash
  git add clients/android/demo/src/main/kotlin/com/clienttools/demo/LoginActivity.kt
  git add clients/android/demo/src/main/kotlin/com/clienttools/demo/UserInfoActivity.kt
  git commit -m "feat(demo): implement LoginActivity with tab switching, validation, and ViewModel binding"
  ```

---

### Task 7: 创建 UserInfoActivity 及布局

**Files:**
- Create: `clients/android/demo/src/main/res/layout/activity_user_info.xml`
- Modify: `clients/android/demo/src/main/kotlin/com/clienttools/demo/UserInfoActivity.kt`

- [ ] **Step 1: 创建 activity_user_info.xml**

  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
      android:id="@+id/user_info_root"
      android:layout_width="match_parent"
      android:layout_height="match_parent"
      android:background="@color/login_bg">

      <LinearLayout
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          android:orientation="vertical"
          android:paddingStart="24dp"
          android:paddingEnd="24dp"
          android:paddingBottom="40dp">

          <!-- 头像（首字母圆形） -->
          <FrameLayout
              android:layout_width="80dp"
              android:layout_height="80dp"
              android:layout_gravity="center_horizontal"
              android:layout_marginTop="64dp"
              android:layout_marginBottom="12dp"
              android:background="@drawable/login_bg_logo_icon">

              <TextView
                  android:id="@+id/user_info_avatar"
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:layout_gravity="center"
                  android:textColor="#0A0B0F"
                  android:textSize="32sp"
                  android:textStyle="bold" />
          </FrameLayout>

          <!-- 昵称 -->
          <TextView
              android:id="@+id/user_info_name"
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:layout_gravity="center_horizontal"
              android:textColor="@color/text_primary"
              android:textSize="22sp"
              android:textStyle="bold"
              android:layout_marginBottom="40dp" />

          <!-- 手机号 -->
          <TextView
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="手机号"
              android:textColor="@color/login_text_hint"
              android:textSize="12sp"
              android:layout_marginBottom="4dp" />

          <TextView
              android:id="@+id/user_info_phone"
              android:layout_width="match_parent"
              android:layout_height="52dp"
              android:gravity="center_vertical"
              android:paddingStart="16dp"
              android:background="@drawable/login_bg_input"
              android:textColor="@color/text_primary"
              android:textSize="16sp"
              android:layout_marginBottom="16dp" />

          <!-- 邮箱 -->
          <TextView
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="邮箱"
              android:textColor="@color/login_text_hint"
              android:textSize="12sp"
              android:layout_marginBottom="4dp" />

          <TextView
              android:id="@+id/user_info_email"
              android:layout_width="match_parent"
              android:layout_height="52dp"
              android:gravity="center_vertical"
              android:paddingStart="16dp"
              android:background="@drawable/login_bg_input"
              android:textColor="@color/text_primary"
              android:textSize="16sp"
              android:layout_marginBottom="16dp" />

          <!-- Token（调试用，截断展示） -->
          <TextView
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="Token"
              android:textColor="@color/login_text_hint"
              android:textSize="12sp"
              android:layout_marginBottom="4dp" />

          <TextView
              android:id="@+id/user_info_token"
              android:layout_width="match_parent"
              android:layout_height="52dp"
              android:gravity="center_vertical"
              android:paddingStart="16dp"
              android:background="@drawable/login_bg_input"
              android:textColor="@color/login_text_hint"
              android:textSize="13sp"
              android:layout_marginBottom="48dp" />

          <!-- 退出登录 -->
          <FrameLayout
              android:id="@+id/user_info_btn_logout"
              android:layout_width="match_parent"
              android:layout_height="52dp"
              android:background="@drawable/login_bg_btn_cyan">

              <TextView
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:layout_gravity="center"
                  android:text="退出登录"
                  android:textColor="@color/login_bg"
                  android:textSize="16sp"
                  android:textStyle="bold" />
          </FrameLayout>

      </LinearLayout>
  </ScrollView>
  ```

- [ ] **Step 2: 完整替换 UserInfoActivity.kt**

  ```kotlin
  package com.clienttools.demo

  import android.content.Intent
  import android.os.Bundle
  import android.widget.FrameLayout
  import android.widget.TextView
  import androidx.appcompat.app.AppCompatActivity
  import com.clienttools.demo.model.UserInfo

  class UserInfoActivity : AppCompatActivity() {

      companion object {
          const val KEY_USER = "user_info"
          const val KEY_TOKEN = "token"
      }

      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          setContentView(R.layout.activity_user_info)

          @Suppress("DEPRECATION")
          val user = intent.getParcelableExtra<UserInfo>(KEY_USER)
              ?: run { finish(); return }
          val token = intent.getStringExtra(KEY_TOKEN) ?: ""

          findViewById<TextView>(R.id.user_info_avatar).text =
              user.name.firstOrNull()?.toString() ?: "?"
          findViewById<TextView>(R.id.user_info_name).text = user.name
          findViewById<TextView>(R.id.user_info_phone).text = user.phone.ifEmpty { "未绑定" }
          findViewById<TextView>(R.id.user_info_email).text = user.email.ifEmpty { "未绑定" }
          findViewById<TextView>(R.id.user_info_token).text =
              if (token.length > 20) "${token.take(20)}..." else token

          findViewById<FrameLayout>(R.id.user_info_btn_logout).setOnClickListener {
              startActivity(Intent(this, LoginActivity::class.java))
              finishAffinity()
          }
      }
  }
  ```

- [ ] **Step 3: 验证编译**

  ```bash
  cd /Users/zzc/Desktop/works/client-tools/clients/android && ./gradlew :demo:assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

  ```bash
  git add clients/android/demo/src/main/res/layout/activity_user_info.xml
  git add clients/android/demo/src/main/kotlin/com/clienttools/demo/UserInfoActivity.kt
  git commit -m "feat(demo): add UserInfoActivity with avatar, info fields, and logout"
  ```

---

### Task 8: 注册 Activity 并更新 MainActivity

**Files:**
- Modify: `clients/android/demo/src/main/AndroidManifest.xml`
- Modify: `clients/android/demo/src/main/kotlin/com/clienttools/demo/MainActivity.kt`

- [ ] **Step 1: 在 AndroidManifest.xml 中注册 UserInfoActivity**

  在 `VerifyCodeActivity` 声明后、`</application>` 前添加：

  ```xml
          <activity
              android:name="com.clienttools.demo.UserInfoActivity"
              android:exported="false" />
  ```

- [ ] **Step 2: 在 MainActivity.kt 中添加 UserInfoActivity Demo 入口**

  将 `pages` 属性替换为：

  ```kotlin
  private val pages by lazy {
      listOf(
          Page("Login Screen") { startActivity(Intent(this, LoginActivity::class.java)) },
          Page("Verify Code") { startActivity(Intent(this, VerifyCodeActivity::class.java)) },
          Page("User Info (Demo)") {
              val demoUser = com.clienttools.demo.model.UserInfo(
                  id = "demo",
                  name = "Demo User",
                  phone = "138****8000",
                  email = "demo@pulse.app",
                  avatar_url = ""
              )
              startActivity(Intent(this, UserInfoActivity::class.java).apply {
                  putExtra(UserInfoActivity.KEY_USER, demoUser)
                  putExtra(UserInfoActivity.KEY_TOKEN, "demo_token_12345")
              })
          }
      )
  }
  ```

- [ ] **Step 3: 验证编译**

  ```bash
  cd /Users/zzc/Desktop/works/client-tools/clients/android && ./gradlew :demo:assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

  ```bash
  git add clients/android/demo/src/main/AndroidManifest.xml
  git add clients/android/demo/src/main/kotlin/com/clienttools/demo/MainActivity.kt
  git commit -m "feat(demo): register UserInfoActivity and add demo entry in MainActivity"
  ```

---

### Task 9: 创建 Mock JSON 规则文件

**Files:**
- Create: `clients/android/demo/mock/` 目录下 6 个文件

> **URL 匹配：** `MockRuleStore.findMatch` 使用 `Regex(entry.url).containsMatchIn(requestUrl)`，路径片段 `auth/login/sms` 可匹配 `http://api.pulse.app/auth/login/sms`。

- [ ] **Step 1: 创建 `clients/android/demo/mock/auth_sms_send.json`**

  ```json
  {
    "url": "auth/sms/send",
    "method": "POST",
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": "{\"code\":0,\"message\":\"ok\"}"
  }
  ```

- [ ] **Step 2: 创建 `clients/android/demo/mock/auth_login_sms.json`**

  ```json
  {
    "url": "auth/login/sms",
    "method": "POST",
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": "{\"code\":0,\"message\":\"ok\",\"data\":{\"token\":\"tok_sms_abc123\",\"user\":{\"id\":\"u001\",\"name\":\"Alice\",\"phone\":\"138****8888\",\"email\":\"alice@example.com\",\"avatar_url\":\"\"}}}"
  }
  ```

- [ ] **Step 3: 创建 `clients/android/demo/mock/auth_login_sms_error.json`**

  ```json
  {
    "url": "auth/login/sms",
    "method": "POST",
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": "{\"code\":1001,\"message\":\"验证码错误或已过期\"}"
  }
  ```

- [ ] **Step 4: 创建 `clients/android/demo/mock/auth_login_password.json`**

  ```json
  {
    "url": "auth/login/password",
    "method": "POST",
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": "{\"code\":0,\"message\":\"ok\",\"data\":{\"token\":\"tok_pwd_def456\",\"user\":{\"id\":\"u002\",\"name\":\"Bob\",\"phone\":\"139****9999\",\"email\":\"bob@example.com\",\"avatar_url\":\"\"}}}"
  }
  ```

- [ ] **Step 5: 创建 `clients/android/demo/mock/auth_login_password_error.json`**

  ```json
  {
    "url": "auth/login/password",
    "method": "POST",
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": "{\"code\":1002,\"message\":\"手机号或密码错误\"}"
  }
  ```

- [ ] **Step 6: 创建 `clients/android/demo/mock/auth_login_email.json`**

  ```json
  {
    "url": "auth/login/email",
    "method": "POST",
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": "{\"code\":0,\"message\":\"ok\",\"data\":{\"token\":\"tok_email_ghi789\",\"user\":{\"id\":\"u003\",\"name\":\"Carol\",\"phone\":\"\",\"email\":\"carol@example.com\",\"avatar_url\":\"\"}}}"
  }
  ```

- [ ] **Step 7: Commit**

  ```bash
  git add clients/android/demo/mock/
  git commit -m "feat(demo): add mock JSON rules for all login API endpoints"
  ```

---

### Task 10: 端到端 MCP 验证与测试报告

> **前提：** App 已安装并运行在真机/模拟器，MCP server 已连接。`mock_add` 路径使用绝对路径。

**mock 文件基础路径：** `/Users/zzc/Desktop/works/client-tools/clients/android/demo/mock/`

- [ ] **场景 1：SMS 登录成功**

  ```
  mock_clear
  mock_add <base>/auth_sms_send.json
  mock_add <base>/auth_login_sms.json

  get_current_page
    → 期望: activity = "LoginActivity"

  get_node login_tab_code
    → 期望: text = "验证码"

  click_view login_tab_code
  modify_view_android login_phone_number text="13800138000"
  modify_view_android login_input_sms_code text="123456"

  click_view login_btn_verify_text
    → 触发发送验证码（mock auth_sms_send 返回 code=0）

  capture_view login_btn_verify_text
    → [截图 1] 按钮显示 "60s 后重发" 或倒计时文字

  click_view login_btn_submit
    → 触发 SMS 登录（mock auth_login_sms 返回 Alice）

  get_current_page
    → 期望: activity = "UserInfoActivity"

  get_node user_info_name
    → 期望: text = "Alice"

  get_node user_info_phone
    → 期望: text = "138****8888"

  capture_view user_info_root
    → [截图 2] 用户信息页完整截图
  ```

- [ ] **场景 2：SMS 登录失败（验证码错误）**

  ```
  mock_list
    → 记录 auth_login_sms 成功规则的 id

  mock_delete <sms_success_rule_id>
  mock_add <base>/auth_login_sms_error.json

  click_view user_info_btn_logout   （如在 UserInfoActivity 则先退出）
  modify_view_android login_phone_number text="13800138000"
  modify_view_android login_input_sms_code text="000000"
  click_view login_btn_submit

  get_current_page
    → 期望: activity = "LoginActivity"（未跳转）

  capture_view login_root
    → [截图 3] 应显示 Toast "验证码错误或已过期"
  ```

- [ ] **场景 3：密码登录成功**

  ```
  mock_clear
  mock_add <base>/auth_login_password.json

  click_view login_tab_password
  modify_view_android login_phone_number text="13900139000"
  modify_view_android login_input_password text="password123"
  click_view login_btn_submit

  get_current_page
    → 期望: activity = "UserInfoActivity"

  get_node user_info_name
    → 期望: text = "Bob"

  capture_view user_info_root
    → [截图 4]
  ```

- [ ] **场景 4：邮箱登录成功**

  ```
  mock_clear
  mock_add <base>/auth_login_email.json

  click_view login_tab_email
  modify_view_android login_input_email text="carol@example.com"
  modify_view_android login_input_email_password text="password123"
  click_view login_btn_submit

  get_current_page
    → 期望: activity = "UserInfoActivity"

  get_node user_info_name
    → 期望: text = "Carol"

  capture_view user_info_root
    → [截图 5]
  ```

- [ ] **场景 5：退出登录**

  ```
  click_view user_info_btn_logout

  get_current_page
    → 期望: activity = "LoginActivity"

  capture_view login_root
    → [截图 6]
  ```

- [ ] **汇总测试报告**

  将以上每步的 `get_current_page`、`get_node` 返回值与截图填入报告：

  ```markdown
  # 登录页端到端测试报告

  **日期：** 2026-05-15
  **平台：** Android Demo
  **测试方式：** MCP mock（MockInterceptor）

  | # | 场景 | 关键验证步骤 | 预期 | 实际 | 截图 | 结论 |
  |---|------|------------|------|------|------|------|
  | 1 | SMS 登录成功 | get_node user_info_name | "Alice" | - | 截图2 | - |
  | 2 | SMS 登录失败 | get_current_page（提交后） | LoginActivity | - | 截图3 | - |
  | 3 | 密码登录成功 | get_node user_info_name | "Bob" | - | 截图4 | - |
  | 4 | 邮箱登录成功 | get_node user_info_name | "Carol" | - | 截图5 | - |
  | 5 | 退出登录 | get_current_page（退出后） | LoginActivity | - | 截图6 | - |
  | 6 | 发送验证码倒计时 | capture_view login_btn_verify_text | 显示倒计时 | - | 截图1 | - |
  ```

---

## 规格覆盖检查

| 规格章节 | 对应 Task |
|---------|----------|
| 三种登录方式（SMS/PWD/EMAIL） | Task 3、4、6 |
| Retrofit 网络层 | Task 1、3 |
| LoginViewModel + StateFlow | Task 4 |
| activity_login.xml 输入区域重构 | Task 5 |
| LoginActivity Tab 切换 + 校验 + 状态观察 | Task 6 |
| UserInfoActivity + 布局 | Task 7 |
| UserInfoActivity 注册 + MainActivity 入口 | Task 8 |
| Mock JSON 规则文件 | Task 9 |
| 端到端 MCP 测试 + 测试报告 | Task 10 |

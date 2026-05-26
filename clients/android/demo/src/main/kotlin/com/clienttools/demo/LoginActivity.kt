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
        DemoApplication.currentUser = user
        DemoApplication.currentToken = token
        startActivity(Intent(this, UserInfoActivity::class.java).apply {
            putExtra(UserInfoActivity.KEY_USER, user)
            putExtra(UserInfoActivity.KEY_TOKEN, token)
        })
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

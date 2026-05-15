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

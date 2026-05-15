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

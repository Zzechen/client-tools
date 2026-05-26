package com.clienttools.demo

import android.app.Application
import com.clienttools.demo.model.UserInfo
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.http.CustomResult
import com.clienttools.sdk.http.CustomRoute
import com.clienttools.sdk.http.HttpMethod
import com.clienttools.sdk.mock.MockInterceptor
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

class DemoApplication : Application() {

    companion object {
        lateinit var httpClient: OkHttpClient
            private set

        var currentUser: UserInfo? = null
        var currentToken: String? = null
    }

    override fun onCreate() {
        super.onCreate()
        ClientToolsSDK.init(
            context = this,
            customRoutes = listOf(
                // 正常：返回当前登录用户信息
                CustomRoute(
                    path = "user/profile",
                    method = HttpMethod.GET,
                    description = "获取当前登录用户信息",
                    handler = { _ ->
                        val user = currentUser
                        if (user != null) {
                            val json = """{"id":"${user.id}","name":"${user.name}","phone":"${user.phone}","email":"${user.email}"}"""
                            CustomResult.ok(json)
                        } else {
                            CustomResult.error("未登录", code = 401)
                        }
                    }
                ),
                // 业务错误：模拟账户被禁用
                CustomRoute(
                    path = "user/settings",
                    method = HttpMethod.POST,
                    description = "更新用户设置（模拟业务错误：账户已被禁用）",
                    params = mapOf("settings" to "JSON 格式的设置项"),
                    handler = { _ ->
                        CustomResult.error("账户已被禁用，无法修改设置", code = 403)
                    }
                ),
                // 超时：延迟 6000ms，超过 handler 默认超时 4500ms
                CustomRoute(
                    path = "debug/slow",
                    method = HttpMethod.GET,
                    description = "慢接口，延迟 6000ms，用于触发 handler 超时",
                    handler = { _ ->
                        delay(6000)
                        CustomResult.ok("should not reach here")
                    }
                ),
                // 崩溃：抛出异常，SDK 捕获并包装为 error 响应
                CustomRoute(
                    path = "debug/crash",
                    method = HttpMethod.POST,
                    description = "故意抛出异常，验证 SDK 异常捕获",
                    handler = { _ ->
                        throw IllegalStateException("demo crash: intentional exception")
                    }
                )
            )
        )
        httpClient = OkHttpClient.Builder()
            .addInterceptor(MockInterceptor())
            .build()
    }
}

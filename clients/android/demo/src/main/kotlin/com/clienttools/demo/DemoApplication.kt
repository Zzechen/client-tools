package com.clienttools.demo

import android.app.Application
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.mock.MockInterceptor
import okhttp3.OkHttpClient

class DemoApplication : Application() {

    companion object {
        lateinit var httpClient: OkHttpClient
            private set
    }

    override fun onCreate() {
        super.onCreate()
        ClientToolsSDK.init(this)
        httpClient = OkHttpClient.Builder()
            .addInterceptor(MockInterceptor())
            .build()
    }
}

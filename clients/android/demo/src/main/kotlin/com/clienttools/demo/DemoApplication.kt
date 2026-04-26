package com.clienttools.demo

import android.app.Application
import com.clienttools.sdk.ClientToolsSDK

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ClientToolsSDK.init(this)
    }
}

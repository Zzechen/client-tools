package com.clienttools.sdk.http

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.clienttools.sdk.proto.DeviceInfo
import com.clienttools.sdk.proto.ResponseMeta

object ProtoHelper {
    private const val SDK_VERSION = 1

    fun getDeviceInfo(context: Context): DeviceInfo {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        return DeviceInfo.newBuilder()
            .setScreenWidthDp(metrics.widthPixels / metrics.density)
            .setScreenHeightDp(metrics.heightPixels / metrics.density)
            .setDensity(metrics.density)
            .build()
    }

    fun okMeta(context: Context): ResponseMeta = ResponseMeta.newBuilder()
        .setCode(0)
        .setMessage("success")
        .setSdkVersion(SDK_VERSION)
        .setDevice(getDeviceInfo(context))
        .build()

    fun errMeta(code: Int, message: String, context: Context): ResponseMeta = ResponseMeta.newBuilder()
        .setCode(code)
        .setMessage(message)
        .setSdkVersion(SDK_VERSION)
        .setDevice(getDeviceInfo(context))
        .build()
}

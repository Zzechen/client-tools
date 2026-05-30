package com.clienttools.sdk.runtime

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.clienttools.sdk.ClientToolsSDK

object ScreenManager {

    /**
     * 亮屏并解锁（仅限无密码锁屏）。
     * 先用 WakeLock 唤醒屏幕，再通过 KeyguardManager 解除锁屏。
     */
    fun wakeAndUnlock() {
        // Application context 始终可用，WakeLock 不需要 Activity
        val ctx = ClientToolsSDK.appContext ?: return

        // 唤醒屏幕
        @Suppress("DEPRECATION")
        val wl = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "ClientTools:WakeScreen"
            )
        wl.acquire(3_000L)
        wl.release()

        // 解除锁屏需要 Activity；若当前无 Activity（极少见），亮屏已完成，解锁跳过
        val activity = ClientToolsSDK.getCurrentActivity() ?: return
        activity.runOnUiThread {
            (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(activity, null)
        }
    }
}

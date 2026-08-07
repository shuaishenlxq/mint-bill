package com.xl.bill.mint.receiver

import android.content.Context
import android.content.Intent

/**
 * 开机拉活：开机后拉起记账守护服务。
 * BOOT_COMPLETED 是 Android 12+ 允许从后台启动前台服务的豁免场景之一。
 */
class BootReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.ensureRunning(context)
        }
    }
}

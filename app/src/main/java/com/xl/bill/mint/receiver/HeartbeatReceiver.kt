package com.xl.bill.mint.receiver

import android.content.Context
import android.content.Intent
import com.xl.bill.mint.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 心跳拉活：周期检查守护服务是否存活。
 * 进程被杀后 AlarmManager 会先把进程拉起再执行本 Receiver，
 * 此时尝试重建前台服务（受系统后台启动限制时静默失败，依赖 START_STICKY 与 NLS 系统绑定）。
 *
 * 同时自愈引导：检测到「通知使用权」被关闭时，每日最多一次发引导通知提醒用户重新开启。
 */
class HeartbeatReceiver : android.content.BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.ensureRunning(context)
        _root_ide_package_.com.xl.bill.mint.util.HeartbeatScheduler.scheduleNext(context)
        scope.launch { checkListenerGuidance(context) }
    }

    private suspend fun checkListenerGuidance(context: Context) {
        if (_root_ide_package_.com.xl.bill.mint.util.PermissionChecker.isNotificationListenerEnabled(context)) return
        val settings = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val lastDate = settings.getLastListenerGuideDate()
        if (lastDate == today) return
        settings.setLastListenerGuideDate(today)
        _root_ide_package_.com.xl.bill.mint.util.NotificationHelper.notifyGuide(
            context,
            context.getString(R.string.notification_listener_off_title),
            context.getString(R.string.notification_listener_off_text)
        )
    }
}

package com.xl.bill.mint.receiver

import android.content.Context
import android.content.Intent
import com.xl.bill.mint.R
import com.xl.bill.mint.di.ServiceLocator
import com.xl.bill.mint.util.DiagEvent
import com.xl.bill.mint.util.DiagLog
import com.xl.bill.mint.util.HeartbeatScheduler
import com.xl.bill.mint.util.KeepAliveHelper
import com.xl.bill.mint.util.NotificationHelper
import com.xl.bill.mint.util.PermissionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 心跳拉活：周期检查守护服务是否存活。
 *
 * 精确闹钟（已授权时）触发属于 Android 12+ 后台启动 FGS 的合法豁免场景，
 * 进程被杀后闹钟先把进程拉起再执行本 Receiver，此时可可靠重建前台服务。
 *
 * 同时承担两项自愈：
 * - NLS 断连自救：观测到断连标记时执行组件 toggle 强制系统重绑（每次断连仅一次）；
 * - 引导提示：检测到「通知使用权」被关闭时，每日最多一次发引导通知提醒用户重开。
 */
class HeartbeatReceiver : android.content.BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        DiagLog.log(DiagEvent.HEARTBEAT_TICK)
        KeepAliveHelper.ensureRunning(context)
        HeartbeatScheduler.scheduleNext(context)
        KeepAliveHelper.rebindNotificationListenerIfNeeded(context)
        scope.launch { checkListenerGuidance(context) }
    }

    private suspend fun checkListenerGuidance(context: Context) {
        if (PermissionChecker.isNotificationListenerEnabled(context)) return
        val settings = ServiceLocator.settingsRepository
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val lastDate = settings.getLastListenerGuideDate()
        if (lastDate == today) return
        settings.setLastListenerGuideDate(today)
        NotificationHelper.notifyGuide(
            context,
            context.getString(R.string.notification_listener_off_title),
            context.getString(R.string.notification_listener_off_text)
        )
    }
}

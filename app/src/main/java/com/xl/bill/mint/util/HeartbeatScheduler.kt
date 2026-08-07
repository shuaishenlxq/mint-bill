package com.xl.bill.mint.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * AlarmManager 心跳调度。
 *
 * 刻意不申请 SCHEDULE_EXACT_ALARM（应用商店审核敏感、用户可拒绝），
 * 使用 setAndAllowWhileIdle（非精确）即可满足 15 分钟量级的存活检查。
 */
object HeartbeatScheduler {

    private const val INTERVAL_MS = 15 * 60_000L

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, _root_ide_package_.com.xl.bill.mint.receiver.HeartbeatReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + INTERVAL_MS,
                pi
            )
        } catch (_: Exception) {
            // 部分 ROM 限制，忽略
        }
    }
}

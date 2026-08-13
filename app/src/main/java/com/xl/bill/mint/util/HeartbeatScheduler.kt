package com.xl.bill.mint.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.xl.bill.mint.receiver.HeartbeatReceiver

/**
 * AlarmManager 心跳调度。
 *
 * 优先使用精确闹钟（setExactAndAllowWhileIdle）：
 * - MIUI 自研省电会无限推迟非精确闹钟，心跳断链是「杀进程后不拉活」的主因；
 * - 精确闹钟（已授予「闹钟和提醒」时）是 Android 12+ 后台启动前台服务的
 *   合法豁免通道，闹钟触发后可 reliably 重建 FGS。
 * 用户未授予 SCHEDULE_EXACT_ALARM（Android 14+ 默认未授予）时回退非精确闹钟，
 * 功能不缺失只是时机不可靠；设置页提供授权引导。
 */
object HeartbeatScheduler {

    private const val INTERVAL_MS = 15 * 60_000L

    fun scheduleNext(context: Context) {
        schedule(context, System.currentTimeMillis() + INTERVAL_MS)
    }

    /**
     * 短延迟触发（默认 10s）：供 onTaskRemoved 等场景快速重建。
     * 走闹钟豁免链，而非直接 startForegroundService（onTaskRemoved 仍属后台上下文）。
     */
    fun scheduleSoon(context: Context, delayMs: Long = 10_000L) {
        schedule(context, System.currentTimeMillis() + delayMs)
    }

    private fun schedule(context: Context, triggerAt: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, HeartbeatReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 每次调度现查：用户可随时在系统设置授予/撤销
        val canExact = runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                DiagLog.log(DiagEvent.HEARTBEAT_EXACT)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                DiagLog.log(DiagEvent.HEARTBEAT_INEXACT, "no_exact_permission")
            }
        } catch (e: Exception) {
            // SecurityException 双保险：授权在两次调用间被撤销等竞态
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
            DiagLog.log(DiagEvent.HEARTBEAT_INEXACT, e.javaClass.simpleName)
        }
    }
}

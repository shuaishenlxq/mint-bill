package com.xl.bill.mint.util

import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 保活/拉活统一入口（App 不拉起，仅拉起记账服务）：
 * 1. 前台服务（常驻通知 + START_STICKY）；
 * 2. AlarmManager 心跳（setAndAllowWhileIdle 15 分钟）；
 * 3. WorkManager 周期任务（存活检查补充）；
 * 4. BootReceiver 开机拉活。
 *
 * 诚实边界：Android 12+ 无法 100% 保活——
 * 用户 Force Stop、开启超级省电、系统未加白名单、系统更新重置权限时，
 * 上述机制全部失效，需在 UI 上引导用户加入厂商白名单。
 */
object KeepAliveHelper {

    fun ensureRunning(context: Context) {
        startForegroundServiceSafely(context)
        HeartbeatScheduler.scheduleNext(context)
        enqueueHeartbeatWork(context)
    }

    private fun startForegroundServiceSafely(context: Context) {
        if (isForegroundServiceAlive()) return
        try {
            context.startForegroundService(Intent(context, _root_ide_package_.com.xl.bill.mint.service.BillForegroundService::class.java))
        } catch (_: Exception) {
            // ForegroundServiceStartNotAllowedException / SecurityException：
            // Android 12+ 后台启动受限时的正常失败，交给 START_STICKY 与 NLS 系统绑定兜底
        }
    }

    private fun enqueueHeartbeatWork(context: Context) {
        val request = PeriodicWorkRequestBuilder<com.xl.bill.mint.receiver.HeartbeatWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "heartbeat",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    /** 进程内标志：前台服务是否存活 */
    @Volatile
    private var fgAlive = false

    fun markForegroundAlive() {
        fgAlive = true
    }

    fun markForegroundDead() {
        fgAlive = false
    }

    fun isForegroundServiceAlive(): Boolean = fgAlive
}

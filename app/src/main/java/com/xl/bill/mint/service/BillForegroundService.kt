package com.xl.bill.mint.service

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.xl.bill.mint.util.DiagEvent
import com.xl.bill.mint.util.DiagLog
import com.xl.bill.mint.util.HeartbeatScheduler
import com.xl.bill.mint.util.KeepAliveHelper
import com.xl.bill.mint.util.NotificationHelper

/**
 * 记账守护前台服务：常驻低优先级通知 + START_STICKY + stopWithTask=false。
 *
 * 拉活链路：
 * - 被系统清理内存 → START_STICKY 重建；
 * - 最近任务上滑（stopWithTask=false 服务不随任务停；MIUI 强杀进程场景）
 *   → onTaskRemoved 通过 10s 精确闹钟走豁免链重建；
 * - 进程被杀 → AlarmManager 心跳 / WorkManager / 开机广播 三级拉活（KeepAliveHelper）。
 *
 * 用户「强行停止」后不再拉起（尊重用户）。
 */
class BillForegroundService : android.app.Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        KeepAliveHelper.markForegroundAlive(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        KeepAliveHelper.markForegroundAlive(this)
        val notification: Notification = NotificationHelper.buildPersistentNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.FGS_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.FGS_ID, notification)
        }
        DiagLog.log(DiagEvent.FGS_START_OK)
        return START_STICKY
    }

    /**
     * 最近任务上滑：不直接 startForegroundService（仍属后台上下文，必撞限制），
     * 排一个 10s 后的（精确）闹钟，由心跳 Receiver 走豁免链重建。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        DiagLog.log(DiagEvent.FGS_TASK_REMOVED)
        HeartbeatScheduler.scheduleSoon(this, 10_000L)
    }

    /** targetSdk 35：dataSync 前台服务 24h 内 6h 运行预算（API 34 回调签名，API 35 已废弃但仍会回调） */
    @Suppress("DEPRECATION")
    override fun onTimeout(startId: Int) {
        onBudgetTimeout()
    }

    /** API 35+ 回调签名 */
    override fun onTimeout(startId: Int, fgsType: Int) {
        onBudgetTimeout()
    }

    private fun onBudgetTimeout() {
        DiagLog.log(DiagEvent.FGS_TIMEOUT)
        // 预算耗尽立即重启会再撞预算：优雅停服，交心跳闹钟下一轮拉起；
        // 期间 NLS 系统绑定仍是记账主数据源（FGS 只是心跳载体）。
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        KeepAliveHelper.markForegroundDead(this)
        // 被系统回收后由 START_STICKY 重建；被用户手动停止则不再拉起（尊重用户）
    }
}

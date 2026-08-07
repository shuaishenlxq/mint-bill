package com.xl.bill.mint.service

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * 记账守护前台服务：常驻低优先级通知 + START_STICKY，
 * 在被系统清理内存时由系统尝试自动重建（被用户 Force Stop 除外）。
 *
 * 注意：App 被清后台时本服务尽量保持存活；若进程被杀，由
 * START_STICKY / AlarmManager 心跳 / 开机广播 三级拉活，详见 KeepAliveHelper。
 */
class BillForegroundService : android.app.Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.markForegroundAlive()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.markForegroundAlive()
        val notification: Notification = _root_ide_package_.com.xl.bill.mint.util.NotificationHelper.buildPersistentNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                _root_ide_package_.com.xl.bill.mint.util.NotificationHelper.FGS_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(_root_ide_package_.com.xl.bill.mint.util.NotificationHelper.FGS_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.markForegroundDead()
        // 被系统回收后由 START_STICKY 重建；被用户手动停止则不再拉起（尊重用户）
    }
}

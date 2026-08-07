package com.xl.bill.mint.util

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xl.bill.mint.R

/**
 * 通知构建与发送。
 */
object NotificationHelper {

    const val CHANNEL_SERVICE = "bill_service"
    const val CHANNEL_GUIDE = "bill_guide"
    const val FGS_ID = 1001
    const val GUIDE_ID = 2001

    /** 补备注提醒通知 id 基数（与 FGS_ID/GUIDE_ID 错开），实际 id = NOTE_ID_BASE + 交易 id */
    const val NOTE_ID_BASE = 3000

    /** 通知携带的目标交易 id（点击直达对应账单详情弹窗） */
    const val EXTRA_NOTE_TX_ID = "note_tx_id"

    /** 前台服务常驻通知：低调（IMPORTANCE_LOW / 静音 / 不可滑动关闭） */
    fun buildPersistentNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_bill)
            .setContentTitle(context.getString(R.string.notification_fgs_title))
            .setContentText(context.getString(R.string.notification_fgs_text))
            .setContentIntent(mainPendingIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build()

    /** 引导类通知（如通知使用权被关闭时提醒） */
    fun buildGuideNotification(context: Context, title: String, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_GUIDE)
            .setSmallIcon(R.drawable.ic_stat_bill)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(mainPendingIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    fun mainPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, _root_ide_package_.com.xl.bill.mint.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 携带目标交易 id 的跳转 PendingIntent（通知点击 → 打开该账单详情弹窗） */
    fun notePendingIntent(context: Context, txId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            txId.toInt(),
            Intent(context, _root_ide_package_.com.xl.bill.mint.MainActivity::class.java)
                .putExtra(EXTRA_NOTE_TX_ID, txId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 发送引导通知（Android 13+ 需 POST_NOTIFICATIONS 权限，无权限时静默忽略） */
    fun notifyGuide(context: Context, title: String, text: String) {
        try {
            NotificationManagerCompat.from(context)
                .notify(GUIDE_ID, buildGuideNotification(context, title, text))
        } catch (_: SecurityException) {
            // 用户未授予通知权限，忽略
        }
    }

    /** 自动记账后提醒用户补充备注（每笔独立通知 id，点击直达该账单详情） */
    fun notifyNoteReminder(context: Context, txId: Long, title: String, text: String) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_GUIDE)
                .setSmallIcon(R.drawable.ic_stat_bill)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(notePendingIntent(context, txId))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context)
                .notify(NOTE_ID_BASE + txId.toInt(), notification)
        } catch (_: SecurityException) {
            // 用户未授予通知权限，忽略
        }
    }
}

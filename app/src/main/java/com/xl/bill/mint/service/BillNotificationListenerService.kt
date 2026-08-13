package com.xl.bill.mint.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.StatusBarNotification
import android.util.Log
import com.xl.bill.mint.parser.PaymentApps
import com.xl.bill.mint.util.DiagEvent
import com.xl.bill.mint.util.DiagLog
import com.xl.bill.mint.util.KeepAliveHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 通知监听服务：自动记账的【主数据源】。
 *
 * 只能由系统绑定，App 无法自行启动——这反而保证了可靠性：
 * 只要用户开启「通知使用权」，即使 App 进程被清理，系统也会在
 * 新通知到来时自动拉起本服务完成记账。
 *
 * 仅处理白名单（支付宝/微信/银行）通知，其余通知直接忽略，不解析不落库。
 *
 * 文本提取覆盖 title/text/bigText/subText/textLines 全字段 + MessagingStyle 消息体：
 * 微信「支付凭证」类通知的金额常位于 BIG_TEXT 或 TEXT_LINES（展开式通知），
 * 只取 EXTRA_TEXT 会漏掉金额导致无法记账。
 *
 * 断连自救：onListenerDisconnected 记录持久化标记 + requestRebind；
 * 心跳/进程拉起时由 [KeepAliveHelper.rebindNotificationListenerIfNeeded] 执行组件 toggle 重绑。
 */
class BillNotificationListenerService : android.service.notification.NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName
        if (!PaymentApps.isSupported(pkg)) return

        val (title, text) = extractTitleAndText(notification)
        if (title == null && text == null) {
            DiagLog.log(DiagEvent.NLS_DROPPED_EMPTY, "pkg=$pkg")
            return
        }

        Log.d(TAG, "收到通知 pkg=$pkg title=$title text=$text")
        DiagLog.log(DiagEvent.NLS_RECEIVED, "pkg=$pkg title=${title?.take(30)} text=${text?.take(30)}")

        val time = notification.`when`.takeIf { it > 0 } ?: System.currentTimeMillis()
        scope.launch {
            BillRecordPipeline.process(
                pkg = pkg,
                title = title,
                text = text,
                occurredAt = time,
                notificationKey = sbn.key
            )
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "通知监听服务已连接（系统绑定成功）")
        KeepAliveHelper.markNlsConnected(this)
        DiagLog.log(DiagEvent.NLS_CONNECTED)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // ROM 杀进程/收权导致的断连：记录持久化标记（进程死了标记不丢），
        // 并立即请求系统重绑；心跳与进程拉起时再兜底一次组件 toggle
        Log.d(TAG, "通知监听服务已断开（系统解绑）")
        KeepAliveHelper.markNlsDisconnected(this)
        DiagLog.log(DiagEvent.NLS_DISCONNECTED)
        runCatching {
            requestRebind(ComponentName(this, BillNotificationListenerService::class.java))
        }
    }

    private fun extractTitleAndText(notification: Notification): Pair<String?, String?> {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()

        val parts = LinkedHashSet<String>()
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
            line?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        }
        // MessagingStyle 通知：金额可能在消息体里（EXTRA_TEXT 为空时兜底）。
        // 注：android-35 stub 未含 extractMessagingStyleFromNotification，直接读
        // EXTRA_MESSAGES Parcelable 数组并转型 Message 取 text。
        @Suppress("DEPRECATION")
        runCatching {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { p ->
                (p as? Notification.MessagingStyle.Message)?.text?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
            }
        }

        return title?.takeIf { it.isNotEmpty() } to parts.joinToString("\n").ifEmpty { null }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "MintBill"
    }
}

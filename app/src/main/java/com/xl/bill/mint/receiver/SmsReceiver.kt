package com.xl.bill.mint.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 短信记账数据源：监听系统短信广播（SMS_RECEIVED），
 * 正文含金额+收支词的短信交给 [com.xl.bill.mint.service.BillRecordPipeline.processSms] 解析入库。
 *
 * 说明：
 * - goAsync + 独立 IO 协程，广播结束后进程保持到解析完成（库操作很快，可接受）。
 * - 长短信分段由 [Telephony.Sms.Intents.getMessagesFromIntent] 按 indexOf 排序返回，按序拼接正文。
 * - 到达时间取 PDU timestampMillis（跨重放稳定），作为 notificationKey 的一部分（DB UNIQUE 兜底去重）。
 */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!_root_ide_package_.com.xl.bill.mint.util.PermissionChecker.hasSmsPermission(context)) {
            // RECEIVE_SMS 未授权：整条短信链路静默丢弃——留痕（设置页可引导授权）
            _root_ide_package_.com.xl.bill.mint.util.DiagLog.log(
                _root_ide_package_.com.xl.bill.mint.util.DiagEvent.SMS_NO_PERMISSION
            )
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val body = buildString {
            messages.forEach { msg ->
                msg.displayMessageBody?.let { append(it) }
            }
        }.trim()
        if (body.isEmpty()) return

        val sender = messages.firstOrNull()?.originatingAddress
        val receivedAt = messages.firstOrNull()?.timestampMillis
            ?: System.currentTimeMillis()

        _root_ide_package_.com.xl.bill.mint.util.DiagLog.log(
            _root_ide_package_.com.xl.bill.mint.util.DiagEvent.SMS_RECEIVED,
            "sender=$sender body=${body.take(30)}"
        )

        val pendingResult = goAsync()
        scope.launch {
            try {
                _root_ide_package_.com.xl.bill.mint.service.BillRecordPipeline.processSms(sender, body, receivedAt)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

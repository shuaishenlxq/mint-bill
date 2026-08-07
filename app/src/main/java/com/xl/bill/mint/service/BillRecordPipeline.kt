package com.xl.bill.mint.service

import android.content.Context
import android.util.Log
import com.xl.bill.mint.R
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自动记账管线：通知监听、无障碍、短信 三条数据源的统一入口。
 *
 * 去重策略（多层）：
 * 1. 跨来源指纹去重：同一笔交易在 2 分钟内只会被记一次（通知 vs 无障碍互斥）；
 * 2. 跨来源短窗去重：微信/银行双通知（3s）与 App 通知 vs 短信（60s）双报只记一条；
 * 3. 通知 key 去重：同一通知/短信重复推送（如 ROM 重放）被拦截；
 * 4. DB 唯一索引兜底（notificationKey UNIQUE）。
 *
 * 日志约定（TAG=MintBill）：便于真机调试时定位「收到通知→解析→落库」链路。
 */
object BillRecordPipeline {

    private const val TAG = "MintBill"

    private val dedup = _root_ide_package_.com.xl.bill.mint.parser.Deduplicator(windowMs = 120_000L)

    /**
     * 跨来源去重（3 秒窗口）：同一笔支付被微信与银行 App 同时通知时只记一条。
     * 与 dedup（base 指纹 120s）并存：base 指纹拦同渠道重复，跨来源拦跨渠道重复。
     */
    private val crossDedup = _root_ide_package_.com.xl.bill.mint.parser.CrossSourceDedup(windowMs = 3_000L)

    /**
     * 短信专用跨来源去重（60 秒窗口）：App 通知先记 → 短信晚到（银行短信常有延迟）的
     * 同一笔支付被双记时拦截。短信为独立 Channel（Channel.SMS），base 指纹与通知不同源不互拦。
     * 已知限制：短信先到、App 通知后到且间隔 > 60s 时反序不拦，可能双记（频率极低，接受）。
     */
    private val crossDedupSms = _root_ide_package_.com.xl.bill.mint.parser.CrossSourceDedup(windowMs = 60_000L)

    /** 通知场景（NLS 主数据源） */
    suspend fun process(
        pkg: String,
        title: String?,
        text: String?,
        occurredAt: Long,
        notificationKey: String?
    ) {
        val channel = _root_ide_package_.com.xl.bill.mint.parser.PaymentApps.channelOf(pkg) ?: return
        if (!_root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository.autoRecordEnabled.first()) return
        if (!_root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository.isChannelEnabled(channel)) return

        val parsed = _root_ide_package_.com.xl.bill.mint.parser.BillParseEngine.parse(pkg, title, text, occurredAt, notificationKey)
        if (parsed == null) {
            Log.d(TAG, "解析失败(可能无金额/非交易通知): $pkg | $title | $text")
            maybeHintManualTransfer(_root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appContext, title, text)
            return
        }
        recordIfValid(parsed, pkg, title, text, notificationKey)
    }

    /** 无障碍场景（兜底数据源：转账/红包详情页读取） */
    suspend fun processAccessibility(
        pkg: String,
        text: String?,
        occurredAt: Long,
        notificationKey: String?
    ) {
        val channel = _root_ide_package_.com.xl.bill.mint.parser.PaymentApps.channelOf(pkg) ?: return
        if (!_root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository.autoRecordEnabled.first()) return
        if (!_root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository.isChannelEnabled(channel)) return

        val parsed = _root_ide_package_.com.xl.bill.mint.parser.BillParseEngine.parseAccessibilityScene(pkg, text, occurredAt, notificationKey)
        if (parsed == null) return

        recordIfValid(parsed, pkg, null, text, notificationKey)
    }

    /**
     * 短信场景（BroadcastReceiver 数据源）：任意短信正文含金额+收支词即解析入库。
     */
    suspend fun processSms(
        sender: String?,
        body: String,
        receivedAt: Long
    ) {
        if (!_root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository.autoRecordEnabled.first()) return
        if (!_root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository.isChannelEnabled(_root_ide_package_.com.xl.bill.mint.parser.Channel.SMS)) return

        val parsed = _root_ide_package_.com.xl.bill.mint.parser.BillParseEngine.parseSms(sender, body, receivedAt)
        if (parsed == null) {
            Log.d(TAG, "短信解析失败(无金额/非交易短信/被拦截): sender=$sender body=${body.take(60)}")
            return
        }
        recordIfValid(parsed, "sms", null, body, parsed.notificationKey)
    }

    private suspend fun recordIfValid(
        parsed: com.xl.bill.mint.parser.ParsedBill,
        pkg: String,
        title: String?,
        text: String?,
        notificationKey: String?
    ): Long {
        Log.d(
            TAG,
            "解析成功: ${parsed.channel.name} ${if (parsed.type == 0) "支出" else "收入"} " +
                "¥${parsed.amount / 100.0} 商户=${parsed.merchant}"
        )

        // 跨来源指纹：同渠道+同金额+同方向+同商户+同分钟（通知/无障碍/短信链路共用）
        val fingerprint = _root_ide_package_.com.xl.bill.mint.parser.DedupFingerprint.base(
            parsed.channel, parsed.amount, parsed.type, parsed.merchant, parsed.occurredAt
        )
        if (!dedup.tryAdd(fingerprint)) {
            Log.d(TAG, "重复交易已拦截(指纹): $fingerprint")
            return 0L
        }

        // 跨来源去重：微信「支付凭证」+ 银行「扣款提醒」双通知（Channel/商户必然错位）只记一条
        if (!crossDedup.tryAdd(parsed.channel, parsed.amount, parsed.type, parsed.merchant)) {
            Log.d(TAG, "重复交易已拦截(跨来源): ${parsed.channel.name} ${parsed.amount}分 type=${parsed.type}")
            return 0L
        }

        // 短信专用跨来源去重：App 通知先记 → 银行短信晚到（延迟常超 3s，独立 60s 窗口）
        if (parsed.channel == _root_ide_package_.com.xl.bill.mint.parser.Channel.SMS &&
            !crossDedupSms.tryAdd(parsed.channel, parsed.amount, parsed.type, parsed.merchant)
        ) {
            Log.d(TAG, "重复交易已拦截(短信跨来源): ${parsed.amount}分 type=${parsed.type}")
            return 0L
        }

        // 通知 key 去重：优先用调用方传入的 key，否则回落解析结果自带 key
        val effectiveKey = notificationKey ?: parsed.notificationKey
        if (effectiveKey != null && !dedup.tryAdd("key:$effectiveKey")) {
            Log.d(TAG, "重复通知已拦截(key): $effectiveKey")
            return 0L
        }

        val categoryId = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.categoryMatcher.resolveCategoryId(parsed.type, title, text)
        val accountId = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.transactionRepository.resolveAccountId(pkg)
        val newId = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.transactionRepository.insert(
            parsed.copy(notificationKey = effectiveKey), categoryId, accountId
        )
        Log.d(TAG, "已落库: 金额=${parsed.amount}分 category=$categoryId account=$accountId id=$newId")

        // 新插入成功（非重复）→ 提醒用户补充备注，点击直达该账单详情
        if (newId > 0) {
            val ctx = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appContext
            _root_ide_package_.com.xl.bill.mint.util.NotificationHelper.notifyNoteReminder(
                ctx,
                newId,
                ctx.getString(
                    R.string.notification_note_remind_title,
                    _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(parsed.amount)
                ),
                ctx.getString(R.string.notification_note_remind_text)
            )
        }
        return newId
    }

    /**
     * 收到疑似转账但无金额的通知时，每日最多一条引导提示：
     * 让用户点开微信转账详情页，由无障碍兜底自动记账。
     */
    private suspend fun maybeHintManualTransfer(context: Context, title: String?, text: String?) {
        val combined = (title.orEmpty() + text.orEmpty())
        val isTransferLike = TRANSFER_HINT_WORDS.any { combined.contains(it) }
        if (!isTransferLike) return

        val settings = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository
        if (!settings.transferHintEnabled.first()) return

        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (settings.getLastTransferHintDate() == today) return
        settings.setLastTransferHintDate(today)

        _root_ide_package_.com.xl.bill.mint.util.NotificationHelper.notifyGuide(
            context,
            context.getString(R.string.notification_transfer_hint_title),
            context.getString(R.string.notification_transfer_hint_text)
        )
    }

    private val TRANSFER_HINT_WORDS = listOf("转账", "红包", "收款")
}

package com.xl.bill.mint.service

import android.content.Context
import android.util.Log
import com.xl.bill.mint.R
import com.xl.bill.mint.di.ServiceLocator
import com.xl.bill.mint.parser.BillParseEngine
import com.xl.bill.mint.parser.BillSource
import com.xl.bill.mint.parser.Channel
import com.xl.bill.mint.parser.CrossDecision
import com.xl.bill.mint.parser.CrossSourceResolver
import com.xl.bill.mint.parser.DedupFingerprint
import com.xl.bill.mint.parser.ExistingTx
import com.xl.bill.mint.parser.FingerprintDeduper
import com.xl.bill.mint.parser.KeyDeduper
import com.xl.bill.mint.parser.ParsedBill
import com.xl.bill.mint.parser.PaymentApps
import com.xl.bill.mint.parser.toDbString
import com.xl.bill.mint.util.DiagEvent
import com.xl.bill.mint.util.DiagLog
import com.xl.bill.mint.util.MoneyFormatter
import com.xl.bill.mint.util.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * 自动记账管线：通知监听、无障碍、短信 三条数据源的统一入口。
 *
 * 去重策略（多层）：
 * 1. 通知 key 去重（[KeyDeduper]，内存 120s）：同 key 且同金额同 60s 窗 = 真重放才拦；
 *    同 key 金额/时间不同 = 通知 id 复用（新交易）放行——否则多笔支付全被 key 维度误杀；
 * 2. 异源模糊指纹去重（[FingerprintDeduper]，内存 120s）：仅「通知 vs 无障碍」
 *    对同一笔的双通道互拦（渠道+金额+方向+商户+分钟），同源放行；
 * 3. DB 跨源优先级判定（[CrossSourceResolver]）：同一笔支付多渠道双报时，
 *    按 短信 > 银行App > 微信/支付宝 保留——低优先级先到则高优先级后来原地升级，
 *    高优先级先到则低优先级后来丢弃；**同渠道候选跳过**（不同 key = 不同交易）；
 *    进程被杀后判定依旧一致（不依赖内存）；
 * 4. DB 唯一索引兜底（notificationKey UNIQUE）：冲突时区分「真重放」与「key 复用」——
 *    真重放丢弃；key 复用换退化 key 重插保记（不丢真实交易）。
 *
 * 全链路埋点 [DiagLog]（TAG=MintBill）：每个拦截/升级/落库判定点均可追溯。
 */
object BillRecordPipeline {

    private const val TAG = "MintBill"

    /**
     * 通知 key 去重（内存 120s）：同 key 且同金额同 60s 窗才算重放；
     * 同 key 但金额/时间不同 = 通知 id 复用（新交易），放行——否则多笔支付全被 key 维度误杀。
     */
    private val keyDeduper = KeyDeduper(windowMs = 120_000L)

    /** 异源模糊指纹去重：仅「通知 vs 无障碍」互拦；同源放行真实多笔 */
    private val fingerprintDeduper = FingerprintDeduper(windowMs = 120_000L)

    /**
     * 跨源判定 + 落库段的协程互斥锁：
     * 微信/银行/短信通知几乎同时到达时（NLS 每条独立协程），并发执行
     * 「查候选 → decide → 插入」会互相查不到对方刚插入的记录 → 双记。
     * 用 Mutex 串行化该段，保证读-判-写原子；DB 仍是跨源去重的真源。
     */
    private val crossSourceMutex = Mutex()

    /** 通知场景（NLS 主数据源） */
    suspend fun process(
        pkg: String,
        title: String?,
        text: String?,
        occurredAt: Long,
        notificationKey: String?
    ) {
        val channel = PaymentApps.channelOf(pkg) ?: return
        if (!ServiceLocator.settingsRepository.autoRecordEnabled.first()) return
        if (!ServiceLocator.settingsRepository.isChannelEnabled(channel)) return

        val blockedWords = ServiceLocator.settingsRepository.getAdBlockWords()
        val customGroups = ServiceLocator.settingsRepository.getCustomMatchGroups()
        val outcome = BillParseEngine.parseWithReason(pkg, title, text, occurredAt, notificationKey, blockedWords, customGroups)
        val parsed = outcome.bill
        if (parsed == null) {
            Log.d(TAG, "解析失败(${outcome.reason}): $pkg | $title | $text")
            DiagLog.log(
                DiagEvent.PARSE_REJECTED,
                "${outcome.reason} pkg=$pkg title=${title?.take(30)} text=${text?.take(30)}"
            )
            maybeHintManualTransfer(ServiceLocator.appContext, title, text)
            return
        }
        recordIfValid(parsed, pkg, title, text, notificationKey, BillSource.NOTIFICATION)
    }

    /** 无障碍场景（兜底数据源：转账/红包详情页读取） */
    suspend fun processAccessibility(
        pkg: String,
        text: String?,
        occurredAt: Long,
        notificationKey: String?
    ) {
        val channel = PaymentApps.channelOf(pkg) ?: return
        if (!ServiceLocator.settingsRepository.autoRecordEnabled.first()) return
        if (!ServiceLocator.settingsRepository.isChannelEnabled(channel)) return

        val outcome = BillParseEngine.parseAccessibilityWithReason(pkg, text, occurredAt, notificationKey)
        val parsed = outcome.bill
        if (parsed == null) {
            DiagLog.log(DiagEvent.PARSE_REJECTED, "${outcome.reason} pkg=$pkg scene=acc")
            return
        }

        recordIfValid(parsed, pkg, null, text, notificationKey, BillSource.ACCESSIBILITY)
    }

    /**
     * 短信场景（BroadcastReceiver 数据源）：任意短信正文含金额+收支词即解析入库。
     */
    suspend fun processSms(
        sender: String?,
        body: String,
        receivedAt: Long
    ) {
        if (!ServiceLocator.settingsRepository.autoRecordEnabled.first()) return
        if (!ServiceLocator.settingsRepository.isChannelEnabled(Channel.SMS)) return

        val blockedWords = ServiceLocator.settingsRepository.getAdBlockWords()
        val customGroups = ServiceLocator.settingsRepository.getCustomMatchGroups()
        val outcome = BillParseEngine.parseSmsWithReason(sender, body, receivedAt, blockedWords, customGroups)
        val parsed = outcome.bill
        if (parsed == null) {
            Log.d(TAG, "短信解析失败(${outcome.reason}): sender=$sender body=${body.take(60)}")
            DiagLog.log(DiagEvent.PARSE_REJECTED, "${outcome.reason} sms sender=$sender body=${body.take(30)}")
            return
        }
        recordIfValid(parsed, "sms", null, body, parsed.notificationKey, BillSource.SMS)
    }

    private suspend fun recordIfValid(
        parsed: ParsedBill,
        pkg: String,
        title: String?,
        text: String?,
        notificationKey: String?,
        source: BillSource
    ): Long {
        Log.d(
            TAG,
            "解析成功: ${parsed.channel.name} ${if (parsed.type == 0) "支出" else "收入"} " +
                "¥${parsed.amount / 100.0} 商户=${parsed.merchant} 来源=$source"
        )

        // 第1层：通知 key 去重（同 key 且同金额同 60s 窗 = 真重放才拦；
        // 同 key 金额不同/时间差大 = 通知 id 复用 → 新交易放行）
        val effectiveKey = notificationKey ?: parsed.notificationKey
        if (effectiveKey != null && !keyDeduper.tryAdd(effectiveKey, parsed.amount, parsed.occurredAt)) {
            Log.d(TAG, "重复通知已拦截(key): $effectiveKey ¥${parsed.amount} @${parsed.occurredAt}")
            DiagLog.log(
                DiagEvent.DEDUP_KEY,
                "$effectiveKey 重放 ¥${MoneyFormatter.yuan(parsed.amount)} @${parsed.occurredAt}"
            )
            return 0L
        }

        // 第2层：异源模糊指纹去重（渠道+金额+方向+商户+分钟）。
        // 仅「通知 vs 无障碍」互拦（同一笔双通道）；其余组合放行（同笔合并交给 DB 跨源层）
        val fingerprint = DedupFingerprint.base(
            parsed.channel, parsed.amount, parsed.type, parsed.merchant, parsed.occurredAt
        )
        if (!fingerprintDeduper.tryAdd(fingerprint, source)) {
            Log.d(TAG, "重复交易已拦截(异源指纹): $fingerprint source=$source")
            DiagLog.log(DiagEvent.DEDUP_FINGERPRINT, "$fingerprint src=$source".take(60))
            return 0L
        }

        val sourceDb = source.toDbString()

        // 锁外预计算只读依赖（缩小持锁时间）
        val defaults = ServiceLocator.settingsRepository.getCategoryDefaults()
        val categoryId = ServiceLocator.categoryMatcher.resolveCategoryId(parsed.type, title, text, defaults)
        val accountId = ServiceLocator.transactionRepository.resolveAccountId(pkg)
        val smsWindowMs = ServiceLocator.settingsRepository.getCrossSourceSmsWindowMs()
        val queryWindowMs = max(60_000L, smsWindowMs)

        // 第3层：DB 跨源判定 + 落库/升级 —— Mutex 串行化（读-判-写原子，防并发双记）
        return crossSourceMutex.withLock {
            val candidates = ServiceLocator.transactionRepository
                .findCrossSourceCandidates(parsed.amount, parsed.type, parsed.occurredAt, queryWindowMs)
                .mapNotNull { e ->
                    val ch = Channel.entries.firstOrNull { it.name.equals(e.channel, ignoreCase = true) }
                        ?: return@mapNotNull null
                    ExistingTx(e.id, ch, e.source, e.merchant, e.occurredAt)
                }
            val cross = CrossSourceResolver.decide(parsed, sourceDb, candidates, smsWindowMs)
            when (cross.decision) {
                CrossDecision.DROP_LOWER_PRIORITY -> {
                    Log.d(TAG, "重复交易已拦截(跨源低优先级): ${parsed.channel} 让位 ${cross.target?.channel}")
                    DiagLog.log(
                        DiagEvent.CROSS_DROP,
                        "${parsed.channel} 让位 ${cross.target?.channel} ¥${MoneyFormatter.yuan(parsed.amount)}"
                    )
                    0L
                }
                CrossDecision.UPGRADE_EXISTING -> {
                    val target = cross.target
                    if (target != null) {
                        ServiceLocator.transactionRepository.upgradeChannelSource(
                            id = target.id,
                            parsed = parsed,
                            existingMerchant = target.merchant,
                            accountId = accountId,
                            effectiveKey = effectiveKey,
                            newSource = sourceDb
                        )
                        Log.d(TAG, "跨源升级: ${target.channel}→${parsed.channel} id=${target.id}")
                        DiagLog.log(
                            DiagEvent.CROSS_UPGRADE,
                            "${target.channel}→${parsed.channel} id=${target.id} ¥${MoneyFormatter.yuan(parsed.amount)}"
                        )
                    }
                    // 原地升级：id 不变（补备注通知深链仍有效），不重发提醒、不重算分类
                    target?.id ?: 0L
                }
                CrossDecision.INSERT_NEW -> {
                    val newId = ServiceLocator.transactionRepository.insert(
                        parsed.copy(notificationKey = effectiveKey), categoryId, accountId, sourceDb
                    )
                    Log.d(TAG, "已落库: 金额=${parsed.amount}分 category=$categoryId account=$accountId id=$newId")

                    if (newId <= 0) {
                        // notificationKey UNIQUE 冲突：区分「真重放」与「key 复用（新交易）」
                        val existing = effectiveKey?.let {
                            ServiceLocator.transactionRepository.getByNotificationKey(it)
                        }
                        val sameTx = existing != null &&
                            existing.amount == parsed.amount &&
                            existing.type == parsed.type &&
                            abs(existing.occurredAt - parsed.occurredAt) <= KeyDeduper.SAME_TX_TIME_TOLERANCE_MS
                        if (sameTx) {
                            // 真重放（同 key 同金额同时刻）→ 丢弃
                            DiagLog.log(DiagEvent.DB_KEY_CONFLICT, "真重放 key=$effectiveKey")
                            0L
                        } else {
                            // key 复用（如通知 id 固定）：不同交易 → 换退化 key 重插保记
                            val fallbackKey = "auto-${System.currentTimeMillis()}-${Random.nextLong()}"
                            val retryId = ServiceLocator.transactionRepository.insert(
                                parsed.copy(notificationKey = fallbackKey), categoryId, accountId, sourceDb
                            )
                            Log.d(TAG, "notificationKey 冲突降级插入: $effectiveKey → $fallbackKey id=$retryId")
                            DiagLog.log(
                                DiagEvent.DB_KEY_CONFLICT,
                                "key复用降级 $effectiveKey → $fallbackKey 金额=${parsed.amount} id=$retryId"
                            )
                            if (retryId <= 0) {
                                0L
                            } else {
                                notifyRecorded(parsed, retryId)
                                retryId
                            }
                        }
                    } else {
                        notifyRecorded(parsed, newId)
                        newId
                    }
                }
            }
        }
    }

    /** 新插入成功（非重复）→ 提醒用户补充备注，点击直达该账单详情 */
    private fun notifyRecorded(parsed: ParsedBill, id: Long) {
        DiagLog.log(
            DiagEvent.RECORDED,
            "${parsed.channel} ¥${MoneyFormatter.yuan(parsed.amount)} id=$id merchant=${parsed.merchant}"
        )
        val ctx = ServiceLocator.appContext
        NotificationHelper.notifyNoteReminder(
            ctx,
            id,
            ctx.getString(
                R.string.notification_note_remind_title,
                MoneyFormatter.yuan(parsed.amount)
            ),
            ctx.getString(R.string.notification_note_remind_text)
        )
    }

    /**
     * 收到疑似转账但无金额的通知时，每日最多一条引导提示：
     * 让用户点开微信转账详情页，由无障碍兜底自动记账。
     */
    private suspend fun maybeHintManualTransfer(context: Context, title: String?, text: String?) {
        val combined = (title.orEmpty() + text.orEmpty())
        val isTransferLike = TRANSFER_HINT_WORDS.any { combined.contains(it) }
        if (!isTransferLike) return

        val settings = ServiceLocator.settingsRepository
        if (!settings.transferHintEnabled.first()) return

        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (settings.getLastTransferHintDate() == today) return
        settings.setLastTransferHintDate(today)

        NotificationHelper.notifyGuide(
            context,
            context.getString(R.string.notification_transfer_hint_title),
            context.getString(R.string.notification_transfer_hint_text)
        )
    }

    private val TRANSFER_HINT_WORDS = listOf("转账", "红包", "收款")
}

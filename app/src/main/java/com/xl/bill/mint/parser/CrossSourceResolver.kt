package com.xl.bill.mint.parser

import kotlin.math.abs

/** 跨源判定结论 */
enum class CrossDecision {
    /** 无同笔候选 → 正常插入 */
    INSERT_NEW,

    /** 窗口内已有更高或同优先级来源 → 丢弃本条（先到先得） */
    DROP_LOWER_PRIORITY,

    /** 本条来源优先级更高 → 原地升级已存在记录（保 id，替换渠道/原文/商户/通知键） */
    UPGRADE_EXISTING
}

/** 已落库的候选账单（跨源判定输入）；source 来自 DB source 列 */
data class ExistingTx(
    val id: Long,
    val channel: Channel,
    val source: String,
    val merchant: String?,
    val occurredAt: Long
)

data class CrossResult(
    val decision: CrossDecision,
    val target: ExistingTx? = null
)

/**
 * 跨来源同笔判定（纯 Kotlin，JVM 可单测）。
 *
 * 同一笔支付常被多渠道双报（微信通知 + 银行 App 通知 + 银行短信），
 * 最终保留优先级：**银行短信(3) > 银行App通知(2) > 支付宝/微信(1)**。
 *
 * - 低优先级先到、高优先级后到 → [CrossDecision.UPGRADE_EXISTING]（原地升级，id 不变）；
 * - 高优先级先到、低优先级后到 → [CrossDecision.DROP_LOWER_PRIORITY]；
 * - **同渠道且同来源**候选 → 视为不同交易跳过（两笔同金额真实支付不误杀）；
 * - **同渠道异来源**（银行App通知 vs 银行短信）→ 参与判定（同一笔合并，短信胜）；
 * - 窗口：任一侧为短信来源 → 短信窗口（默认 5s，设置页可调），否则 3s；
 * - 商户豁免：双方商户均非空且不同 → 视为不同交易放行。
 *
 * 替代原内存版 CrossSourceDedup：判定基于 DB 已落库记录，进程被杀后依然一致；
 * 并发安全由上层（BillRecordPipeline 的 Mutex 串行化读-判-写）保证。
 */
object CrossSourceResolver {

    /** DB source 列取值 */
    const val SOURCE_NOTIFICATION = "notification"
    const val SOURCE_ACCESSIBILITY = "accessibility"
    const val SOURCE_SMS = "sms"
    const val SOURCE_MANUAL = "manual"
    const val SOURCE_IMPORT = "import"

    /** 默认短信窗口：5s（短信延迟通常 <5s；可调大防延迟双记，调小防误合并） */
    const val DEFAULT_SMS_WINDOW_MS = 5_000L

    /** App↔App 窗口：3s（微信/支付宝/银行 App 通知几乎同时到达） */
    const val WINDOW_APP_MS = 3_000L

    /** 来源优先级：银行短信 > 银行App通知 > 支付宝/微信；非银行短信与 App 同级（1） */
    fun priority(channel: Channel, source: String): Int = when {
        source == SOURCE_SMS -> 3
        channel == Channel.BANK -> 2
        else -> 1
    }

    /** 任一侧为短信来源 → 短信窗口；否则 App 窗口 */
    fun windowFor(
        a: Channel,
        aSource: String,
        b: Channel,
        bSource: String,
        smsWindowMs: Long = DEFAULT_SMS_WINDOW_MS
    ): Long = if (aSource == SOURCE_SMS || bSource == SOURCE_SMS) smsWindowMs else WINDOW_APP_MS

    /**
     * 判定一条新解析账单该如何处理。
     *
     * - 同渠道**且同来源**候选 → 不同交易跳过（不同 key = 不同笔，1 分钟内多笔同金额不误杀）；
     * - 同渠道**异来源**（BANK 通知 vs BANK 短信）→ 参与跨源判定（同一笔，短信优先升级）；
     * - 跨渠道候选（微信 vs 银行 App vs 短信）→ 窗口内按优先级 UPGRADE/DROP。
     *
     * @param newSource 新账单来源（DB source 串，由上层从 BillSource 转换）
     * @param candidates 同金额+同方向+非手动+非导入的已落库记录（按 occurredAt DESC, id DESC 排序）
     */
    fun decide(
        newBill: ParsedBill,
        newSource: String,
        candidates: List<ExistingTx>,
        smsWindowMs: Long = DEFAULT_SMS_WINDOW_MS
    ): CrossResult {
        for (c in candidates) {
            // 同渠道且同来源 → 不同交易（真实多笔），跳过
            if (c.channel == newBill.channel && c.source == newSource) continue
            // 窗口过滤
            if (abs(newBill.occurredAt - c.occurredAt) >
                windowFor(newBill.channel, newSource, c.channel, c.source, smsWindowMs)
            ) {
                continue
            }
            // 商户豁免：双非空且不同 → 不同交易，看下一个候选
            if (newBill.merchant != null && c.merchant != null && newBill.merchant != c.merchant) continue
            // 同笔（跨渠道 / 同渠道异来源）：比优先级
            return if (priority(newBill.channel, newSource) > priority(c.channel, c.source)) {
                CrossResult(CrossDecision.UPGRADE_EXISTING, c)
            } else {
                CrossResult(CrossDecision.DROP_LOWER_PRIORITY, c)
            }
        }
        return CrossResult(CrossDecision.INSERT_NEW)
    }
}

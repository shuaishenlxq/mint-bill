package com.xl.bill.mint.parser

import java.util.LinkedHashMap

/**
 * 去重指纹构造（纯函数，通知链路与无障碍/短信链路共用，防止同一笔交易被多条链路双写）。
 */
object DedupFingerprint {

    /**
     * 跨来源基础指纹：渠道 + 金额 + 方向 + 商户 + 分钟。
     * 通知先记 → 短信/无障碍再读同笔被拦；反之亦然。
     */
    fun base(channel: Channel, amount: Long, type: Int, merchant: String?, occurredAt: Long): String =
        "${channel.name}|$amount|$type|$merchant|${occurredAt / 60_000}"
}

/**
 * 内存去重器：LRU + 时间窗口。
 * 用于通知与无障碍两条数据源对「同一笔交易」的二次防重（DB 唯一索引是第一道防线）。
 */
class Deduplicator(private val windowMs: Long = 120_000L) {

    private val map = LinkedHashMap<String, Long>()

    /** 若 key 已存在且未过期返回 false；否则记录并返回 true */
    @Synchronized
    fun tryAdd(key: String): Boolean {
        val now = System.currentTimeMillis()
        map.entries.removeAll { now - it.value > windowMs }
        if (map.containsKey(key)) return false
        map[key] = now
        return true
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}

/**
 * 通知 key 去重（内存 120s）：拦截「同一通知的重复投递」（ROM 重放/系统重发）。
 *
 * 关键：**仅当 key 相同且内容一致（金额相同、occurredAt 差 ≤ 60s）才判为重复**。
 * 原因：部分支付 App 用固定 notification id/tag 发「支付凭证」覆盖式通知，
 * 导致不同笔支付的通知 key（sbn.key）**完全相同**——若只看 key 不看内容，
 * 1 分钟内（甚至永远，叠加 DB 唯一索引）只有第一笔能记，金额不同的多笔也全漏。
 *
 * 语义：
 * - 同 key + 同金额 + 时间差 ≤ 60s → 真重放 → 拦截；
 * - 同 key 但金额不同或时间差 > 60s → key 被复用 = 新交易 → 放行并更新；
 * - 窗口过期（> windowMs 无同 key 记录）→ 放行。
 */
class KeyDeduper(private val windowMs: Long = 120_000L) {

    companion object {
        /** 同 key 下「同笔」的时间容差：occurredAt 差超过该值视为另一笔交易 */
        const val SAME_TX_TIME_TOLERANCE_MS = 60_000L
    }

    private data class Entry(val amount: Long, val occurredAt: Long, val ts: Long)

    private val map = HashMap<String, Entry>()

    /** @return true 放行（并记录）；false 拦截（同 key 同内容的重放） */
    @Synchronized
    fun tryAdd(key: String, amount: Long, occurredAt: Long): Boolean {
        val now = System.currentTimeMillis()
        val prev = map[key]
        if (prev != null && now - prev.ts <= windowMs) {
            val sameTx = prev.amount == amount &&
                kotlin.math.abs(prev.occurredAt - occurredAt) <= SAME_TX_TIME_TOLERANCE_MS
            if (sameTx) return false
            // 同 key 内容不同 → id 复用，新交易
        }
        map[key] = Entry(amount, occurredAt, now)
        return true
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}

/** 记账数据源类型：决定模糊指纹去重是否互拦（仅通知↔无障碍互拦，其余放行）。 */
enum class BillSource {
    /** 通知监听（NLS），notificationKey = 系统 sbn.key */
    NOTIFICATION,

    /** 无障碍读屏（兜底），notificationKey = acc-... */
    ACCESSIBILITY,

    /** 短信，notificationKey = sms-... */
    SMS
}

/** BillSource → DB source 列取值（与 CrossSourceResolver.SOURCE_* 常量一致） */
fun BillSource.toDbString(): String = when (this) {
    BillSource.NOTIFICATION -> CrossSourceResolver.SOURCE_NOTIFICATION
    BillSource.ACCESSIBILITY -> CrossSourceResolver.SOURCE_ACCESSIBILITY
    BillSource.SMS -> CrossSourceResolver.SOURCE_SMS
}

/**
 * 异源模糊指纹去重（内存 120s）。
 *
 * 用途：防「同一笔被同渠道的双通道重复记」——典型场景是微信支付通知先记、
 * 用户随后打开转账详情页由无障碍再读同一笔。此时两条记录 channel 相同、
 * key 必然不同（sbn.key vs acc-...），只能用「渠道|金额|方向|商户|分钟」模糊匹配。
 *
 * 关键约束：**仅 NOTIFICATION ↔ ACCESSIBILITY 互拦**。同源（通知 vs 通知、
 * 无障碍 vs 无障碍）一律放行——否则 1 分钟内多笔同金额真实支付会被误杀
 * （商户为 null 的微信扫码场景尤为常见）；**通知 vs 短信 / 无障碍 vs 短信
 * 也不互拦**——短信归银行后银行短信 channel=BANK 与银行 App 通知同渠道，
 * 若互拦会导致银行短信被内存指纹层误拦（同笔合并交给 DB 跨源层，
 * 那里按 (channel, source) 判定，短信优先）。
 *
 * 同 key 的重复投递由 [Deduplicator]（notificationKey）与 DB 唯一索引兜底。
 * 商户豁免天然生效：商户不同 → 指纹本身不同 → 不命中。
 */
class FingerprintDeduper(private val windowMs: Long = 120_000L) {

    private data class Entry(val source: BillSource, val ts: Long)

    private val map = HashMap<String, Entry>()

    /**
     * @return true 放行（并记录）；false 拦截（窗口内已有「通知↔无障碍」异源同指纹记录）
     */
    @Synchronized
    fun tryAdd(fingerprint: String, source: BillSource): Boolean {
        val now = System.currentTimeMillis()
        val prev = map[fingerprint]
        val notifAccPair = (source == BillSource.NOTIFICATION && prev?.source == BillSource.ACCESSIBILITY) ||
            (source == BillSource.ACCESSIBILITY && prev?.source == BillSource.NOTIFICATION)
        if (prev != null && now - prev.ts <= windowMs && notifAccPair) {
            return false
        }
        map[fingerprint] = Entry(source, now)
        return true
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}

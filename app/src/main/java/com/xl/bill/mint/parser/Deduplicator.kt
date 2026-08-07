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
 * 跨来源短窗口去重：同一笔支付被多个 App 同时通知（如微信支付从银行卡扣款 →
 * 微信「支付凭证」通知 + 银行 App「扣款提醒」通知）时的二次防重。
 *
 * 与 [Deduplicator] 的区别：key = 金额|方向，**不含渠道/商户/时间**——
 * 微信与银行 App 的 [Channel] 不同、商户提取结果必然错位（银行通知几乎提取不到商户），
 * 直接拼指纹永远命中不了；时间维度由窗口控制（微信与银行通知几乎同时到达）。
 *
 * 商户仅作**豁免条件**：窗口内同金额同方向时，若双方商户均非空且不同，
 * 视为不同交易放行（避免误杀 3 秒内跨渠道同金额不同商户的真实多笔消费）。
 */
class CrossSourceDedup(private val windowMs: Long = 3_000L) {

    private data class Entry(val channel: Channel, val merchant: String?, val ts: Long)

    private val map = HashMap<String, Entry>()

    /** 记录一笔；若 3 秒窗口内已存在同金额同方向的「同一笔」交易返回 false（拦截） */
    @Synchronized
    fun tryAdd(channel: Channel, amount: Long, type: Int, merchant: String?): Boolean {
        val now = System.currentTimeMillis()
        val key = "$amount|$type"
        val prev = map[key]
        if (prev != null && now - prev.ts <= windowMs) {
            val bothMerchants = prev.merchant != null && merchant != null
            if (bothMerchants && prev.merchant != merchant) {
                // 商户双非空且不同 → 不同交易，更新记录并放行
                map[key] = Entry(channel, merchant, now)
                return true
            }
            // 同一笔（跨渠道 / 商户缺失 / 商户相同）→ 拦截
            return false
        }
        map[key] = Entry(channel, merchant, now)
        return true
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}

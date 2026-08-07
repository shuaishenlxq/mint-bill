package com.example.bill.parser

import com.xl.bill.mint.parser.Channel
import com.xl.bill.mint.parser.DedupFingerprint
import com.xl.bill.mint.parser.Deduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 去重指纹（DedupFingerprint.base）与内存去重器（Deduplicator）JVM 单测。
 * 通知/无障碍/短信三条链路共用的跨来源基础指纹。
 */
class DeduplicatorTest {

    private val channel = Channel.WECHAT
    private val at = 1_700_000_000_000L // 固定基准（分钟：28333333）

    @Test
    fun baseFingerprintFormat() {
        assertEquals(
            "WECHAT|1200|0|美团|28333333",
            DedupFingerprint.base(channel, 1200L, 0, "美团", at)
        )
    }

    @Test
    fun baseFingerprintChangesAcrossMinutes() {
        val a = DedupFingerprint.base(channel, 1200L, 0, "美团", at)
        val b = DedupFingerprint.base(channel, 1200L, 0, "美团", at + 60_000)
        assertNotEquals(a, b)
    }

    @Test
    fun baseBlocksAcrossSourcesWithSameDeduplicator() {
        // 模拟：通知链路先记，短信/无障碍链路再读同一笔 → 被同一实例拦截（跨源共用）
        val dedup = Deduplicator(windowMs = 120_000L)
        val fingerprint = DedupFingerprint.base(channel, 1200L, 0, "美团", at)
        assertTrue(dedup.tryAdd(fingerprint))
        assertFalse(dedup.tryAdd(fingerprint))
    }

    @Test
    fun baseDifferentChannelOrAmountOrMerchantDistinct() {
        val base = DedupFingerprint.base(channel, 1200L, 0, "美团", at)
        val otherChannel = DedupFingerprint.base(Channel.SMS, 1200L, 0, "美团", at)
        val otherAmount = DedupFingerprint.base(channel, 1300L, 0, "美团", at)
        val otherMerchant = DedupFingerprint.base(channel, 1200L, 0, "瑞幸", at)
        assertNotEquals(base, otherChannel)
        assertNotEquals(base, otherAmount)
        assertNotEquals(base, otherMerchant)
    }
}

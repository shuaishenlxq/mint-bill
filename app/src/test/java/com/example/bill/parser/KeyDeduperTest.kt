package com.example.bill.parser

import com.xl.bill.mint.parser.KeyDeduper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知 key 去重（KeyDeduper）JVM 单测。
 *
 * 核心语义：**仅「同 key + 同金额 + occurredAt 差 ≤ 60s」判为真重放**；
 * 同 key 但金额不同或时间差大 = 通知 id/tag 复用（覆盖式通知）→ 新交易放行——
 * 否则多笔支付（无论金额是否相同）只有第一笔能记。
 */
class KeyDeduperTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun sameKeySameAmountSameWindowBlocksReplay() {
        // 同一通知重放（同 key 同金额同时刻）→ 拦
        val d = KeyDeduper()
        assertTrue(d.tryAdd("0|com.tencent.mm|1001|0|1", 1300L, t0))
        assertFalse(d.tryAdd("0|com.tencent.mm|1001|0|1", 1300L, t0))
    }

    @Test
    fun sameKeyDifferentAmountReleases() {
        // 通知 id 复用 + 金额不同（新支付）→ 放行
        val d = KeyDeduper()
        assertTrue(d.tryAdd("0|com.tencent.mm|1001|0|1", 1300L, t0))
        assertTrue(d.tryAdd("0|com.tencent.mm|1001|0|1", 2500L, t0 + 30_000))
    }

    @Test
    fun sameKeySameAmountBeyondTimeToleranceReleases() {
        // 同 key 同金额但时间差 > 60s（相隔较久的新支付，id 复用）→ 放行
        val d = KeyDeduper()
        assertTrue(d.tryAdd("0|com.tencent.mm|1001|0|1", 1300L, t0))
        assertTrue(d.tryAdd("0|com.tencent.mm|1001|0|1", 1300L, t0 + 61_000))
    }

    @Test
    fun differentKeyAlwaysReleases() {
        val d = KeyDeduper()
        assertTrue(d.tryAdd("0|com.tencent.mm|1001|0|1", 1300L, t0))
        assertTrue(d.tryAdd("0|com.tencent.mm|1002|0|1", 1300L, t0 + 5_000))
    }

    @Test
    fun windowExpiryReleases() {
        val d = KeyDeduper(windowMs = 1L)
        assertTrue(d.tryAdd("k", 1300L, t0))
        Thread.sleep(5)
        assertTrue(d.tryAdd("k", 1300L, t0))
    }

    @Test
    fun reusedKeyAfterDifferentContentReleasesNextSameContent() {
        // key 复用链：id=1001 先后用于 13 元、25 元两笔，之后又出现 13 元（>60s 容差）→ 放行
        val d = KeyDeduper()
        assertTrue(d.tryAdd("k", 1300L, t0))
        assertTrue(d.tryAdd("k", 2500L, t0 + 30_000))
        assertTrue(d.tryAdd("k", 1300L, t0 + 90_000))
    }

    @Test
    fun clearResets() {
        val d = KeyDeduper()
        assertTrue(d.tryAdd("k", 1300L, t0))
        d.clear()
        assertTrue(d.tryAdd("k", 1300L, t0))
    }
}

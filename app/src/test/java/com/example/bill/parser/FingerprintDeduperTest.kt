package com.example.bill.parser

import com.xl.bill.mint.parser.BillSource
import com.xl.bill.mint.parser.FingerprintDeduper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 异源模糊指纹去重（FingerprintDeduper）JVM 单测。
 *
 * 语义：同渠道「通知 vs 无障碍」对同一笔的双通道互拦（防双记），
 * 但**同源**（通知 vs 通知、无障碍 vs 无障碍、短信 vs 短信）放行——
 * 1 分钟内多笔同金额真实支付不被误杀（同 key 重复由 notificationKey 去重兜底）。
 */
class FingerprintDeduperTest {

    private val f1 = "WECHAT|1300|0|null|28333333"

    @Test
    fun sameSourceReleasesRealMultiBills() {
        // 通知1 记 → 通知2（同指纹、同来源）→ 放行（真实多笔）
        val d = FingerprintDeduper()
        assertTrue(d.tryAdd(f1, BillSource.NOTIFICATION))
        assertTrue(d.tryAdd(f1, BillSource.NOTIFICATION))
    }

    @Test
    fun differentSourceBlocksSameBill() {
        // 通知先记 → 无障碍再读同一笔 → 拦（防双记）
        val d = FingerprintDeduper()
        assertTrue(d.tryAdd(f1, BillSource.NOTIFICATION))
        assertFalse(d.tryAdd(f1, BillSource.ACCESSIBILITY))
    }

    @Test
    fun notificationAndSmsDoNotBlockEachOther() {
        // 通知 vs 短信 不互拦：短信归银行后银行短信与银行App通知同渠道同指纹，
        // 若互拦会导致银行短信被内存指纹层误拦——同笔合并交给 DB 跨源层
        val d = FingerprintDeduper()
        assertTrue(d.tryAdd(f1, BillSource.NOTIFICATION))
        assertTrue(d.tryAdd(f1, BillSource.SMS))
        // 反向同理
        val d2 = FingerprintDeduper()
        assertTrue(d2.tryAdd(f1, BillSource.SMS))
        assertTrue(d2.tryAdd(f1, BillSource.NOTIFICATION))
    }

    @Test
    fun accessibilityFirstThenNotificationAlsoBlocked() {
        val d = FingerprintDeduper()
        assertTrue(d.tryAdd(f1, BillSource.ACCESSIBILITY))
        assertFalse(d.tryAdd(f1, BillSource.NOTIFICATION))
    }

    @Test
    fun sameSourceSmsNotBlockedBySms() {
        // 两笔同金额银行短信（同源）→ 放行（真实多笔，靠 sms-key 去重）
        val d = FingerprintDeduper()
        assertTrue(d.tryAdd(f1, BillSource.SMS))
        assertTrue(d.tryAdd(f1, BillSource.SMS))
    }

    @Test
    fun differentMerchantNaturallyDistinct() {
        // 商户不同 → 指纹本身不同 → 异源也不拦
        val d = FingerprintDeduper()
        assertTrue(d.tryAdd("WECHAT|1300|0|美团|28333333", BillSource.NOTIFICATION))
        assertTrue(d.tryAdd("WECHAT|1300|0|瑞幸|28333333", BillSource.ACCESSIBILITY))
    }

    @Test
    fun windowExpiryReleases() {
        val d = FingerprintDeduper(windowMs = 1L)
        assertTrue(d.tryAdd(f1, BillSource.NOTIFICATION))
        Thread.sleep(5)
        // 超窗后异源再读 → 视为新交易放行
        assertTrue(d.tryAdd(f1, BillSource.ACCESSIBILITY))
    }

    @Test
    fun differentChannelNaturallyDistinct() {
        // 渠道不同（短信 vs 微信）→ 指纹不同 → 不互拦（跨渠道由 DB 跨源层负责）
        val d = FingerprintDeduper()
        assertTrue(d.tryAdd("WECHAT|1300|0|null|28333333", BillSource.NOTIFICATION))
        assertTrue(d.tryAdd("SMS|1300|0|null|28333333", BillSource.SMS))
    }

    @Test
    fun clearResets() {
        val d = FingerprintDeduper()
        assertTrue(d.tryAdd(f1, BillSource.NOTIFICATION))
        d.clear()
        assertTrue(d.tryAdd(f1, BillSource.ACCESSIBILITY))
    }
}

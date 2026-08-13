package com.example.bill.parser

import com.xl.bill.mint.parser.Channel
import com.xl.bill.mint.parser.CrossDecision
import com.xl.bill.mint.parser.CrossSourceResolver
import com.xl.bill.mint.parser.ExistingTx
import com.xl.bill.mint.parser.ParsedBill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨源优先级判定（CrossSourceResolver）JVM 单测。
 *
 * 规则：银行短信(3) > 银行App通知(2) > 支付宝/微信(1)（短信来源恒 3）；
 * 窗口：任一侧短信来源 → 短信窗口（默认 5s，可配置），App↔App 3s；
 * 同渠道**且同来源** → 不同交易跳过；同渠道**异来源**（银行App通知 vs 银行短信）→ 参与合并；
 * 商户双非空且不同 → 不同交易放行。
 */
class CrossSourceResolverTest {

    private val t0 = 1_700_000_000_000L
    private val SRC_NOTIF = CrossSourceResolver.SOURCE_NOTIFICATION
    private val SRC_SMS = CrossSourceResolver.SOURCE_SMS

    private fun bill(channel: Channel, merchant: String? = null, at: Long = t0) = ParsedBill(
        channel = channel,
        amount = 1300L,
        type = ParsedBill.TYPE_EXPENSE,
        merchant = merchant,
        rawTitle = null,
        rawText = null,
        occurredAt = at,
        notificationKey = null
    )

    private fun tx(id: Long, channel: Channel, source: String = SRC_NOTIF, merchant: String? = null, at: Long = t0) =
        ExistingTx(id = id, channel = channel, source = source, merchant = merchant, occurredAt = at)

    @Test
    fun priorityOrder() {
        // 短信来源（含银行短信与非银行短信）恒最高
        assertTrue(CrossSourceResolver.priority(Channel.BANK, SRC_SMS) == 3)
        assertTrue(CrossSourceResolver.priority(Channel.SMS, SRC_SMS) == 3)
        assertTrue(CrossSourceResolver.priority(Channel.BANK, SRC_NOTIF) > CrossSourceResolver.priority(Channel.WECHAT, SRC_NOTIF))
        assertEquals(
            CrossSourceResolver.priority(Channel.WECHAT, SRC_NOTIF),
            CrossSourceResolver.priority(Channel.ALIPAY, SRC_NOTIF)
        )
    }

    @Test
    fun windowSelection() {
        assertEquals(5_000L, CrossSourceResolver.windowFor(Channel.SMS, SRC_SMS, Channel.WECHAT, SRC_NOTIF))
        assertEquals(5_000L, CrossSourceResolver.windowFor(Channel.BANK, SRC_NOTIF, Channel.BANK, SRC_SMS))
        assertEquals(3_000L, CrossSourceResolver.windowFor(Channel.WECHAT, SRC_NOTIF, Channel.BANK, SRC_NOTIF))
        assertEquals(3_000L, CrossSourceResolver.windowFor(Channel.WECHAT, SRC_NOTIF, Channel.ALIPAY, SRC_NOTIF))
    }

    @Test
    fun noCandidatesInsertsNew() {
        val r = CrossSourceResolver.decide(bill(Channel.WECHAT), SRC_NOTIF, emptyList())
        assertEquals(CrossDecision.INSERT_NEW, r.decision)
    }

    @Test
    fun sameChannelSameSourceSkippedAsDifferentTx() {
        // 同渠道同来源（如微信两笔同金额支付）→ 不同交易放行，不误杀真实多笔
        val r = CrossSourceResolver.decide(
            bill(Channel.WECHAT), SRC_NOTIF, listOf(tx(1, Channel.WECHAT, SRC_NOTIF))
        )
        assertEquals(CrossDecision.INSERT_NEW, r.decision)
    }

    @Test
    fun sameChannelDifferentSourceBankSmsUpgradesBankNotification() {
        // 银行App通知先记（BANK, notification）→ 银行短信 2s 后到（BANK, sms）→ 短信胜，升级
        val r = CrossSourceResolver.decide(
            bill(Channel.BANK), SRC_SMS,
            listOf(tx(3, Channel.BANK, SRC_NOTIF, at = t0)),
            smsWindowMs = 5_000L
        )
        assertEquals(CrossDecision.UPGRADE_EXISTING, r.decision)
        assertEquals(3L, r.target?.id)
    }

    @Test
    fun bankNotificationAfterBankSmsDrops() {
        // 银行短信先记 → 银行App通知 2s 后到 → 通知被丢（短信保留）
        val r = CrossSourceResolver.decide(
            bill(Channel.BANK), SRC_NOTIF,
            listOf(tx(4, Channel.BANK, SRC_SMS, at = t0)),
            smsWindowMs = 5_000L
        )
        assertEquals(CrossDecision.DROP_LOWER_PRIORITY, r.decision)
        assertEquals(4L, r.target?.id)
    }

    @Test
    fun smsUpgradesWechatWithinWindow() {
        // 微信通知先记（t0），银行短信 2s 后到 → 短信胜，升级（channel=BANK）
        val r = CrossSourceResolver.decide(
            bill(Channel.BANK), SRC_SMS,
            listOf(tx(7, Channel.WECHAT, SRC_NOTIF, at = t0)),
            smsWindowMs = 5_000L
        )
        assertEquals(CrossDecision.UPGRADE_EXISTING, r.decision)
        assertEquals(7L, r.target?.id)
    }

    @Test
    fun smsBeyondWindowInsertsNew() {
        // 默认短信窗口 5s：6s 差 → 不同账单
        val r = CrossSourceResolver.decide(
            bill(Channel.BANK, at = t0 + 6_000), SRC_SMS,
            listOf(tx(7, Channel.WECHAT, SRC_NOTIF, at = t0))
        )
        assertEquals(CrossDecision.INSERT_NEW, r.decision)
    }

    @Test
    fun smsWindowConfigurable() {
        // 配置 30s 窗口：20s 差仍判定同笔
        val r1 = CrossSourceResolver.decide(
            bill(Channel.BANK, at = t0 + 20_000), SRC_SMS,
            listOf(tx(7, Channel.WECHAT, SRC_NOTIF, at = t0)),
            smsWindowMs = 30_000L
        )
        assertEquals(CrossDecision.UPGRADE_EXISTING, r1.decision)
        // 默认 5s 窗口：20s 差判不同笔
        val r2 = CrossSourceResolver.decide(
            bill(Channel.BANK, at = t0 + 20_000), SRC_SMS,
            listOf(tx(7, Channel.WECHAT, SRC_NOTIF, at = t0))
        )
        assertEquals(CrossDecision.INSERT_NEW, r2.decision)
    }

    @Test
    fun bankUpgradesWechatWithin3s() {
        val r = CrossSourceResolver.decide(
            bill(Channel.BANK), SRC_NOTIF,
            listOf(tx(3, Channel.WECHAT, SRC_NOTIF, at = t0))
        )
        assertEquals(CrossDecision.UPGRADE_EXISTING, r.decision)
        assertEquals(3L, r.target?.id)
    }

    @Test
    fun bankBeyond3sInsertsNew() {
        // App↔App 窗口 3s：10s 差 → 另一笔
        val r = CrossSourceResolver.decide(
            bill(Channel.BANK), SRC_NOTIF,
            listOf(tx(3, Channel.WECHAT, SRC_NOTIF, at = t0 + 10_000))
        )
        assertEquals(CrossDecision.INSERT_NEW, r.decision)
    }

    @Test
    fun wechatAfterBankDropsLowerPriority() {
        val r = CrossSourceResolver.decide(
            bill(Channel.WECHAT), SRC_NOTIF,
            listOf(tx(5, Channel.BANK, SRC_NOTIF, at = t0))
        )
        assertEquals(CrossDecision.DROP_LOWER_PRIORITY, r.decision)
        assertEquals(5L, r.target?.id)
    }

    @Test
    fun wechatAfterBankSmsDrops() {
        // 短信先记，微信 2s 后到 → 微信被丢（短信保留）
        val r = CrossSourceResolver.decide(
            bill(Channel.WECHAT), SRC_NOTIF,
            listOf(tx(9, Channel.BANK, SRC_SMS, at = t0)),
            smsWindowMs = 5_000L
        )
        assertEquals(CrossDecision.DROP_LOWER_PRIORITY, r.decision)
    }

    @Test
    fun samePriorityFirstWins() {
        // 微信先记，支付宝 2s 后到（同优先级）→ 支付宝被丢（先到先得）
        val r = CrossSourceResolver.decide(
            bill(Channel.ALIPAY), SRC_NOTIF,
            listOf(tx(4, Channel.WECHAT, SRC_NOTIF, at = t0))
        )
        assertEquals(CrossDecision.DROP_LOWER_PRIORITY, r.decision)
    }

    @Test
    fun merchantExemptionAllowsBoth() {
        // 商户双非空且不同 → 不同交易放行
        val r = CrossSourceResolver.decide(
            bill(Channel.WECHAT, merchant = "美团", at = t0 + 1_000), SRC_NOTIF,
            listOf(tx(2, Channel.BANK, SRC_NOTIF, merchant = "瑞幸", at = t0))
        )
        assertEquals(CrossDecision.INSERT_NEW, r.decision)
    }

    @Test
    fun oneSideNullMerchantTreatedAsSame() {
        // 一侧商户为空 → 仍视为同笔
        val r = CrossSourceResolver.decide(
            bill(Channel.BANK, merchant = null, at = t0 + 2_000), SRC_SMS,
            listOf(tx(6, Channel.WECHAT, SRC_NOTIF, merchant = "美团", at = t0)),
            smsWindowMs = 5_000L
        )
        assertEquals(CrossDecision.UPGRADE_EXISTING, r.decision)
    }

    @Test
    fun upgradeChainWechatBankSms() {
        // 三连到达：微信记 → 银行App通知升级 → 银行短信再升级
        var r = CrossSourceResolver.decide(
            bill(Channel.BANK), SRC_NOTIF,
            listOf(tx(1, Channel.WECHAT, SRC_NOTIF, at = t0))
        )
        assertEquals(CrossDecision.UPGRADE_EXISTING, r.decision)
        r = CrossSourceResolver.decide(
            bill(Channel.BANK), SRC_SMS,
            listOf(tx(1, Channel.BANK, SRC_NOTIF, at = t0)),
            smsWindowMs = 5_000L
        )
        assertEquals(CrossDecision.UPGRADE_EXISTING, r.decision)
        assertEquals(1L, r.target?.id)
    }
}

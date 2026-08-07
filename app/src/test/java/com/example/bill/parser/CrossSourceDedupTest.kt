package com.xl.bill.mint.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨来源去重（CrossSourceDedup）JVM 单测。
 *
 * 核心场景：微信支付 10 元从银行卡扣款 → 微信「支付凭证」通知 + 银行 App「扣款提醒」通知，
 * 两条通知金额相同、方向相同、到达时间几乎同时，但 Channel 不同、商户提取结果错位
 * （微信能提取到商户名，银行通知几乎为 null）——3 秒窗口内只记第一条。
 */
class CrossSourceDedupTest {

    private val wechat = Channel.WECHAT
    private val bank = Channel.BANK
    private val alipay = Channel.ALIPAY

    private val EXPENSE = 0
    private val INCOME = 1

    // ---- 核心场景：跨渠道同金额同方向（商户缺失/错位）→ 拦截 ----

    @Test
    fun wechatFirstThenBankNullMerchantBlocked() {
        // 微信通知先到（提取到商户「美团」），银行扣款通知后到（商户 null）→ 第二条被拦
        val dedup = CrossSourceDedup()
        assertTrue(dedup.tryAdd(wechat, 1000L, EXPENSE, "美团"))
        assertFalse(dedup.tryAdd(bank, 1000L, EXPENSE, null))
    }

    @Test
    fun bankFirstThenWechatBlocked() {
        // 银行通知先到（商户 null），微信通知后到（商户「美团」）→ 反向顺序同样被拦
        val dedup = CrossSourceDedup()
        assertTrue(dedup.tryAdd(bank, 1000L, EXPENSE, null))
        assertFalse(dedup.tryAdd(wechat, 1000L, EXPENSE, "美团"))
    }

    @Test
    fun thirdNoticeWithinWindowStillBlocked() {
        // 3 秒窗口内第三条（即使渠道再不同）→ 仍只保留第一条
        val dedup = CrossSourceDedup()
        assertTrue(dedup.tryAdd(wechat, 1000L, EXPENSE, "美团"))
        assertFalse(dedup.tryAdd(bank, 1000L, EXPENSE, null))
        assertFalse(dedup.tryAdd(alipay, 1000L, EXPENSE, null))
    }

    // ---- 不误杀：跨渠道但商户双非空且不同 → 放行 ----

    @Test
    fun crossChannelDifferentMerchantsBothNonNullAllowed() {
        // 3 秒内微信付美团 10 元 + 支付宝付瑞幸 10 元（各自独立消费）→ 都放行
        val dedup = CrossSourceDedup()
        assertTrue(dedup.tryAdd(wechat, 1000L, EXPENSE, "美团"))
        assertTrue(dedup.tryAdd(alipay, 1000L, EXPENSE, "瑞幸"))
    }

    @Test
    fun sameChannelDifferentMerchantsAllowed() {
        // 同渠道连续两笔不同商户 → 放行
        val dedup = CrossSourceDedup()
        assertTrue(dedup.tryAdd(wechat, 1000L, EXPENSE, "美团"))
        assertTrue(dedup.tryAdd(wechat, 1000L, EXPENSE, "瑞幸"))
    }

    // ---- 同渠道同商户同金额（通知重放/双链路）→ 拦截（与 base 指纹语义一致，冗余无害） ----

    @Test
    fun sameChannelSameMerchantReplayBlocked() {
        val dedup = CrossSourceDedup()
        assertTrue(dedup.tryAdd(wechat, 1000L, EXPENSE, "美团"))
        assertFalse(dedup.tryAdd(wechat, 1000L, EXPENSE, "美团"))
    }

    // ---- 区分维度：金额 / 方向不同 → 放行 ----

    @Test
    fun differentAmountAllowed() {
        val dedup = CrossSourceDedup()
        assertTrue(dedup.tryAdd(wechat, 1000L, EXPENSE, "美团"))
        assertTrue(dedup.tryAdd(bank, 1100L, EXPENSE, null))
    }

    @Test
    fun differentDirectionAllowed() {
        // 支出 10 元 vs 收入 10 元 → 方向不同，放行
        val dedup = CrossSourceDedup()
        assertTrue(dedup.tryAdd(wechat, 1000L, EXPENSE, "美团"))
        assertTrue(dedup.tryAdd(bank, 1000L, INCOME, null))
    }

    // ---- 窗口过期：超过窗口时长 → 放行 ----

    @Test
    fun windowExpiredAllowed() {
        // windowMs=1：两次调用间隔必然 > 1ms → 模拟超窗后同 key 恢复正常记账
        val dedup = CrossSourceDedup(windowMs = 1L)
        assertTrue(dedup.tryAdd(wechat, 1000L, EXPENSE, "美团"))
        Thread.sleep(5)
        assertTrue(dedup.tryAdd(bank, 1000L, EXPENSE, null))
    }

    // ---- 商户更新放行后，新商户成为基准 ----

    @Test
    fun merchantUpdateBecomesNewBaseline() {
        // 美团(微信) → 瑞幸(支付宝) 放行后，基准更新为瑞幸；
        // 紧接着再来一笔与瑞幸同金额同方向且商户缺失的通知 → 被视为瑞幸那笔的重复，拦截
        val dedup = CrossSourceDedup()
        assertTrue(dedup.tryAdd(wechat, 1000L, EXPENSE, "美团"))
        assertTrue(dedup.tryAdd(alipay, 1000L, EXPENSE, "瑞幸"))
        assertFalse(dedup.tryAdd(bank, 1000L, EXPENSE, null))
    }

    // ---- clear 后恢复正常 ----

    @Test
    fun clearResets() {
        val dedup = CrossSourceDedup()
        assertTrue(dedup.tryAdd(wechat, 1000L, EXPENSE, "美团"))
        dedup.clear()
        assertTrue(dedup.tryAdd(bank, 1000L, EXPENSE, null))
    }
}

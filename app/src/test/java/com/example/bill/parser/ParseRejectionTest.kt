package com.example.bill.parser

import com.xl.bill.mint.parser.BillParseEngine
import com.xl.bill.mint.parser.ParseRejectReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 解析拒绝原因（parseWithReason / parseSmsWithReason / parseAccessibilityWithReason）JVM 单测。
 * 每个拒绝分支对应一个 ParseRejectReason，供诊断日志精确落「为什么没有记账」。
 */
class ParseRejectionTest {

    private val at = 1_700_000_000_000L

    // ---------------- 通知入口 ----------------

    @Test
    fun unsupportedPackage() {
        val out = BillParseEngine.parseWithReason("com.unknown.app", "支付成功", "¥13.00", at)
        assertNull(out.bill)
        assertEquals(ParseRejectReason.UNSUPPORTED_PACKAGE, out.reason)
    }

    @Test
    fun emptyTitleAndText() {
        val out = BillParseEngine.parseWithReason("com.tencent.mm", "", "  ", at)
        assertNull(out.bill)
        assertEquals(ParseRejectReason.EMPTY_TEXT, out.reason)
    }

    @Test
    fun adBlocked() {
        // 命中广告词（免费/住院），且无交易守卫词
        val out = BillParseEngine.parseWithReason("com.tencent.mm", "微信支付", "免费领取住院津贴", at)
        assertNull(out.bill)
        assertEquals(ParseRejectReason.AD_BLOCKED, out.reason)
    }

    @Test
    fun noAmount() {
        val out = BillParseEngine.parseWithReason("com.tencent.mm", "微信支付", "你今天吃了吗", at)
        assertNull(out.bill)
        assertEquals(ParseRejectReason.NO_AMOUNT, out.reason)
    }

    @Test
    fun amountOutOfRange() {
        // 2 亿元 = 2e10 分 > 上限 1e10 分
        val out = BillParseEngine.parseWithReason("com.tencent.mm", "微信支付", "支付成功 ¥200000000", at)
        assertNull(out.bill)
        assertEquals(ParseRejectReason.AMOUNT_OUT_OF_RANGE, out.reason)
    }

    @Test
    fun parseSuccessAndLegacyDelegateIdentical() {
        val out = BillParseEngine.parseWithReason("com.tencent.mm", "微信支付", "已支付¥13.00", at)
        assertNull(out.reason)
        assertNotNull(out.bill)
        assertEquals(1300L, out.bill!!.amount)
        // 旧入口委托：同参数结果完全一致
        assertEquals(out.bill, BillParseEngine.parse("com.tencent.mm", "微信支付", "已支付¥13.00", at))
    }

    // ---------------- 短信入口 ----------------

    @Test
    fun smsBlockedWord() {
        val out = BillParseEngine.parseSmsWithReason("10690000", "【XX银行】您的验证码为 123456，请勿泄露", at)
        assertNull(out.bill)
        assertEquals(ParseRejectReason.SMS_BLOCKED_WORD, out.reason)
    }

    @Test
    fun smsBalanceOnly() {
        val out = BillParseEngine.parseSmsWithReason("95599", "【XX银行】您的账户余额为 ¥950.00", at)
        assertNull(out.bill)
        assertEquals(ParseRejectReason.BALANCE_ONLY_SMS, out.reason)
    }

    @Test
    fun smsNoAmount() {
        val out = BillParseEngine.parseSmsWithReason("95599", "【XX银行】您有一笔新的交易提醒", at)
        assertNull(out.bill)
        assertEquals(ParseRejectReason.NO_AMOUNT, out.reason)
    }

    @Test
    fun smsSuccess() {
        val body = "您尾号为1471的农行借记卡于08月11日18:43发生一笔支出13.00元，详情请点击"
        val out = BillParseEngine.parseSmsWithReason("95599", body, at)
        assertNull(out.reason)
        assertNotNull(out.bill)
        assertEquals(1300L, out.bill!!.amount)
        assertEquals(out.bill, BillParseEngine.parseSms("95599", body, at))
    }

    // ---------------- 无障碍入口 ----------------

    @Test
    fun accessibilityEmpty() {
        val out = BillParseEngine.parseAccessibilityWithReason("com.tencent.mm", "  ", at)
        assertNull(out.bill)
        assertEquals(ParseRejectReason.EMPTY_TEXT, out.reason)
    }

    @Test
    fun accessibilityNotTradeScene() {
        val out = BillParseEngine.parseAccessibilityWithReason("com.tencent.mm", "今天天气不错\n去爬山吧", at)
        assertNull(out.bill)
        assertEquals(ParseRejectReason.NOT_TRADE_SCENE, out.reason)
    }

    @Test
    fun accessibilityBalancePage() {
        // 含场景词（支付成功）+ 余额页特征词 → BALANCE_PAGE
        val out = BillParseEngine.parseAccessibilityWithReason("com.tencent.mm", "支付成功\n零钱余额 ¥4065.33", at)
        assertNull(out.bill)
        assertEquals(ParseRejectReason.BALANCE_PAGE, out.reason)
    }
}

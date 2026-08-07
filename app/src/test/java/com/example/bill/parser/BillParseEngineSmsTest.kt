package com.example.bill.parser

import com.xl.bill.mint.parser.BillParseEngine
import com.xl.bill.mint.parser.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 短信记账解析（BillParseEngine.parseSms）JVM 单测。
 * 覆盖：消费/到账短信、余额播报拦截、验证码拦截、商户提取、无金额忽略。
 */
class BillParseEngineSmsTest {

    private val at = 1_700_000_000_000L

    // ---------- 正常解析 ----------

    @Test
    fun parseBankConsumptionSms() {
        val body = "【XX银行】您尾号1234的储蓄卡于08月06日18:30消费人民币10.00元，商户：瑞幸咖啡，余额950.00元。"
        val parsed = BillParseEngine.parseSms("95588", body, at)
        assertNotNull(parsed)
        assertEquals(Channel.SMS, parsed!!.channel)
        assertEquals(1000L, parsed.amount) // 10.00 元 = 1000 分
        assertEquals(0, parsed.type)       // 支出
        assertEquals("瑞幸咖啡", parsed.merchant)
        assertEquals("95588", parsed.rawTitle)
        assertEquals("sms-95588-$at", parsed.notificationKey)
    }

    @Test
    fun parseIncomeSms() {
        val body = "【XX银行】您尾号5678的储蓄卡收到转账人民币100.00元，对方：张三。"
        val parsed = BillParseEngine.parseSms("95533", body, at)
        assertNotNull(parsed)
        assertEquals(Channel.SMS, parsed!!.channel)
        assertEquals(10000L, parsed.amount)
        assertEquals(1, parsed.type) // 收入
    }

    @Test
    fun parseMerchantWithColonForm() {
        val body = "您尾号1234的卡于18:30消费10.00元，商户名称：美团，余额100元。"
        val parsed = BillParseEngine.parseSms("95588", body, at)
        assertNotNull(parsed)
        assertEquals("美团", parsed!!.merchant)
    }

    @Test
    fun parseAlipaySms() {
        val body = "支付宝支付成功，向商家付款10.00元，商户：全家便利店。"
        val parsed = BillParseEngine.parseSms("95188", body, at)
        assertNotNull(parsed)
        assertEquals(1000L, parsed!!.amount)
        assertEquals(0, parsed.type)
    }

    // ---------- 守卫拦截 ----------

    @Test
    fun rejectBalanceOnlySms() {
        // 纯余额播报：无交易词 → 不记账（防误记）
        val body = "【XX银行】您的尾号1234储蓄卡余额为 ¥950.00 元。"
        assertNull(BillParseEngine.parseSms("95588", body, at))
    }

    @Test
    fun allowBalanceWithTradeWord() {
        // 含消费词 + 余额并存 → 放行，取首个金额
        val body = "您尾号1234的卡消费10元，余额950元。"
        val parsed = BillParseEngine.parseSms("95588", body, at)
        assertNotNull(parsed)
        assertEquals(1000L, parsed!!.amount)
    }

    @Test
    fun rejectVerificationCodeSms() {
        val body = "【XX银行】您的验证码是 123456，10分钟内有效。请勿泄露。"
        assertNull(BillParseEngine.parseSms("95588", body, at))
    }

    @Test
    fun rejectLoginSms() {
        val body = "您正在尝试登录，验证码 654321，若非本人操作请忽略。"
        assertNull(BillParseEngine.parseSms("10086", body, at))
    }

    @Test
    fun rejectNoAmountSms() {
        val body = "周末全场八折，欢迎惠顾！"
        assertNull(BillParseEngine.parseSms("10690001", body, at))
    }

    @Test
    fun rejectEmptyBody() {
        assertNull(BillParseEngine.parseSms("95588", "   ", at))
    }

    // ---------- 边界 ----------

    @Test
    fun parseOverflowAmountRejected() {
        val body = "您消费 ¥1000000000000.00 元。" // 超 1 亿上限
        assertNull(BillParseEngine.parseSms("95588", body, at))
    }

    @Test
    fun smsChannelDistinctFromNotificationChannels() {
        // 短信为独立 Channel：与通知渠道（微信/银行）base 指纹不同源，防误拦
        val smsFp = com.xl.bill.mint.parser.DedupFingerprint.base(Channel.SMS, 1000L, 0, "瑞幸", at)
        val bankFp = com.xl.bill.mint.parser.DedupFingerprint.base(Channel.BANK, 1000L, 0, "瑞幸", at)
        assertTrue(smsFp != bankFp)
    }
}

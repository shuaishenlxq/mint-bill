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
        // 短信归银行：正文含「银行」「储蓄卡」→ 渠道为银行卡
        assertEquals(Channel.BANK, parsed!!.channel)
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
        assertEquals(Channel.BANK, parsed!!.channel)
        assertEquals(10000L, parsed.amount)
        assertEquals(1, parsed.type) // 收入
    }

    @Test
    fun parseAbcBankRmbNoYuanSuffix() {
        // 农行格式：人民币-14.79，无「元」后缀、无 ¥ 符号、金额带负号（此前解析失败被丢弃）
        // 同时验证：尾号 1471 / 日期 08月09日 / 时间 21:09 不会被误当金额（若误匹配，amount 不会是 1479）
        val body = "【中国农业银行】您尾号1471账户08月09日21:09向沃尔玛完成微信支付交易人民币-14.79，余额4759.86，详见掌银。"
        val parsed = BillParseEngine.parseSms("95599", body, at)
        assertNotNull(parsed)
        assertEquals(1479L, parsed!!.amount) // -14.79 → 1479 分（正数）
        assertEquals(0, parsed.type)          // 支出
        assertEquals("沃尔玛", parsed.merchant)
        // 含「中国农业银行」→ 归银行卡渠道
        assertEquals(Channel.BANK, parsed.channel)
    }

    @Test
    fun parseAbcBankDouyinExactUserSms() {
        // 用户上报原文：完成抖音支付交易人民币-44.80（无「向X完成」结构 → merchant=null，不影响记账）
        // 金额带负号 + 无「元」/「¥」后缀 → 必须命中 AMOUNT_RMB_RE（曾因缺失此正则 NO_AMOUNT 拒记）
        val body = "【中国农业银行】您尾号1471账户08月13日10:48完成抖音支付交易人民币-44.80，余额4386.97，详见掌银。"
        val parsed = BillParseEngine.parseSms("95599", body, at)
        assertNotNull(parsed)
        assertEquals(4480L, parsed!!.amount)          // -44.80 → 4480 分
        assertEquals(0, parsed.type)                   // 支出（命中 EXPENSE_WORDS「支付」）
        assertEquals(Channel.BANK, parsed.channel)     // 含「银行」→ 银行卡渠道
        assertNull(parsed.merchant)                    // 无「向X完成」结构，不提取商户
        assertEquals("sms-95599-$at", parsed.notificationKey)
    }

    @Test
    fun parseBankSmsKeywordVariants() {
        // 各银行名缩写/借记卡字眼 → 一律归银行卡
        val variants = listOf(
            "【招行】您尾号1234的储蓄卡消费10.00元，余额100元。",
            "【邮储银行】您尾号1234的卡支出10.00元。",
            "您尾号1234的兴业银行卡消费10.00元。",
            "您尾号1234的信用卡账单支出10.00元。",
            "【建设银行】您尾号1234账户消费10.00元。"
        )
        variants.forEach { body ->
            val parsed = BillParseEngine.parseSms("95588", body, at)
            assertNotNull("应解析成功: $body", parsed)
            assertEquals("应归银行卡渠道: $body", Channel.BANK, parsed!!.channel)
        }
    }

    @Test
    fun parseMerchantWithColonForm() {
        val body = "您尾号1234的卡于18:30消费10.00元，商户名称：美团，余额100元。"
        val parsed = BillParseEngine.parseSms("95588", body, at)
        assertNotNull(parsed)
        assertEquals("美团", parsed!!.merchant)
        // 无银行特征词 → 仍为短信渠道
        assertEquals(Channel.SMS, parsed.channel)
    }

    @Test
    fun parseAlipaySms() {
        val body = "支付宝支付成功，向商家付款10.00元，商户：全家便利店。"
        val parsed = BillParseEngine.parseSms("95188", body, at)
        assertNotNull(parsed)
        assertEquals(1000L, parsed!!.amount)
        assertEquals(0, parsed.type)
        // 无银行特征词 → 仍为短信渠道
        assertEquals(Channel.SMS, parsed.channel)
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

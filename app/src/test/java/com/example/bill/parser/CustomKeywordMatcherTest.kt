package com.example.bill.parser

import com.xl.bill.mint.parser.BillParseEngine
import com.xl.bill.mint.parser.BillParseEngine.CustomKeywordScope
import com.xl.bill.mint.parser.BillParseEngine.CustomMatchGroup
import com.xl.bill.mint.parser.ParseRejectReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义匹配关键词（BillParseEngine.matchCustomGroup + parse/parseSms 兜底）JVM 单测。
 *
 * 语义（用户确认）：系统预设词未命中时，任一组的「作用范围匹配 + 组内全部关键词命中」→ 放行记账；
 * 仅覆盖广告门禁（AD_BLOCKED）与短信余额守卫（BALANCE_ONLY_SMS）；
 * 验证码/登录硬拦截（SMS_BLOCKED_WORD）、无金额、非白名单包名（UNSUPPORTED_PACKAGE）永不可覆盖。
 */
class CustomKeywordMatcherTest {

    private val at = 1_700_000_000_000L

    /** 会被广告门禁拦截的营销短信（含 AD_BLOCK_WORDS「赠送/优惠券」） */
    private val adSms = "【抖音电商】恭喜您获得3元优惠券，赠送限时福利，点击领取。"

    // ---------- 兜底放行（广告门禁） ----------

    @Test
    fun adBlockedWithoutCustomGroup() {
        assertNull(BillParseEngine.parseSms("10690001", adSms, at))
    }

    @Test
    fun customGroupFullyMatchedOverridesAdBlock() {
        // 组内全部关键词命中 + 短信作用域 → 放行记账（金额可解析）
        val groups = listOf(CustomMatchGroup(listOf("抖音", "优惠券"), CustomKeywordScope.SMS))
        val parsed = BillParseEngine.parseSms("10690001", adSms, at, customGroups = groups)
        assertNotNull(parsed)
        assertEquals(300L, parsed!!.amount) // 3元 = 300 分
    }

    @Test
    fun partialGroupMatchStillRejected() {
        // 组内只有部分关键词命中（文本无「红包」）→ 仍拒
        val groups = listOf(CustomMatchGroup(listOf("抖音", "红包"), CustomKeywordScope.SMS))
        assertNull(BillParseEngine.parseSms("10690001", adSms, at, customGroups = groups))
    }

    @Test
    fun scopeMismatchRejected() {
        // 通知作用域的组不作用于短信
        val groups = listOf(CustomMatchGroup(listOf("抖音", "优惠券"), CustomKeywordScope.NOTIFICATION))
        assertNull(BillParseEngine.parseSms("10690001", adSms, at, customGroups = groups))
    }

    @Test
    fun scopeAllMatchesSmsChannel() {
        val groups = listOf(CustomMatchGroup(listOf("抖音", "优惠券"), CustomKeywordScope.ALL))
        assertNotNull(BillParseEngine.parseSms("10690001", adSms, at, customGroups = groups))
    }

    // ---------- 兜底放行（短信余额守卫） ----------

    @Test
    fun customGroupOverridesBalanceGuard() {
        // 纯余额播报短信 + 自定义组全中 → 放行（余额守卫被覆盖）
        val body = "【XX银行】您的储蓄卡余额为 50.00 元。"
        val groups = listOf(CustomMatchGroup(listOf("余额", "储蓄卡"), CustomKeywordScope.SMS))
        val parsed = BillParseEngine.parseSms("95588", body, at, customGroups = groups)
        assertNotNull(parsed)
        assertEquals(5000L, parsed!!.amount)
    }

    @Test
    fun balanceGuardStillActiveWithoutCustomGroup() {
        val body = "【XX银行】您的储蓄卡余额为 50.00 元。"
        assertNull(BillParseEngine.parseSms("95588", body, at))
    }

    // ---------- 永不可覆盖 ----------

    @Test
    fun smsBlockWordsNeverOverridden() {
        // 验证码短信 + 自定义组全中 → 仍硬拦截
        val body = "【XX银行】您的验证码是 123456，10分钟内有效，请勿泄露。"
        val groups = listOf(CustomMatchGroup(listOf("验证码", "银行"), CustomKeywordScope.ALL))
        assertNull(BillParseEngine.parseSms("95588", body, at, customGroups = groups))
    }

    @Test
    fun unsupportedPackageStillRejected() {
        // 非白名单包名（抖音 App）→ 即使自定义组全中也不解析（通知范围仅限已支持 App）
        val groups = listOf(CustomMatchGroup(listOf("支付", "抖音"), CustomKeywordScope.NOTIFICATION))
        val outcome = BillParseEngine.parseWithReason(
            pkg = "com.ss.android.ugc.aweme",
            title = "抖音",
            text = "您已成功支付44.80元，抖音小店订单已完成。",
            occurredAt = at,
            customGroups = groups
        )
        assertNull(outcome.bill)
        assertEquals(ParseRejectReason.UNSUPPORTED_PACKAGE, outcome.reason)
    }

    // ---------- 通知入口 ----------

    @Test
    fun notificationPathAdBlockedWithoutCustomGroup() {
        // 白名单包名（微信）+ 广告词内容 + 无自定义组 → AD_BLOCKED
        val outcome = BillParseEngine.parseWithReason(
            pkg = "com.tencent.mm",
            title = "微信",
            text = "恭喜您获得重疾保障，安心住院不花钱，立即领取，保费9.9元。",
            occurredAt = at
        )
        assertNull(outcome.bill)
        assertEquals(ParseRejectReason.AD_BLOCKED, outcome.reason)
    }

    @Test
    fun notificationPathCustomGroupOverridesAdBlock() {
        // 白名单包名 + 广告词内容 + 自定义组（通知作用域）→ 放行记账
        val groups = listOf(CustomMatchGroup(listOf("保障", "安心"), CustomKeywordScope.NOTIFICATION))
        val parsed = BillParseEngine.parse(
            pkg = "com.tencent.mm",
            title = "微信",
            text = "恭喜您获得重疾保障，安心住院不花钱，立即领取，保费9.9元。",
            occurredAt = at,
            customGroups = groups
        )
        assertNotNull(parsed)
        assertEquals(990L, parsed!!.amount) // 9.9元 = 990 分
    }

    // ---------- 纯函数 ----------

    @Test
    fun matchCustomGroupRequiresAllKeywords() {
        assertTrue(
            BillParseEngine.matchCustomGroup(
                "【中国农业银行】您完成抖音支付交易",
                listOf(CustomMatchGroup(listOf("中国农业银行", "抖音", "交易"), CustomKeywordScope.ALL)),
                CustomKeywordScope.SMS
            )
        )
        // 缺「抖音」→ 组内未全中
        assertFalse(
            BillParseEngine.matchCustomGroup(
                "【中国农业银行】您完成微信支付交易",
                listOf(CustomMatchGroup(listOf("中国农业银行", "抖音"), CustomKeywordScope.SMS)),
                CustomKeywordScope.SMS
            )
        )
        // 空关键词组永不匹配
        assertFalse(
            BillParseEngine.matchCustomGroup(
                "任意内容",
                listOf(CustomMatchGroup(emptyList(), CustomKeywordScope.ALL)),
                CustomKeywordScope.SMS
            )
        )
    }

    @Test
    fun inputParsingSplitsAndNormalizesSemicolon() {
        // 输入解析（UI 同款逻辑）：中文分号 → 英文分号，trim，过滤空串
        val raw = "中国农业银行；抖音;  交易;"
        val keywords = raw.replace('；', ';').split(';').map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(listOf("中国农业银行", "抖音", "交易"), keywords)
    }
}

package com.xl.bill.mint.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解析引擎纯 JVM 单测：覆盖支付宝/微信/银行三类模板、
 * 千分位容错、收支方向、商户提取与非法输入。
 */
class BillParseEngineTest {

    @Test
    fun alipayExpense() {
        val r = BillParseEngine.parse("com.eg.android.AlipayGphone", "支付宝", "您已成功付款¥99.00")
        assertNotNull(r)
        assertEquals(9900L, r!!.amount)
        assertEquals(ParsedBill.TYPE_EXPENSE, r.type)
        assertEquals(Channel.ALIPAY, r.channel)
    }

    @Test
    fun alipayIncomeTransfer() {
        val r = BillParseEngine.parse("com.eg.android.AlipayGphone", "支付宝", "收到一笔转账¥200.00")
        assertNotNull(r)
        assertEquals(20000L, r!!.amount)
        assertEquals(ParsedBill.TYPE_INCOME, r.type)
    }

    @Test
    fun alipayRefundIsIncome() {
        val r = BillParseEngine.parse("com.eg.android.AlipayGphone", "退款到账", "您的退款¥35.00已退回原支付方式")
        assertNotNull(r)
        assertEquals(3500L, r!!.amount)
        assertEquals(ParsedBill.TYPE_INCOME, r.type)
    }

    @Test
    fun wechatExpenseWithMerchantLine() {
        val r = BillParseEngine.parse(
            "com.tencent.mm", "微信支付凭证", "支出\n¥12.50\n星巴克(深圳湾店)"
        )
        assertNotNull(r)
        assertEquals(1250L, r!!.amount)
        assertEquals(ParsedBill.TYPE_EXPENSE, r.type)
        assertEquals("星巴克(深圳湾店)", r.merchant)
    }

    @Test
    fun wechatIncome() {
        val r = BillParseEngine.parse("com.tencent.mm", "微信支付凭证", "收入\n¥30.00")
        assertNotNull(r)
        assertEquals(3000L, r!!.amount)
        assertEquals(ParsedBill.TYPE_INCOME, r.type)
    }

    @Test
    fun wechatYuanFallback() {
        val r = BillParseEngine.parse("com.tencent.mm", "微信支付凭证", "向海底捞付款50.00元")
        assertNotNull(r)
        assertEquals(5000L, r!!.amount)
        assertEquals("海底捞", r.merchant)
    }

    @Test
    fun bankExpenseWithThousandsSeparator() {
        val r = BillParseEngine.parse(
            "com.icbc", "工行信使",
            "您尾号8888的储蓄卡支出(消费)人民币5,000.00元,余额12,345.67元"
        )
        assertNotNull(r)
        assertEquals(500000L, r!!.amount)
        assertEquals(ParsedBill.TYPE_EXPENSE, r.type)
    }

    @Test
    fun bankIncome() {
        val r = BillParseEngine.parse("com.chinamworld.main", "建行短信", "您的账户收到转账10,000.00元,请注意查收")
        assertNotNull(r)
        assertEquals(1000000L, r!!.amount)
        assertEquals(ParsedBill.TYPE_INCOME, r.type)
    }

    @Test
    fun merchantFromToPattern() {
        val r = BillParseEngine.parse("com.eg.android.AlipayGphone", "支付宝", "向星巴克付款¥35.00")
        assertNotNull(r)
        assertEquals("星巴克", r!!.merchant)
    }

    @Test
    fun noAmountReturnsNull() {
        assertNull(BillParseEngine.parse("com.eg.android.AlipayGphone", "支付宝", "您有一条新消息"))
    }

    @Test
    fun unsupportedPackageReturnsNull() {
        assertNull(BillParseEngine.parse("com.unknown.app", "某App", "您已成功付款¥99.00"))
    }

    @Test
    fun emptyTextReturnsNull() {
        assertNull(BillParseEngine.parse("com.tencent.mm", "", ""))
    }

    @Test
    fun amountOverCapReturnsNull() {
        assertNull(BillParseEngine.parse("com.tencent.mm", "微信支付凭证", "支出¥500000000.00"))
    }

    @Test
    fun extractAmountStripsComma() {
        assertEquals(123456L, BillParseEngine.extractAmountFen("¥1,234.56"))
        assertEquals(500000L, BillParseEngine.extractAmountFen("支取5,000.00元"))
    }

    @Test
    fun directionDefaultsToExpense() {
        // 无方向关键词时默认支出（支付通知 90% 为支出）
        val r = BillParseEngine.parse("com.eg.android.AlipayGphone", "支付宝", "¥66.00")
        assertNotNull(r)
        assertEquals(ParsedBill.TYPE_EXPENSE, r!!.type)
        assertTrue(r.amount > 0)
    }

    // ================== 微信转账/红包场景（无障碍兜底） ==================

    @Test
    fun transferToYouIsIncome() {
        // 关键回归：文本含支出词「向」，但「向你转账」必须判收入
        val r = BillParseEngine.parse("com.tencent.mm", "微信", "小梁向你转账¥0.01")
        assertNotNull(r)
        assertEquals(ParsedBill.TYPE_INCOME, r!!.type)
        assertEquals("小梁", r.merchant)
    }

    @Test
    fun pleaseCollectIsIncome() {
        val r = BillParseEngine.parse("com.tencent.mm", "小梁", "请收款 ¥0.01")
        assertNotNull(r)
        assertEquals(ParsedBill.TYPE_INCOME, r!!.type)
    }

    @Test
    fun transferSuccessIsExpense() {
        val r = BillParseEngine.parse("com.tencent.mm", "微信", "转账成功 ¥0.01")
        assertNotNull(r)
        assertEquals(ParsedBill.TYPE_EXPENSE, r!!.type)
    }

    @Test
    fun sentRedPacketIsExpense() {
        // 含收入词「红包」，但「你发出的红包」必须判支出
        val r = BillParseEngine.parse("com.tencent.mm", "微信", "你发出的红包¥6.66已被领取")
        assertNotNull(r)
        assertEquals(ParsedBill.TYPE_EXPENSE, r!!.type)
    }

    @Test
    fun receivedRedPacketIsIncome() {
        val r = BillParseEngine.parse("com.tencent.mm", "微信", "红包已领取 ¥6.66")
        assertNotNull(r)
        assertEquals(ParsedBill.TYPE_INCOME, r!!.type)
    }

    @Test
    fun accessibilityScenePleaseCollect() {
        val r = BillParseEngine.parseAccessibilityScene(
            "com.tencent.mm",
            "小梁\n请收款\n¥0.01",
            occurredAt = 1_700_000_000_000L
        )
        assertNotNull(r)
        assertEquals(1L, r!!.amount)
        assertEquals(ParsedBill.TYPE_INCOME, r.type)
    }

    @Test
    fun accessibilitySceneNearestAmount() {
        // 聊天页多个金额：取「请收款」动作词最近的金额（¥0.01，距离1行）
        val r = BillParseEngine.parseAccessibilityScene(
            "com.tencent.mm",
            "¥0.01\n请收款\n¥5.00\n已被接收",
            occurredAt = 1_700_000_000_000L
        )
        assertNotNull(r)
        assertEquals(1L, r!!.amount)
    }

    @Test
    fun accessibilitySceneChatScrollIgnored() {
        // 无动作词：聊天页滚动不记账
        assertNull(
            BillParseEngine.parseAccessibilityScene("com.tencent.mm", "小梁\n[转账] 请收款\n[红包]")
        )
    }

    @Test
    fun isTransferScenePositiveAndNegative() {
        assertTrue(BillParseEngine.isTransferScene("请收款 ¥0.01"))
        assertTrue(BillParseEngine.isTransferScene("转账成功 ¥0.01"))
        assertTrue(BillParseEngine.isTransferScene("收款成功 ¥0.01"))
        assertTrue(BillParseEngine.isTransferScene("交易成功 ¥0.01"))
        // 导航页/余额页不是交易动作，不得作为场景
        assertTrue(!BillParseEngine.isTransferScene("零钱明细 ¥4065.33"))
        assertTrue(!BillParseEngine.isTransferScene("小梁: 晚上一起吃饭吗"))
        assertTrue(!BillParseEngine.isTransferScene(null))
    }

    @Test
    fun extractSceneAmountUniqueOnly() {
        // 详情页同金额重复展示（大字+列表）：唯一值=1 直接用
        assertEquals(
            1L,
            BillParseEngine.extractSceneAmount(listOf("¥0.01", "请收款", "¥0.01"))
        )
    }

    @Test
    fun fullWidthAmount() {
        assertEquals(1L, BillParseEngine.extractAmountFen("¥０．０１"))
        assertEquals(123400L, BillParseEngine.extractAmountFen("¥１,２３４"))
    }

    @Test
    fun balancePageIgnored() {
        // 零钱余额页：余额 + 多笔历史明细，绝不能记账（曾误记 ¥4065.33 余额为收入）
        assertNull(
            BillParseEngine.parseAccessibilityScene(
                "com.tencent.mm",
                "零钱\n零钱余额\n¥4065.33\n全部账单\n¥0.01 转账\n¥1.00 红包"
            )
        )
    }

    @Test
    fun navigationPageIgnored() {
        // 「零钱明细」曾是场景词导致整页误记，现已移除
        assertNull(
            BillParseEngine.parseAccessibilityScene(
                "com.tencent.mm",
                "零钱明细\n¥4065.33\n¥0.01"
            )
        )
    }

    @Test
    fun transferDetailPageRecorded() {
        // 转账详情页（单金额 + 动作词）正常记账：1 分支出（转账方视角）
        val r = BillParseEngine.parseAccessibilityScene(
            "com.tencent.mm",
            "小梁\n¥0.01\n已被接收",
            occurredAt = 1_700_000_000_000L
        )
        assertNotNull(r)
        assertEquals(1L, r!!.amount)
        assertEquals(ParsedBill.TYPE_EXPENSE, r.type)
    }

    // ================== 广告过滤（通知/短信入口） ==================

    @Test
    fun adNotificationRejected() {
        // 报告案例：保险广告「补齐住院保障更安心1元」含金额「1元」但无交易动作词 → 拒绝
        assertNull(
            BillParseEngine.parse("com.tencent.mm", "微信支付", "补齐住院保障更安心1元")
        )
        // 纯营销广告同样拒绝
        assertNull(
            BillParseEngine.parse("com.tencent.mm", "微信支付", "限时特惠 全场9.9元 领券立减")
        )
    }

    @Test
    fun adNotificationWithCustomWordRejected() {
        // 自定义过滤词：命中即拒（无需内置词）
        assertNull(
            BillParseEngine.parse(
                "com.tencent.mm", "微信支付", "专属福利 5元红包等你拿",
                blockedWords = listOf("专属福利")
            )
        )
    }

    @Test
    fun realPaymentNotBlockedByAdWords() {
        // 真实支付含交易动作词「支付成功」→ 即使文本含「保障/住院」类词也不拦截
        val r = BillParseEngine.parse("com.tencent.mm", "微信支付凭证", "XX医院支付成功 5000元")
        assertNotNull(r)
        assertEquals(500000L, r!!.amount)
        // 保险缴费类真实支出（含「扣款」）放行
        val insurance = BillParseEngine.parse(
            "com.tencent.mm", "微信支付凭证", "平安保险 保费自动扣款 500.00元"
        )
        assertNotNull(insurance)
        assertEquals(ParsedBill.TYPE_EXPENSE, insurance!!.type)
    }

    @Test
    fun adWordWithoutMoneyStillNull() {
        // 广告词但无金额：parse 本就返回 null（验证不回归）
        assertNull(BillParseEngine.parse("com.tencent.mm", "微信支付", "免费领取保障"))
    }

    @Test
    fun isAdNotificationCombinesBuiltinAndCustom() {
        assertTrue(BillParseEngine.isAdNotification("补齐住院保障更安心1元"))
        assertTrue(BillParseEngine.isAdNotification("免费领券"))
        assertTrue(BillParseEngine.isAdNotification("专属福利", listOf("专属福利")))
        // 含交易动作词 → 不拦截
        assertTrue(!BillParseEngine.isAdNotification("支付成功 5000元"))
        assertTrue(!BillParseEngine.isAdNotification("转账成功 100元"))
        // 无广告词 → 不拦截
        assertTrue(!BillParseEngine.isAdNotification("星巴克 消费35元"))
    }

    @Test
    fun adSmsRejected() {
        // 短信入口广告门禁一致
        assertNull(BillParseEngine.parseSms("10690001", "点击领取免费保障1元"))
        // 真实消费短信（含「消费」交易词）不受影响
        val r = BillParseEngine.parseSms("95588", "您尾号8888卡消费35.00元，余额1000.00元")
        assertNotNull(r)
        assertEquals(3500L, r!!.amount)
    }
}

package com.xl.bill.mint.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 微信账单 Excel 解析 JVM 单测。
 *
 * 直接构造单元格矩阵（行×列，空为 null），模拟 XlsxWorkbookReader 的输出。
 * 矩阵结构：元信息区（可省略）+ 表头行 + 数据行。
 */
class ExcelBillParserTest {

    /** 表头行（与真实微信 Excel 一致，A=交易时间 … K=备注） */
    private val header = listOf(
        "交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)",
        "支付方式", "当前状态", "交易单号", "商户单号", "备注"
    )

    private fun parse(vararg dataRows: List<String?>) =
        ExcelBillParser.parse(listOf(header) + dataRows.toList())

    /** 构造 11 列数据行，缺省列填 null */
    private fun row(
        time: String, type: String = "", counterparty: String = "", goods: String = "",
        direction: String = "", amount: String = "", payMethod: String = "", status: String = "",
        txNo: String = "", merchantNo: String = "", note: String = ""
    ): List<String?> = listOf(time, type, counterparty, goods, direction, amount, payMethod, status, txNo, merchantNo, note)

    // ------------------------------------------------------------------
    // 1-3：收支 / 中性方向
    // ------------------------------------------------------------------

    @Test
    fun expenseRow_parsesCorrectly() {
        val r = parse(row(
            time = "46238.75121527778", type = "商户消费", counterparty = "朴朴超市",
            goods = "朴朴商品订单", direction = "支出", amount = "65.98",
            payMethod = "招商银行储蓄卡(8498)", status = "支付成功",
            txNo = "4200003210202606134899084559", merchantNo = "0746781311548212PAY01", note = "/"
        ))
        assertEquals(1, r.rows.size)
        val bill = r.rows[0]
        assertEquals(6598L, bill.amountFen)
        assertEquals(ParsedBill.TYPE_EXPENSE, bill.type)
        assertEquals(false, bill.wasNeutral)
        assertEquals("朴朴超市", bill.merchant)
        assertEquals("朴朴商品订单", bill.note)      // 商品列优先于备注
        assertEquals("商户消费", bill.tradeType)
        assertEquals("招商银行储蓄卡(8498)", bill.paymentMethod)
        assertEquals(1785837705000L, bill.occurredAt)   // 2026-08-04 18:01:45 +08
    }

    @Test
    fun incomeRow_parsesCorrectly() {
        val r = parse(row(
            time = "46238.750393518516", type = "转账", counterparty = "小梁",
            goods = "转账备注:微信转账", direction = "收入", amount = "0.01",
            payMethod = "/", status = "已存入零钱", txNo = "1000050001202608040521920326674", note = "/"
        ))
        assertEquals(1, r.rows.size)
        val bill = r.rows[0]
        assertEquals(ParsedBill.TYPE_INCOME, bill.type)
        assertEquals(1L, bill.amountFen)
        assertEquals("小梁", bill.merchant)
    }

    @Test
    fun neutralRecharge_filtered() {
        // 用户确认（2026-08-07）：中性（收/支列为 "/"）记录不再映射导入，一律过滤
        val r = parse(row(
            time = "46238.72634259259", type = "零钱充值", counterparty = "XX银行(1234)",
            goods = "/", direction = "/", amount = "50.00",
            payMethod = "XX银行(1234)", status = "支付成功", txNo = "1000050001202608040125876990518", note = "/"
        ))
        assertEquals(0, r.rows.size)
        assertEquals(1, r.unrecognizedCount)
    }

    @Test
    fun neutralWithdraw_filtered() {
        val r = parse(row(
            time = "46238.72634259259", type = "零钱提现", counterparty = "/",
            goods = "提现", direction = "/", amount = "100.00",
            payMethod = "XX银行(1234)", status = "提现到账", txNo = "1000050001202608040125876990518", note = "手续费¥0.10"
        ))
        assertEquals(0, r.rows.size)
        assertEquals(1, r.unrecognizedCount)
    }

    // ------------------------------------------------------------------
    // 4：序列号转时间
    // ------------------------------------------------------------------

    @Test
    fun serialToEpochMillis_fixedUtc8() {
        // 与 Python 实测一致（UTC+8），不依赖系统时区
        assertEquals(1785837705000L, ExcelBillParser.serialToEpochMillis(46238.75121527778))  // 2026-08-04 18:01:45
        assertEquals(1781311551000L, ExcelBillParser.serialToEpochMillis(46186.36517361111))  // 2026-06-13 08:45:51
        // 整数整天 → 00:00:00 +08
        assertEquals(1778342400000L, ExcelBillParser.serialToEpochMillis(46152.0))             // 2026-05-10 00:00:00
    }

    @Test
    fun textTimeFormat_fallbackParsed() {
        // 兜底文本日期（微信未来改格式）
        val r = parse(row(
            time = "2026-08-04 18:01:45", type = "转账", counterparty = "A",
            goods = "/", direction = "收入", amount = "1.00", txNo = "123456789012"
        ))
        assertEquals(1, r.rows.size)
        assertEquals(1785837705000L, r.rows[0].occurredAt)
    }

    @Test
    fun invalidTime_excluded() {
        val r = parse(row(time = "99999999", direction = "支出", amount = "1.00", txNo = "123456789012"))
        assertEquals(0, r.rows.size)
        assertEquals(1, r.unrecognizedCount)
    }

    // ------------------------------------------------------------------
    // 5：浮点金额精度
    // ------------------------------------------------------------------

    @Test
    fun amountPrecision_round() {
        assertEquals(1L, ExcelBillParser.amountYuanToFen("0.01"))
        assertEquals(6598L, ExcelBillParser.amountYuanToFen("65.98"))
        assertEquals(6599L, ExcelBillParser.amountYuanToFen("65.99"))
        assertEquals(100000000L, ExcelBillParser.amountYuanToFen("1000000"))
        assertEquals(null, ExcelBillParser.amountYuanToFen("/"))
        assertEquals(null, ExcelBillParser.amountYuanToFen(""))
        assertEquals(null, ExcelBillParser.amountYuanToFen("0"))
    }

    @Test
    fun missingAmount_excluded() {
        val r = parse(row(time = "46238.75121527778", direction = "支出", amount = "/", txNo = "123456789012"))
        assertEquals(0, r.rows.size)
        assertEquals(1, r.unrecognizedCount)
    }

    // ------------------------------------------------------------------
    // 6：表头定位 / 元信息跳过
    // ------------------------------------------------------------------

    @Test
    fun metaInfoAndHeader_skipped() {
        val metaLines = listOf(
            listOf("微信支付账单明细"),
            listOf("微信昵称：[小梁同学]"),
            listOf("共7笔记录"),
            listOf("收入：5笔 0.05元"),
            listOf("----------------------微信支付账单明细列表--------------------")
        )
        val matrix = metaLines + listOf(header) + listOf(
            row(time = "46238.75121527778", type = "转账", counterparty = "A", direction = "收入", amount = "0.01", txNo = "1000050001202608040323896503035")
        )
        val r = ExcelBillParser.parse(matrix)
        assertEquals(1, r.rows.size)          // 元信息 + 分隔线 + 表头不产生记录
        assertEquals(0, r.unrecognizedCount)  // 表头之前行不算 unrecognized
    }

    @Test
    fun noHeader_emptyResult() {
        val matrix = listOf(
            listOf("微信支付账单明细"),
            listOf("微信昵称：[小梁同学]")
        )
        val r = ExcelBillParser.parse(matrix)
        assertEquals(0, r.rows.size)
        assertEquals(2, r.unrecognizedCount)   // 无表头 → 所有有内容行视为未识别
    }

    // ------------------------------------------------------------------
    // 7：缺列容错
    // ------------------------------------------------------------------

    @Test
    fun sparseRow_withoutDirection_filtered() {
        // 数据行只有 A(时间)/F(金额)/I(单号)，其余列 null；无方向（中性）→ 过滤
        val sparse = listOf<String?>("46238.75121527778", null, null, null, null, "65.98", null, null, "123456789012", null, null)
        val r = parse(sparse)
        assertEquals(0, r.rows.size)
        assertEquals(1, r.unrecognizedCount)
    }

    // ------------------------------------------------------------------
    // 8-9：去重键
    // ------------------------------------------------------------------

    @Test
    fun dedupKey_txNoAndComposite() {
        val r = parse(
            row(time = "46238.75121527778", direction = "支出", amount = "1.00", txNo = "4200003210202606134899084559"),
            row(time = "46238.750393518516", direction = "支出", amount = "2.00", txNo = "/")
        )
        assertEquals(2, r.rows.size)
        assertTrue(r.rows[0].notificationKey.startsWith("pdf-wechat-txno-"))
        assertTrue(r.rows[1].notificationKey.startsWith("pdf-wechat-"))
        assertTrue(!r.rows[1].notificationKey.startsWith("pdf-wechat-txno-"))
    }

    @Test
    fun dedupKey_stableForSameInput() {
        val rows = listOf(
            row(time = "46238.75121527778", direction = "支出", amount = "1.00", txNo = "1234567890123456"),
            row(time = "46238.750393518516", direction = "支出", amount = "2.00", txNo = "/")
        )
        val r1 = ExcelBillParser.parse(listOf(header) + rows)
        val r2 = ExcelBillParser.parse(listOf(header) + rows)
        assertEquals(r1.rows.map { it.notificationKey }, r2.rows.map { it.notificationKey })
    }

    @Test
    fun duplicatedTxNo_fallsBackToCompositeKey() {
        val sameTxNo = "1000050001202608040323896503035"
        val r = parse(
            row(time = "46238.75121527778", type = "转账", counterparty = "A", direction = "收入", amount = "0.01", txNo = sameTxNo),
            row(time = "46238.750393518516", type = "转账", counterparty = "B", direction = "收入", amount = "0.01", txNo = sameTxNo)
        )
        assertEquals(2, r.rows.size)
        val keys = r.rows.map { it.notificationKey }
        assertTrue(keys.none { it.startsWith("pdf-wechat-txno-") })
        assertNotEquals(keys[0], keys[1])
    }

    @Test
    fun fullWidthTxNo_normalized() {
        val r = parse(row(
            time = "46238.75121527778", direction = "支出", amount = "1.00",
            txNo = "４２００００３２１０２０２６０６１３４８９９０８４５５９"
        ))
        assertEquals(1, r.rows.size)
        assertEquals(
            "pdf-wechat-txno-4200003210202606134899084559",
            r.rows[0].notificationKey
        )
    }

    // ------------------------------------------------------------------
    // 10：无效行统计
    // ------------------------------------------------------------------

    @Test
    fun invalidRows_counted() {
        val r = parse(
            row(time = "/", direction = "支出", amount = "1.00", txNo = "123456789012"),       // 缺时间
            row(time = "46238.75121527778", direction = "支出", amount = "/", txNo = "123456789013"),  // 缺金额
            row(time = "46238.75121527778", direction = "支出", amount = "3.00", txNo = "123456789014") // 有效
        )
        assertEquals(1, r.rows.size)
        assertEquals(2, r.unrecognizedCount)
    }
}

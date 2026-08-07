package com.xl.bill.mint.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 支付宝账单 CSV 解析 JVM 单测。
 *
 * 直接构造单元格矩阵（行×列，空为 null），模拟 CsvReader 的输出。
 * 矩阵结构与真实支付宝导出文件一致（2026-08-07 实测验证）：
 * 元信息区（可省略）+ 表头行 + 数据行。
 */
class AlipayCsvParserTest {

    /** 表头行（与真实支付宝 CSV 一致，A=交易时间 … K=备注） */
    private val header = listOf(
        "交易时间", "交易分类", "对方账号", "商品说明", "收/支", "金额",
        "收/付款方式", "交易状态", "交易订单号", "商家订单号", "备注"
    )

    private fun parse(vararg dataRows: List<String?>) =
        AlipayCsvParser.parse(listOf(header) + dataRows.toList())

    /** 构造 11 列数据行，缺省列填 null */
    private fun row(
        time: String, category: String = "", counterparty: String = "", goods: String = "",
        direction: String = "", amount: String = "", payMethod: String = "", status: String = "",
        txNo: String = "", merchantNo: String = "", note: String = ""
    ): List<String?> =
        listOf(time, category, counterparty, goods, direction, amount, payMethod, status, txNo, merchantNo, note)

    // ------------------------------------------------------------------
    // 1：支出 / 收入解析（真实文件样例）
    // ------------------------------------------------------------------

    @Test
    fun expenseRow_parsesCorrectly() {
        // 真实行：分账-基础软件服务费扣款
        val r = parse(row(
            time = "2026-07-31 18:34:39", category = "商业服务", counterparty = "xys***@service.aliyun.com",
            goods = "分账-基础软件服务费(3314353225715072492)扣款", direction = "支出", amount = "0.53",
            payMethod = "", status = "交易成功", txNo = "2026073110032004450254949967", merchantNo = "", note = ""
        ))
        assertEquals(1, r.rows.size)
        val bill = r.rows[0]
        assertEquals(53L, bill.amountFen)
        assertEquals(ParsedBill.TYPE_EXPENSE, bill.type)
        assertEquals(false, bill.wasNeutral)
        assertEquals("商业服务", bill.merchant)   // 交易分类 → 标题
        assertEquals("分账-基础软件服务费(3314353225715072492)扣款", bill.note)   // 商品说明 → 备注（不截断）
        assertEquals("商业服务", bill.tradeType)
        assertEquals(1785494079000L, bill.occurredAt)   // 2026-07-31 18:34:39 UTC+8
        assertEquals("pdf-alipay-txno-2026073110032004450254949967", bill.notificationKey)
    }

    @Test
    fun incomeRow_parsesCorrectly() {
        // 真实行：卖出华为平板，商品说明超长不截断（进备注）
        val r = parse(row(
            time = "2026-07-24 14:40:52", category = "收入", counterparty = "158******95",
            goods = "华为M3青春版平板，8寸，骁龙435，3+32G，安卓7.0", direction = "收入", amount = "89.10",
            payMethod = "", status = "交易成功", txNo = "2026072423001102451435480995",
            merchantNo = "T200P3314353225715072492", note = ""
        ))
        assertEquals(1, r.rows.size)
        val bill = r.rows[0]
        assertEquals(8910L, bill.amountFen)
        assertEquals(ParsedBill.TYPE_INCOME, bill.type)
        assertEquals("收入", bill.merchant)   // 交易分类 → 标题
        assertEquals("华为M3青春版平板，8寸，骁龙435，3+32G，安卓7.0", bill.note)   // 商品说明全量进备注
        assertEquals(1784875252000L, bill.occurredAt)   // 2026-07-24 14:40:52 UTC+8
    }

    @Test
    fun merchant_fallbackChain() {
        // 交易分类为空 → 回退商品说明 → 再回退对方账号
        val r = parse(
            row(time = "2026-07-22 22:11:03", category = "", counterparty = "xia***@163.com",
                goods = "小兔充充余额充值", direction = "支出", amount = "9.99",
                status = "交易成功", txNo = "2026072223001441021413741787", note = ""),
            row(time = "2026-07-21 12:05:57", category = "", counterparty = "e50***@alibaba-inc.com",
                goods = "", direction = "支出", amount = "12.50",
                status = "交易成功", txNo = "2026072123001141021403559287", note = "")
        )
        assertEquals(2, r.rows.size)
        assertEquals("小兔充充余额充值", r.rows[0].merchant)      // 分类空 → 商品说明
        assertEquals("e50***@alibaba-inc.com", r.rows[1].merchant)   // 分类、商品都空 → 对方账号
    }

    @Test
    fun note_joinsGoodsAndKColumn() {
        // 商品说明 + K 列备注拼接；K 空仅商品说明；商品空用 K 列
        val r = parse(
            row(time = "2026-07-31 18:34:39", category = "商业服务", goods = "服务费扣款", direction = "支出",
                amount = "0.53", status = "交易成功", txNo = "2026073110032004450254949967", note = "自定义备注"),
            row(time = "2026-07-24 14:40:52", category = "收入", goods = "卖平板", direction = "收入",
                amount = "89.10", status = "交易成功", txNo = "2026072423001102451435480995", note = ""),
            row(time = "2026-07-23 12:14:42", category = "餐饮美食", goods = "", direction = "支出",
                amount = "6.80", status = "交易成功", txNo = "2026072323001141021408304715", note = "仅备注列")
        )
        assertEquals(3, r.rows.size)
        assertEquals("服务费扣款 | 自定义备注", r.rows[0].note)
        assertEquals("卖平板", r.rows[1].note)
        assertEquals("仅备注列", r.rows[2].note)
    }

    // ------------------------------------------------------------------
    // 2：过滤规则（不计收支 / 交易关闭）
    // ------------------------------------------------------------------

    @Test
    fun neutralRow_filtered() {
        // 余额宝-自动转入：不计收支 → 过滤
        val r = parse(row(
            time = "2026-08-01 00:50:48", category = "投资理财", counterparty = "hua***@htffund.com",
            goods = "余额宝-自动转入", direction = "不计收支", amount = "87.23",
            payMethod = "账户余额", status = "交易成功", txNo = "20260801019130101000020091724854",
            merchantNo = "20260801009130501000020046622405", note = ""
        ))
        assertEquals(0, r.rows.size)
        assertEquals(1, r.unrecognizedCount)
    }

    @Test
    fun closedStatus_filtered() {
        // 交易关闭（未完成交易）→ 过滤
        val r = parse(row(
            time = "2026-07-14 10:13:57", category = "收入", counterparty = "/",
            goods = "云账户", direction = "收入", amount = "4000.00",
            payMethod = "", status = "交易关闭", txNo = "20260714020070011530020004189307",
            merchantNo = "1216428366417613323", note = ""
        ))
        assertEquals(0, r.rows.size)
        assertEquals(1, r.unrecognizedCount)
    }

    @Test
    fun refundNeutral_filtered() {
        // 退款属「不计收支」，且订单号带 * 后缀 → 同样过滤
        val r = parse(row(
            time = "2026-07-23 12:14:42", category = "退款", counterparty = "/",
            goods = "退款-盒马鲜生(港汇天地店)", direction = "不计收支", amount = "0.37",
            payMethod = "农业银行储蓄卡(1471)", status = "退款成功",
            txNo = "2026072323001141021422893447*13120601326072313661830927384", merchantNo = "", note = ""
        ))
        assertEquals(0, r.rows.size)
        assertEquals(1, r.unrecognizedCount)
    }

    @Test
    fun mixed_rowsAndUnrecognized() {
        // 3 有效 + 2 过滤：统计正确，index 只对有効行递增
        val r = parse(
            row(time = "2026-08-01 00:50:48", goods = "余额宝-自动转入", direction = "不计收支", amount = "87.23", status = "交易成功", txNo = "20260801019130101000020091724854"),
            row(time = "2026-07-31 18:34:39", goods = "服务费扣款", direction = "支出", amount = "0.53", status = "交易成功", txNo = "2026073110032004450254949967"),
            row(time = "2026-07-24 14:40:52", goods = "卖平板", direction = "收入", amount = "89.10", status = "交易成功", txNo = "2026072423001102451435480995"),
            row(time = "2026-07-23 12:14:42", goods = "退款", direction = "不计收支", amount = "0.37", status = "退款成功", txNo = "2026072323001141021422893447"),
            row(time = "2026-07-22 22:11:03", goods = "小兔充充", direction = "支出", amount = "9.99", status = "交易成功", txNo = "2026072223001441021413741787")
        )
        assertEquals(3, r.rows.size)
        assertEquals(2, r.unrecognizedCount)
        assertEquals(listOf(0, 1, 2), r.rows.map { it.index })   // index 只对成功行递增
    }

    // ------------------------------------------------------------------
    // 3：时间 / 金额
    // ------------------------------------------------------------------

    @Test
    fun toEpochMillis_fixedUtc8() {
        // 与 Python 实测一致（Asia/Shanghai），不依赖系统时区
        assertEquals(1785516648000L, AlipayCsvParser.toEpochMillis("2026-08-01 00:50:48"))
        assertEquals(1785494079000L, AlipayCsvParser.toEpochMillis("2026-07-31 18:34:39"))
        assertEquals(null, AlipayCsvParser.toEpochMillis("2026-13-01 00:50:48"))   // 非法月份
        assertEquals(null, AlipayCsvParser.toEpochMillis(""))
        assertEquals(null, AlipayCsvParser.toEpochMillis("   "))
    }

    @Test
    fun invalidTime_excluded() {
        val r = parse(row(time = "not-a-time", direction = "支出", amount = "1.00", status = "交易成功", txNo = "2026073110032004450254949967"))
        assertEquals(0, r.rows.size)
        assertEquals(1, r.unrecognizedCount)
    }

    @Test
    fun amountRound_precision() {
        // 元 → 分，round 防浮点误差（与微信解析同一函数）
        assertEquals(8723L, ExcelBillParser.amountYuanToFen("87.23"))
        assertEquals(1420L, ExcelBillParser.amountYuanToFen("14.20"))
        assertEquals(399L, ExcelBillParser.amountYuanToFen("3.99"))
        assertEquals(null, ExcelBillParser.amountYuanToFen("/"))
        assertEquals(null, ExcelBillParser.amountYuanToFen(""))
    }

    // ------------------------------------------------------------------
    // 4：去重键
    // ------------------------------------------------------------------

    @Test
    fun dedupKey_txNoDigits() {
        // 订单号含 tab/空格（真实文件尾随制表符）→ trim 后取全量数字
        val key = AlipayCsvParser.buildNotificationKey(
            "20260801019130101000020091724854\t", 8723L, 0, "余额宝-自动转入", 1785516648000L, 0
        )
        assertEquals("pdf-alipay-txno-20260801019130101000020091724854", key)
    }

    @Test
    fun dedupKey_refundTxNo_distinctFromOriginal() {
        // 退款单号带 * 后缀：全量数字与关联支出单不同 → 天然区分
        val refund = AlipayCsvParser.buildNotificationKey(
            "2026072323001141021422893447*13120601326072313661830927384", 37L, 1, "退款-盒马鲜生", 1785494079000L, 0
        )
        val original = AlipayCsvParser.buildNotificationKey(
            "2026072323001141021422893447", 4271L, 0, "盒马鲜生(港汇天地店)", 1785494079000L, 1
        )
        assertTrue(refund.startsWith("pdf-alipay-txno-"))
        assertTrue(original.startsWith("pdf-alipay-txno-"))
        assertTrue(refund != original)
    }

    @Test
    fun dedupKey_shortTxNo_compositeFallback() {
        // 异常短单号 → 组合键兜底（含行号区分同分钟同金额）
        val a = AlipayCsvParser.buildNotificationKey("12", 100L, 0, "商户A", 1785494079000L, 0)
        val b = AlipayCsvParser.buildNotificationKey("12", 100L, 0, "商户A", 1785494079000L, 1)
        assertTrue(a.startsWith("pdf-alipay-"))
        assertTrue(!a.startsWith("pdf-alipay-txno-"))
        assertTrue(a != b)
    }

    // ------------------------------------------------------------------
    // 5：表头定位 / 元信息跳过
    // ------------------------------------------------------------------

    @Test
    fun metaInfoAndHeader_skipped() {
        val metaLines = listOf(
            listOf("------------------------------------------------------------------------------------"),
            listOf("导出信息："),
            listOf("姓名：梁旭强"),
            listOf("起始时间：[2026-05-06 00:00:00]    终止时间：[2026-08-06 23:59:59]"),
            listOf("共83笔记录"),
            listOf("------------------------支付宝支付科技有限公司  电子客户回单------------------------")
        )
        val matrix = metaLines + listOf(header) + listOf(
            row(time = "2026-07-31 18:34:39", goods = "服务费扣款", direction = "支出", amount = "0.53", status = "交易成功", txNo = "2026073110032004450254949967")
        )
        val r = AlipayCsvParser.parse(matrix)
        assertEquals(1, r.rows.size)
        assertEquals(0, r.unrecognizedCount)   // 元信息/分隔线/表头之前行不算 unrecognized
    }

    @Test
    fun noHeader_emptyResult() {
        val matrix = listOf(
            listOf("导出信息："),
            listOf("共83笔记录")
        )
        val r = AlipayCsvParser.parse(matrix)
        assertEquals(0, r.rows.size)
        assertEquals(2, r.unrecognizedCount)   // 无表头 → 所有有内容行视为未识别
    }
}

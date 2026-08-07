package com.xl.bill.mint.billimport

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

/**
 * 通用 CSV 读取器 JVM 单测。
 * 覆盖：标准 RFC 4180（引号转义/CRLF/空字段）、GB18030/GBK 解码、UTF-8 BOM、尾部换行。
 */
class CsvReaderTest {

    private fun parseUtf8(text: String): List<List<String?>> =
        CsvReader.parseCharset(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)

    private fun parseGbk(text: String): List<List<String?>> {
        val gbk = Charset.forName("GBK")
        return CsvReader.parseCharset(ByteArrayInputStream(text.toByteArray(gbk)), gbk)
    }

    // ------------------------------------------------------------------
    // 1：基础 CSV
    // ------------------------------------------------------------------

    @Test
    fun simpleCsv_splitsFields() {
        val rows = parseUtf8("a,b,c\n1,2,3\n")
        assertEquals(2, rows.size)
        assertEquals(listOf("a", "b", "c"), rows[0])
        assertEquals(listOf("1", "2", "3"), rows[1])
    }

    @Test
    fun crlf_and_lf_mixed() {
        val rows = parseUtf8("a,b\r\nc,d\ne,f")
        assertEquals(3, rows.size)
        assertEquals(listOf("a", "b"), rows[0])
        assertEquals(listOf("c", "d"), rows[1])
        assertEquals(listOf("e", "f"), rows[2])
    }

    @Test
    fun trailingNewline_noExtraRow() {
        val rows = parseUtf8("a,b\n")
        assertEquals(1, rows.size)
    }

    @Test
    fun emptyFields_asNull() {
        val rows = parseUtf8("a,,c\n,,\n")
        assertEquals(2, rows.size)
        assertEquals(listOf("a", null, "c"), rows[0])
        assertEquals(listOf(null, null, null), rows[1])   // 空行 → 全 null，解析器层跳过
    }

    // ------------------------------------------------------------------
    // 2：引号引用与转义
    // ------------------------------------------------------------------

    @Test
    fun quotedField_withComma() {
        val rows = parseUtf8("\"a,b\",c\n")
        assertEquals(1, rows.size)
        assertEquals(listOf("a,b", "c"), rows[0])
    }

    @Test
    fun escapedQuote_doubleQuotes() {
        val rows = parseUtf8("\"say \"\"hi\"\"\",c\n")
        assertEquals(1, rows.size)
        assertEquals(listOf("say \"hi\"", "c"), rows[0])
    }

    @Test
    fun quotedMultiline_fieldKept() {
        val rows = parseUtf8("\"line1\nline2\",c\n")
        assertEquals(1, rows.size)
        assertEquals(listOf("line1\nline2", "c"), rows[0])
    }

    @Test
    fun quotedField_trailingText_looseAccept() {
        // 宽松兼容：闭合引号后直接跟字符（非 RFC 但容忍）
        val rows = parseUtf8("\"ab\"cd,e\n")
        assertEquals(listOf("abcd", "e"), rows[0])
    }

    // ------------------------------------------------------------------
    // 3：编码
    // ------------------------------------------------------------------

    @Test
    fun gbk_text_decoded() {
        // 支付宝默认导出编码（GB18030/GBK）
        val text = "交易时间,交易分类,对方账号\n2026-08-01 00:50:48,投资理财,hua***@htffund.com\n"
        val rows = parseGbk(text)
        assertEquals(2, rows.size)
        assertEquals("交易时间", rows[0][0])
        assertEquals("投资理财", rows[1][1])
        assertEquals("hua***@htffund.com", rows[1][2])
    }

    @Test
    fun utf8Bom_detected() {
        val content = "a,b\n1,2\n".toByteArray(Charsets.UTF_8)
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + content
        val rows = CsvReader.parse(ByteArrayInputStream(withBom))
        assertEquals(listOf("a", "b"), rows[0])
        assertEquals(listOf("1", "2"), rows[1])
    }

    @Test
    fun utf8Bom_singleByteFile_safe() {
        // 不足 3 字节的文件不误判 BOM、不丢字节
        val rows = CsvReader.parse(ByteArrayInputStream("x".toByteArray()))
        assertEquals(listOf("x"), rows[0])
    }

    @Test
    fun parse_autoGbk() {
        // 无 BOM → 默认 GB18030 路径（支付宝真实文件走此入口）
        val text = "交易时间,收/支,金额\n2026-08-01 00:50:48,支出,14.20\n"
        val gbk = Charset.forName("GBK")
        val rows = CsvReader.parse(ByteArrayInputStream(text.toByteArray(gbk)))
        assertEquals("交易时间", rows[0][0])
        assertEquals("支出", rows[1][1])
        assertEquals("14.20", rows[1][2])
    }

    // ------------------------------------------------------------------
    // 4：支付宝真实片段（含尾部 tab 的订单号、尾列空字段）
    // ------------------------------------------------------------------

    @Test
    fun alipayRealFragment() {
        val text = "交易时间,交易分类,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,\n" +
            "2026-08-01 00:50:48,投资理财,hua***@htffund.com,余额宝-自动转入,不计收支,87.23,账户余额,交易成功,20260801019130101000020091724854\t,20260801009130501000020046622405\t,,\n" +
            "2026-07-31 18:34:39,商业服务,xys***@service.aliyun.com,分账-基础软件服务费扣款,支出,0.53,,交易成功,2026073110032004450254949967\t,,\n"
        val rows = CsvReader.parse(ByteArrayInputStream(text.toByteArray(Charset.forName("GBK"))))
        assertEquals(3, rows.size)                       // 表头 + 2 数据
        assertEquals("交易时间", rows[0][0])
        assertEquals("余额宝-自动转入", rows[1][3])
        assertEquals("20260801019130101000020091724854\t", rows[1][8])   // 原始保留 tab，解析层 trim
        assertEquals(null, rows[1][10])                  // 尾列空字段 → null
        assertEquals("分账-基础软件服务费扣款", rows[2][3])
    }
}

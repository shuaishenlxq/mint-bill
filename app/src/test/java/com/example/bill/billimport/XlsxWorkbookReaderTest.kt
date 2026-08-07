package com.xl.bill.mint.billimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * xlsx 轻量读取器 JVM 单测：用 ZipOutputStream 构造内存 xlsx 字节，喂 ByteArrayInputStream。
 *
 * 解析器为 XmlPullParser（kxml2，Android 同源实现）——JVM 测试与真机行为一致。
 */
class XlsxWorkbookReaderTest {

    /** 构造内存 xlsx：sharedStrings + worksheet */
    private fun buildXlsx(
        sharedXml: String,
        sheetXml: String,
        entries: List<Pair<String, String>> = emptyList()
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("xl/sharedStrings.xml", sharedXml)
            put("xl/worksheets/sheet1.xml", sheetXml)
            for ((n, c) in entries) put(n, c)
        }
        return out.toByteArray()
    }

    private fun shared(vararg items: String): String {
        val body = items.joinToString("") { "<si><t>$it</t></si>" }
        return """<?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${items.size}" uniqueCount="${items.size}">$body</sst>""".trimIndent()
    }

    private fun sheet(rowsXml: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>$rowsXml</sheetData>
            </worksheet>""".trimIndent()

    private fun read(xlsx: ByteArray): List<List<String?>> =
        XlsxWorkbookReader.readSheetCells(ByteArrayInputStream(xlsx))

    // ------------------------------------------------------------------

    @Test
    fun basic_sharedStringAndNumberAndInline() {
        // A1: t="s" 共享字符串(索引0) | B1: 数字 | C1: inlineStr
        val xlsx = buildXlsx(
            sharedXml = shared("微信支付账单明细", "收入"),
            sheetXml = sheet(
                """<row r="1">
                     <c r="A1" t="s"><v>0</v></c>
                     <c r="B1"><v>46238.75121527778</v></c>
                     <c r="C1" t="inlineStr"><is><t>内联</t></is></c>
                   </row>"""
            )
        )
        val matrix = read(xlsx)
        assertEquals(1, matrix.size)
        assertEquals(listOf("微信支付账单明细", "46238.75121527778", "内联"), matrix[0])
    }

    @Test
    fun missingColumns_paddedWithNull() {
        val xlsx = buildXlsx(
            sharedXml = shared("A值", "D值"),
            sheetXml = sheet(
                """<row r="1">
                     <c r="A1" t="s"><v>0</v></c>
                     <c r="D1" t="s"><v>1</v></c>
                   </row>"""
            )
        )
        val matrix = read(xlsx)
        assertEquals(1, matrix.size)
        assertEquals(4, matrix[0].size)
        assertEquals("A值", matrix[0][0])
        assertTrue(matrix[0][1] == null)
        assertTrue(matrix[0][2] == null)
        assertEquals("D值", matrix[0][3])
    }

    @Test
    fun missingRows_orderPreserved() {
        val xlsx = buildXlsx(
            sharedXml = shared("r1", "r3"),
            sheetXml = sheet(
                """<row r="1"><c r="A1" t="s"><v>0</v></c></row>
                   <row r="3"><c r="A3" t="s"><v>1</v></c></row>"""
            )
        )
        val matrix = read(xlsx)
        assertEquals(2, matrix.size)
        assertEquals("r1", matrix[0][0])
        assertEquals("r3", matrix[1][0])
    }

    @Test
    fun multiLetterColumn_AA() {
        assertEquals(26, XlsxWorkbookReader.colIndexFromRef("AA1"))
        assertEquals(0, XlsxWorkbookReader.colIndexFromRef("A19"))
        assertEquals(10, XlsxWorkbookReader.colIndexFromRef("K18"))
        assertEquals(27, XlsxWorkbookReader.colIndexFromRef("AB1"))
    }

    @Test
    fun corruptZip_throwsBillImportException() {
        val garbage = ByteArray(256) { it.toByte() }
        assertThrows(BillImportException::class.java) {
            XlsxWorkbookReader.readSheetCells(ByteArrayInputStream(garbage))
        }
    }

    @Test
    fun missingSheet_throwsBillImportException() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(shared("x").toByteArray())
            zip.closeEntry()
        }
        assertThrows(BillImportException::class.java) {
            XlsxWorkbookReader.readSheetCells(ByteArrayInputStream(out.toByteArray()))
        }
    }

    @Test
    fun oversizedEntry_throws() {
        // 构造一个超过 20MB 的条目 → "文件过大"
        val big = ByteArray(21 * 1024 * 1024) { 'a'.code.toByte() }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(big)
            zip.closeEntry()
        }
        val e = assertThrows(BillImportException::class.java) {
            XlsxWorkbookReader.readSheetCells(ByteArrayInputStream(out.toByteArray()))
        }
        assertTrue(e.message!!.contains("过大"))
    }

    @Test
    fun sharedStringWithPreserveWhitespace() {
        val xlsx = buildXlsx(
            sharedXml = """<?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="1" uniqueCount="1">
                  <si><t xml:space="preserve"> 带空格 文本 </t></si>
                </sst>""",
            sheetXml = sheet("""<row r="1"><c r="A1" t="s"><v>0</v></c></row>""")
        )
        val matrix = read(xlsx)
        assertEquals(" 带空格 文本 ", matrix[0][0])
    }

    // ------------------------------------------------------------------
    // XmlPullParser 迁移新增用例
    // ------------------------------------------------------------------

    /** 微信真实格式：默认命名空间 + 表头/数据行（构造 1 行，验证解析正确） */
    @Test
    fun namespaceDefault_parsesCorrectly() {
        val xlsx = buildXlsx(
            sharedXml = shared("交易时间", "转账", "小梁", "收入", "0.01"),
            sheetXml = sheet(
                """<row r="1">
                     <c r="A1" t="s"><v>0</v></c>
                     <c r="B1" t="s"><v>1</v></c>
                     <c r="C1" t="s"><v>2</v></c>
                     <c r="E1" t="s"><v>3</v></c>
                     <c r="F1"><v>0.01</v></c>
                   </row>"""
            )
        )
        val matrix = read(xlsx)
        assertEquals(1, matrix.size)
        assertEquals("交易时间", matrix[0][0])
        assertEquals("转账", matrix[0][1])
        assertEquals("小梁", matrix[0][2])
        assertTrue(matrix[0][3] == null)          // D 列缺失
        assertEquals("收入", matrix[0][4])
        assertEquals("0.01", matrix[0][5])
    }

    /** 富文本：<si> 内多个 <t> 拼接 */
    @Test
    fun sharedStrings_multipleT_concatenated() {
        val xlsx = buildXlsx(
            sharedXml = """<?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="1" uniqueCount="1">
                  <si><r><t>前</t></r><r><t>后</t></r></si>
                </sst>""",
            sheetXml = sheet("""<row r="1"><c r="A1" t="s"><v>0</v></c></row>""")
        )
        val matrix = read(xlsx)
        assertEquals("前后", matrix[0][0])
    }

    /** inlineStr 单元格取值（防御性支持） */
    @Test
    fun inlineStr_cell_parsed() {
        val xlsx = buildXlsx(
            sharedXml = shared("x"),
            sheetXml = sheet(
                """<row r="1"><c r="A1" t="inlineStr"><is><t>内联文本</t></is></c></row>"""
            )
        )
        val matrix = read(xlsx)
        assertEquals("内联文本", matrix[0][0])
    }

    /** 空单元格（无 <v>）→ null */
    @Test
    fun cellWithoutV_nullValue() {
        val xlsx = buildXlsx(
            sharedXml = shared("x"),
            sheetXml = sheet(
                """<row r="1">
                     <c r="A1" t="s"><v>0</v></c>
                     <c r="B1"/>
                   </row>"""
            )
        )
        val matrix = read(xlsx)
        assertEquals(2, matrix[0].size)
        assertEquals("x", matrix[0][0])
        assertTrue(matrix[0][1] == null)
    }

    /** 损坏 XML（非合法 XML 内容）→ BillImportException */
    @Test
    fun corruptXml_throwsBillImportException() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write("not xml at all".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write("<worksheet><sheetData></worksheet>".toByteArray())
            zip.closeEntry()
        }
        assertThrows(BillImportException::class.java) {
            XlsxWorkbookReader.readSheetCells(ByteArrayInputStream(out.toByteArray()))
        }
    }
}

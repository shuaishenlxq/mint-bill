package com.xl.bill.mint.billimport

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * xlsx 轻量读取器（纯 Kotlin/JDK + org.xmlpull，可 JVM 单测）。
 *
 * 只读取微信账单 xlsx 所需的最小结构：zip 内 `xl/sharedStrings.xml` 与 `xl/worksheets/sheet1.xml`，
 * 还原为单元格矩阵（行序 = sheet 内 <row> 顺序，缺列补 null）。
 *
 * 解析器选型说明：**使用 Android 原生 XmlPullParser（kXML）而非 DOM**。
 * 历史教训：曾用 javax.xml.parsers.DocumentBuilder（DOM），JVM 单测全绿但真机（Android 的
 * harmony/Expat DOM 实现与 JDK Xerces 行为有差异）解析任何 xlsx 都抛异常报「文件无法解析」。
 * XmlPullParser 是 Android framework 内置解析器；JVM 单测用 kxml2 依赖（Android 同源实现），
 * 测试环境与真机解析行为完全一致，此类环境差异被根治。
 *
 * 不依赖 Apache POI：微信导出格式固定（单 sheet、共享字符串 + 数字单元格），零第三方运行时依赖。
 */
object XlsxWorkbookReader {

    /** 单个 XML 条目最大字节数（微信全年账单很小；防恶意 zip 炸弹/超大文件 OOM） */
    const val MAX_XML_ENTRY_BYTES = 20L * 1024 * 1024

    private const val ENTRY_SHARED_STRINGS = "xl/sharedStrings.xml"
    private const val ENTRY_SHEET = "xl/worksheets/sheet1.xml"

    /**
     * xlsx zip 输入流 → 单元格矩阵。
     *
     * @throws BillImportException 文件不是有效 xlsx / 缺 sheet / 条目过大 / XML 解析失败
     */
    fun readSheetCells(input: InputStream): List<List<String?>> {
        var sharedBytes: ByteArray? = null
        var sheetBytes: ByteArray? = null

        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    val bytes = readEntryLimited(zip)
                    when {
                        name == ENTRY_SHARED_STRINGS -> sharedBytes = bytes
                        name == ENTRY_SHEET -> sheetBytes = bytes
                        // 兜底：微信未来改 sheet 名时取第一个 worksheet
                        sheetBytes == null && name.startsWith("xl/worksheets/") && name.endsWith(".xml") ->
                            sheetBytes = bytes
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: BillImportException) {
            throw e
        } catch (e: Exception) {
            throw BillImportException("文件无法解析，请确认是微信导出的账单 Excel 文件", e)
        }

        val sheetBytesFinal = sheetBytes
            ?: throw BillImportException("文件无法解析，请确认是微信导出的账单 Excel 文件")
        val shared = sharedBytes?.let { parseSharedStrings(it) } ?: emptyList()
        return parseSheet(sheetBytesFinal, shared)
    }

    /** 读取单个 zip 条目全部字节，超过上限抛「文件过大」 */
    private fun readEntryLimited(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream(64 * 1024)
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_XML_ENTRY_BYTES) {
                throw BillImportException("文件过大，请拆分账单后重试")
            }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** 新建 XmlPullParser（kXML，Android 原生；JVM 测试用 kxml2 同源实现） */
    private fun newParser(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        return parser
    }

    /**
     * 解析共享字符串表：所有 <si> 的文本拼接（支持富文本多 <t>，保留空白）。
     * 微信实际格式 `<si><t>文本</t></si>`，逐事件累积 TEXT 即可。
     */
    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = newParser(bytes)
        val result = ArrayList<String>()
        var current: StringBuilder? = null
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG ->
                        if (parser.name == "si") current = StringBuilder()
                    XmlPullParser.TEXT ->
                        current?.append(parser.text ?: "")
                    XmlPullParser.CDSECT ->
                        current?.append(parser.text ?: "")
                    XmlPullParser.END_TAG ->
                        if (parser.name == "si") {
                            result.add(current?.toString() ?: "")
                            current = null
                        }
                }
                event = parser.next()
            }
        } catch (e: BillImportException) {
            throw e
        } catch (e: Exception) {
            throw BillImportException("文件无法解析，请确认是微信导出的账单 Excel 文件", e)
        }
        return result
    }

    /**
     * 解析 worksheet：sheetData 下所有 <row> → 单元格矩阵（缺列补 null）。
     *
     * 状态机：START_TAG <c> 记录 r/t 属性与起始列 → <v>（或 <is>）内累积文本 →
     * END_TAG </c> 按类型取值 → END_TAG </row> 收行。
     */
    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String?>> {
        val parser = newParser(bytes)
        val rows = ArrayList<List<String?>>()
        try {
            var event = parser.eventType
            var inSheetData = false
            var cells: ArrayList<String?>? = null
            var lastCol = -1
            var cellCol = -1
            var cellType: String? = null
            var inCellText = false   // 处于 <v> 或 <is> 内，累积文本
            var cellText: StringBuilder? = null

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "sheetData" -> inSheetData = true
                        "row" -> if (inSheetData) {
                            cells = ArrayList()
                            lastCol = -1
                        }
                        "c" -> if (cells != null) {
                            val ref = parser.getAttributeValue(null, "r")
                            cellCol = if (ref.isNullOrEmpty()) lastCol + 1 else colIndexFromRef(ref)
                            cellType = parser.getAttributeValue(null, "t")
                            cellText = StringBuilder()
                            inCellText = true   // c 内 v/is/t 的 TEXT 都累积
                        }
                        // <v> / <is> / <t>：inCellText 保持 true，无需区分；TEXT 事件统一累积
                    }
                    XmlPullParser.TEXT ->
                        if (inCellText && cellText != null) cellText!!.append(parser.text ?: "")
                    XmlPullParser.CDSECT ->
                        if (inCellText && cellText != null) cellText!!.append(parser.text ?: "")
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "c" -> {
                            val cols = cells ?: return rows
                            // 缺 <c> 的列补位
                            for (i in (lastCol + 1) until cellCol) cols.add(null)
                            cols.add(cellValue(cellType, cellText?.toString(), shared))
                            lastCol = cellCol
                            inCellText = false
                            cellText = null
                        }
                        "row" -> {
                            cells?.let { rows.add(it) }
                            cells = null
                        }
                        "sheetData" -> inSheetData = false
                    }
                }
                event = parser.next()
            }
        } catch (e: BillImportException) {
            throw e
        } catch (e: Exception) {
            throw BillImportException("文件无法解析，请确认是微信导出的账单 Excel 文件", e)
        }
        return rows
    }

    /** 单元格取值：t="s"→共享字符串索引；inlineStr→<is> 文本；其余→<v> 原文（数字保持字符串）；空 → null */
    private fun cellValue(type: String?, text: String?, shared: List<String>): String? {
        val t = text?.trim()
        if (t.isNullOrEmpty()) return null
        return when (type) {
            "s" -> t.toIntOrNull()?.let { shared.getOrNull(it) }
            else -> t   // inlineStr 与数字（无 t）都直接取文本
        }
    }

    /** 列引用转索引："A19" → 0，"AA1" → 26（支持多字母） */
    fun colIndexFromRef(ref: String): Int {
        var i = 0
        for (ch in ref) {
            if (ch !in 'A'..'Z') break
            i = i * 26 + (ch - 'A' + 1)
        }
        return i - 1
    }
}

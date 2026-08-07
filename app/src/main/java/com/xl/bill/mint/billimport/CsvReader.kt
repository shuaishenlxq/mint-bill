package com.xl.bill.mint.billimport

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.nio.charset.Charset

/**
 * 通用 CSV 读取器（纯 Kotlin/JDK，可 JVM 单测）。
 *
 * 为支付宝账单 CSV（GB18030 编码）而建，但实现完全通用：
 * - 编码：优先检测 UTF-8 BOM；否则按 GB18030 解码（Android minSdk 31 的 ICU 支持，
 *   异常时 fallback GBK）；UTF-8 无 BOM 文件也可显式传入 charset 处理（见 [parseCharset]）；
 * - 标准 RFC 4180 CSV：CRLF/LF 换行、`"` 引用字段（内含逗号/换行/引号）、`""` 转义内部引号、
 *   空字段保留为 null（与 xlsx 单元格矩阵 [XlsxWorkbookReader] 的输出格式对齐）；
 * - 逐字符状态机 + mark/reset 前瞻，不整文件读入内存，单行/总行数均有上限防恶意文件。
 */
object CsvReader {

    /** 最大行数（支付宝/微信账单最多几百行；防异常文件 OOM） */
    const val MAX_ROWS = 200_000

    /** 单字段最大字符数（防超长单字段内存膨胀） */
    const val MAX_FIELD_CHARS = 65_536

    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /**
     * 输入流 → 单元格矩阵（行序 = 文件行序，空字段为 null）。
     *
     * 编码自动探测：UTF-8 BOM → UTF-8；否则 GB18030（GBK 超集，支付宝导出默认编码）。
     *
     * @throws BillImportException 解析失败 / 行数或字段超限
     */
    fun parse(input: InputStream): List<List<String?>> {
        val pushback = PushbackInputStream(input, UTF8_BOM.size)
        val head = ByteArray(UTF8_BOM.size)
        val n = pushback.read(head)
        val hasBom = n == UTF8_BOM.size && head.contentEquals(UTF8_BOM)
        if (!hasBom && n > 0) {
            pushback.unread(head, 0, n)   // 非 BOM 前缀推回流，交给解码器
        }
        val charset = if (hasBom) Charsets.UTF_8 else gbCharset()
        return parseCharset(pushback, charset)
    }

    /**
     * 显式指定编码解析（UTF-8 无 BOM 文件走此入口；支付宝默认文件仍走 [parse]）。
     */
    fun parseCharset(input: InputStream, charset: Charset): List<List<String?>> {
        return parseReader(BufferedReader(InputStreamReader(input, charset)))
    }

    /** GB18030 优先，Android 老设备异常时降级 GBK */
    private fun gbCharset(): Charset = try {
        Charset.forName("GB18030")
    } catch (_: Exception) {
        Charset.forName("GBK")
    }

    /** 逐字符 RFC 4180 状态机 */
    private fun parseReader(reader: BufferedReader): List<List<String?>> {
        val rows = ArrayList<List<String?>>()
        val row = ArrayList<String?>()
        val field = StringBuilder()
        var inQuotes = false

        fun addField() {
            val s = field.toString()
            row.add(if (s.isEmpty()) null else s)
            field.setLength(0)
        }

        fun endRow() {
            addField()
            rows.add(row.toList())
            row.clear()
        }

        try {
            while (true) {
                val c = reader.read()
                if (c == -1) break

                if (inQuotes) {
                    if (c == '"'.code) {
                        // 前瞻判断："" 转义 or 闭合引号
                        reader.mark(1)
                        val next = reader.read()
                        if (next == '"'.code) {
                            field.append('"')          // "" → 字面引号
                        } else {
                            inQuotes = false            // 闭合引号，后续字符交回外层分支
                            if (next != -1) reader.reset()
                        }
                    } else {
                        field.append(c.toChar())        // 引号内保留原文（含逗号/换行）
                    }
                } else {
                    when (c) {
                        '"'.code -> {
                            // 仅字段起始位置视为引用标记；字段中段出现的引号按字面处理
                            if (field.isEmpty()) inQuotes = true else field.append('"')
                        }
                        ','.code -> addField()
                        '\r'.code -> {
                            // CRLF 或单独 CR 都算行结束；前瞻吞掉紧随的 LF
                            reader.mark(1)
                            val next = reader.read()
                            if (next != '\n'.code && next != -1) reader.reset()
                            endRow()
                        }
                        '\n'.code -> endRow()
                        else -> {
                            if (field.length >= MAX_FIELD_CHARS) {
                                throw BillImportException("文件过大，请拆分账单后重试")
                            }
                            field.append(c.toChar())
                        }
                    }
                }
            }
            if (field.isNotEmpty() || row.isNotEmpty()) endRow()   // 文件尾无换行的最后一行

            if (rows.size > MAX_ROWS) {
                throw BillImportException("文件过大，请拆分账单后重试")
            }
            return rows
        } catch (e: BillImportException) {
            throw e
        } catch (e: Exception) {
            throw BillImportException("文件无法解析，请确认是支付宝导出的账单 CSV 文件", e)
        }
    }
}

package com.xl.bill.mint.parser

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToLong

/**
 * 微信账单 Excel 解析器（纯 Kotlin，JVM 可单测）。
 *
 * 输入为单元格矩阵（[XlsxWorkbookReader] 还原的行×列，空单元格为 null），
 * 输出结构化的 [BillImportRow] 列表。
 *
 * 微信导出 Excel 特征（已实测真实文件）：
 * - 表头行：A=交易时间 | B=交易类型 | C=交易对方 | D=商品 | E=收/支 | F=金额(元) |
 *   G=支付方式 | H=当前状态 | I=交易单号 | J=商户单号 | K=备注
 * - 表头上方是元信息（昵称/起止时间/统计/注）与分隔线，按"表头之后"条件自动跳过；
 * - 交易时间为 Excel 序列号（天，基准 1899-12-30），微信标注「所有时间均为UTC+08:00」→ 固定 UTC+8 换算；
 * - 金额为元单位数字（浮点）→ 转分必须 round 防浮点误差；
 * - 收/支列：收入 / 支出 / "/"（中性）。**只导入明确的收支，中性记录一律过滤**
 *   （用户确认 2026-08-07：不再映射导入，与支付宝「不计收支」处理保持一致）。
 */
object ExcelBillParser {

    /** 交易单号最少有效数字位数（≥12 位视为可靠全局唯一，走单号去重键） */
    const val MIN_TX_NO_DIGITS = 12

    /** 微信账单固定时区（所有时间均为 UTC+08:00，绝不可用系统时区） */
    private val UTC8 = ZoneOffset.ofHours(8)

    /** Excel 序列号基准：1899-12-30T00:00 在 UTC+8 下的 epoch 秒 */
    private val BASE_EPOCH_SEC: Long = LocalDate.of(1899, 12, 30)
        .atStartOfDay(UTC8)
        .toEpochSecond()

    /** 序列号合法范围：25569≈1970-01-01，60000≈2064-04-11（越界视为无效） */
    private const val SERIAL_MIN = 25569.0
    private const val SERIAL_MAX = 60000.0

    /** 表头列索引（与微信 Excel 列序一致，A=0） */
    private const val COL_TIME = 0
    private const val COL_TYPE = 1
    private const val COL_COUNTERPARTY = 2
    private const val COL_GOODS = 3
    private const val COL_DIRECTION = 4
    private const val COL_AMOUNT = 5
    private const val COL_PAY_METHOD = 6
    private const val COL_STATUS = 7
    private const val COL_TX_NO = 8
    private const val COL_MERCHANT_NO = 9
    private const val COL_NOTE = 10

    /** 文本日期兜底正则（微信未来把时间列改成文本时的降级）：yyyy-MM-dd HH:mm:ss / yyyy/M/d H:mm:ss */
    private val TEXT_TIME_RE = Regex(
        "(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})日?\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?"
    )

    // ------------------------------------------------------------------
    // 入口
    // ------------------------------------------------------------------

    /**
     * 单元格矩阵 → 账单行列表。
     * 定位表头行（col0 含「交易时间」且整行含「金额」），表头之前（元信息/分隔线）跳过，
     * 表头之后逐行解析。
     */
    fun parse(matrix: List<List<String?>>): BillParseResult {
        val headerIndex = findHeaderIndex(matrix)
        if (headerIndex < 0) {
            // 非微信账单表 / 无表头 → 无法区分数据行，全部视为未识别
            val contentRows = matrix.count { row -> row.any { !it.isNullOrBlank() } }
            return BillParseResult(emptyList(), contentRows)
        }

        val rawRows = ArrayList<BillImportRow>()
        var unrecognized = 0
        var globalIndex = 0
        for (i in headerIndex + 1 until matrix.size) {
            val row = matrix[i]
            if (row.all { it.isNullOrBlank() }) continue   // 空行跳过，不计 unrecognized
            val parsed = parseRow(row, globalIndex)
            if (parsed != null) {
                rawRows.add(parsed)
                globalIndex++
            } else {
                unrecognized++
            }
        }
        return BillParseResult(applyDuplicatedTxNoDefense(rawRows), unrecognized)
    }

    /** 表头定位：col0 含「交易时间」且整行含「金额」（contains 免疫全角/半角括号差异） */
    fun findHeaderIndex(matrix: List<List<String?>>): Int {
        for ((i, row) in matrix.withIndex()) {
            val col0 = row.getOrNull(COL_TIME).orEmpty()
            if (col0.contains("交易时间") && row.joinToString("").contains("金额")) return i
        }
        return -1
    }

    // ------------------------------------------------------------------
    // 行解析
    // ------------------------------------------------------------------

    private fun parseRow(row: List<String?>, rowIndex: Int): BillImportRow? {
        val rawText = row.joinToString("|") { it.orEmpty().trim() }

        // 时间：序列号或文本兜底；非法 → 丢弃
        val occurredAt = toEpochMillis(row.getOrNull(COL_TIME)) ?: return null

        // 金额：元 → 分（round 防浮点）；"/"/空/非法 → 丢弃
        val amountFen = amountYuanToFen(row.getOrNull(COL_AMOUNT)) ?: return null

        val typeText = row.getOrNull(COL_TYPE).orEmpty().trim()
        val counterparty = row.getOrNull(COL_COUNTERPARTY).orEmpty().trim()
        val goods = row.getOrNull(COL_GOODS).orEmpty().trim()
        val direction = row.getOrNull(COL_DIRECTION).orEmpty().trim()
        val payMethod = row.getOrNull(COL_PAY_METHOD).orEmpty().trim()
        val txNo = normalize(row.getOrNull(COL_TX_NO).orEmpty()).trim()   // 全角数字单号 → 半角
        val noteText = row.getOrNull(COL_NOTE).orEmpty().trim()

        // 只导入明确的收支；中性（"/"、"／"、"|"、空等）一律过滤，不再映射导入
        val type = when {
            direction.contains("支出") -> ParsedBill.TYPE_EXPENSE
            direction.contains("收入") -> ParsedBill.TYPE_INCOME
            else -> return null
        }
        val wasNeutral = false   // 中性行已在上面过滤，恒 false（保留字段兼容预览统计）

        val merchant = counterparty.ifEmpty { null }?.take(24)
        val note = when {
            goods.isNotEmpty() && goods != "/" -> goods
            noteText.isNotEmpty() && noteText != "/" -> noteText
            else -> null
        }

        return BillImportRow(
            index = rowIndex,
            occurredAt = occurredAt,
            type = type,
            wasNeutral = wasNeutral,
            amountFen = amountFen,
            merchant = merchant,
            tradeType = typeText.ifEmpty { null },
            paymentMethod = payMethod.ifEmpty { null },
            note = note,
            rawText = rawText,
            notificationKey = buildNotificationKey(txNo, amountFen, type, merchant, occurredAt, rowIndex)
        )
    }

    // ------------------------------------------------------------------
    // 时间 / 金额 / 中性 / 去重键
    // ------------------------------------------------------------------

    /**
     * 交易时间 → epoch millis：
     * 优先 Excel 序列号路径（固定 UTC+8，round 消除浮点尾差，范围 [1970,2064] 校验）；
     * 非数字则走文本日期正则兜底（UTC+8 组装，同样不依赖系统时区）。
     */
    fun toEpochMillis(text: String?): Long? {
        val t = text?.trim() ?: return null
        if (t.isEmpty() || t == "/") return null

        t.toDoubleOrNull()?.let { serial ->
            if (serial < SERIAL_MIN || serial > SERIAL_MAX) return null
            return serialToEpochMillis(serial)
        }

        TEXT_TIME_RE.find(t)?.let { m ->
            val sec = m.groupValues[6].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            return try {
                LocalDateTime.of(
                    m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                    m.groupValues[4].toInt(), m.groupValues[5].toInt(), sec
                ).toEpochSecond(UTC8) * 1000L
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /** Excel 序列号（天）→ epoch millis（UTC+8 固定） */
    fun serialToEpochMillis(serial: Double): Long {
        val totalSeconds = (serial * 86_400.0).roundToLong()
        return (BASE_EPOCH_SEC + totalSeconds) * 1000L
    }

    /** 元 → 分：数字文本 round(x*100)；"/"/空/≤0 → null；非数字兜底 ¥/元 文本格式 */
    fun amountYuanToFen(text: String?): Long? {
        val t = text?.trim() ?: return null
        if (t.isEmpty() || t == "/") return null
        t.toDoubleOrNull()?.let { d ->
            if (d <= 0.0) return null
            return (d * 100.0).roundToLong()   // round 防浮点误差，绝不能用 toLong() 截断
        }
        return BillParseEngine.extractAmountFen(t)   // 兜底："¥0.01" / "0.01元"
    }

    /**
     * 去重键：
     * - 交易单号 ≥12 位数字 → `pdf-wechat-txno-<单号>`（跨文件重复导入同一笔仍被拦截）；
     * - 否则组合键（金额+方向+商户+分钟+行号），行号区分同分钟同金额同商户多笔。
     *
     * 前缀保留历史命名 `pdf-wechat-`（PDF 导入已上线，库中可能已有该前缀记录；
     * 改名会导致跨版本重复导入同一笔），**不可再改**。
     */
    fun buildNotificationKey(
        txNo: String,
        amountFen: Long,
        type: Int,
        merchant: String?,
        occurredAt: Long,
        rowIndex: Int
    ): String {
        val digits = txNo.filter { it.isDigit() }
        return if (digits.length >= MIN_TX_NO_DIGITS) "pdf-wechat-txno-$digits"
        else "pdf-wechat-$amountFen-$type-$merchant-${occurredAt / 60_000}-$rowIndex"
    }

    /**
     * 防御：示例/脱敏文件交易单号可能多行相同（同文件重复出现），若直接用单号做主键
     * 会导致同文件多行 key 冲突被 IGNORE 丢弃。统计后对重复单号所在行全部改用组合键。
     */
    private fun applyDuplicatedTxNoDefense(rows: List<BillImportRow>): List<BillImportRow> {
        if (rows.isEmpty()) return rows
        val txKeyPrefix = "pdf-wechat-txno-"
        val counts = HashMap<String, Int>()
        for (r in rows) {
            if (r.notificationKey.startsWith(txKeyPrefix)) {
                counts[r.notificationKey] = (counts[r.notificationKey] ?: 0) + 1
            }
        }
        if (counts.values.none { it > 1 }) return rows

        return rows.map { r ->
            if (r.notificationKey.startsWith(txKeyPrefix) && (counts[r.notificationKey] ?: 0) > 1) {
                r.copy(
                    notificationKey = "pdf-wechat-${r.amountFen}-${r.type}-${r.merchant}-${r.occurredAt / 60_000}-${r.index}"
                )
            } else r
        }
    }

    /** 全角 → 半角（数字/逗号/句点/斜杠/冒号/空格） */
    fun normalize(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in '０'..'９' -> (c - '０' + '0'.code).toChar()
                    '，' -> ','
                    '．' -> '.'
                    '／' -> '/'
                    '：' -> ':'
                    '\u3000' -> ' '
                    else -> c
                }
            )
        }
    }
}

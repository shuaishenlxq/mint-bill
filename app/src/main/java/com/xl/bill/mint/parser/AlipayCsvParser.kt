package com.xl.bill.mint.parser

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 支付宝账单 CSV 解析器（纯 Kotlin，JVM 可单测）。
 *
 * 输入为 [CsvReader] 还原的行×列矩阵（空字段为 null），输出结构化的 [BillImportRow] 列表。
 *
 * 支付宝导出 CSV 特征（已实测真实文件）：
 * - 表头行：A=交易时间 | B=交易分类 | C=对方账号 | D=商品说明 | E=收/支 | F=金额 | G=收/付款方式 |
 *   H=交易状态 | I=交易订单号 | J=商家订单号 | K=备注；
 * - 表头上方是导出信息/统计/特别提示与分隔线，按"表头之后"条件自动跳过；
 * - 交易时间为文本 `yyyy-MM-dd HH:mm:ss`（北京时间，固定 UTC+8 换算，勿用系统时区）；
 * - 金额为元单位数字（浮点）→ 转分必须 round 防浮点误差；
 * - 收/支列取值：支出 / 收入 / 不计收支（**不计收支一律过滤，不映射导入**）；
 * - 交易状态取值：交易成功 / 退款成功 / 交易关闭（**非成功状态过滤**，未完成交易不入账）；
 * - 字段映射（用户确认 2026-08-07）：**交易分类 → 标题 merchant**；**商品说明 + 备注列 → note**（两列拼接）；
 * - 交易订单号为支付宝内部唯一单号；退款记录带 `*` 或 `_` 后缀关联原支出单（去重键取全量数字，
 *   与支出单天然区分）；字段尾部可能带 tab/空格，需 trim。
 */
object AlipayCsvParser {

    /** 支付宝账单固定时区（导出的就是北京时间，绝不可用系统时区） */
    private val UTC8 = ZoneOffset.ofHours(8)

    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 交易单号最少有效数字位数（≥12 位视为可靠全局唯一，走单号去重键） */
    private const val MIN_TX_NO_DIGITS = 12

    /** 表头列索引（与支付宝 CSV 列序一致，A=0） */
    private const val COL_TIME = 0
    private const val COL_CATEGORY = 1
    private const val COL_COUNTERPARTY = 2
    private const val COL_GOODS = 3
    private const val COL_DIRECTION = 4
    private const val COL_AMOUNT = 5
    private const val COL_PAY_METHOD = 6
    private const val COL_STATUS = 7
    private const val COL_TX_NO = 8
    private const val COL_NOTE = 10

    /** 合法交易状态（其余如「交易关闭」视为未完成交易，过滤） */
    private val VALID_STATUS = setOf("交易成功", "退款成功")

    // ------------------------------------------------------------------
    // 入口
    // ------------------------------------------------------------------

    /**
     * 单元格矩阵 → 账单行列表。
     * 定位表头行（col0 含「交易时间」且整行含「金额」），表头之前跳过，表头之后逐行解析。
     * 被有意过滤的行（不计收支 / 非成功状态 / 解析失败）计入 [BillParseResult.unrecognizedCount]。
     */
    fun parse(matrix: List<List<String?>>): BillParseResult {
        val headerIndex = findHeaderIndex(matrix)
        if (headerIndex < 0) {
            // 非支付宝账单表 / 无表头 → 无法区分数据行，全部视为未识别
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
        return BillParseResult(rawRows, unrecognized)
    }

    /** 表头定位：col0 含「交易时间」且整行含「金额」（contains 免疫全角/半角差异） */
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

        val direction = row.getOrNull(COL_DIRECTION).orEmpty().trim()
        // 只导入明确的收支；「不计收支」及其余中性值一律过滤
        val type = when {
            direction.contains("支出") -> ParsedBill.TYPE_EXPENSE
            direction.contains("收入") -> ParsedBill.TYPE_INCOME
            else -> return null
        }

        // 交易状态：仅「交易成功 / 退款成功」入账，交易关闭等未完成交易过滤
        val status = row.getOrNull(COL_STATUS).orEmpty().trim()
        if (status !in VALID_STATUS) return null

        // 时间：文本 yyyy-MM-dd HH:mm:ss → epoch millis；非法 → 丢弃
        val occurredAt = toEpochMillis(row.getOrNull(COL_TIME)) ?: return null

        // 金额：元 → 分（round 防浮点）；空/非法 → 丢弃
        val amountFen = ExcelBillParser.amountYuanToFen(row.getOrNull(COL_AMOUNT)) ?: return null

        val category = row.getOrNull(COL_CATEGORY).orEmpty().trim()
        val goods = row.getOrNull(COL_GOODS).orEmpty().trim()
        val counterparty = row.getOrNull(COL_COUNTERPARTY).orEmpty().trim()
        val payMethod = row.getOrNull(COL_PAY_METHOD).orEmpty().trim()
        val txNo = ExcelBillParser.normalize(row.getOrNull(COL_TX_NO).orEmpty()).trim()
        val noteText = row.getOrNull(COL_NOTE).orEmpty().trim()

        // 标题（merchant）= 交易分类（用户确认 2026-08-07）；为空时回退商品说明 → 对方账号
        val merchant = (category.ifEmpty { goods }.ifEmpty { counterparty }).ifEmpty { null }?.take(24)
        // 备注 = 商品说明（D 列）+ 原备注列（K 列）拼接，两列信息都不丢
        val note = when {
            goods.isNotEmpty() && noteText.isNotEmpty() && noteText != goods -> "$goods | $noteText"
            goods.isNotEmpty() -> goods
            noteText.isNotEmpty() && noteText != "/" -> noteText
            else -> null
        }

        return BillImportRow(
            index = rowIndex,
            occurredAt = occurredAt,
            type = type,
            wasNeutral = false,
            amountFen = amountFen,
            merchant = merchant,
            tradeType = category.ifEmpty { null },
            paymentMethod = payMethod.ifEmpty { null },
            note = note,
            rawText = rawText,
            notificationKey = buildNotificationKey(txNo, amountFen, type, merchant, occurredAt, rowIndex)
        )
    }

    // ------------------------------------------------------------------
    // 时间 / 去重键
    // ------------------------------------------------------------------

    /**
     * 交易时间文本 `yyyy-MM-dd HH:mm:ss` → epoch millis（固定 UTC+8，不依赖系统时区）。
     */
    fun toEpochMillis(text: String?): Long? {
        val t = text?.trim() ?: return null
        if (t.isEmpty()) return null
        return try {
            LocalDateTime.parse(t, TIME_FORMATTER).toEpochSecond(UTC8) * 1000L
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 去重键：
     * - 交易订单号（过滤 tab/空格后取全量数字）≥12 位 → `pdf-alipay-txno-<数字单号>`；
     *   支付宝单号全局唯一，同一文件重复导入 / 跨文件重复导入同一笔都被 UNIQUE 拦截；
     *   退款单号 `*`/`_` 后缀的数字部分与关联支出单不同 → 天然区分，不冲突；
     * - 异常短单号兜底组合键（金额+方向+商户+分钟+行号）。
     *
     * 前缀命名规范：微信为历史遗留 `pdf-wechat-`，支付宝新建 `pdf-alipay-`，互不干扰。
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
        return if (digits.length >= MIN_TX_NO_DIGITS) "pdf-alipay-txno-$digits"
        else "pdf-alipay-$amountFen-$type-$merchant-${occurredAt / 60_000}-$rowIndex"
    }
}

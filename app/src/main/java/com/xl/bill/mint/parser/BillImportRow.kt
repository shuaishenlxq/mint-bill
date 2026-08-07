package com.xl.bill.mint.parser

/**
 * 解析结果（微信 Excel 与支付宝 CSV 共用）：
 * - [rows]：可导入行（已按过滤规则剔除中性/无效记录）；
 * - [unrecognizedCount]：表头之后被跳过/解析失败的行数（无法识别、不计收支、无效状态等）。
 */
data class BillParseResult(
    val rows: List<BillImportRow>,
    val unrecognizedCount: Int
)

/**
 * 导入解析后的单条账单行（纯数据，JVM 可单测）。
 *
 * 字段语义与微信账单 Excel / 支付宝 CSV 表格列一一对应；type 已按映射规则完成。
 * 原为 PDF 导入的 PdfRow，Excel/CSV 导入复用同一模型。
 */
data class BillImportRow(
    /** 文件内成功行全局序号（0 起），用于回退去重键与预览定位 */
    val index: Int,
    /** epoch millis（秒精度，按 UTC+8 固定换算） */
    val occurredAt: Long,
    /** 0=支出 / 1=收入（中性/不计收支已在解析层过滤，不再映射导入） */
    val type: Int,
    /** 原「收/支」列是否为中性（恒 false，保留字段兼容预览统计） */
    val wasNeutral: Boolean,
    /** 金额，单位分 */
    val amountFen: Long,
    /** 交易对方（merchant 落库来源） */
    val merchant: String?,
    /** 交易类型原文（微信：转账/商户消费等；支付宝：餐饮美食/投资理财等，分类提示用） */
    val tradeType: String?,
    /** 支付方式（零钱/招商银行储蓄卡(8498) 等） */
    val paymentMethod: String?,
    /** 商品/备注合并（非空则落 note 字段） */
    val note: String?,
    /** 整行拼接文本（rawText 落库 + 预览详情） */
    val rawText: String,
    /** 去重键（notificationKey 落库，UNIQUE 兜底） */
    val notificationKey: String
) {
    /** 转 ParsedBill；channel 区分来源（默认微信，支付宝导入传 Channel.ALIPAY） */
    fun toParsedBill(channel: Channel = Channel.WECHAT): ParsedBill = ParsedBill(
        channel = channel,
        amount = amountFen,
        type = type,
        merchant = merchant,
        rawTitle = null,
        rawText = rawText,
        occurredAt = occurredAt,
        notificationKey = notificationKey,
        lineIndex = index
    )
}

package com.xl.bill.mint.parser

/**
 * 解析结果（纯 Kotlin 数据类）。
 * amount 单位：分；type：0=支出 1=收入。
 */
data class ParsedBill(
    val channel: Channel,
    val amount: Long,
    val type: Int,
    val merchant: String?,
    val rawTitle: String?,
    val rawText: String?,
    val occurredAt: Long,
    val notificationKey: String?,
    /** 屏幕列表页批量解析时的行序号（通知/转账详情路径不传）；用于 DB key 区分同分钟同金额同商户多笔 */
    val lineIndex: Int? = null
) {
    companion object {
        const val TYPE_EXPENSE = _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_EXPENSE
        const val TYPE_INCOME = _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_INCOME
    }
}

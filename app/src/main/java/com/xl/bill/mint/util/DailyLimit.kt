package com.xl.bill.mint.util

import kotlin.math.round

/**
 * 每日限额计算（纯 Kotlin，可测）。
 *
 * 口径：净支出 = max(0, 支出 − 收入)，收入可抵扣限额；
 * 百分比 = 净支出 ÷ 限额（四舍五入到个位整数，可 >100%）。
 */
object DailyLimit {

    /** 净支出：支出 − 收入，最低 0（收入大于支出时不消耗限额） */
    fun netExpense(expense: Long, income: Long): Long = (expense - income).coerceAtLeast(0L)

    /**
     * 净支出占每日限额的百分比（四舍五入到个位，可 >100）；
     * limit 为 null 或 <=0 视为未设置，返回 null。
     */
    fun percentInt(expense: Long, income: Long, limit: Long?): Int? {
        if (limit == null || limit <= 0) return null
        return round(netExpense(expense, income) * 100.0 / limit).toInt()
    }

    /** 超额金额（分）：净支出超出限额的部分，最低 0；未设置限额 → 0 */
    fun overage(expense: Long, income: Long, limit: Long?): Long {
        if (limit == null || limit <= 0) return 0L
        return (netExpense(expense, income) - limit).coerceAtLeast(0L)
    }

    /** 是否超额：超额金额 > 0（未设置限额恒为 false） */
    fun isOver(expense: Long, income: Long, limit: Long?): Boolean =
        overage(expense, income, limit) > 0L
}

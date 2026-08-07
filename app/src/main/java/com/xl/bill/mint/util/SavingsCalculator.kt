package com.xl.bill.mint.util

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * 存款计算：纯 Kotlin，可 JVM 单测。
 *
 * 口径（与产品约定一致）：
 * - 每月存款 = 当月收入 - 当月支出（净结余），只统计有账单记录的月份；
 * - 当前存款 = 初始化金额 + 全部历史账单累计净结余，随记账自动更新；
 * - 月度达标 = 有每月目标时，当月净结余 ≥ 目标；无目标时 met=null（不对比）。
 *
 * 金额单位「分」，occurredAt 为 epoch 毫秒，按系统时区归月（与 App 区间计算一致）。
 */
object SavingsCalculator {

    /** 单月收支聚合（无记录月份不会产出） */
    data class MonthBalance(
        val month: YearMonth,
        val income: Long,
        val expense: Long
    ) {
        val balance: Long get() = income - expense
    }

    /** 报表单月视图：balance=当月净结余、cumulative=截至当月的滚动累计（含初始化金额）、met=是否达标（无月目标为 null） */
    data class SavingsMonthView(
        val month: YearMonth,
        val balance: Long,
        val cumulative: Long,
        val met: Boolean?
    )

    /** 首页进度摘要；goalTotal ≤ 0 时 summary() 返回 null（引导态） */
    data class SavingsSummary(
        val initial: Long,
        val cumulativeBalance: Long,
        val current: Long,
        val goalTotal: Long,
        val progress: Float
    ) {
        val achieved: Boolean get() = current >= goalTotal
    }

    /** 按月份分组聚合收支，升序返回；空表返回空列表 */
    fun monthBalances(list: List<com.xl.bill.mint.data.db.TransactionEntity>): List<MonthBalance> {
        val acc = HashMap<YearMonth, LongArray>() // [income, expense]
        for (tx in list) {
            val ym = YearMonth.from(
                Instant.ofEpochMilli(tx.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
            )
            val pair = acc.getOrPut(ym) { LongArray(2) }
            if (tx.type == _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_INCOME) {
                pair[0] += tx.amount
            } else {
                pair[1] += tx.amount
            }
        }
        return acc.entries
            .sortedBy { it.key }
            .map { MonthBalance(it.key, it.value[0], it.value[1]) }
    }

    /** 月度存款序列：滚动累计（初始金额为起点），升序；met 三态（达标/未达标/无月目标 null） */
    fun savingsMonthSeries(
        list: List<com.xl.bill.mint.data.db.TransactionEntity>,
        initial: Long,
        monthlyGoal: Long?
    ): List<SavingsMonthView> {
        var cumulative = initial
        return monthBalances(list).map { mb ->
            cumulative += mb.balance
            SavingsMonthView(
                month = mb.month,
                balance = mb.balance,
                cumulative = cumulative,
                met = monthlyGoal?.let { mb.balance >= it }
            )
        }
    }

    /** 首页进度摘要：当前存款 = initial + 全量累计净结余；goalTotal ≤ 0 返回 null（未设置引导态） */
    fun summary(list: List<com.xl.bill.mint.data.db.TransactionEntity>, initial: Long, goalTotal: Long): SavingsSummary? {
        if (goalTotal <= 0) return null
        val cumulative = list.sumOf { tx ->
            if (tx.type == _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_INCOME) tx.amount else -tx.amount
        }
        val current = initial + cumulative
        val progress = (current.toFloat() / goalTotal.toFloat()).coerceIn(0f, 1f)
        return SavingsSummary(initial, cumulative, current, goalTotal, progress)
    }
}

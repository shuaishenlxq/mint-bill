package com.xl.bill.mint.util

import com.xl.bill.mint.data.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

/**
 * SavingsCalculator 纯 JVM 单测：按月分组聚合 / 滚动累计与达标三态 / 进度摘要边界。
 */
class SavingsCalculatorTest {

    private fun tx(occurredAt: Long, amount: Long, type: Int) = TransactionEntity(
        channel = "manual",
        amount = amount,
        type = type,
        categoryId = 0L,
        accountId = 0L,
        occurredAt = occurredAt
    )

    private fun ts(ym: YearMonth, day: Int = 1): Long =
        ym.atDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ==================== monthBalances ====================

    @Test
    fun monthBalances_emptyList_returnsEmpty() {
        assertEquals(emptyList<SavingsCalculator.MonthBalance>(), SavingsCalculator.monthBalances(emptyList()))
    }

    @Test
    fun monthBalances_aggregatesByMonth() {
        val jan = YearMonth.of(2026, 1)
        val list = listOf(
            tx(ts(jan, 1), 10_000, TransactionEntity.TYPE_INCOME),
            tx(ts(jan, 15), 20_000, TransactionEntity.TYPE_INCOME),
            tx(ts(jan, 20), 5_000, TransactionEntity.TYPE_EXPENSE)
        )
        val result = SavingsCalculator.monthBalances(list)
        assertEquals(1, result.size)
        assertEquals(jan, result[0].month)
        assertEquals(30_000, result[0].income)
        assertEquals(5_000, result[0].expense)
        assertEquals(25_000, result[0].balance)
    }

    @Test
    fun monthBalances_noRecordMonthsNotProduced_sortedAscending() {
        val jan = YearMonth.of(2026, 1)
        val mar = YearMonth.of(2026, 3)
        val result = SavingsCalculator.monthBalances(
            listOf(
                tx(ts(mar), 1_000, TransactionEntity.TYPE_EXPENSE),
                tx(ts(jan), 2_000, TransactionEntity.TYPE_INCOME)
            )
        )
        // 2 月无记录 → 不产出；且按月份升序
        assertEquals(listOf(jan, mar), result.map { it.month })
    }

    // ==================== savingsMonthSeries ====================

    @Test
    fun savingsMonthSeries_rollingCumulative() {
        val jan = YearMonth.of(2026, 1)
        val feb = YearMonth.of(2026, 2)
        val result = SavingsCalculator.savingsMonthSeries(
            listOf(
                tx(ts(jan), 10_000, TransactionEntity.TYPE_INCOME),
                tx(ts(feb), 4_000, TransactionEntity.TYPE_EXPENSE)
            ),
            initial = 100_000,
            monthlyGoal = 5_000
        )
        assertEquals(2, result.size)
        // 1 月：结余 +10000，累计 = 100000 + 10000
        assertEquals(10_000, result[0].balance)
        assertEquals(110_000, result[0].cumulative)
        assertTrue(result[0].met!!)
        // 2 月：结余 -4000，累计 = 110000 - 4000
        assertEquals(-4_000, result[1].balance)
        assertEquals(106_000, result[1].cumulative)
        assertFalse(result[1].met!!)
    }

    @Test
    fun savingsMonthSeries_metThreeStates() {
        val jan = YearMonth.of(2026, 1)
        val feb = YearMonth.of(2026, 2)
        val mar = YearMonth.of(2026, 3)
        // 月目标 5000：1 月达标(10000)、2 月未达标(3000)、无目标月份不参与
        val result = SavingsCalculator.savingsMonthSeries(
            listOf(
                tx(ts(jan), 10_000, TransactionEntity.TYPE_INCOME),
                tx(ts(feb), 3_000, TransactionEntity.TYPE_INCOME)
            ),
            initial = 0,
            monthlyGoal = 5_000
        )
        assertEquals(2, result.size)
        assertTrue(result[0].met!!)
        assertFalse(result[1].met!!)

        // 无每月目标 → met 全为 null
        val noGoal = SavingsCalculator.savingsMonthSeries(
            listOf(tx(ts(mar), 10_000, TransactionEntity.TYPE_INCOME)),
            initial = 0,
            monthlyGoal = null
        )
        assertNull(noGoal[0].met)
    }

    @Test
    fun savingsMonthSeries_emptyList_returnsEmpty() {
        assertEquals(emptyList<SavingsCalculator.SavingsMonthView>(),
            SavingsCalculator.savingsMonthSeries(emptyList(), initial = 0, monthlyGoal = null))
    }

    // ==================== summary ====================

    @Test
    fun summary_initialPlusCumulative() {
        val jan = YearMonth.of(2026, 1)
        val summary = SavingsCalculator.summary(
            listOf(
                tx(ts(jan), 30_000, TransactionEntity.TYPE_INCOME),
                tx(ts(jan, 10), 5_000, TransactionEntity.TYPE_EXPENSE)
            ),
            initial = 100_000,
            goalTotal = 200_000
        )!!
        assertEquals(100_000, summary.initial)
        assertEquals(25_000, summary.cumulativeBalance)
        assertEquals(125_000, summary.current)
        assertEquals(200_000, summary.goalTotal)
        assertEquals(0.625f, summary.progress, 0.001f)
        assertFalse(summary.achieved)
    }

    @Test
    fun summary_negativeCumulative() {
        val jan = YearMonth.of(2026, 1)
        val summary = SavingsCalculator.summary(
            listOf(tx(ts(jan), 20_000, TransactionEntity.TYPE_EXPENSE)),
            initial = 10_000,
            goalTotal = 50_000
        )!!
        assertEquals(0, summary.current) // 存款不展示为负：钳 0
        assertEquals(0f, summary.progress, 0f)
    }

    @Test
    fun summary_filtersByBaseTime() {
        val jan = YearMonth.of(2026, 1)
        val mar = YearMonth.of(2026, 3)
        val base = ts(mar)
        // 基准前：1 月支出 30_000；基准后：3 月收入 50_000 → 只累计基准后净结余
        val summary = SavingsCalculator.summary(
            listOf(
                tx(ts(jan), 30_000, TransactionEntity.TYPE_EXPENSE),
                tx(ts(mar), 50_000, TransactionEntity.TYPE_INCOME)
            ),
            initial = 100_000,
            goalTotal = 200_000,
            baseTime = base
        )!!
        assertEquals(50_000, summary.cumulativeBalance)
        assertEquals(150_000, summary.current)
    }

    @Test
    fun summary_baseTimeNull_keepsLegacyBehavior() {
        val jan = YearMonth.of(2026, 1)
        val mar = YearMonth.of(2026, 3)
        val summary = SavingsCalculator.summary(
            listOf(
                tx(ts(jan), 30_000, TransactionEntity.TYPE_EXPENSE),
                tx(ts(mar), 50_000, TransactionEntity.TYPE_INCOME)
            ),
            initial = 100_000,
            goalTotal = 200_000
        )!!
        // 未设基准：全量累计（-30000 + 50000 = 20000）
        assertEquals(20_000, summary.cumulativeBalance)
        assertEquals(120_000, summary.current)
    }

    @Test
    fun savingsMonthSeries_filtersByBaseTime() {
        val jan = YearMonth.of(2026, 1)
        val feb = YearMonth.of(2026, 2)
        val mar = YearMonth.of(2026, 3)
        // 基准 = 2 月 1 日 → 1 月账单不参与，累计从 2 月起算
        val result = SavingsCalculator.savingsMonthSeries(
            listOf(
                tx(ts(jan), 30_000, TransactionEntity.TYPE_EXPENSE),
                tx(ts(feb), 10_000, TransactionEntity.TYPE_INCOME),
                tx(ts(mar), 4_000, TransactionEntity.TYPE_EXPENSE)
            ),
            initial = 100_000,
            monthlyGoal = null,
            baseTime = ts(feb)
        )
        assertEquals(2, result.size)
        assertEquals(feb, result[0].month)
        assertEquals(110_000, result[0].cumulative) // 100000 + 10000
        assertEquals(106_000, result[1].cumulative) // 110000 - 4000
    }

    @Test
    fun summary_overGoal_clampsProgressToOne() {
        val jan = YearMonth.of(2026, 1)
        val summary = SavingsCalculator.summary(
            listOf(tx(ts(jan), 200_000, TransactionEntity.TYPE_INCOME)),
            initial = 0,
            goalTotal = 100_000
        )!!
        assertEquals(1f, summary.progress, 0f)
        assertTrue(summary.achieved)
    }

    @Test
    fun summary_goalNotSetOrZero_returnsNull() {
        val jan = YearMonth.of(2026, 1)
        val list = listOf(tx(ts(jan), 10_000, TransactionEntity.TYPE_INCOME))
        assertNull(SavingsCalculator.summary(list, initial = 0, goalTotal = 0))
        assertNull(SavingsCalculator.summary(list, initial = 0, goalTotal = -100))
    }
}

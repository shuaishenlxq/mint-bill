package com.xl.bill.mint.util

import com.xl.bill.mint.data.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * StatisticsCalculator 纯 JVM 单测：按日聚合（dayBalances）与区间过滤。
 */
class StatisticsCalculatorTest {

    private fun tx(occurredAt: Long, amount: Long, type: Int) = TransactionEntity(
        channel = "manual",
        amount = amount,
        type = type,
        categoryId = 0L,
        accountId = 0L,
        occurredAt = occurredAt
    )

    private fun ts(ym: YearMonth, day: Int, hour: Int = 10): Long =
        ym.atDay(day).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ==================== dayBalances ====================

    @Test
    fun dayBalances_emptyList_returnsEmpty() {
        val (start, end) = StatisticsCalculator.monthRange(YearMonth.of(2026, 3))
        assertEquals(emptyList<StatisticsCalculator.DayBalance>(), StatisticsCalculator.dayBalances(emptyList(), start, end))
    }

    @Test
    fun dayBalances_aggregatesByDay_descending() {
        val mar = YearMonth.of(2026, 3)
        val list = listOf(
            tx(ts(mar, 1), 10_000, TransactionEntity.TYPE_INCOME),
            tx(ts(mar, 1, 20), 3_000, TransactionEntity.TYPE_EXPENSE), // 同一天合并
            tx(ts(mar, 2), 5_000, TransactionEntity.TYPE_EXPENSE)
        )
        val (start, end) = StatisticsCalculator.monthRange(mar)
        val result = StatisticsCalculator.dayBalances(list, start, end)
        assertEquals(2, result.size)
        // 日期降序：最新在前
        assertEquals(LocalDate.of(2026, 3, 2), result[0].date)
        assertEquals(0L, result[0].income)
        assertEquals(5_000, result[0].expense)
        assertEquals(-5_000, result[0].balance)
        assertEquals(LocalDate.of(2026, 3, 1), result[1].date)
        assertEquals(10_000, result[1].income)
        assertEquals(3_000, result[1].expense)
        assertEquals(7_000, result[1].balance)
    }

    @Test
    fun dayBalances_filtersOutsideRange() {
        val mar = YearMonth.of(2026, 3)
        val feb = YearMonth.of(2026, 2)
        val apr = YearMonth.of(2026, 4)
        val list = listOf(
            tx(ts(feb, 28), 9_999, TransactionEntity.TYPE_EXPENSE),  // 区间外
            tx(ts(mar, 5), 1_000, TransactionEntity.TYPE_INCOME),
            tx(ts(apr, 1), 8_888, TransactionEntity.TYPE_EXPENSE)    // 区间外
        )
        val (start, end) = StatisticsCalculator.monthRange(mar)
        val result = StatisticsCalculator.dayBalances(list, start, end)
        assertEquals(1, result.size)
        assertEquals(LocalDate.of(2026, 3, 5), result[0].date)
        assertEquals(1_000, result[0].income)
        assertEquals(0L, result[0].expense)
    }

    @Test
    fun dayBalances_noRecordDaysNotProduced() {
        val mar = YearMonth.of(2026, 3)
        val list = listOf(
            tx(ts(mar, 5), 100, TransactionEntity.TYPE_EXPENSE),
            tx(ts(mar, 10), 200, TransactionEntity.TYPE_EXPENSE)
        )
        val (start, end) = StatisticsCalculator.monthRange(mar)
        val result = StatisticsCalculator.dayBalances(list, start, end)
        // 只产出有记录的 2 天，而非整个月 31 天
        assertEquals(2, result.size)
        assertEquals(listOf(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 5)), result.map { it.date })
    }
}

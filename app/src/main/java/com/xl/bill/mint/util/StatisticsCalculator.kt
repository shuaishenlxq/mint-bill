package com.xl.bill.mint.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 报表统计计算（纯 Kotlin，从账单列表派生全部统计指标）。
 *
 * 支持三种周期：周（自然周·周一起始）、月、年。
 */
object StatisticsCalculator {

    /** 报表周期 */
    enum class ReportPeriod { WEEK, MONTH, YEAR }

    data class MonthOverview(val income: Long, val expense: Long) {
        val balance: Long get() = income - expense
    }

    data class CategoryStat(
        val categoryId: Long,
        val name: String,
        val icon: String,
        val total: Long,
        val percent: Float
    )

    /** 趋势点：label 可为 "M月"（月度）或 "M/d"（每日） */
    data class MonthPoint(val label: String, val income: Long, val expense: Long)

    // ==================== 周期区间 ====================

    /** YearMonth → [startMillis, endMillis) */
    fun monthRange(yearMonth: YearMonth): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    /** 自然周（周一起始）→ [startMillis, endMillis)，anchor 可为周内任意一天 */
    fun weekRange(anchor: LocalDate): Pair<Long, Long> {
        val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val zone = ZoneId.systemDefault()
        val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    /** 当日 → [startMillis, endMillis) */
    fun dayRange(anchor: LocalDate): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = anchor.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = anchor.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    /** 自然年 → [startMillis, endMillis) */
    fun yearRange(year: Int): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    /** 按周期解析区间：anchor 在周视图为周内任意一天、月视图为当月任意一天、年视图为当年任意一天 */
    fun range(period: ReportPeriod, anchor: LocalDate): Pair<Long, Long> = when (period) {
        ReportPeriod.WEEK -> weekRange(anchor)
        ReportPeriod.MONTH -> monthRange(YearMonth.from(anchor))
        ReportPeriod.YEAR -> yearRange(anchor.year)
    }

    // ==================== 概览 ====================

    fun monthOverview(list: List<com.xl.bill.mint.data.db.TransactionEntity>, start: Long, end: Long): MonthOverview =
        overview(list, start, end)

    fun overview(list: List<com.xl.bill.mint.data.db.TransactionEntity>, start: Long, end: Long): MonthOverview {
        var income = 0L
        var expense = 0L
        for (tx in list) {
            if (tx.occurredAt !in start until end) continue
            if (tx.type == _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_INCOME) income += tx.amount else expense += tx.amount
        }
        return MonthOverview(income, expense)
    }

    // ==================== 分类 / 大额 ====================

    /** 分类占比（按金额降序，percent = 占该类收支总额比例 0..1） */
    fun categoryBreakdown(
        list: List<com.xl.bill.mint.data.db.TransactionEntity>,
        type: Int,
        start: Long,
        end: Long,
        categories: List<com.xl.bill.mint.data.db.CategoryEntity>
    ): List<CategoryStat> {
        val byId = categories.associateBy { it.id }
        val sums = HashMap<Long, Long>()
        var total = 0L
        for (tx in list) {
            if (tx.occurredAt !in start until end || tx.type != type) continue
            sums[tx.categoryId] = (sums[tx.categoryId] ?: 0L) + tx.amount
            total += tx.amount
        }
        if (total == 0L) return emptyList()
        return sums.entries
            .map { (id, sum) ->
                val cat = byId[id]
                CategoryStat(
                    categoryId = id,
                    name = cat?.name ?: "未分类",
                    icon = cat?.icon ?: "🏷️",
                    total = sum,
                    percent = sum.toFloat() / total
                )
            }
            .sortedByDescending { it.total }
    }

    fun topN(
        list: List<com.xl.bill.mint.data.db.TransactionEntity>,
        type: Int,
        start: Long,
        end: Long,
        n: Int
    ): List<com.xl.bill.mint.data.db.TransactionEntity> =
        list.asSequence()
            .filter { it.occurredAt in start until end && it.type == type }
            .sortedByDescending { it.amount }
            .take(n)
            .toList()

    // ==================== 趋势 ====================

    /** 逐日趋势：区间内每一天一个点（周=7 天、月=28~31 天），label = "M/d" */
    fun dailyTrend(
        list: List<com.xl.bill.mint.data.db.TransactionEntity>,
        start: Long,
        end: Long
    ): List<MonthPoint> {
        val zone = ZoneId.systemDefault()
        val startDate = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(startDate, Instant.ofEpochMilli(end).atZone(zone).toLocalDate()).toInt()
        if (days <= 0) return emptyList()
        // 交替存储 [income, expense]
        val buckets = LongArray(days * 2)
        for (tx in list) {
            if (tx.occurredAt !in start until end) continue
            val d = ChronoUnit.DAYS.between(startDate, Instant.ofEpochMilli(tx.occurredAt).atZone(zone).toLocalDate()).toInt()
            if (d !in 0 until days) continue
            if (tx.type == _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_INCOME) buckets[d * 2] += tx.amount
            else buckets[d * 2 + 1] += tx.amount
        }
        val fmt = DateTimeFormatter.ofPattern("M/d")
        return (0 until days).map { i ->
            MonthPoint(startDate.plusDays(i.toLong()).format(fmt), buckets[i * 2], buckets[i * 2 + 1])
        }
    }

    /** 年度趋势：固定 1~12 月，label = "M月" */
    fun yearTrend(list: List<com.xl.bill.mint.data.db.TransactionEntity>, year: Int): List<MonthPoint> {
        val zone = ZoneId.systemDefault()
        // 交替存储 [income, expense]
        val buckets = LongArray(12 * 2)
        for (tx in list) {
            val d = Instant.ofEpochMilli(tx.occurredAt).atZone(zone).toLocalDate()
            if (d.year != year) continue
            val m = d.monthValue - 1
            if (tx.type == _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_INCOME) buckets[m * 2] += tx.amount
            else buckets[m * 2 + 1] += tx.amount
        }
        return (1..12).map { m ->
            MonthPoint("${m}月", buckets[(m - 1) * 2], buckets[(m - 1) * 2 + 1])
        }
    }

    /** 按周期取趋势：周/月 = 逐日，年 = 12 个月 */
    fun periodTrend(period: ReportPeriod, anchor: LocalDate, list: List<com.xl.bill.mint.data.db.TransactionEntity>): List<MonthPoint> =
        when (period) {
            ReportPeriod.WEEK -> {
                val (start, end) = weekRange(anchor)
                dailyTrend(list, start, end)
            }
            ReportPeriod.MONTH -> {
                val (start, end) = monthRange(YearMonth.from(anchor))
                dailyTrend(list, start, end)
            }
            ReportPeriod.YEAR -> yearTrend(list, anchor.year)
        }
}

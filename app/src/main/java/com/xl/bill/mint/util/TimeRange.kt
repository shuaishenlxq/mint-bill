package com.xl.bill.mint.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * 时间范围工具：把「今日/本周/本月/今年/自定义」统一换算成毫秒半开区间 [start, end)，
 * 负责与 Material3 日期选择器（返回 UTC 零点毫秒）互转，并生成展示文案。
 *
 * 关键坑：Material3 DatePicker/DateRangePicker 返回的是 UTC 零点毫秒，
 * 转 LocalDate 必须用 [ZoneOffset.UTC]；而区间计算统一走系统时区。
 */
object TimeRange {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 最近 3 个自然日（含今天）的起始时刻：今天零点往前 2 天 */
    fun recent3DaysStart(): Long = StatisticsCalculator.dayRange(LocalDate.now().minusDays(2)).first

    // ---------------- Material3 日期选择器互转 ----------------

    /** picker 的 UTC 零点毫秒 → LocalDate（用 UTC 解析） */
    fun fromPickerUtc(utcMillis: Long): LocalDate =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

    /** LocalDate → picker 的 UTC 零点毫秒 */
    fun toPickerUtc(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** 区间起始毫秒（系统时区零点）→ LocalDate */
    fun startDateOf(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** 区间结束毫秒（开区间，系统时区零点）→ 实际覆盖的最后一天 */
    fun inclusiveEndDateOf(endMillis: Long): LocalDate =
        Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate().minusDays(1)

    // ---------------- 区间计算（委托 StatisticsCalculator，全部系统时区） ----------------

    fun dayRangeOf(date: LocalDate): Pair<Long, Long> = StatisticsCalculator.dayRange(date)

    fun weekRangeOf(anchor: LocalDate): Pair<Long, Long> = StatisticsCalculator.weekRange(anchor)

    fun monthRangeOf(ym: YearMonth): Pair<Long, Long> = StatisticsCalculator.monthRange(ym)

    fun yearRangeOf(year: Int): Pair<Long, Long> = StatisticsCalculator.yearRange(year)

    // ---------------- 展示文案 ----------------

    /**
     * 区间展示文案。
     * @param homeDefault true=首页口径（null 回落「近3天」）；false=全部账单页口径（null 显示「全部时间」）
     */
    fun label(
        start: Long?,
        end: Long?,
        homeDefault: Boolean,
        recentLabel: String = "近3天",
        allTimeLabel: String = "全部时间",
        todayLabel: String = "今天"
    ): String {
        if (start == null && end == null) {
            return if (homeDefault) recentLabel else allTimeLabel
        }
        val todayRange = StatisticsCalculator.dayRange(LocalDate.now())
        if (start == todayRange.first && (end == null || end == todayRange.second)) return todayLabel
        if (homeDefault && start == recent3DaysStart() && end == null) return recentLabel

        if (start != null && end != null) {
            val s = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            // end 为开区间，减一天得到实际覆盖的最后一天
            val e = Instant.ofEpochMilli(end).atZone(zone).toLocalDate().minusDays(1)
            // 整周（周一起始且恰好 7 天，任意周均可）
            if (s.dayOfWeek == DayOfWeek.MONDAY && ChronoUnit.DAYS.between(s, e) == 6L) {
                return weekRangeLabel(s)
            }
            // 整月
            if (s.dayOfMonth == 1 && s.plusMonths(1).minusDays(1) == e) {
                return "${s.year}年${s.monthValue}月"
            }
            // 整年
            if (s.dayOfMonth == 1 && s.dayOfYear == 1 && s.plusYears(1).minusDays(1) == e) {
                return "${s.year}年"
            }
            return genericRangeLabel(s, e)
        }
        // start 有值、end 为空（首页默认近3天已在上方命中），兜底
        return if (homeDefault) recentLabel else allTimeLabel
    }

    /** 周区间标题（周一起始）：同一年内「2026年8月3日 - 8月9日」，跨年带年份 */
    fun weekRangeLabel(monday: LocalDate): String {
        val end = monday.plusDays(6)
        return if (monday.year == end.year) {
            "${monday.year}年${monday.monthValue}月${monday.dayOfMonth}日 - ${end.monthValue}月${end.dayOfMonth}日"
        } else {
            "${monday.year}年${monday.monthValue}月${monday.dayOfMonth}日 - ${end.year}年${end.monthValue}月${end.dayOfMonth}日"
        }
    }

    /** 通用区间：单日带年份；同年跨日「M月d日 - M月d日」；跨年两侧带年份 */
    private fun genericRangeLabel(s: LocalDate, e: LocalDate): String = when {
        s == e -> "${s.year}年${s.monthValue}月${s.dayOfMonth}日"
        s.year == e.year -> "${s.monthValue}月${s.dayOfMonth}日 - ${e.monthValue}月${e.dayOfMonth}日"
        else -> "${s.year}年${s.monthValue}月${s.dayOfMonth}日 - ${e.year}年${e.monthValue}月${e.dayOfMonth}日"
    }

    // ---------------- 回显（打开弹窗时还原快捷模式） ----------------

    /**
     * 把当前生效区间映射为弹窗的快捷模式：
     * null → NONE；首页默认近3天 → NONE（回落默认）；
     * 单日（含今天）→ TODAY；整周 → THIS_WEEK；整月 → THIS_MONTH；整年 → THIS_YEAR；其余 → CUSTOM。
     */
    fun decode(start: Long?, end: Long?, homeDefault: Boolean): com.xl.bill.mint.ui.filter.TimeRangePreset {
        if (start == null && end == null) return _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.NONE
        if (homeDefault && start == recent3DaysStart() && end == null) {
            return _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.NONE
        }
        if (start != null && end != null) {
            val s = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            val e = Instant.ofEpochMilli(end).atZone(zone).toLocalDate().minusDays(1)
            // 单日（含今天）
            if (s == e) return _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.TODAY
            // 整周（周一起始且恰好 7 天）
            if (s.dayOfWeek == DayOfWeek.MONDAY && ChronoUnit.DAYS.between(s, e) == 6L) {
                return _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.THIS_WEEK
            }
            // 整月
            if (s.dayOfMonth == 1 && s.plusMonths(1).minusDays(1) == e) {
                return _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.THIS_MONTH
            }
            // 整年
            if (s.dayOfMonth == 1 && s.dayOfYear == 1 && s.plusYears(1).minusDays(1) == e) {
                return _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.THIS_YEAR
            }
        }
        return _root_ide_package_.com.xl.bill.mint.ui.filter.TimeRangePreset.CUSTOM
    }

    /** 区间对应的单日（区间恰好覆盖一天时返回该日，否则 null），供 TODAY 模式回显 */
    fun singleDayOf(start: Long?, end: Long?): LocalDate? {
        if (start == null || end == null) return null
        val s = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val e = Instant.ofEpochMilli(end).atZone(zone).toLocalDate().minusDays(1)
        return if (s == e) s else null
    }

    /** 区间对应的整周起始日（周一起始且恰好 7 天），供 THIS_WEEK 模式回显 */
    fun weekAnchorOf(start: Long?, end: Long?): LocalDate? {
        if (start == null || end == null) return null
        val s = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val e = Instant.ofEpochMilli(end).atZone(zone).toLocalDate().minusDays(1)
        return if (s.dayOfWeek == DayOfWeek.MONDAY && ChronoUnit.DAYS.between(s, e) == 6L) {
            s
        } else {
            null
        }
    }
}

package com.xl.bill.mint.util

import com.xl.bill.mint.ui.filter.TimeRangePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * TimeRange 纯 JVM 单测：label 文案 / decode 回显 / 单日与区间辅助函数。
 */
class TimeRangeTest {

    private val now = LocalDate.now()

    @Test
    fun label_nullRange() {
        assertEquals("近3天", TimeRange.label(null, null, homeDefault = true))
        assertEquals("全部时间", TimeRange.label(null, null, homeDefault = false))
    }

    @Test
    fun label_recent3Days() {
        assertEquals("近3天", TimeRange.label(TimeRange.recent3DaysStart(), null, homeDefault = true))
    }

    @Test
    fun label_today() {
        val (s, e) = TimeRange.dayRangeOf(now)
        assertEquals("今天", TimeRange.label(s, e, homeDefault = true))
        assertEquals("今天", TimeRange.label(s, e, homeDefault = false))
    }

    @Test
    fun label_singleDay() {
        val day = LocalDate.of(2020, 3, 3)
        val (s, e) = TimeRange.dayRangeOf(day)
        assertEquals("2020年3月3日", TimeRange.label(s, e, homeDefault = false))
    }

    @Test
    fun label_month() {
        val ym = YearMonth.of(2025, 3)
        val (s, e) = TimeRange.monthRangeOf(ym)
        assertEquals("2025年3月", TimeRange.label(s, e, homeDefault = false))
    }

    @Test
    fun label_year() {
        val (s, e) = TimeRange.yearRangeOf(2025)
        assertEquals("2025年", TimeRange.label(s, e, homeDefault = false))
    }

    @Test
    fun label_week() {
        val monday = now.with(DayOfWeek.MONDAY)
        val (s, e) = TimeRange.weekRangeOf(monday)
        assertEquals(TimeRange.weekRangeLabel(monday), TimeRange.label(s, e, homeDefault = false))
    }

    @Test
    fun label_customRange() {
        // 非整周/整月/整年的普通区间（2025-02-11 为周二，跨 11 天）
        val s = LocalDate.of(2025, 2, 11)
        val e = LocalDate.of(2025, 2, 21)
        val start = TimeRange.dayRangeOf(s).first
        val end = TimeRange.dayRangeOf(e).second
        assertEquals("2月11日 - 2月21日", TimeRange.label(start, end, homeDefault = false))
    }

    @Test
    fun decode_cases() {
        assertEquals(TimeRangePreset.NONE, TimeRange.decode(null, null, homeDefault = false))
        assertEquals(
            TimeRangePreset.NONE,
            TimeRange.decode(TimeRange.recent3DaysStart(), null, homeDefault = true)
        )

        val (ts, te) = TimeRange.dayRangeOf(now)
        assertEquals(TimeRangePreset.TODAY, TimeRange.decode(ts, te, homeDefault = false))

        val ym = YearMonth.of(2025, 3)
        val (ms, me) = TimeRange.monthRangeOf(ym)
        assertEquals(TimeRangePreset.THIS_MONTH, TimeRange.decode(ms, me, homeDefault = false))

        val (ys, ye) = TimeRange.yearRangeOf(2025)
        assertEquals(TimeRangePreset.THIS_YEAR, TimeRange.decode(ys, ye, homeDefault = false))

        val monday = now.with(DayOfWeek.MONDAY)
        val (ws, we) = TimeRange.weekRangeOf(monday)
        assertEquals(TimeRangePreset.THIS_WEEK, TimeRange.decode(ws, we, homeDefault = false))

        val cs = LocalDate.of(2025, 2, 11)
        val ce = LocalDate.of(2025, 2, 21)
        assertEquals(
            TimeRangePreset.CUSTOM,
            TimeRange.decode(TimeRange.dayRangeOf(cs).first, TimeRange.dayRangeOf(ce).second, homeDefault = false)
        )
    }

    @Test
    fun singleDay_helpers() {
        val day = LocalDate.of(2025, 3, 3)
        val (s, e) = TimeRange.dayRangeOf(day)
        assertEquals(day, TimeRange.singleDayOf(s, e))
        // 多日区间返回 null
        val monthRange = TimeRange.monthRangeOf(YearMonth.of(2025, 3))
        assertNull(TimeRange.singleDayOf(monthRange.first, monthRange.second))
    }
}

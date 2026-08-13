package com.xl.bill.mint.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DailyLimit 纯 JVM 单测：净支出口径 / 百分比（个位四舍五入，可>100）/ 超额判定与金额 / 未设置边界。
 * 金额单位「分」。
 */
class DailyLimitTest {

    private val limit50 = 5_000L   // 50 元

    // ==================== netExpense ====================

    @Test
    fun netExpense_expenseOnly_isExpense() {
        assertEquals(6_000L, DailyLimit.netExpense(6_000L, 0L))
    }

    @Test
    fun netExpense_incomeDeducts() {
        assertEquals(4_000L, DailyLimit.netExpense(6_000L, 2_000L))
    }

    @Test
    fun netExpense_incomeExceedsExpense_isZero() {
        assertEquals(0L, DailyLimit.netExpense(2_000L, 6_000L))
    }

    // ==================== percentInt ====================

    @Test
    fun percentInt_example_limit50_expense60_income0_is120() {
        // 每日限额 50，支付总额 60，收入 0 → 120%
        assertEquals(120, DailyLimit.percentInt(6_000L, 0L, limit50))
    }

    @Test
    fun percentInt_incomeDeducts() {
        // 支出 60、收入 20、限额 50 → 净支出 40 → 80%
        assertEquals(80, DailyLimit.percentInt(6_000L, 2_000L, limit50))
    }

    @Test
    fun percentInt_incomeExceedsExpense_isZero() {
        assertEquals(0, DailyLimit.percentInt(2_000L, 6_000L, limit50))
    }

    @Test
    fun percentInt_exactLimit_is100() {
        assertEquals(100, DailyLimit.percentInt(5_000L, 0L, limit50))
    }

    @Test
    fun percentInt_roundsToInteger() {
        // 净支出 50.4 元（5040 分）÷ 限额 50 元 → 100.8% → 四舍五入 101%
        assertEquals(101, DailyLimit.percentInt(5_040L, 0L, limit50))
        // 净支出 49.4 元（4940 分）→ 98.8% → 99%
        assertEquals(99, DailyLimit.percentInt(4_940L, 0L, limit50))
    }

    @Test
    fun percentInt_overLimitExceeds100() {
        // 支出 120、收入 0、限额 50 → 240%
        assertEquals(240, DailyLimit.percentInt(12_000L, 0L, limit50))
    }

    @Test
    fun percentInt_limitNull_isNull() {
        assertNull(DailyLimit.percentInt(6_000L, 0L, null))
    }

    @Test
    fun percentInt_limitZero_isNull() {
        assertNull(DailyLimit.percentInt(6_000L, 0L, 0L))
        assertNull(DailyLimit.percentInt(6_000L, 0L, -100L))
    }

    // ==================== overage ====================

    @Test
    fun overage_overLimit_isExcessAmount() {
        // 支出 60、收入 0、限额 50 → 超额 10 元 = 1000 分
        assertEquals(1_000L, DailyLimit.overage(6_000L, 0L, limit50))
    }

    @Test
    fun overage_incomeDeducts() {
        // 支出 60、收入 20、限额 50 → 净支出 40 → 不超额
        assertEquals(0L, DailyLimit.overage(6_000L, 2_000L, limit50))
    }

    @Test
    fun overage_underLimit_isZero() {
        assertEquals(0L, DailyLimit.overage(4_000L, 0L, limit50))
    }

    @Test
    fun overage_limitNull_isZero() {
        assertEquals(0L, DailyLimit.overage(6_000L, 0L, null))
    }

    // ==================== isOver ====================

    @Test
    fun isOver_overLimit_true() {
        assertTrue(DailyLimit.isOver(6_000L, 0L, limit50))
    }

    @Test
    fun isOver_withinLimit_false() {
        assertFalse(DailyLimit.isOver(5_000L, 0L, limit50))
        assertFalse(DailyLimit.isOver(4_000L, 0L, limit50))
    }

    @Test
    fun isOver_limitUnset_false() {
        assertFalse(DailyLimit.isOver(6_000L, 0L, null))
        assertFalse(DailyLimit.isOver(6_000L, 0L, 0L))
    }
}

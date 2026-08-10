package com.xl.bill.mint.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MoneyFormatter 纯 JVM 单测：金额与百分比格式化。
 */
class MoneyFormatterTest {

    @Test
    fun yuan_stripsZeroDecimals() {
        assertEquals("100", MoneyFormatter.yuan(10_000))
        assertEquals("12.50", MoneyFormatter.yuan(1_250))
        assertEquals("0.01", MoneyFormatter.yuan(1))
    }

    @Test
    fun signed_incomeAndExpense() {
        assertEquals("+¥100", MoneyFormatter.signed(10_000, income = true))
        assertEquals("-¥100", MoneyFormatter.signed(10_000, income = false))
    }

    // ==================== percent（2 位小数，四舍五入） ====================

    @Test
    fun percent_roundsToTwoDecimals() {
        assertEquals("19.88%", MoneyFormatter.percent(0.19876f))
        assertEquals("19.87%", MoneyFormatter.percent(0.19874f))
        assertEquals("19.90%", MoneyFormatter.percent(0.19898f))
    }

    @Test
    fun percent_boundaries() {
        assertEquals("0.00%", MoneyFormatter.percent(0f))
        assertEquals("100.00%", MoneyFormatter.percent(1f))
        // coerceIn(0f,1f) 后仍正确
        assertEquals("100.00%", MoneyFormatter.percent(0.99999f))
        assertEquals("50.00%", MoneyFormatter.percent(0.5f))
    }
}

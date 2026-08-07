package com.xl.bill.mint.ui.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BillSort / sortOptions / BillFilters.sort 纯 JVM 单测。
 * 验证 SQL 排序模式映射、下拉选项顺序与 label、默认兼容性（首页快照/旧 Bundle）。
 */
class BillSortTest {

    @Test
    fun sortMode_mapping() {
        assertEquals(0, BillSort.TIME_DESC.sortMode)
        assertEquals(1, BillSort.AMOUNT_ASC.sortMode)
        assertEquals(2, BillSort.AMOUNT_DESC.sortMode)
    }

    @Test
    fun sortOptions_orderAndValues() {
        val opts = sortOptions("时间最新", "金额从高到低", "金额从低到高")
        assertEquals(3, opts.size)
        assertEquals("时间最新", opts[0].label)
        assertEquals(BillSort.TIME_DESC, opts[0].value)
        assertEquals("金额从高到低", opts[1].label)
        assertEquals(BillSort.AMOUNT_DESC, opts[1].value)
        assertEquals("金额从低到高", opts[2].label)
        assertEquals(BillSort.AMOUNT_ASC, opts[2].value)
    }

    @Test
    fun billFilters_defaultSortIsNull() {
        // 默认 null = 时间倒序，与首页 filtersSnapshot()（位置参数构造）及旧 Bundle 反序列化兼容
        assertNull(BillFilters().sort)
    }
}

package com.xl.bill.mint.data.repo

import com.xl.bill.mint.data.db.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CategoryRepository 纯 JVM 单测：resolveFallback 兜底分类选择逻辑。
 */
class CategoryRepositoryTest {

    private fun cat(id: Long, name: String, type: Int, sort: Int, isCustom: Boolean = false) =
        CategoryEntity(id = id, name = name, icon = "🏷️", type = type, sort = sort, isCustom = isCustom)

    @Test
    fun prefersSameNameDefaultCategory() {
        val sameType = listOf(
            cat(1, "餐饮", CategoryEntity.TYPE_EXPENSE, 0),
            cat(2, "其他支出", CategoryEntity.TYPE_EXPENSE, 9),
            cat(3, "购物", CategoryEntity.TYPE_EXPENSE, 5)
        )
        val fallback = CategoryRepository.resolveFallback(sameType, CategoryEntity.TYPE_EXPENSE)
        assertEquals(2L, fallback!!.id)
    }

    @Test
    fun renamedPresetFallsBackToMinSort() {
        // 预置分类被改名后按名字找不到「其他支出」，应退化为同类型 sort 最小者
        val sameType = listOf(
            cat(1, "干饭", CategoryEntity.TYPE_EXPENSE, 0),
            cat(2, "剁手", CategoryEntity.TYPE_EXPENSE, 5)
        )
        val fallback = CategoryRepository.resolveFallback(sameType, CategoryEntity.TYPE_EXPENSE)
        assertEquals(1L, fallback!!.id)
    }

    @Test
    fun incomeUsesOtherIncomeName() {
        val sameType = listOf(
            cat(1, "工资", CategoryEntity.TYPE_INCOME, 0),
            cat(2, "其他收入", CategoryEntity.TYPE_INCOME, 6)
        )
        val fallback = CategoryRepository.resolveFallback(sameType, CategoryEntity.TYPE_INCOME)
        assertEquals(2L, fallback!!.id)
    }

    @Test
    fun emptyListReturnsNull() {
        assertNull(CategoryRepository.resolveFallback(emptyList(), CategoryEntity.TYPE_EXPENSE))
    }

    @Test
    fun singleCandidateIsSelected() {
        val sameType = listOf(cat(7, "只剩我一个", CategoryEntity.TYPE_EXPENSE, 3))
        val fallback = CategoryRepository.resolveFallback(sameType, CategoryEntity.TYPE_EXPENSE)
        assertEquals(7L, fallback!!.id)
    }
}

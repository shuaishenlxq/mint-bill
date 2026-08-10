package com.xl.bill.mint.parser

import com.xl.bill.mint.data.db.CategoryDao
import com.xl.bill.mint.data.db.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CategoryMatcher 纯 JVM 单测：prepare 预计算 / match 匹配规则 / resolveBatch 批量与同文本短路。
 */
class CategoryMatcherTest {

    private fun cat(id: Long, name: String, type: Int, keywords: String = "", sort: Int = 0) =
        CategoryEntity(id = id, name = name, icon = "🏷️", type = type, keywords = keywords, sort = sort)

    // ---------- prepare ----------

    @Test
    fun prepareSplitsAndTrimsKeywords() {
        val prepared = CategoryMatcher.prepare(
            listOf(cat(1, "餐饮", CategoryEntity.TYPE_EXPENSE, " 美食 ,,外卖 , 奶茶 "))
        )
        assertEquals(1, prepared.size)
        assertEquals(1L, prepared[0].id)
        assertEquals("餐饮", prepared[0].name)
        assertEquals(CategoryEntity.TYPE_EXPENSE, prepared[0].type)
        assertEquals(listOf("美食", "外卖", "奶茶"), prepared[0].keywords)
    }

    @Test
    fun prepareKeepsEmptyKeywordsListWhenBlank() {
        val prepared = CategoryMatcher.prepare(listOf(cat(2, "其他支出", CategoryEntity.TYPE_EXPENSE, " ,, ")))
        assertEquals(emptyList<String>(), prepared[0].keywords)
    }

    // ---------- match ----------

    @Test
    fun matchPicksHighestHitCount() {
        val prepared = CategoryMatcher.prepare(
            listOf(
                cat(1, "餐饮", CategoryEntity.TYPE_EXPENSE, "美食,外卖"),
                cat(2, "购物", CategoryEntity.TYPE_EXPENSE, "淘宝,京东"),
                cat(3, "其他支出", CategoryEntity.TYPE_EXPENSE)
            )
        )
        assertEquals(1L, CategoryMatcher.match(prepared, CategoryEntity.TYPE_EXPENSE, "美团外卖美食"))
        assertEquals(2L, CategoryMatcher.match(prepared, CategoryEntity.TYPE_EXPENSE, "京东淘宝购物"))
    }

    @Test
    fun matchFallsBackToInitialDefaultFoodWhenNoHit() {
        // 支出未配置默认分类 → 回落初始默认「餐饮」（有餐饮分类时）
        val prepared = CategoryMatcher.prepare(
            listOf(
                cat(1, "餐饮", CategoryEntity.TYPE_EXPENSE, "美食"),
                cat(2, "其他支出", CategoryEntity.TYPE_EXPENSE)
            )
        )
        assertEquals(1L, CategoryMatcher.match(prepared, CategoryEntity.TYPE_EXPENSE, "完全无关文本"))
    }

    @Test
    fun matchFallsBackToFirstOfTypeWhenDefaultRenamed() {
        // 「其他支出」「餐饮」都不存在 → 退化为同 type 首个
        val prepared = CategoryMatcher.prepare(
            listOf(
                cat(1, "干饭", CategoryEntity.TYPE_EXPENSE, "干饭"),
                cat(2, "剁手", CategoryEntity.TYPE_EXPENSE, "剁手")
            )
        )
        assertEquals(1L, CategoryMatcher.match(prepared, CategoryEntity.TYPE_EXPENSE, "完全无关文本"))
    }

    @Test
    fun matchUsesConfiguredDefault() {
        // 设置页配置默认分类（其他支出 id=2）→ 回落用配置值，而非初始默认餐饮
        val prepared = CategoryMatcher.prepare(
            listOf(
                cat(1, "餐饮", CategoryEntity.TYPE_EXPENSE, "美食"),
                cat(2, "其他支出", CategoryEntity.TYPE_EXPENSE)
            )
        )
        assertEquals(
            2L,
            CategoryMatcher.match(
                prepared, CategoryEntity.TYPE_EXPENSE, "完全无关文本",
                Defaults(expenseId = 2L)
            )
        )
    }

    @Test
    fun matchConfiguredDefaultMissing_fallsBackToFood() {
        // 配置的默认分类 id 已失效（被删/改名）→ 回落初始默认餐饮
        val prepared = CategoryMatcher.prepare(
            listOf(
                cat(1, "餐饮", CategoryEntity.TYPE_EXPENSE, "美食"),
                cat(2, "其他支出", CategoryEntity.TYPE_EXPENSE)
            )
        )
        assertEquals(
            1L,
            CategoryMatcher.match(
                prepared, CategoryEntity.TYPE_EXPENSE, "完全无关文本",
                Defaults(expenseId = 999L)
            )
        )
    }

    @Test
    fun matchIncomeConfiguredDefault() {
        val prepared = CategoryMatcher.prepare(
            listOf(
                cat(1, "工资", CategoryEntity.TYPE_INCOME, "工资"),
                cat(2, "其他收入", CategoryEntity.TYPE_INCOME)
            )
        )
        // 收入配置默认分类（工资 id=1）→ 未命中关键词时用配置值
        assertEquals(
            1L,
            CategoryMatcher.match(
                prepared, CategoryEntity.TYPE_INCOME, "红包到账",
                Defaults(incomeId = 1L)
            )
        )
    }

    @Test
    fun matchIncomeUsesOtherIncomeName() {
        val prepared = CategoryMatcher.prepare(
            listOf(
                cat(1, "工资", CategoryEntity.TYPE_INCOME, "工资"),
                cat(2, "其他收入", CategoryEntity.TYPE_INCOME)
            )
        )
        assertEquals(2L, CategoryMatcher.match(prepared, CategoryEntity.TYPE_INCOME, "红包到账"))
        assertEquals(1L, CategoryMatcher.match(prepared, CategoryEntity.TYPE_INCOME, "本月工资"))
    }

    @Test
    fun matchEmptyListReturnsMinusOne() {
        assertEquals(-1L, CategoryMatcher.match(emptyList(), CategoryEntity.TYPE_EXPENSE, "任意文本"))
    }

    @Test
    fun matchFiltersByType() {
        // 收入关键词「工资」不得命中支出分类
        val prepared = CategoryMatcher.prepare(
            listOf(
                cat(1, "工资", CategoryEntity.TYPE_INCOME, "工资"),
                cat(2, "其他支出", CategoryEntity.TYPE_EXPENSE)
            )
        )
        assertEquals(2L, CategoryMatcher.match(prepared, CategoryEntity.TYPE_EXPENSE, "工资"))
    }

    // ---------- resolveBatch ----------

    private class FakeCategoryDao(private val cats: List<CategoryEntity>) : CategoryDao {
        override suspend fun insertAll(categories: List<CategoryEntity>) = Unit
        override suspend fun insert(category: CategoryEntity): Long = 0L
        override suspend fun update(id: Long, name: String, icon: String, keywords: String) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun getById(id: Long): CategoryEntity? = cats.firstOrNull { it.id == id }
        override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(cats)
        override suspend fun getAll(): List<CategoryEntity> = cats
        override suspend fun count(): Int = cats.size
        override suspend fun deleteAll() = Unit
    }

    @Test
    fun resolveBatchReturnsSameIdForSameText() = runBlocking {
        val matcher = CategoryMatcher(
            FakeCategoryDao(
                listOf(
                    cat(1, "餐饮", CategoryEntity.TYPE_EXPENSE, "美食,外卖"),
                    cat(2, "其他支出", CategoryEntity.TYPE_EXPENSE)
                )
            )
        )
        // 微信同商户多行 rawText 相同 → 短路返回一致分类；未识别回落初始默认「餐饮」
        val ids = matcher.resolveBatch(
            listOf(
                CategoryEntity.TYPE_EXPENSE to "美团外卖",
                CategoryEntity.TYPE_EXPENSE to "美团外卖",
                CategoryEntity.TYPE_EXPENSE to "某某银行转账"
            )
        )
        assertEquals(listOf(1L, 1L, 1L), ids)
    }

    @Test
    fun resolveCategoryIdDelegatesToBatch() = runBlocking {
        val matcher = CategoryMatcher(
            FakeCategoryDao(
                listOf(
                    cat(1, "餐饮", CategoryEntity.TYPE_EXPENSE, "美食"),
                    cat(2, "其他支出", CategoryEntity.TYPE_EXPENSE)
                )
            )
        )
        assertEquals(1L, matcher.resolveCategoryId(CategoryEntity.TYPE_EXPENSE, null, "点了一份美食"))
        // 未识别回落初始默认「餐饮」（id=1），而非「其他支出」
        assertEquals(1L, matcher.resolveCategoryId(CategoryEntity.TYPE_EXPENSE, null, "无关文本"))
    }
}

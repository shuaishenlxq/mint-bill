package com.xl.bill.mint.parser

import android.content.Context
import org.json.JSONObject

/**
 * 分类规则（与 assets/categories.json 一一对应）。
 */
data class CategoryRule(
    val name: String,
    val icon: String,
    val type: Int,
    val keywords: List<String>
)

/**
 * 分类匹配器：从数据库中的分类关键词自动归类。
 * 关键词匹配得分最高者胜出；无匹配归「其他支出 / 其他收入」。
 */
class CategoryMatcher(private val categoryDao: com.xl.bill.mint.data.db.CategoryDao) {

    private var cached: List<com.xl.bill.mint.data.db.CategoryEntity>? = null

    /** 分类增删改后调用，使进程内缓存失效，自动归类立即用上新关键词 */
    fun invalidateCache() {
        cached = null
    }

    suspend fun resolveCategoryId(type: Int, title: String?, text: String?): Long {
        val full = title.orEmpty() + text.orEmpty()
        val cats = categories()
        val best = cats
            .filter { it.type == type }
            .map { cat ->
                val kws = cat.keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                cat to kws.count { full.contains(it) }
            }
            .maxByOrNull { it.second }
        if (best != null && best.second > 0) return best.first.id
        return defaultId(type)
    }

    private suspend fun categories(): List<com.xl.bill.mint.data.db.CategoryEntity> {
        cached?.let { return it }
        return categoryDao.getAll().also { cached = it }
    }

    private suspend fun defaultId(type: Int): Long {
        val cats = categories()
        val name = if (type == _root_ide_package_.com.xl.bill.mint.data.db.CategoryEntity.Companion.TYPE_EXPENSE) "其他支出" else "其他收入"
        return cats.firstOrNull { it.type == type && it.name == name }?.id
            ?: cats.firstOrNull { it.type == type }?.id
            ?: -1L
    }

    companion object {

        /** 从 assets/categories.json 读取规则 */
        fun loadRules(context: Context): List<CategoryRule> {
            val json = context.assets.open("categories.json")
                .bufferedReader()
                .use { it.readText() }
            val arr = JSONObject(json).getJSONArray("categories")
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CategoryRule(
                    name = o.getString("name"),
                    icon = o.getString("icon"),
                    type = o.getInt("type"),
                    keywords = o.optString("keywords")
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                )
            }
        }

        /** 首次启动种子分类（保持与 JSON 一致的 sort 顺序） */
        fun defaultCategoryEntities(context: Context): List<com.xl.bill.mint.data.db.CategoryEntity> =
            loadRules(context).mapIndexed { index, r ->
                _root_ide_package_.com.xl.bill.mint.data.db.CategoryEntity(
                    name = r.name,
                    icon = r.icon,
                    type = r.type,
                    keywords = r.keywords.joinToString(","),
                    sort = index
                )
            }
    }
}

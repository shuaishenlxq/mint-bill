package com.xl.bill.mint.parser

import android.content.Context
import com.xl.bill.mint.data.db.CategoryDao
import com.xl.bill.mint.data.db.CategoryEntity
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
 * 预计算后的分类（关键词已拆分清洗，避免逐行重复 split）。
 * [prepare] 纯函数产物，JVM 可单测。
 */
internal data class PreparedCategory(
    val id: Long,
    val name: String,
    val type: Int,
    val keywords: List<String>
)

/**
 * 默认分类配置（设置页可配，支出/收入分开）：
 * 未配置（null）时按类型回落初始默认——支出→「餐饮」→「其他支出」；收入→「其他收入」。
 * 由调用方（自动记账管线/导入服务）从 SettingsRepository 读取后传入，不缓存、即时生效。
 */
data class Defaults(val expenseId: Long? = null, val incomeId: Long? = null)

/**
 * 分类匹配器：从数据库中的分类关键词自动归类。
 * 关键词匹配得分最高者胜出；无匹配归「餐饮/其他支出 / 其他收入」（可配置默认分类）。
 *
 * 性能：进程内缓存预计算结果（[PreparedCategory]），批量归类走 [resolveBatch]——
 * 同文本（如微信同商户多行 rawText 相同）经 HashMap 短路只匹配一次，避免 O(N×C×K) 重复计算。
 */
class CategoryMatcher(private val categoryDao: CategoryDao) {

    private var preparedCache: List<PreparedCategory>? = null

    /** 分类增删改后调用，使进程内缓存失效，自动归类立即用上新关键词 */
    fun invalidateCache() {
        preparedCache = null
    }

    /**
     * 批量归类：对每项 (type, 全文) 返回分类 id。
     * 同 full 文本短路——同商户重复行只做一次关键词匹配，结果复用。
     */
    suspend fun resolveBatch(
        items: List<Pair<Int, String>>,
        defaults: Defaults = Defaults()
    ): List<Long> {
        if (items.isEmpty()) return emptyList()
        val prepared = prepared()
        val cache = HashMap<String, Long>(items.size)
        return items.map { (type, full) ->
            cache.getOrPut(full) { match(prepared, type, full, defaults) }
        }
    }

    suspend fun resolveCategoryId(
        type: Int,
        title: String?,
        text: String?,
        defaults: Defaults = Defaults()
    ): Long =
        resolveBatch(listOf(type to (title.orEmpty() + text.orEmpty())), defaults).first()

    private suspend fun prepared(): List<PreparedCategory> {
        preparedCache?.let { return it }
        return prepare(categoryDao.getAll()).also { preparedCache = it }
    }

    companion object {

        internal const val DEFAULT_EXPENSE_NAME = "其他支出"
        internal const val DEFAULT_INCOME_NAME = "其他收入"

        /** 支出初始默认分类（未配置默认分类时的回落目标，设置页可改） */
        internal const val INITIAL_DEFAULT_EXPENSE_NAME = "餐饮"

        /** 分类实体 → 预计算结构（关键词一次性 split/trim/去空，与 loadRules 同款清洗） */
        internal fun prepare(cats: List<CategoryEntity>): List<PreparedCategory> = cats.map { c ->
            PreparedCategory(
                id = c.id,
                name = c.name,
                type = c.type,
                keywords = c.keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            )
        }

        /**
         * 单条匹配：同 type 分类中关键词命中数最高者胜出；
         * 全部未命中回落：配置的默认分类 → 支出「餐饮」/「其他支出」、收入「其他收入」→ 同 type 首个 → -1。
         */
        internal fun match(
            prepared: List<PreparedCategory>,
            type: Int,
            full: String,
            defaults: Defaults = Defaults()
        ): Long {
            val same = prepared.filter { it.type == type }
            val best = same
                .map { it to it.keywords.count { kw -> full.contains(kw) } }
                .maxByOrNull { it.second }
            if (best != null && best.second > 0) return best.first.id

            // 设置页配置的默认分类优先；id 已失效（分类被删/改名）时回落初始默认
            val configuredId =
                if (type == CategoryEntity.TYPE_EXPENSE) defaults.expenseId else defaults.incomeId
            val configured = configuredId?.let { cid -> same.firstOrNull { it.id == cid }?.id }
            if (configured != null) return configured

            val fallback = when (type) {
                CategoryEntity.TYPE_EXPENSE ->
                    // 初始默认「餐饮」；被改名/删除后退化为「其他支出」
                    same.firstOrNull { it.name == INITIAL_DEFAULT_EXPENSE_NAME }?.id
                        ?: same.firstOrNull { it.name == DEFAULT_EXPENSE_NAME }?.id
                else -> same.firstOrNull { it.name == DEFAULT_INCOME_NAME }?.id
            }
            return fallback ?: same.firstOrNull()?.id
                ?: -1L
        }

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
        fun defaultCategoryEntities(context: Context): List<CategoryEntity> =
            loadRules(context).mapIndexed { index, r ->
                CategoryEntity(
                    name = r.name,
                    icon = r.icon,
                    type = r.type,
                    keywords = r.keywords.joinToString(","),
                    sort = index
                )
            }
    }
}

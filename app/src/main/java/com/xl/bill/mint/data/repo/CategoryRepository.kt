package com.xl.bill.mint.data.repo

import androidx.room.withTransaction
import com.xl.bill.mint.data.repo.CategoryRepository.Companion.resolveFallback

/**
 * 分类增删改。删除时把该分类下账单归并到同类型兜底分类，保证无悬空 categoryId。
 */
class CategoryRepository(
    private val categoryDao: com.xl.bill.mint.data.db.CategoryDao,
    private val txDao: com.xl.bill.mint.data.db.TransactionDao,
    private val db: com.xl.bill.mint.data.db.AppDatabase,
    private val matcher: com.xl.bill.mint.parser.CategoryMatcher
) {

    /** 新建自定义分类，返回自增 id（sort 取同类型最大值 +1） */
    suspend fun addCategory(name: String, icon: String, type: Int, keywords: String): Long {
        val maxSort = categoryDao.getAll().filter { it.type == type }.maxOfOrNull { it.sort } ?: 0
        val id = categoryDao.insert(
            _root_ide_package_.com.xl.bill.mint.data.db.CategoryEntity(
                name = name.trim(),
                icon = icon.ifEmpty { "🏷️" },
                type = type,
                keywords = keywords.trim(),
                sort = maxSort + 1,
                isCustom = true
            )
        )
        matcher.invalidateCache()
        return id
    }

    suspend fun updateCategory(id: Long, name: String, icon: String, keywords: String) {
        categoryDao.update(id, name.trim(), icon.ifEmpty { "🏷️" }, keywords.trim())
        matcher.invalidateCache()
    }

    /**
     * 删除分类（仅自定义）。事务内先归并账单到同类型兜底分类，再删分类。
     * 兜底规则见 [resolveFallback]；找不到兜底时中止删除（防御，正常不会触发，
     * 因预置分类含「其他支出/其他收入」且不可删）。
     */
    suspend fun deleteCategory(id: Long, type: Int) {
        val fallback = db.withTransaction {
            val sameType = categoryDao.getAll().filter { it.type == type && it.id != id }
            val target = resolveFallback(sameType, type) ?: return@withTransaction null
            txDao.updateCategoryIdByOldId(id, target.id)
            categoryDao.delete(id)
            target
        }
        if (fallback != null) matcher.invalidateCache()
    }

    companion object {
        /** 纯函数，供 JVM 单测：优先同名「其他支出/其他收入」，否则取同类型 sort 最小者 */
        fun resolveFallback(sameType: List<com.xl.bill.mint.data.db.CategoryEntity>, type: Int): com.xl.bill.mint.data.db.CategoryEntity? {
            val defaultName = if (type == _root_ide_package_.com.xl.bill.mint.data.db.CategoryEntity.Companion.TYPE_EXPENSE) "其他支出" else "其他收入"
            return sameType.firstOrNull { it.name == defaultName } ?: sameType.minByOrNull { it.sort }
        }
    }
}

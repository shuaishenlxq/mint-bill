package com.xl.bill.mint.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记账分类。keywords 为逗号分隔的匹配关键词，用于自动归类。
 * type: 0=支出类 1=收入类
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val icon: String,
    val type: Int,
    val keywords: String = "",
    val sort: Int = 0,
    /** true=用户自定义分类，false=预置分类（不可删除） */
    val isCustom: Boolean = false
) {
    companion object {
        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1
    }
}

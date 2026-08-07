package com.xl.bill.mint.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    /** 单条插入（自定义分类），返回自增 id */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name, icon = :icon, keywords = :keywords WHERE id = :id")
    suspend fun update(id: Long, name: String, icon: String, keywords: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY type ASC, sort ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type ASC, sort ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /** 清空全表（导入前使用） */
    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

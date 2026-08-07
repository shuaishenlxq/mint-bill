package com.xl.bill.mint.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    /** 全量读取（导出用） */
    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<SettingEntity>

    /** 清空全表（导入前使用） */
    @Query("DELETE FROM settings")
    suspend fun deleteAll()

    /** 批量写入（导入用） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settings: List<SettingEntity>)
}

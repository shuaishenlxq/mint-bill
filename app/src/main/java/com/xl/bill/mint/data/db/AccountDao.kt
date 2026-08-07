package com.xl.bill.mint.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Query("SELECT * FROM accounts")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    /** 清空全表（导入前使用） */
    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}

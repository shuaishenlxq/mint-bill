package com.xl.bill.mint.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 收支合计投影（分）：income=收入合计、expense=支出合计（type 0=支出/1=收入） */
data class SumResult(val income: Long, val expense: Long)

@Dao
interface TransactionDao {

    /** 唯一索引 IGNORE：通知类重复推送天然被去重 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: TransactionEntity): Long

    /**
     * 批量插入（导入用）：IGNORE 以 notificationKey 唯一索引兜底去重。
     * @return 每行插入后的 rowId，-1 表示因冲突被忽略（未插入）
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(txs: List<TransactionEntity>): LongArray

    /** 批量去重：返回已存在（同 notificationKey）的键集合，供导入前过滤 */
    @Query("SELECT notificationKey FROM transactions WHERE notificationKey IN (:keys)")
    suspend fun findExistingNotificationKeys(keys: List<String>): List<String>

    /** 覆盖导入：按去重键批量删除旧记录（notificationKey UNIQUE 定位），返回删除行数 */
    @Query("DELETE FROM transactions WHERE notificationKey IN (:keys)")
    suspend fun deleteByNotificationKeys(keys: List<String>): Int

    /** 全量读取（导出用） */
    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<TransactionEntity>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateCategory(id: Long, categoryId: Long)

    /** 删除分类时，把该分类下全部账单改挂到兜底分类 */
    @Query("UPDATE transactions SET categoryId = :newId WHERE categoryId = :oldId")
    suspend fun updateCategoryIdByOldId(oldId: Long, newId: Long)

    @Query("UPDATE transactions SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** 缺备注的自动记录（非手动渠道且备注为空），用于补备注提醒 */
    @Query(
        "SELECT * FROM transactions " +
            "WHERE channel != 'manual' AND (note IS NULL OR TRIM(note) = '') " +
            "ORDER BY occurredAt DESC"
    )
    fun observeUnnoted(): Flow<List<TransactionEntity>>

    /** 按时间范围查询（[start, end)，供小组件等单次渲染使用，命中 occurredAt 索引） */
    @Query("SELECT * FROM transactions WHERE occurredAt >= :start AND occurredAt < :end ORDER BY occurredAt DESC")
    suspend fun getByRange(start: Long, end: Long): List<TransactionEntity>

    /**
     * 多条件交集分页查询（全部账单页）。start/end/type/channel/categoryId 传 null 表示不限制。
     *
     * @param sortMode 排序方式：0=时间倒序（默认），1=金额升序，2=金额降序，3=时间正序。
     *                 SQL 用 CASE 表达式实现（Room 的 @Query 是编译期常量，不能直接拼 ORDER BY 方向），
     *                 id DESC 兜底保证分页跨页顺序稳定。
     */
    @Query(
        "SELECT * FROM transactions " +
            "WHERE (:start IS NULL OR occurredAt >= :start) " +
            "AND (:end IS NULL OR occurredAt < :end) " +
            "AND (:type IS NULL OR type = :type) " +
            "AND (:channel IS NULL OR channel = :channel) " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId) " +
            "ORDER BY CASE :sortMode " +
            "WHEN 1 THEN amount " +
            "WHEN 2 THEN -amount " +
            "WHEN 3 THEN occurredAt " +
            "ELSE -occurredAt END ASC, id DESC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun getFiltered(
        start: Long?,
        end: Long?,
        type: Int?,
        channel: String?,
        categoryId: Long?,
        sortMode: Int,
        limit: Int,
        offset: Int
    ): List<TransactionEntity>

    /** 多条件交集总数（分页 hasMore 判定 + 列表页「共 N 笔」） */
    @Query(
        "SELECT COUNT(*) FROM transactions " +
            "WHERE (:start IS NULL OR occurredAt >= :start) " +
            "AND (:end IS NULL OR occurredAt < :end) " +
            "AND (:type IS NULL OR type = :type) " +
            "AND (:channel IS NULL OR channel = :channel) " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId)"
    )
    suspend fun countFiltered(start: Long?, end: Long?, type: Int?, channel: String?, categoryId: Long?): Int

    /** 多条件交集收支合计（全部账单页统计栏，与 countFiltered 同 WHERE 口径） */
    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 0 THEN amount ELSE 0 END), 0) AS expense " +
            "FROM transactions " +
            "WHERE (:start IS NULL OR occurredAt >= :start) " +
            "AND (:end IS NULL OR occurredAt < :end) " +
            "AND (:type IS NULL OR type = :type) " +
            "AND (:channel IS NULL OR channel = :channel) " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId)"
    )
    suspend fun sumFiltered(start: Long?, end: Long?, type: Int?, channel: String?, categoryId: Long?): SumResult

    /** 多条件交集响应式列表（首页，LIMIT 20）。表级失效重发，参数变化需外层 flatMapLatest。 */
    @Query(
        "SELECT * FROM transactions " +
            "WHERE (:start IS NULL OR occurredAt >= :start) " +
            "AND (:end IS NULL OR occurredAt < :end) " +
            "AND (:type IS NULL OR type = :type) " +
            "AND (:channel IS NULL OR channel = :channel) " +
            "ORDER BY occurredAt DESC, id DESC " +
            "LIMIT :limit"
    )
    fun observeFiltered(
        start: Long?,
        end: Long?,
        type: Int?,
        channel: String?,
        limit: Int
    ): Flow<List<TransactionEntity>>
}

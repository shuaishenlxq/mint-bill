package com.xl.bill.mint.data.repo

import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

/**
 * 账单仓库：写库（自动/手动）、纠错（改分类/备注/删除）、查询流。
 */
class TransactionRepository(
    private val txDao: com.xl.bill.mint.data.db.TransactionDao,
    private val accountDao: com.xl.bill.mint.data.db.AccountDao
) {

    fun observeAll(): Flow<List<com.xl.bill.mint.data.db.TransactionEntity>> = txDao.observeAll()

    /** 缺备注的自动记录（补备注提醒用） */
    fun observeUnnoted(): Flow<List<com.xl.bill.mint.data.db.TransactionEntity>> = txDao.observeUnnoted()

    /** 按时间范围查询（[start, end)，供桌面小组件渲染） */
    suspend fun getByRange(start: Long, end: Long): List<com.xl.bill.mint.data.db.TransactionEntity> =
        txDao.getByRange(start, end)

    /**
     * 多条件交集分页查询（全部账单页）；null 参数表示该条件不限制。
     * @param sortMode 排序方式：0=时间倒序，1=金额升序，2=金额降序，3=时间正序
     */
    suspend fun getFiltered(
        start: Long?,
        end: Long?,
        type: Int?,
        channel: String?,
        categoryId: Long?,
        sortMode: Int,
        limit: Int,
        offset: Int
    ): List<com.xl.bill.mint.data.db.TransactionEntity> = txDao.getFiltered(start, end, type, channel, categoryId, sortMode, limit, offset)

    /** 多条件交集总数（分页 hasMore 判定 + 列表页「共 N 笔」） */
    suspend fun countFiltered(start: Long?, end: Long?, type: Int?, channel: String?, categoryId: Long?): Int =
        txDao.countFiltered(start, end, type, channel, categoryId)

    /** 多条件交集收支合计（全部账单页统计栏，与 countFiltered 同 WHERE 口径） */
    suspend fun sumFiltered(start: Long?, end: Long?, type: Int?, channel: String?, categoryId: Long?): com.xl.bill.mint.data.db.SumResult =
        txDao.sumFiltered(start, end, type, channel, categoryId)

    /** 多条件交集响应式列表（首页，LIMIT 20） */
    fun observeFiltered(
        start: Long?,
        end: Long?,
        type: Int?,
        channel: String?,
        limit: Int
    ): Flow<List<com.xl.bill.mint.data.db.TransactionEntity>> = txDao.observeFiltered(start, end, type, channel, limit)

    /** 自动记账写入（解析结果 + 已解析出的分类/账户） */
    suspend fun insert(parsed: com.xl.bill.mint.parser.ParsedBill, categoryId: Long, accountId: Long): Long {
        val id = txDao.insert(
            _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity(
                channel = parsed.channel.name.lowercase(),
                rawTitle = parsed.rawTitle,
                rawText = parsed.rawText,
                amount = parsed.amount,
                type = parsed.type,
                categoryId = categoryId,
                accountId = accountId,
                merchant = parsed.merchant,
                occurredAt = parsed.occurredAt,
                notificationKey = parsed.notificationKey,
                createdAt = System.currentTimeMillis()
            )
        )
        notifyWidgetDataChanged()
        return id
    }

    /** 手动记账 */
    suspend fun insertManual(type: Int, amountFen: Long, categoryId: Long, note: String?): Long {
        val now = System.currentTimeMillis()
        val id = txDao.insert(
            _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity(
                channel = "manual",
                amount = amountFen,
                type = type,
                categoryId = categoryId,
                accountId = manualAccountId(),
                merchant = note?.take(24),
                occurredAt = now,
                notificationKey = "manual-$now-${Random.nextLong()}",
                note = note,
                createdAt = now
            )
        )
        notifyWidgetDataChanged()
        return id
    }

    /**
     * 批量追加导入（PDF 导入用）：IGNORE 兜底去重，返回实际插入条数。
     * 整个批次只刷新一次小组件。
     */
    suspend fun insertAll(entities: List<com.xl.bill.mint.data.db.TransactionEntity>): Int {
        val inserted = insertAllQuiet(entities)
        if (inserted > 0) notifyWidgetDataChanged()
        return inserted
    }

    /**
     * 静默批量插入（不刷新小组件）：供导入事务内调用——
     * 调用方用 db.withTransaction 包裹整体写入，事务提交后再统一 [notifyWidgetDataChanged]，
     * 避免事务内广播读到旧数据，也避免 REPLACE 模式下删/插双事务双刷新。
     */
    suspend fun insertAllQuiet(entities: List<com.xl.bill.mint.data.db.TransactionEntity>): Int {
        if (entities.isEmpty()) return 0
        return txDao.insertAll(entities).count { it > 0 }
    }

    /** 批量去重查询：返回已存在的 notificationKey 集合（重复导入同一文件跳过） */
    suspend fun existingNotificationKeys(keys: List<String>): Set<String> {
        if (keys.isEmpty()) return emptySet()
        return txDao.findExistingNotificationKeys(keys.distinct()).toSet()
    }

    /** 按去重键批量删除（覆盖导入用：先删旧记录再整体重插） */
    suspend fun deleteByNotificationKeys(keys: List<String>): Int {
        if (keys.isEmpty()) return 0
        return txDao.deleteByNotificationKeys(keys.distinct())
    }

    suspend fun updateCategory(id: Long, categoryId: Long) {
        txDao.updateCategory(id, categoryId)
        notifyWidgetDataChanged()
    }

    suspend fun updateNote(id: Long, note: String?) {
        txDao.updateNote(id, note)
        notifyWidgetDataChanged()
    }

    suspend fun delete(id: Long) {
        txDao.deleteById(id)
        notifyWidgetDataChanged()
    }

    suspend fun clearAll() {
        txDao.deleteAll()
        notifyWidgetDataChanged()
    }

    /** 账单数据变更后，事件刷新桌面小组件（同进程直调，开销极小；导入事务内调用需移出事务） */
    fun notifyWidgetDataChanged() {
        _root_ide_package_.com.xl.bill.mint.widget.BudgetWidgetReceiver.Companion.notifyDataChanged(
            _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appContext)
    }

    private val accountCache = HashMap<String, Long>()

    /** 支付包名 → 账户 id（找不到回退到「银行卡」） */
    suspend fun resolveAccountId(pkg: String): Long {
        accountCache[pkg]?.let { return it }
        val id = accountDao.getByPackage(pkg)?.id
            ?: accountDao.getByName(ACCOUNT_BANK)?.id
            ?: 1L
        accountCache[pkg] = id
        return id
    }

    suspend fun manualAccountId(): Long = accountDao.getByName(ACCOUNT_MANUAL)?.id ?: 1L

    companion object {
        const val ACCOUNT_BANK = "银行卡"
        const val ACCOUNT_MANUAL = "手动记账"
    }
}

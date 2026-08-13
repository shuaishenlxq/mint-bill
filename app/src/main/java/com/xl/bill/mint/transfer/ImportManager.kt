package com.xl.bill.mint.transfer

import androidx.room.withTransaction
import com.xl.bill.mint.data.db.AppDatabase
import com.xl.bill.mint.data.repo.SettingsRepository

/**
 * 导入：校验 JSON → 事务内清空四表 → 整体写入（原子，任一步失败回滚）。
 * 表已清空故带原 id 直接插入无冲突，SQLite 自增序列自动接续 max+1，
 * transactions 的 categoryId/accountId 引用与 categories/accounts 天然一致，无需重映射。
 * DataStore 预置项在 Room 事务之外写回（失败不影响已提交的 DB 数据）。
 */
class ImportManager(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository
) {

    data class ImportResult(val transactionCount: Int)

    suspend fun import(json: String): ImportResult {
        val snapshot = TransferCodec.decode(json)
        val inserted = db.withTransaction {
            val txDao = db.transactionDao()
            val catDao = db.categoryDao()
            val accDao = db.accountDao()
            val setDao = db.settingDao()
            setDao.deleteAll()
            catDao.deleteAll()
            accDao.deleteAll()
            txDao.deleteAll()
            catDao.insertAll(snapshot.categories)
            accDao.insertAll(snapshot.accounts)
            // insertAll 为 OnConflictStrategy.IGNORE，返回 LongArray（-1=被忽略），据此统计真实写入条数
            txDao.insertAll(snapshot.transactions).count { it != -1L }
        }
        // 预置项写回（不在事务内；DB 已提交，预置项失败仅影响个别开关）
        settingsRepository.importPreferences(snapshot.preferences)
        // 导入成功后刷新桌面小组件
        _root_ide_package_.com.xl.bill.mint.widget.BudgetWidgetReceiver.Companion.notifyDataChanged(
            _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appContext)
        return ImportResult(inserted)
    }
}

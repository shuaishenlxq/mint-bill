package com.xl.bill.mint.transfer

import androidx.room.withTransaction

/**
 * 导入：校验 JSON → 事务内清空四表 → 整体写入（原子，任一步失败回滚）。
 * 表已清空故带原 id 直接插入无冲突，SQLite 自增序列自动接续 max+1，
 * transactions 的 categoryId/accountId 引用与 categories/accounts 天然一致，无需重映射。
 */
class ImportManager(private val db: com.xl.bill.mint.data.db.AppDatabase) {

    data class ImportResult(val transactionCount: Int)

    suspend fun import(json: String): ImportResult {
        val snapshot = TransferCodec.decode(json)
        val count = db.withTransaction {
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
            txDao.insertAll(snapshot.transactions)
            setDao.insertAll(snapshot.settings)
            snapshot.transactions.size
        }
        // 导入成功后刷新桌面小组件
        _root_ide_package_.com.xl.bill.mint.widget.BudgetWidgetReceiver.Companion.notifyDataChanged(
            _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appContext)
        return ImportResult(count)
    }
}

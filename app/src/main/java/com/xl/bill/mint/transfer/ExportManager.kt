package com.xl.bill.mint.transfer

import android.os.Build
import androidx.room.withTransaction
import com.xl.bill.mint.BuildConfig
import com.xl.bill.mint.data.db.AppDatabase
import com.xl.bill.mint.data.repo.SettingsRepository

/**
 * 导出：四张表全量读取 → 组装 [DbSnapshot] → JSON 序列化。
 * 读取包在事务中，保证与记账写入并发时的一致性快照。
 * DataStore 预置项独立于 Room 事务读取，再并入快照。
 */
class ExportManager(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository
) {

    suspend fun export(): String {
        val snapshot = db.withTransaction {
            DbSnapshot(
                formatVersion = TransferCodec.FORMAT_VERSION,
                exportedAt = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                deviceInfo = DeviceInfo(
                    model = Build.MODEL ?: "",
                    sdk = Build.VERSION.SDK_INT
                ),
                categories = db.categoryDao().getAll(),
                accounts = db.accountDao().getAll(),
                transactions = db.transactionDao().getAll(),
                settings = db.settingDao().getAll()
            )
        }
        val preferences = settingsRepository.exportPreferences()
        return TransferCodec.encode(snapshot.copy(preferences = preferences))
    }
}

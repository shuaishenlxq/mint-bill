package com.xl.bill.mint.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        SettingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun settingDao(): SettingDao

    companion object {

        private const val DB_NAME = "mint_bill.db"
        private const val BACKUP_SUFFIX_PLAIN = ".plain.bak"
        private const val BACKUP_SUFFIX_FAIL = ".init-fail.bak"

        /** 1→2：categories 表加 isCustom 列（NOT NULL 必须带 DEFAULT，SQLite ALTER 限制） */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 构建数据库（优先 SQLCipher 加密）：
         * 1. 加载 SQLCipher native lib；
         * 2. 从 Keystore 托管中取 passphrase；
         * 3. 若存在旧版明文库，先无损迁移到加密库（幂等：迁移后明文文件已删除）；
         * 4. 任一步失败 → 备份/清走旧文件 → 降级为全新明文库，**保证 app 可启动**（不裸崩）。
         * 所有失败路径都会写入 crash.log，便于事后定位。
         */
        fun build(context: Context): AppDatabase {
            return try {
                SQLiteDatabase.loadLibs(context)
                val passphrase = _root_ide_package_.com.xl.bill.mint.security.KeyStoreManager.getOrCreatePassphrase(context)
                runBlocking { migrateLegacyPlainDbIfNeeded(context, passphrase) }
                encryptedBuilder(context, passphrase).build()
            } catch (t: Throwable) {
                // 加密初始化彻底失败（loadLibs 失败 / Keystore 不可用 / 意外）：
                // 备份旧文件后清库，降级明文全新构建，保证启动；数据留备份文件。
                _root_ide_package_.com.xl.bill.mint.util.CrashLog.record(context, "encrypted DB init failed, recover with fresh plain DB", t)
                recoverFromInitFailure(context)
                plainBuilder(context).build()
            }
        }

        private fun encryptedBuilder(context: Context, passphrase: ByteArray) =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(SupportFactory(passphrase))
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()

        private fun plainBuilder(context: Context) =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()

        /**
         * 旧版明文库 → 加密库迁移：
         * - 文件不存在：全新安装，直接建加密库；
         * - 能以加密方式打开：已是加密库（或空文件），跳过；
         * - 否则按明文库处理：先备份明文文件 → 事务读出四表全量 → 删除明文 → 加密重建写回 → 成功后删备份。
         * 任一步失败：移走旧文件（备份保留），后续以全新加密库启动，不崩溃。
         * 全程在 IO 线程执行，一次性成本。
         */
        private suspend fun migrateLegacyPlainDbIfNeeded(context: Context, passphrase: ByteArray) {
            withContext(Dispatchers.IO) {
                val dbFile = context.getDatabasePath(DB_NAME)
                if (!dbFile.exists()) return@withContext
                if (canOpenEncrypted(dbFile, passphrase)) return@withContext

                backupPlainDb(context)

                try {
                    val legacy = plainBuilder(context).build()
                    val snapshot = try {
                        legacy.withTransaction {
                            LegacySnapshot(
                                categories = legacy.categoryDao().getAll(),
                                accounts = legacy.accountDao().getAll(),
                                transactions = legacy.transactionDao().getAll(),
                                settings = legacy.settingDao().getAll()
                            )
                        }
                    } finally {
                        legacy.close()
                    }

                    context.deleteDatabase(DB_NAME) // 连 -wal/-shm 一并删除

                    val encrypted = encryptedBuilder(context, passphrase).build()
                    try {
                        encrypted.withTransaction {
                            encrypted.categoryDao().insertAll(snapshot.categories)
                            encrypted.accountDao().insertAll(snapshot.accounts)
                            encrypted.transactionDao().insertAll(snapshot.transactions)
                            encrypted.settingDao().insertAll(snapshot.settings)
                        }
                    } finally {
                        encrypted.close()
                    }

                    // 迁移成功，清理明文备份
                    runCatching { context.deleteDatabase("$DB_NAME$BACKUP_SUFFIX_PLAIN") }
                } catch (t: Throwable) {
                    // 迁移失败：移走明文文件（备份仍在 .plain.bak），后续以全新加密库启动，不崩
                    _root_ide_package_.com.xl.bill.mint.util.CrashLog.record(context, "plain->encrypted migration failed", t)
                    runCatching { context.deleteDatabase(DB_NAME) }
                }
            }
        }

        private fun backupPlainDb(context: Context) {
            runCatching {
                val src = context.getDatabasePath(DB_NAME)
                val dst = context.getDatabasePath("$DB_NAME$BACKUP_SUFFIX_PLAIN")
                src.copyTo(dst, overwrite = true)
            }
        }

        private fun recoverFromInitFailure(context: Context) {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return
            runCatching { dbFile.copyTo(File(dbFile.path + BACKUP_SUFFIX_FAIL), overwrite = true) }
            runCatching { context.deleteDatabase(DB_NAME) }
        }

        /** 尝试以加密方式打开：成功 → 已是加密库（或空文件）；抛异常（含 Error）→ 旧明文库或损坏 */
        private fun canOpenEncrypted(file: File, passphrase: ByteArray): Boolean = try {
            val db = SQLiteDatabase.openOrCreateDatabase(file.path, passphrase, null)
            db.close()
            true
        } catch (_: Throwable) {
            false
        }
    }
}

/** 明文 → 加密迁移的中间快照（四表全量） */
private data class LegacySnapshot(
    val categories: List<CategoryEntity>,
    val accounts: List<AccountEntity>,
    val transactions: List<TransactionEntity>,
    val settings: List<SettingEntity>
)

package com.xl.bill.mint.di

import android.content.Context

/**
 * 轻量手动依赖注入（MVP 不引入 Hilt）。
 */
object ServiceLocator {

    val appContext: Context
        get() = _root_ide_package_.com.xl.bill.mint.BillApplication.Companion.instance

    val appDatabase: com.xl.bill.mint.data.db.AppDatabase by lazy { _root_ide_package_.com.xl.bill.mint.data.db.AppDatabase.Companion.build(appContext) }

    val transactionRepository: com.xl.bill.mint.data.repo.TransactionRepository by lazy {
        _root_ide_package_.com.xl.bill.mint.data.repo.TransactionRepository(
            appDatabase.transactionDao(),
            appDatabase.accountDao()
        )
    }

    val settingsRepository: com.xl.bill.mint.data.repo.SettingsRepository by lazy {
        _root_ide_package_.com.xl.bill.mint.data.repo.SettingsRepository(
            appContext,
            appDatabase.settingDao()
        )
    }

    val categoryMatcher: com.xl.bill.mint.parser.CategoryMatcher by lazy {
        _root_ide_package_.com.xl.bill.mint.parser.CategoryMatcher(appDatabase.categoryDao())
    }

    val categoryRepository: com.xl.bill.mint.data.repo.CategoryRepository by lazy {
        _root_ide_package_.com.xl.bill.mint.data.repo.CategoryRepository(
            appDatabase.categoryDao(),
            appDatabase.transactionDao(),
            appDatabase,
            categoryMatcher // 同一单例，invalidateCache 才对自动归类管线生效
        )
    }

    val exportManager: com.xl.bill.mint.transfer.ExportManager by lazy {
        _root_ide_package_.com.xl.bill.mint.transfer.ExportManager(appDatabase)
    }

    val importManager: com.xl.bill.mint.transfer.ImportManager by lazy {
        _root_ide_package_.com.xl.bill.mint.transfer.ImportManager(appDatabase)
    }

    /** 微信账单 Excel 导入：解析预览与批量入库编排 */
    val billImportService: com.xl.bill.mint.billimport.BillImportService by lazy {
        _root_ide_package_.com.xl.bill.mint.billimport.BillImportService(appContext)
    }
}

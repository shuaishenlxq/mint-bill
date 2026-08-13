package com.xl.bill.mint

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BillApplication : android.app.Application() {

    companion object {
        lateinit var instance: BillApplication
            private set
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 最先安装全局崩溃捕获：任何未捕获异常都会写入 filesDir/crash.log
        _root_ide_package_.com.xl.bill.mint.util.CrashLog.install(this)
        ensureNotificationChannels()
        // 注册桌面小组件 30 分钟周期刷新
        _root_ide_package_.com.xl.bill.mint.widget.BudgetWidgetScheduler.schedule(this)
        // 预热数据库（触发 SQLCipher 明文→加密迁移，在 IO 线程执行，避免主线程首次访问阻塞）
        appScope.launch { _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appDatabase }
        appScope.launch { seedDefaults() }
        // 进程被 NLS 绑定/广播/闹钟拉起时重挂心跳计时链（MIUI 杀进程会清闹钟，
        // 不重挂则断链后只能等用户手动开 App）；后台上下文不启动 FGS，由闹钟豁免链负责
        _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.rearmTimers(this)
        // NLS 断连自救：上次运行观测到绑定断开时，进程拉起即触发一次组件 toggle 重绑
        _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.rebindNotificationListenerIfNeeded(this)
    }

    private fun ensureNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                "bill_service",
                getString(_root_ide_package_.com.xl.bill.mint.R.string.notification_service_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                "bill_guide",
                getString(_root_ide_package_.com.xl.bill.mint.R.string.notification_guide_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    /** 首次启动：种子分类与渠道账户 */
    private suspend fun seedDefaults() {
        val db = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appDatabase
        if (db.categoryDao().count() == 0) {
            db.categoryDao().insertAll(_root_ide_package_.com.xl.bill.mint.parser.CategoryMatcher.Companion.defaultCategoryEntities(this))
        }
        if (db.accountDao().count() == 0) {
            db.accountDao().insertAll(
                listOf(
                    _root_ide_package_.com.xl.bill.mint.data.db.AccountEntity(
                        name = "支付宝",
                        packageName = "com.eg.android.AlipayGphone"
                    ),
                    _root_ide_package_.com.xl.bill.mint.data.db.AccountEntity(
                        name = "微信",
                        packageName = "com.tencent.mm"
                    ),
                    _root_ide_package_.com.xl.bill.mint.data.db.AccountEntity(name = "银行卡"),
                    _root_ide_package_.com.xl.bill.mint.data.db.AccountEntity(name = "手动记账")
                )
            )
        }
    }
}

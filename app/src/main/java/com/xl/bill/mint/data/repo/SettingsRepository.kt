package com.xl.bill.mint.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mint_prefs")

/**
 * 设置仓库：
 * - 用户偏好（自动记账总开关、首次启动标记）走 DataStore；
 * - 渠道级开关（支付宝/微信/银行）走 Room Setting 表，便于 Service 侧读取。
 */
class SettingsRepository(
    private val context: Context,
    private val settingDao: com.xl.bill.mint.data.db.SettingDao
) {

    val autoRecordEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_RECORD] ?: true }

    val firstLaunchDone: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_FIRST_LAUNCH] ?: false }

    /** 无金额转账提示开关（默认开，每日最多一条） */
    val transferHintEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_TRANSFER_HINT] ?: true }

    /** 应用锁开关（默认关；开启后启动与回到前台需指纹/设备密码验证） */
    val appLockEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_APP_LOCK] ?: false }

    suspend fun setAutoRecordEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_RECORD] = enabled }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_APP_LOCK] = enabled }
    }

    suspend fun setTransferHintEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TRANSFER_HINT] = enabled }
    }

    suspend fun markFirstLaunchDone() {
        context.dataStore.edit { it[KEY_FIRST_LAUNCH] = true }
    }

    suspend fun isChannelEnabled(channel: com.xl.bill.mint.parser.Channel): Boolean =
        settingDao.get(channelKey(channel))?.toBoolean() ?: true

    suspend fun setChannelEnabled(channel: com.xl.bill.mint.parser.Channel, enabled: Boolean) {
        settingDao.put(
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                channelKey(channel),
                enabled.toString()
            )
        )
    }

    suspend fun getLastListenerGuideDate(): String? = settingDao.get(KEY_LAST_GUIDE_DATE)

    suspend fun setLastListenerGuideDate(date: String) {
        settingDao.put(
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                KEY_LAST_GUIDE_DATE,
                date
            )
        )
    }

    suspend fun getLastTransferHintDate(): String? = settingDao.get(KEY_LAST_TRANSFER_HINT)

    suspend fun setLastTransferHintDate(date: String) {
        settingDao.put(
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                KEY_LAST_TRANSFER_HINT,
                date
            )
        )
    }

    fun observeChannelEnabled(channel: com.xl.bill.mint.parser.Channel): Flow<Boolean> =
        settingDao.observeAll().map { list ->
            list.firstOrNull { it.key == channelKey(channel) }?.value?.toBoolean() ?: true
        }

    // ==================== 存款目标 ====================
    // 金额单位「分」；total/monthly 未设置存空串（读回 null），initial 缺省 0L。
    // 走 Room settings 表：导出导入（TransferCodec）全量透传，无需 DB 迁移。

    /** 存款目标配置：total=目标总存款(null=未设置)、initial=当前存款(初始化基准)、monthly=每月目标存款(null=未设置) */
    data class SavingsGoal(val total: Long?, val initial: Long, val monthly: Long?)

    val savingsGoal: Flow<SavingsGoal> = settingDao.observeAll().map { list ->
        SavingsGoal(
            total = list.firstOrNull { it.key == KEY_SAVINGS_TOTAL }?.value?.toLongOrNull(),
            initial = list.firstOrNull { it.key == KEY_SAVINGS_INITIAL }?.value?.toLongOrNull() ?: 0L,
            monthly = list.firstOrNull { it.key == KEY_SAVINGS_MONTHLY }?.value?.toLongOrNull()
        )
    }

    suspend fun getSavingsGoal(): SavingsGoal =
        SavingsGoal(
            total = settingDao.get(KEY_SAVINGS_TOTAL)?.toLongOrNull(),
            initial = settingDao.get(KEY_SAVINGS_INITIAL)?.toLongOrNull() ?: 0L,
            monthly = settingDao.get(KEY_SAVINGS_MONTHLY)?.toLongOrNull()
        )

    suspend fun setSavingsGoal(goal: SavingsGoal) {
        settingDao.put(
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                KEY_SAVINGS_TOTAL,
                goal.total?.toString() ?: ""
            )
        )
        settingDao.put(
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                KEY_SAVINGS_INITIAL,
                goal.initial.toString()
            )
        )
        settingDao.put(
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                KEY_SAVINGS_MONTHLY,
                goal.monthly?.toString() ?: ""
            )
        )
    }

    private fun channelKey(channel: com.xl.bill.mint.parser.Channel) = "channel_on_${channel.name}"

    companion object {
        private val KEY_AUTO_RECORD = booleanPreferencesKey("auto_record_enabled")
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch_done")
        private val KEY_TRANSFER_HINT = booleanPreferencesKey("transfer_hint_enabled")
        private val KEY_APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        private const val KEY_LAST_GUIDE_DATE = "last_listener_guide_date"
        private const val KEY_LAST_TRANSFER_HINT = "last_transfer_hint_date"
        private const val KEY_SAVINGS_TOTAL = "savings_goal_total"
        private const val KEY_SAVINGS_INITIAL = "savings_goal_initial"
        private const val KEY_SAVINGS_MONTHLY = "savings_goal_monthly"
    }
}

package com.xl.bill.mint.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    // ==================== 备份/恢复预置项 ====================
    // DataStore 不走 Room 事务，导出/导入时由 ExportManager/ImportManager 单独处理。
    // 仅搬运 4 个开关；未知键忽略，避免污染。

    /** 读取全部 4 个开关（含默认值）。v1 备份文件无此字段时返回空 map。 */
    suspend fun exportPreferences(): Map<String, Boolean> {
        val data = context.dataStore.data.first()
        return mapOf(
            KEY_AUTO_RECORD.name to (data[KEY_AUTO_RECORD] ?: true),
            KEY_FIRST_LAUNCH.name to (data[KEY_FIRST_LAUNCH] ?: false),
            KEY_TRANSFER_HINT.name to (data[KEY_TRANSFER_HINT] ?: true),
            KEY_APP_LOCK.name to (data[KEY_APP_LOCK] ?: false)
        )
    }

    /** 把备份中的预置项写回（仅处理已知键）。 */
    suspend fun importPreferences(map: Map<String, Boolean>) {
        val keyByName = mapOf(
            KEY_AUTO_RECORD.name to KEY_AUTO_RECORD,
            KEY_FIRST_LAUNCH.name to KEY_FIRST_LAUNCH,
            KEY_TRANSFER_HINT.name to KEY_TRANSFER_HINT,
            KEY_APP_LOCK.name to KEY_APP_LOCK
        )
        context.dataStore.edit { prefs ->
            map.forEach { (name, value) ->
                keyByName[name]?.let { prefs[it] = value }
            }
        }
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

    /** 存款目标配置：total=目标总存款(null=未设置)、initial=当前存款(初始化基准)、monthly=每月目标存款(null=未设置)、baseTime=净结余起始日(毫秒，null=未设置全量累计) */
    data class SavingsGoal(val total: Long?, val initial: Long, val monthly: Long?, val baseTime: Long? = null)

    val savingsGoal: Flow<SavingsGoal> = settingDao.observeAll().map { list ->
        SavingsGoal(
            total = list.firstOrNull { it.key == KEY_SAVINGS_TOTAL }?.value?.toLongOrNull(),
            initial = list.firstOrNull { it.key == KEY_SAVINGS_INITIAL }?.value?.toLongOrNull() ?: 0L,
            monthly = list.firstOrNull { it.key == KEY_SAVINGS_MONTHLY }?.value?.toLongOrNull(),
            baseTime = list.firstOrNull { it.key == KEY_SAVINGS_BASE_TIME }?.value?.toLongOrNull()
        )
    }

    suspend fun getSavingsGoal(): SavingsGoal =
        SavingsGoal(
            total = settingDao.get(KEY_SAVINGS_TOTAL)?.toLongOrNull(),
            initial = settingDao.get(KEY_SAVINGS_INITIAL)?.toLongOrNull() ?: 0L,
            monthly = settingDao.get(KEY_SAVINGS_MONTHLY)?.toLongOrNull(),
            baseTime = settingDao.get(KEY_SAVINGS_BASE_TIME)?.toLongOrNull()
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
        settingDao.put(
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                KEY_SAVINGS_BASE_TIME,
                goal.baseTime?.toString() ?: ""
            )
        )
    }

    // ==================== 每日限额 ====================
    // 金额单位「分」；未设置存空串（读回 null）。走 Room settings 表：
    // 导出导入（TransferCodec）全量透传，无需 DB 迁移。

    /** 每日限额（分，null=未设置） */
    val dailyLimit: Flow<Long?> = settingDao.observeAll().map { list ->
        list.firstOrNull { it.key == KEY_DAILY_LIMIT }?.value?.toLongOrNull()
    }

    suspend fun getDailyLimit(): Long? = settingDao.get(KEY_DAILY_LIMIT)?.toLongOrNull()

    suspend fun setDailyLimit(fen: Long?) {
        settingDao.put(
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                KEY_DAILY_LIMIT,
                fen?.toString() ?: ""
            )
        )
    }

    // ==================== 短信去重窗口 ====================
    // 短信与支付通知视为同一笔的判定时长（毫秒）；默认 5s，设置页可调（3/5/10/30/60s）。

    suspend fun getCrossSourceSmsWindowMs(): Long =
        settingDao.get(KEY_CROSS_SOURCE_SMS_WINDOW_MS)?.toLongOrNull()
            ?: _root_ide_package_.com.xl.bill.mint.parser.CrossSourceResolver.DEFAULT_SMS_WINDOW_MS

    suspend fun setCrossSourceSmsWindowMs(ms: Long) {
        settingDao.put(
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                KEY_CROSS_SOURCE_SMS_WINDOW_MS,
                ms.toString()
            )
        )
    }

    fun observeCrossSourceSmsWindowMs(): Flow<Long> = settingDao.observeAll().map { list ->
        list.firstOrNull { it.key == KEY_CROSS_SOURCE_SMS_WINDOW_MS }?.value?.toLongOrNull()
            ?: _root_ide_package_.com.xl.bill.mint.parser.CrossSourceResolver.DEFAULT_SMS_WINDOW_MS
    }

    /** 幂等写入净结余起始日：仅当从未设置过时写入（首次设目标/首次导入触发），先到先得 */
    suspend fun ensureSavingsBaseTime(now: Long) {
        if (settingDao.get(KEY_SAVINGS_BASE_TIME) == null) {
            settingDao.put(
                _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                    KEY_SAVINGS_BASE_TIME,
                    now.toString()
                )
            )
        }
    }

    // ==================== 默认分类（支出/收入分开配置） ====================
    // 存分类 id 字符串，空/缺失 = 未配置（回落初始默认：支出→餐饮、收入→其他收入）。

    fun observeCategoryDefaults(): Flow<com.xl.bill.mint.parser.Defaults> =
        settingDao.observeAll().map { list ->
            com.xl.bill.mint.parser.Defaults(
                expenseId = list.firstOrNull { it.key == KEY_DEFAULT_CATEGORY_EXPENSE }?.value?.toLongOrNull(),
                incomeId = list.firstOrNull { it.key == KEY_DEFAULT_CATEGORY_INCOME }?.value?.toLongOrNull()
            )
        }

    suspend fun getCategoryDefaults(): com.xl.bill.mint.parser.Defaults =
        com.xl.bill.mint.parser.Defaults(
            expenseId = settingDao.get(KEY_DEFAULT_CATEGORY_EXPENSE)?.toLongOrNull(),
            incomeId = settingDao.get(KEY_DEFAULT_CATEGORY_INCOME)?.toLongOrNull()
        )

    /** 设置默认分类；categoryId=null 表示清除配置（恢复初始默认） */
    suspend fun setDefaultCategory(type: Int, categoryId: Long?) {
        val key = if (type == com.xl.bill.mint.data.db.CategoryEntity.TYPE_EXPENSE) {
            KEY_DEFAULT_CATEGORY_EXPENSE
        } else {
            KEY_DEFAULT_CATEGORY_INCOME
        }
        settingDao.put(
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                key,
                categoryId?.toString() ?: ""
            )
        )
    }

    // ==================== 广告过滤自定义词 ====================
    // 存 JSON 数组字符串（org.json 依赖已有）；仅自定义词入库，内置词表在解析引擎内。

    fun observeAdBlockWords(): Flow<List<String>> =
        settingDao.observeAll().map { list ->
            decodeAdBlockWords(list.firstOrNull { it.key == KEY_AD_BLOCK_WORDS }?.value)
        }

    suspend fun getAdBlockWords(): List<String> =
        decodeAdBlockWords(settingDao.get(KEY_AD_BLOCK_WORDS))

    suspend fun setAdBlockWords(words: List<String>) {
        settingDao.put(
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                KEY_AD_BLOCK_WORDS,
                org.json.JSONArray(words).toString()
            )
        )
    }

    private fun decodeAdBlockWords(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(value)
            (0 until arr.length()).map { arr.getString(it).trim() }.filter { it.isNotEmpty() }
        } catch (e: org.json.JSONException) {
            emptyList()
        }
    }

    // ==================== 自定义匹配关键词 ====================
    // 存 JSON 数组字符串：[{"keywords":["词1","词2"],"scope":"sms"}]
    // scope: sms=短信 / notification=通知 / all=全部（与解析引擎 CustomKeywordScope 一致）；
    // 系统预设词未命中时，组内全部关键词命中（AND）即兜底放行记账。

    fun observeCustomMatchGroups(): Flow<List<com.xl.bill.mint.parser.BillParseEngine.CustomMatchGroup>> =
        settingDao.observeAll().map { list ->
            decodeCustomMatchGroups(list.firstOrNull { it.key == KEY_CUSTOM_MATCH_KEYWORDS }?.value)
        }

    suspend fun getCustomMatchGroups(): List<com.xl.bill.mint.parser.BillParseEngine.CustomMatchGroup> =
        decodeCustomMatchGroups(settingDao.get(KEY_CUSTOM_MATCH_KEYWORDS))

    suspend fun setCustomMatchGroups(groups: List<com.xl.bill.mint.parser.BillParseEngine.CustomMatchGroup>) {
        settingDao.put(
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                KEY_CUSTOM_MATCH_KEYWORDS,
                encodeCustomMatchGroups(groups)
            )
        )
    }

    private fun encodeCustomMatchGroups(
        groups: List<com.xl.bill.mint.parser.BillParseEngine.CustomMatchGroup>
    ): String {
        val arr = org.json.JSONArray()
        groups.forEach { g ->
            val obj = org.json.JSONObject()
            obj.put("keywords", org.json.JSONArray(g.keywords))
            obj.put("scope", g.scope)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeCustomMatchGroups(
        value: String?
    ): List<com.xl.bill.mint.parser.BillParseEngine.CustomMatchGroup> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            val validScopes = setOf(
                com.xl.bill.mint.parser.BillParseEngine.CustomKeywordScope.SMS,
                com.xl.bill.mint.parser.BillParseEngine.CustomKeywordScope.NOTIFICATION,
                com.xl.bill.mint.parser.BillParseEngine.CustomKeywordScope.ALL
            )
            val arr = org.json.JSONArray(value)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val scope = obj.optString("scope").trim()
                if (scope !in validScopes) return@mapNotNull null
                val raw = obj.optJSONArray("keywords") ?: return@mapNotNull null
                val keywords = (0 until raw.length())
                    .map { raw.getString(it).trim() }
                    .filter { it.isNotEmpty() }
                if (keywords.isEmpty()) null
                else com.xl.bill.mint.parser.BillParseEngine.CustomMatchGroup(keywords, scope)
            }
        } catch (e: org.json.JSONException) {
            emptyList()
        }
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
        private const val KEY_SAVINGS_BASE_TIME = "savings_goal_base_time"
        private const val KEY_DAILY_LIMIT = "daily_limit"
        private const val KEY_CROSS_SOURCE_SMS_WINDOW_MS = "cross_source_sms_window_ms"
        private const val KEY_DEFAULT_CATEGORY_EXPENSE = "default_category_expense"
        private const val KEY_DEFAULT_CATEGORY_INCOME = "default_category_income"
        private const val KEY_AD_BLOCK_WORDS = "ad_block_words"
        private const val KEY_CUSTOM_MATCH_KEYWORDS = "custom_match_keywords"
    }
}

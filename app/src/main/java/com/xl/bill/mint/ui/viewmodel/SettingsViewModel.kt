package com.xl.bill.mint.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xl.bill.mint.di.ServiceLocator
import com.xl.bill.mint.transfer.BackupFileManager
import com.xl.bill.mint.transfer.TransferException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 备份/恢复操作的反馈状态，供 UI 展示进度与结果 */
sealed interface BackupOpState {
    data object Idle : BackupOpState
    data object Busy : BackupOpState
    data class Success(val message: String) : BackupOpState
    data class Error(val message: String) : BackupOpState
}

/**
 * 设置页：自动记账总开关、渠道开关、数据管理。
 */
class SettingsViewModel : ViewModel() {

    private val _backupOpState = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val backupOpState: StateFlow<BackupOpState> = _backupOpState.asStateFlow()

    private val settings = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository

    val autoRecord: StateFlow<Boolean> = settings.autoRecordEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val transferHintEnabled: StateFlow<Boolean> = settings.transferHintEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 应用锁开关（隐私安全分组） */
    val appLockEnabled: StateFlow<Boolean> = settings.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val firstLaunchDone: StateFlow<Boolean> = settings.firstLaunchDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val channelEnabled: StateFlow<Map<com.xl.bill.mint.parser.Channel, Boolean>> = _root_ide_package_.kotlinx.coroutines.flow.combine(
        _root_ide_package_.com.xl.bill.mint.parser.PaymentApps.ALL_CHANNELS.map { channel ->
            settings.observeChannelEnabled(channel).map { channel to it }
        }
    ) { array -> array.map { it as Pair<com.xl.bill.mint.parser.Channel, Boolean> }.toMap() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            _root_ide_package_.com.xl.bill.mint.parser.PaymentApps.ALL_CHANNELS.associateWith { true }
        )

    /** 存款目标配置（设置页摘要与弹窗回显） */
    val savingsGoal: StateFlow<com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal> = settings.savingsGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            _root_ide_package_.com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal(
                null,
                0L,
                null
            )
        )

    /** 默认分类配置（支出/收入分开；null=未配置回落初始默认） */
    val categoryDefaults: StateFlow<com.xl.bill.mint.parser.Defaults> = settings.observeCategoryDefaults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            _root_ide_package_.com.xl.bill.mint.parser.Defaults()
        )

    /** 广告过滤自定义词 */
    val adBlockWords: StateFlow<List<String>> = settings.observeAdBlockWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 短信去重窗口（毫秒；设置页可调档位） */
    val smsWindowMs: StateFlow<Long> = settings.observeCrossSourceSmsWindowMs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            _root_ide_package_.com.xl.bill.mint.parser.CrossSourceResolver.DEFAULT_SMS_WINDOW_MS
        )

    /** 每日限额（分，null=未设置；设置页摘要与弹窗回显） */
    val dailyLimit: StateFlow<Long?> = settings.dailyLimit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDailyLimit(fen: Long?) = viewModelScope.launch {
        settings.setDailyLimit(fen)
    }

    fun setSmsWindowMs(ms: Long) = viewModelScope.launch {
        settings.setCrossSourceSmsWindowMs(ms)
    }

    fun setSavingsGoal(goal: com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal) = viewModelScope.launch {
        settings.setSavingsGoal(goal)
    }

    fun setDefaultCategory(type: Int, categoryId: Long?) = viewModelScope.launch {
        settings.setDefaultCategory(type, categoryId)
    }

    fun addAdBlockWord(word: String) = viewModelScope.launch {
        val t = word.trim()
        if (t.isEmpty()) return@launch
        val current = settings.getAdBlockWords()
        if (t in current) return@launch
        settings.setAdBlockWords(current + t)
    }

    fun removeAdBlockWord(word: String) = viewModelScope.launch {
        settings.setAdBlockWords(settings.getAdBlockWords() - word)
    }

    fun setAutoRecord(enabled: Boolean) = viewModelScope.launch {
        settings.setAutoRecordEnabled(enabled)
    }

    fun setTransferHintEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setTransferHintEnabled(enabled)
    }

    fun setAppLockEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setAppLockEnabled(enabled)
    }

    fun setChannelEnabled(channel: com.xl.bill.mint.parser.Channel, enabled: Boolean) = viewModelScope.launch {
        settings.setChannelEnabled(channel, enabled)
    }

    fun markFirstLaunchDone() = viewModelScope.launch {
        settings.markFirstLaunchDone()
    }

    fun clearAll() = viewModelScope.launch {
        _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.transactionRepository.clearAll()
    }

    // ==================== 备份 / 恢复（文件级，扛卸载重装） ====================

    /** 把当前全部数据导出为 JSON 并写入用户选定的 Uri（下载目录/云盘） */
    fun backupToUri(uri: Uri) {
        if (_backupOpState.value is BackupOpState.Busy) return
        _backupOpState.value = BackupOpState.Busy
        viewModelScope.launch {
            runCatching {
                val json = ServiceLocator.exportManager.export()
                BackupFileManager.writeBackup(ServiceLocator.appContext, uri, json)
            }.onSuccess {
                _backupOpState.value = BackupOpState.Success("备份成功，已保存到所选位置")
            }.onFailure { e ->
                _backupOpState.value = BackupOpState.Error(messageOf(e, "备份失败"))
            }
        }
    }

    /** 从用户选定的 Uri 读取备份 JSON 并整库还原（全量覆盖） */
    fun restoreFromUri(uri: Uri) {
        if (_backupOpState.value is BackupOpState.Busy) return
        _backupOpState.value = BackupOpState.Busy
        viewModelScope.launch {
            runCatching {
                val json = BackupFileManager.readBackup(ServiceLocator.appContext, uri)
                val result = ServiceLocator.importManager.import(json)
                result
            }.onSuccess { result ->
                _backupOpState.value = BackupOpState.Success("恢复成功，共 ${result.transactionCount} 笔账单")
            }.onFailure { e ->
                _backupOpState.value = BackupOpState.Error(messageOf(e, "恢复失败"))
            }
        }
    }

    fun resetBackupOpState() {
        _backupOpState.value = BackupOpState.Idle
    }

    private fun messageOf(e: Throwable, fallback: String): String =
        if (e is TransferException && e.message?.isNotBlank() == true) e.message!!
        else (e.message?.takeIf { it.isNotBlank() } ?: fallback)
}

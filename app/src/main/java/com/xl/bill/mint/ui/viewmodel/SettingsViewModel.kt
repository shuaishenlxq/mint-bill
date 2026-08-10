package com.xl.bill.mint.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页：自动记账总开关、渠道开关、数据管理。
 */
class SettingsViewModel : ViewModel() {

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
}

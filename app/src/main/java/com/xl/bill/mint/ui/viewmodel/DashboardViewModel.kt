package com.xl.bill.mint.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * 首页：本月收支总览 + 最近账单（4 条件交集筛选：作用域 × 收支类型 × 支付渠道 × 时间范围）。
 */
class DashboardViewModel : ViewModel() {

    private val repo = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.transactionRepository
    private val categoryDao = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appDatabase.categoryDao()
    private val settingsRepo = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository
    private val currentMonth = YearMonth.now()

    val transactions: StateFlow<List<com.xl.bill.mint.data.db.TransactionEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<com.xl.bill.mint.data.db.CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val overview: StateFlow<com.xl.bill.mint.util.StatisticsCalculator.MonthOverview> = transactions
        .map { list ->
            val (start, end) = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.monthRange(currentMonth)
            _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.monthOverview(list, start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.MonthOverview(0, 0)
        )

    // ==================== 存款目标 ====================

    val savingsGoal: StateFlow<com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal> = settingsRepo.savingsGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            _root_ide_package_.com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal(
                null,
                0L,
                null
            )
        )

    /** 存款进度摘要：未设置目标（total null/0）时为 null → 首页显示引导态 */
    val savingsSummary: StateFlow<com.xl.bill.mint.util.SavingsCalculator.SavingsSummary?> =
        combine(transactions, settingsRepo.savingsGoal) { txs, goal ->
            goal.total?.let { _root_ide_package_.com.xl.bill.mint.util.SavingsCalculator.summary(txs, goal.initial, it) }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSavingsGoal(goal: com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal) = viewModelScope.launch {
        settingsRepo.setSavingsGoal(goal)
    }

    // ==================== 筛选状态（跨旋转保留） ====================

    /** 作用域：最近（近3天20条）/ 全部（跳转全部账单页） */
    private val _scopeMode = MutableStateFlow(_root_ide_package_.com.xl.bill.mint.ui.filter.ScopeMode.RECENT)
    val scopeMode: StateFlow<com.xl.bill.mint.ui.filter.ScopeMode> = _scopeMode.asStateFlow()

    /** 收支类型：null=全部, 0=支出, 1=收入 */
    private val _typeFilter = MutableStateFlow<Int?>(null)
    val typeFilter: StateFlow<Int?> = _typeFilter.asStateFlow()

    /** 支付渠道：null=全部, alipay/wechat/bank/manual */
    private val _channelFilter = MutableStateFlow<String?>(null)
    val channelFilter: StateFlow<String?> = _channelFilter.asStateFlow()

    /** 时间范围弹窗设置的自定义区间（null=未设置，回落作用域默认） */
    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val customRange: StateFlow<Pair<Long, Long>?> = _customRange.asStateFlow()

    /** 当前生效时间范围起点（null=不限）；end 为 null 表示无上界 */
    val effectiveStart: StateFlow<Long?> = combine(_customRange, _scopeMode) { custom, mode ->
        custom?.first ?: if (mode == _root_ide_package_.com.xl.bill.mint.ui.filter.ScopeMode.RECENT) _root_ide_package_.com.xl.bill.mint.util.TimeRange.recent3DaysStart() else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val effectiveEnd: StateFlow<Long?> = _customRange
        .map { it?.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 最近账单：4 条件交集 + LIMIT 20（参数变化用 flatMapLatest 重建流） */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recent: StateFlow<List<com.xl.bill.mint.data.db.TransactionEntity>> =
        combine(_customRange, _scopeMode, _typeFilter, _channelFilter) { custom, mode, type, ch ->
            Triple(
                custom?.first ?: (if (mode == _root_ide_package_.com.xl.bill.mint.ui.filter.ScopeMode.RECENT) _root_ide_package_.com.xl.bill.mint.util.TimeRange.recent3DaysStart() else null),
                custom?.second,
                type to ch
            )
        }.flatMapLatest { (start, end, tc) ->
            repo.observeFiltered(start, end, tc.first, tc.second,
                _root_ide_package_.com.xl.bill.mint.ui.filter.RECENT_LIMIT
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 缺备注的自动记录（首页横幅与卡片角标数据源） */
    val unnoted: StateFlow<List<com.xl.bill.mint.data.db.TransactionEntity>> = repo.observeUnnoted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 单向事件流：备注保存成功通知 UI 弹提示（无缓冲，只消费一次，不重复弹窗）
    private val _noteSaved = MutableSharedFlow<Unit>()
    val noteSaved: SharedFlow<Unit> = _noteSaved.asSharedFlow()

    // ==================== 筛选操作 ====================

    fun setScopeMode(mode: com.xl.bill.mint.ui.filter.ScopeMode) {
        _scopeMode.value = mode
    }

    fun setTypeFilter(value: Int?) {
        _typeFilter.value = value
    }

    fun setChannelFilter(value: String?) {
        _channelFilter.value = value
    }

    /** null=清除自定义范围（回落作用域默认：近3天） */
    fun setCustomRange(value: Pair<Long, Long>?) {
        _customRange.value = value
    }

    /** 当前筛选快照（跳转全部账单页时传给新页面） */
    fun filtersSnapshot(): com.xl.bill.mint.ui.filter.BillFilters =
        _root_ide_package_.com.xl.bill.mint.ui.filter.BillFilters(
            _typeFilter.value,
            _channelFilter.value,
            _customRange.value
        )

    // ==================== 账单操作 ====================

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }

    fun updateCategory(id: Long, categoryId: Long) = viewModelScope.launch {
        repo.updateCategory(id, categoryId)
    }

    fun updateNote(id: Long, note: String?) = viewModelScope.launch {
        repo.updateNote(id, note)
        _noteSaved.emit(Unit)
    }

    fun addManual(type: Int, amountFen: Long, categoryId: Long, note: String?) =
        viewModelScope.launch { repo.insertManual(type, amountFen, categoryId, note) }

    /** 新增自定义分类（设置页分类管理用） */
    fun addCategory(name: String, icon: String, type: Int, keywords: String) =
        viewModelScope.launch { _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.categoryRepository.addCategory(name, icon, type, keywords) }

    /** 快捷新增分类并立刻把指定账单改到新分类（账单详情弹窗「＋ 新建」用） */
    fun addCategoryAndAssign(name: String, icon: String, type: Int, keywords: String, txId: Long) =
        viewModelScope.launch {
            val newId = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.categoryRepository.addCategory(name, icon, type, keywords)
            repo.updateCategory(txId, newId)
        }
}

package com.xl.bill.mint.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * 报表页状态：支持周 / 月 / 年三种周期。
 * [anchor] 为当前周期的锚点日期——周视图是周内任意一天（计算时归一到周一），
 * 月视图是当月任意一天，年视图是当年任意一天。
 */
data class StatisticsState(
    val period: com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod,
    val anchor: LocalDate,
    val overview: com.xl.bill.mint.util.StatisticsCalculator.MonthOverview,
    val expenseBreakdown: List<com.xl.bill.mint.util.StatisticsCalculator.CategoryStat>,
    val incomeBreakdown: List<com.xl.bill.mint.util.StatisticsCalculator.CategoryStat>,
    val topExpense: List<com.xl.bill.mint.data.db.TransactionEntity>,
    val topIncome: List<com.xl.bill.mint.data.db.TransactionEntity>,
    val trend: List<com.xl.bill.mint.util.StatisticsCalculator.MonthPoint>
)

/** 存款报表状态：summary=进度摘要（未设目标为 null）、months=有记录的月度序列（升序）、monthlyGoal=每月目标 */
data class SavingsStatistics(
    val summary: com.xl.bill.mint.util.SavingsCalculator.SavingsSummary?,
    val months: List<com.xl.bill.mint.util.SavingsCalculator.SavingsMonthView>,
    val monthlyGoal: Long?
)

class StatisticsViewModel : ViewModel() {

    private val txDao = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appDatabase.transactionDao()
    private val categoryDao = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appDatabase.categoryDao()
    private val settingsRepo = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository
    private val repo = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.transactionRepository

    private val period = MutableStateFlow(_root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.MONTH)
    private val anchor = MutableStateFlow(LocalDate.now().withDayOfMonth(1))

    /** 报表页顶层 Tab：false=收支（默认，含 日/周/月/年 周期）、true=存款 */
    private val _savingsTab = MutableStateFlow(false)
    val savingsTab: StateFlow<Boolean> = _savingsTab.asStateFlow()

    fun setSavingsTab(showSavings: Boolean) {
        _savingsTab.value = showSavings
    }

    private val transactions: StateFlow<List<com.xl.bill.mint.data.db.TransactionEntity>> = txDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<com.xl.bill.mint.data.db.CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<StatisticsState> = combine(transactions, categories, period, anchor) { txs, cats, p, a ->
        val (start, end) = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.range(p, a)
        StatisticsState(
            period = p,
            anchor = a,
            overview = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.overview(txs, start, end),
            expenseBreakdown = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.categoryBreakdown(
                txs, _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_EXPENSE, start, end, cats
            ),
            incomeBreakdown = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.categoryBreakdown(
                txs, _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_INCOME, start, end, cats
            ),
            topExpense = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.topN(txs, _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_EXPENSE, start, end, 10),
            topIncome = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.topN(txs, _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_INCOME, start, end, 10),
            trend = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.periodTrend(p, a, txs)
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatisticsState(
            period = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.MONTH,
            anchor = LocalDate.now().withDayOfMonth(1),
            overview = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.MonthOverview(
                0,
                0
            ),
            expenseBreakdown = emptyList(),
            incomeBreakdown = emptyList(),
            topExpense = emptyList(),
            topIncome = emptyList(),
            trend = emptyList()
        )
    )

    /** 存款报表：全量账单 + 目标配置 → 月度序列（有记录的月份）与进度摘要 */
    val savings: StateFlow<SavingsStatistics> =
        combine(transactions, settingsRepo.savingsGoal) { txs, goal ->
            SavingsStatistics(
                summary = goal.total?.let { _root_ide_package_.com.xl.bill.mint.util.SavingsCalculator.summary(txs, goal.initial, it, goal.baseTime) },
                months = _root_ide_package_.com.xl.bill.mint.util.SavingsCalculator.savingsMonthSeries(txs, goal.initial, goal.monthly, goal.baseTime),
                monthlyGoal = goal.monthly
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SavingsStatistics(summary = null, months = emptyList(), monthlyGoal = null)
        )

    /** 存款目标配置（存款报表「设置/编辑目标」弹窗回显用） */
    val savingsGoal: StateFlow<com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal> = settingsRepo.savingsGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            _root_ide_package_.com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal(
                null,
                0L,
                null
            )
        )

    /** 日收支列表：仅「日」周期下按锚点月份聚合（有记录的日期，最新在前），其余周期为空 */
    val dailyList: StateFlow<List<com.xl.bill.mint.util.StatisticsCalculator.DayBalance>> =
        combine(transactions, period, anchor) { txs, p, a ->
            if (p == _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.DAY) {
                val (start, end) = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.monthRange(YearMonth.from(a))
                _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.dayBalances(txs, start, end)
            } else {
                emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSavingsGoal(goal: com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal) = viewModelScope.launch {
        settingsRepo.setSavingsGoal(goal)
    }

    /** 切换周期：始终定位到目标周期的当前期（今日/本周/本月/今年），不沿用旧浏览上下文 */
    fun selectPeriod(target: com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod) {
        period.value = target
        anchor.value = currentAnchor(target)
    }

    /** 回到当前期（进入报表页时调用）：周期不变，锚点重置为当前日/周/月/年 */
    fun resetToCurrent() {
        anchor.value = currentAnchor(period.value)
    }

    private fun currentAnchor(target: com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod): LocalDate = when (target) {
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.DAY -> LocalDate.now()
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.WEEK -> LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.MONTH -> LocalDate.now().withDayOfMonth(1)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.YEAR -> LocalDate.now().withDayOfYear(1)
    }

    fun prev() = anchor.update { a ->
        when (period.value) {
            _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.DAY -> a.minusMonths(1)
            _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.WEEK -> a.minusWeeks(1)
            _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.MONTH -> a.minusMonths(1)
            _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.YEAR -> a.minusYears(1)
        }
    }

    fun next() = anchor.update { a ->
        when (period.value) {
            _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.DAY -> a.plusMonths(1)
            _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.WEEK -> a.plusWeeks(1)
            _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.MONTH -> a.plusMonths(1)
            _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.YEAR -> a.plusYears(1)
        }
    }

    // ==================== 账单操作（TOP 列表详情弹窗用，与 DashboardViewModel 一致） ====================

    /** 备注保存成功提示（详情弹窗展示用） */
    private val _noteSaved = MutableSharedFlow<Unit>()
    val noteSaved: SharedFlow<Unit> = _noteSaved.asSharedFlow()

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }

    fun updateCategory(id: Long, categoryId: Long) = viewModelScope.launch {
        repo.updateCategory(id, categoryId)
    }

    fun updateNote(id: Long, note: String?) = viewModelScope.launch {
        repo.updateNote(id, note)
        _noteSaved.emit(Unit)
    }

    /** 快捷新增分类并立刻把指定账单改到新分类（账单详情弹窗「＋ 新建」用） */
    fun addCategoryAndAssign(name: String, icon: String, type: Int, keywords: String, txId: Long) =
        viewModelScope.launch {
            val newId = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.categoryRepository.addCategory(name, icon, type, keywords)
            repo.updateCategory(txId, newId)
        }
}

package com.xl.bill.mint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xl.bill.mint.R
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.GoalMet
import com.xl.bill.mint.ui.theme.IncomeMint
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 首页：本月总览 + 最近账单（可筛选：作用域/收支类型/支付渠道/时间范围）。
 *
 * @param onViewAll 选中「全部」时跳转全部账单列表页的回调
 * @param notePromptTxId 通知点击携带的目标账单 id（非空时自动打开该账单详情弹窗，用于补备注直达）
 * @param onNotePromptConsumed 直达目标已消费的回调（消费后置空，防止重组重复弹出）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onAddClick: () -> Unit,
    onViewAll: () -> Unit,
    viewModel: com.xl.bill.mint.ui.viewmodel.DashboardViewModel = viewModel(),
    notePromptTxId: Long? = null,
    onNotePromptConsumed: () -> Unit = {}
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val todayOverview by viewModel.todayOverview.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val unnoted by viewModel.unnoted.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    val savingsGoal by viewModel.savingsGoal.collectAsStateWithLifecycle()
    val savingsSummary by viewModel.savingsSummary.collectAsStateWithLifecycle()

    val scopeMode by viewModel.scopeMode.collectAsStateWithLifecycle()
    val typeFilter by viewModel.typeFilter.collectAsStateWithLifecycle()
    val channelFilter by viewModel.channelFilter.collectAsStateWithLifecycle()
    val effectiveStart by viewModel.effectiveStart.collectAsStateWithLifecycle()
    val effectiveEnd by viewModel.effectiveEnd.collectAsStateWithLifecycle()

    val catById = remember(categories) { categories.associateBy { it.id } }
    // 只保存选中 id，从 transactions 流实时派生最新对象：
    // 改分类落库后 Flow 刷新 → 详情弹窗自动拿到新数据，无需手动同步快照
    var selectedTxId by remember { mutableStateOf<Long?>(null) }
    val selectedTx = remember(transactions, selectedTxId) {
        selectedTxId?.let { id -> transactions.firstOrNull { it.id == id } }
    }
    // 备注保存成功提示框（由 ViewModel 事件流驱动，仅弹一次）
    var showSaveSuccess by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.noteSaved.collect { showSaveSuccess = true }
    }
    // 时间范围选择弹窗
    var showTimePicker by remember { mutableStateOf(false) }
    // 存款目标设置弹窗
    var showSavingsGoal by remember { mutableStateOf(false) }
    val monthLabel = remember {
        YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy年M月"))
    }

    // 下拉文案与选项
    val scopeLabel = if (scopeMode == _root_ide_package_.com.xl.bill.mint.ui.filter.ScopeMode.RECENT) {
        stringResource(R.string.filter_scope_recent)
    } else {
        stringResource(R.string.filter_scope_all)
    }
    val scopeRecentLabel = stringResource(R.string.filter_scope_recent)
    val scopeAllLabel = stringResource(R.string.filter_scope_all)
    val typeExpenseLabel = stringResource(R.string.filter_type_expense)
    val typeIncomeLabel = stringResource(R.string.filter_type_income)
    val typeAllLabel = stringResource(R.string.filter_type_all)
    val channelWechatLabel = stringResource(R.string.filter_channel_wechat)
    val channelAlipayLabel = stringResource(R.string.filter_channel_alipay)
    val channelBankLabel = stringResource(R.string.filter_channel_bank)
    val channelSmsLabel = stringResource(R.string.filter_channel_sms)
    val channelManualLabel = stringResource(R.string.filter_channel_manual)
    val channelAllLabel = stringResource(R.string.filter_channel_all)
    val scopeOptions = remember {
        listOf(
            _root_ide_package_.com.xl.bill.mint.ui.filter.SelectOption(
                scopeRecentLabel,
                _root_ide_package_.com.xl.bill.mint.ui.filter.ScopeMode.RECENT
            ),
            _root_ide_package_.com.xl.bill.mint.ui.filter.SelectOption(
                scopeAllLabel,
                _root_ide_package_.com.xl.bill.mint.ui.filter.ScopeMode.ALL
            )
        )
    }
    val typeOpts = remember {
        _root_ide_package_.com.xl.bill.mint.ui.filter.typeOptions(
            typeExpenseLabel,
            typeIncomeLabel,
            typeAllLabel
        )
    }
    val channelOpts = remember {
        _root_ide_package_.com.xl.bill.mint.ui.filter.channelOptions(
            channelWechatLabel,
            channelAlipayLabel,
            channelBankLabel,
            channelSmsLabel,
            channelManualLabel,
            channelAllLabel
        )
    }

    // 通知点击直达：目标交易可查到时打开详情弹窗并消费
    LaunchedEffect(notePromptTxId, transactions) {
        val target = notePromptTxId?.let { id -> transactions.firstOrNull { it.id == id } }
        if (target != null) {
            selectedTxId = target.id
            onNotePromptConsumed()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dashboard_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.dashboard_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                _root_ide_package_.com.xl.bill.mint.ui.components.Mascot(modifier = Modifier.size(52.dp))
            }
        }

        item {
            _root_ide_package_.com.xl.bill.mint.ui.components.OverviewCard(
                overview = overview,
                monthLabel = monthLabel
            )
        }

        // 当日收支小报表：今日支出 / 今日收入 / 今日结余
        item {
            TodayOverviewCard(overview = todayOverview)
        }

        // 存款进度卡：未设置目标 → 引导态；已设置 → 环形进度（点击可编辑）
        item {
            val summary = savingsSummary
            if (summary == null) {
                _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard(onClick = {
                    showSavingsGoal = true
                }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🎯", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.savings_goal_set),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.savings_goal_set_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                SavingsProgressCard(summary = summary, onClick = { showSavingsGoal = true })
            }
        }

        if (unnoted.isNotEmpty()) {
            item {
                NoteReminderBanner(
                    count = unnoted.size,
                    onClick = { selectedTxId = unnoted.first().id }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.recent_bills),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                // 时间范围入口：点击弹出日历对话框（显示当前生效范围，柔和凸起 pill）
                _root_ide_package_.com.xl.bill.mint.ui.components.SoftSurface(
                    cornerRadius = 16.dp,
                    elevation = 6.dp,
                    onClick = { showTimePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = _root_ide_package_.com.xl.bill.mint.util.TimeRange.label(
                                effectiveStart,
                                effectiveEnd,
                                homeDefault = true,
                                recentLabel = stringResource(R.string.time_range_recent3),
                                allTimeLabel = stringResource(R.string.time_range_all_time),
                                todayLabel = stringResource(R.string.time_range_today)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 筛选下拉：作用域（最近/全部）· 收支类型 · 支付渠道（FlowRow 窄屏自动换行）
        item {
            FlowRow(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                _root_ide_package_.com.xl.bill.mint.ui.components.FilterDropdown(
                    label = scopeLabel,
                    options = scopeOptions,
                    selected = scopeMode,
                    onSelect = { mode ->
                        if (mode == _root_ide_package_.com.xl.bill.mint.ui.filter.ScopeMode.ALL) {
                            // 选中「全部」→ 复位为「最近」并跳转全部账单列表页
                            viewModel.setScopeMode(_root_ide_package_.com.xl.bill.mint.ui.filter.ScopeMode.RECENT)
                            onViewAll()
                        } else {
                            viewModel.setScopeMode(mode)
                        }
                    }
                )
                _root_ide_package_.com.xl.bill.mint.ui.components.FilterDropdown(
                    label = typeOpts.firstOrNull { it.value == typeFilter }?.label
                        ?: stringResource(R.string.filter_type_all),
                    options = typeOpts,
                    selected = typeFilter,
                    onSelect = viewModel::setTypeFilter
                )
                _root_ide_package_.com.xl.bill.mint.ui.components.FilterDropdown(
                    label = channelOpts.firstOrNull { it.value == channelFilter }?.label
                        ?: stringResource(R.string.filter_channel_all),
                    options = channelOpts,
                    selected = channelFilter,
                    onSelect = viewModel::setChannelFilter
                )
            }
        }

        if (recent.isEmpty()) {
            item {
                _root_ide_package_.com.xl.bill.mint.ui.components.EmptyState(
                    title = stringResource(R.string.empty_bills_title),
                    description = stringResource(R.string.empty_bills_desc)
                )
            }
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = stringResource(R.string.empty_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        } else {
            items(recent, key = { it.id }) { tx ->
                _root_ide_package_.com.xl.bill.mint.ui.components.BillCard(
                    tx = tx,
                    category = catById[tx.categoryId],
                    onClick = { selectedTxId = tx.id }
                )
            }
        }
    }

    selectedTx?.let { tx ->
        BillDetailSheet(
            tx = tx,
            categories = categories,
            onDismiss = { selectedTxId = null },
            onDelete = {
                viewModel.delete(tx.id)
                selectedTxId = null
            },
            onUpdateCategory = { categoryId -> viewModel.updateCategory(tx.id, categoryId) },
            onUpdateNote = { note -> viewModel.updateNote(tx.id, note) },
            onAddCategory = { name, icon, type, keywords ->
                viewModel.addCategoryAndAssign(name, icon, type, keywords, tx.id)
            },
            showSaveSuccess = showSaveSuccess,
            onSaveSuccessDismiss = { showSaveSuccess = false }
        )
    }

    // 时间范围选择弹窗：确认后覆盖当前生效范围（null=清除，回落「近3天」）
    if (showTimePicker) {
        TimeRangePickerSheet(
            currentStart = effectiveStart,
            currentEnd = effectiveEnd,
            homeDefault = true,
            onConfirm = { range ->
                viewModel.setCustomRange(range)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    // 存款目标设置弹窗：保存后首页进度卡随 settings 流自动刷新
    if (showSavingsGoal) {
        SavingsGoalSheet(
            onDismiss = { showSavingsGoal = false },
            initialGoal = savingsGoal,
            onSave = { goal ->
                viewModel.setSavingsGoal(goal)
                showSavingsGoal = false
            }
        )
    }
}

/** 当日收支小报表：今日支出 / 今日收入 / 今日结余 三列（GlassCard，样式对齐首页现有卡片） */
@Composable
private fun TodayOverviewCard(overview: com.xl.bill.mint.util.StatisticsCalculator.MonthOverview) {
    _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.today_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodayStatCell(
                    label = stringResource(R.string.today_expense),
                    value = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(overview.expense),
                    color = ExpenseRose,
                    modifier = Modifier.weight(1f)
                )
                TodayStatCell(
                    label = stringResource(R.string.today_income),
                    value = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(overview.income),
                    color = IncomeMint,
                    modifier = Modifier.weight(1f)
                )
                TodayStatCell(
                    label = stringResource(R.string.today_balance),
                    value = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(overview.balance),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 当日收支单列：label(labelSmall) + value(titleMedium 着色)，列内水平居中 */
@Composable
private fun TodayStatCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** 存款进度卡：环形进度 + 已存/目标/还差（新拟物风格，点击编辑目标） */
@Composable
private fun SavingsProgressCard(
    summary: com.xl.bill.mint.util.SavingsCalculator.SavingsSummary,
    onClick: () -> Unit
) {
    val ringColor = if (summary.achieved) GoalMet else MaterialTheme.colorScheme.primary
    val remainText = if (summary.achieved) {
        stringResource(
            R.string.savings_exceed,
            _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(summary.current - summary.goalTotal)
        )
    } else {
        stringResource(
            R.string.savings_remain,
            _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(summary.goalTotal - summary.current)
        )
    }
    _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.savings_progress),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.savings_current,
                        _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(summary.current)
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (summary.achieved) GoalMet else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.savings_target,
                        _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(summary.goalTotal)
                    ) + " · " + remainText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            _root_ide_package_.com.xl.bill.mint.ui.components.RingChart(
                segments = listOf(ringColor to summary.progress),
                modifier = Modifier.size(84.dp),
                strokeWidth = 10.dp,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ) {
                Text(
                    text = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.percent(summary.progress),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

/** 补备注提醒横幅：显示待补备注数量，点击打开最近一条缺备注账单的详情 */
@Composable
private fun NoteReminderBanner(
    count: Int,
    onClick: () -> Unit
) {
    _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📝",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.dashboard_note_remind_banner, count),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

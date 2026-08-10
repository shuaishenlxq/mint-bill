package com.xl.bill.mint.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xl.bill.mint.R
import com.xl.bill.mint.data.db.TransactionEntity
import com.xl.bill.mint.ui.theme.GoalMet
import com.xl.bill.mint.ui.theme.GoalMissed
import com.xl.bill.mint.ui.theme.ShadowSoft
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** 可爱分类配色盘 */
private val Palette = listOf(
    Color(0xFF4ECDC4), Color(0xFFFF8A9E), Color(0xFFFFB86B), Color(0xFFA8D8EA),
    Color(0xFFB8A8EA), Color(0xFF8ED6A8), Color(0xFFF4A6C8), Color(0xFF9BB8E8),
    Color(0xFFE8C47F), Color(0xFF7FD6C4), Color(0xFFD9A6E8), Color(0xFF8FD0E8)
)

/**
 * 报表页：顶层「收支 | 存款」切换。
 * - 收支：周 / 月 / 年三周期，展示收支概览、占比、分类占比、TOP10 与趋势；
 * - 存款：按月展示存款情况（净结余 vs 每月目标），只展示有账单记录的月份。
 */
@Composable
fun StatisticsScreen(viewModel: com.xl.bill.mint.ui.viewmodel.StatisticsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savingsTab by viewModel.savingsTab.collectAsStateWithLifecycle()
    val savings by viewModel.savings.collectAsStateWithLifecycle()
    val savingsGoal by viewModel.savingsGoal.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val dailyList by viewModel.dailyList.collectAsStateWithLifecycle()

    var showSavingsGoal by remember { mutableStateOf(false) }

    // TOP 列表点击 → 账单详情弹窗（selectedTx 非空即展示）
    var selectedTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var showSaveSuccess by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.noteSaved.collect { showSaveSuccess = true }
    }

    // 日视图点击 → 当日账单列表弹窗
    var daySheet by remember { mutableStateOf<com.xl.bill.mint.util.StatisticsCalculator.DayBalance?>(null) }

    // 每次进入报表页（重新组合）自动定位到当前周/月/年；
    // 页内手动翻页不会触发重组，因此不会被拉回。
    LaunchedEffect(Unit) { viewModel.resetToCurrent() }

    Column(Modifier.fillMaxSize()) {
        SavingsTopTabs(selected = savingsTab, onSelect = viewModel::setSavingsTab)

        if (savingsTab) {
            SavingsReport(
                stats = savings,
                onEditGoal = { showSavingsGoal = true },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 周期切换（日 / 周 / 月 / 年）
                item {
                    PeriodTabs(
                        selected = state.period,
                        onSelect = viewModel::selectPeriod
                    )
                }

                // 时间导航（上一期 / 当前期 / 下一期）
                item {
                    PeriodNavigator(
                        state = state,
                        onPrev = viewModel::prev,
                        onNext = viewModel::next
                    )
                }

                if (state.period == _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.DAY) {
                    // 日视图：按月列出每日收支小报表
                    if (dailyList.isEmpty()) {
                        item {
                            _root_ide_package_.com.xl.bill.mint.ui.components.EmptyState(
                                title = stringResource(R.string.stat_daily_empty_title),
                                description = stringResource(R.string.stat_daily_empty_desc)
                            )
                        }
                    } else {
                        items(dailyList, key = { it.date.toString() }) { day ->
                            DailyCell(day, onClick = { daySheet = day })
                        }
                    }
                } else {
                    // 收支概览三卡
                    item {
                        OverviewRow(state.overview)
                    }

                    // 收支占比
                    item {
                        RatioCard(state.overview)
                    }

                    // 支出分类占比
                    item {
                        CategoryBreakdownCard(
                            title = stringResource(R.string.stat_category_breakdown),
                            data = state.expenseBreakdown,
                            isExpense = true
                        )
                    }

                    // 收入分类占比
                    item {
                        CategoryBreakdownCard(
                            title = stringResource(R.string.stat_income),
                            data = state.incomeBreakdown,
                            isExpense = false
                        )
                    }

                    // 大额支出 TOP10
                    item {
                        TopListCard(
                            title = stringResource(R.string.stat_big_expense),
                            data = state.topExpense,
                            isExpense = true,
                            onItemClick = { selectedTx = it }
                        )
                    }

                    // 大额收入 TOP10
                    item {
                        TopListCard(
                            title = stringResource(R.string.stat_big_income),
                            data = state.topIncome,
                            isExpense = false,
                            onItemClick = { selectedTx = it }
                        )
                    }

                    // 周期趋势
                    item {
                        TrendCard(period = state.period, trend = state.trend)
                    }
                }
            }
        }
    }

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

    // 账单详情弹窗（TOP 列表点击；改分类/备注/删除后 VM 内 Flow 自动刷新统计）
    selectedTx?.let { tx ->
        BillDetailSheet(
            tx = tx,
            categories = categories,
            onDismiss = { selectedTx = null },
            onDelete = {
                viewModel.delete(tx.id)
                selectedTx = null
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

    // 日视图点击 → 当日账单列表弹窗（内嵌详情编辑）
    daySheet?.let { day ->
        DayTransactionsSheet(
            day = day,
            categories = categories,
            onDismiss = { daySheet = null }
        )
    }
}

/** 顶层「收支 | 存款」Tab（样式与 PeriodTabs 一致） */
@Composable
private fun SavingsTopTabs(selected: Boolean, onSelect: (Boolean) -> Unit) {
    val tabs = listOf(
        false to stringResource(R.string.stat_tab_income_expense),
        true to stringResource(R.string.stat_tab_savings)
    )
    TabRow(
        selectedTabIndex = if (selected) 1 else 0,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        tabs.forEach { (isSavings, label) ->
            Tab(
                selected = selected == isSavings,
                onClick = { onSelect(isSavings) },
                text = {
                    Text(
                        text = label,
                        fontWeight = if (selected == isSavings) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

/** 单日收支小报表：日期（含星期）+ 支出 / 收入 / 结余 三列（居中，结余正绿负红）；点击打开当日账单列表 */
@Composable
private fun DailyCell(
    day: com.xl.bill.mint.util.StatisticsCalculator.DayBalance,
    onClick: () -> Unit
) {
    _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = day.date.format(DateTimeFormatter.ofPattern("M月d日 E", java.util.Locale.CHINESE)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DailyStatCell(
                    label = stringResource(R.string.stat_expense),
                    value = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(day.expense),
                    color = Color(0xFFFF8A9E),
                    modifier = Modifier.weight(1f)
                )
                DailyStatCell(
                    label = stringResource(R.string.stat_income),
                    value = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(day.income),
                    color = Color(0xFF4ECDC4),
                    modifier = Modifier.weight(1f)
                )
                DailyStatCell(
                    label = stringResource(R.string.stat_balance),
                    value = signedYuan(day.balance),
                    color = if (day.balance >= 0) Color(0xFF4ECDC4) else Color(0xFFFF8A9E),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 单日统计列：label(labelSmall) + value(titleMedium 着色)，列内水平居中 */
@Composable
private fun DailyStatCell(
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

/** 存款报表：汇总卡（跨行）+ 月度格子（只展示有记录的月份，最新在前） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavingsReport(
    stats: com.xl.bill.mint.ui.viewmodel.SavingsStatistics,
    onEditGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SavingsSummaryCard(stats = stats, onEditGoal = onEditGoal)
        }

        if (stats.months.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                _root_ide_package_.com.xl.bill.mint.ui.components.EmptyState(
                    title = stringResource(R.string.savings_empty_title),
                    description = stringResource(R.string.savings_empty_desc)
                )
            }
        } else {
            items(stats.months.reversed(), key = { it.month.toString() }) { view ->
                SavingsMonthCell(view = view, monthlyGoal = stats.monthlyGoal)
            }
        }
    }
}

/** 存款汇总卡：当前存款 / 目标 / 进度条 + 编辑入口 */
@Composable
private fun SavingsSummaryCard(stats: com.xl.bill.mint.ui.viewmodel.SavingsStatistics, onEditGoal: () -> Unit) {
    _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.savings_progress),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onEditGoal) {
                    Text(stringResource(R.string.savings_edit_action))
                }
            }

            val summary = stats.summary
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.savings_current,
                        _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(summary.current)
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (summary.achieved) GoalMet else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
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
                Text(
                    text = stringResource(
                        R.string.savings_target,
                        _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(summary.goalTotal)
                    ) + " · " + remainText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { summary.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.small),
                    color = if (summary.achieved) GoalMet else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {}
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = com.xl.bill.mint.util.MoneyFormatter.percent(summary.progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (stats.monthlyGoal != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.savings_summary_monthly,
                            _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(stats.monthlyGoal)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.savings_goal_set),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.savings_goal_set_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 月度存款格子：月份 + 净结余 + 达标徽标（达标绿底、未达标红底，底色/描边随状态） */
@Composable
private fun SavingsMonthCell(view: com.xl.bill.mint.util.SavingsCalculator.SavingsMonthView, monthlyGoal: Long?) {
    val shape = RoundedCornerShape(20.dp)
    val accent = when {
        view.met == true -> GoalMet
        view.met == false -> GoalMissed
        view.balance < 0 -> GoalMissed
        else -> MaterialTheme.colorScheme.primary
    }
    val bg = when (view.met) {
        true -> GoalMet.copy(alpha = 0.12f)
        false -> GoalMissed.copy(alpha = 0.12f)
        null -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    }
    val stroke = when (view.met) {
        true -> GoalMet.copy(alpha = 0.55f)
        false -> GoalMissed.copy(alpha = 0.55f)
        null -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = ShadowSoft, spotColor = ShadowSoft)
            .clip(shape)
            .background(bg)
            .border(1.dp, stroke, shape)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = view.month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (view.met != null) {
                    GoalBadge(
                        text = stringResource(if (view.met) R.string.savings_met else R.string.savings_missed),
                        color = accent
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.savings_net),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = signedYuan(view.balance),
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.savings_cumulative) + " ¥" + _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(view.cumulative),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 达标/未达标徽标 */
@Composable
private fun GoalBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 带符号金额：正 +¥x / 负 -¥x（中式记账：正向绿、负向红由调用方着色） */
private fun signedYuan(balance: Long): String {
    val sign = if (balance >= 0) "+" else "-"
    return "$sign¥${_root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(abs(balance))}"
}

/** 日 / 周 / 月 / 年周期切换 Tab */
@Composable
private fun PeriodTabs(selected: com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod, onSelect: (com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod) -> Unit) {
    val tabs = listOf(
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.DAY to stringResource(R.string.stat_period_day),
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.WEEK to stringResource(R.string.stat_period_week),
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.MONTH to stringResource(R.string.stat_period_month),
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.YEAR to stringResource(R.string.stat_period_year)
    )
    TabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
    ) {
        tabs.forEach { (period, label) ->
            Tab(
                selected = selected == period,
                onClick = { onSelect(period) },
                text = {
                    Text(
                        text = label,
                        fontWeight = if (selected == period) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

/** 上一期 / 当前期 / 下一期导航 */
@Composable
private fun PeriodNavigator(state: com.xl.bill.mint.ui.viewmodel.StatisticsState, onPrev: () -> Unit, onNext: () -> Unit) {
    val title = remember(state.period, state.anchor) { periodTitle(state.period, state.anchor) }
    val prevDesc = when (state.period) {
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.DAY -> stringResource(R.string.stat_prev_month)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.WEEK -> stringResource(R.string.stat_prev_week)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.MONTH -> stringResource(R.string.stat_prev_month)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.YEAR -> stringResource(R.string.stat_prev_year)
    }
    val nextDesc = when (state.period) {
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.DAY -> stringResource(R.string.stat_next_month)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.WEEK -> stringResource(R.string.stat_next_week)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.MONTH -> stringResource(R.string.stat_next_month)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.YEAR -> stringResource(R.string.stat_next_year)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = prevDesc)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = nextDesc)
        }
    }
}

/** 周期标题：日=yyyy年M月（按月浏览每日列表）、周=日期区间、月=yyyy年M月、年=yyyy年 */
private fun periodTitle(period: com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod, anchor: LocalDate): String = when (period) {
    _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.DAY ->
        anchor.format(DateTimeFormatter.ofPattern("yyyy年M月"))
    _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.WEEK -> {
        val end = anchor.plusDays(6)
        if (anchor.year == end.year) {
            "${anchor.year}年${anchor.monthValue}月${anchor.dayOfMonth}日 - ${end.monthValue}月${end.dayOfMonth}日"
        } else {
            "${anchor.year}年${anchor.monthValue}月${anchor.dayOfMonth}日 - ${end.year}年${end.monthValue}月${end.dayOfMonth}日"
        }
    }
    _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.MONTH -> anchor.format(DateTimeFormatter.ofPattern("yyyy年M月"))
    _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.YEAR -> "${anchor.year}年"
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun OverviewRow(overview: com.xl.bill.mint.util.StatisticsCalculator.MonthOverview) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MiniStatCard(
            label = stringResource(R.string.stat_expense),
            value = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(overview.expense),
            color = Color(0xFFFF8A9E),
            modifier = Modifier.weight(1f)
        )
        MiniStatCard(
            label = stringResource(R.string.stat_income),
            value = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(overview.income),
            color = Color(0xFF4ECDC4),
            modifier = Modifier.weight(1f)
        )
        MiniStatCard(
            label = stringResource(R.string.stat_balance),
            value = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(overview.balance),
            color = Color(0xFFA8D8EA),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MiniStatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard(
        modifier = modifier,
        cornerRadius = 20.dp
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 收支占比环形卡 */
@Composable
private fun RatioCard(overview: com.xl.bill.mint.util.StatisticsCalculator.MonthOverview) {
    val total = (overview.income + overview.expense).toFloat().coerceAtLeast(1f)
    val expensePct = overview.expense.toFloat() / total
    val incomePct = overview.income.toFloat() / total

    _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.stat_income_expense_ratio),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                _root_ide_package_.com.xl.bill.mint.ui.components.RingChart(
                    segments = listOf(
                        Color(0xFFFF8A9E) to expensePct,
                        Color(0xFF4ECDC4) to incomePct
                    ),
                    modifier = Modifier.size(130.dp),
                    strokeWidth = 20.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.stat_balance),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(
                                overview.balance
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RatioLegend(
                        color = Color(0xFFFF8A9E),
                        label = stringResource(R.string.stat_expense),
                        pct = expensePct,
                        amount = overview.expense
                    )
                    RatioLegend(
                        color = Color(0xFF4ECDC4),
                        label = stringResource(R.string.stat_income),
                        pct = incomePct,
                        amount = overview.income
                    )
                }
            }
        }
    }
}

@Composable
private fun RatioLegend(color: Color, label: String, pct: Float, amount: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendDot(color)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text = "${(pct * 100).toInt()}% · ¥${_root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(amount)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 分类占比卡 */
@Composable
private fun CategoryBreakdownCard(
    title: String,
    data: List<com.xl.bill.mint.util.StatisticsCalculator.CategoryStat>,
    isExpense: Boolean
) {
    _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))
            if (data.isEmpty()) {
                Text(
                    text = stringResource(R.string.stat_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                _root_ide_package_.com.xl.bill.mint.ui.components.RingChart(
                    segments = data.mapIndexed { i, stat ->
                        Palette[i % Palette.size] to stat.percent
                    },
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally),
                    strokeWidth = 16.dp
                ) {}
                Spacer(Modifier.height(16.dp))
                data.forEachIndexed { i, stat ->
                    CategoryLegendRow(
                        stat = stat,
                        color = Palette[i % Palette.size]
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryLegendRow(stat: com.xl.bill.mint.util.StatisticsCalculator.CategoryStat, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stat.icon, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = stat.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(stat.percent * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { stat.percent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.small),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {}
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "¥${_root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(stat.total)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 大额 TOP 卡 */
@Composable
private fun TopListCard(
    title: String,
    data: List<TransactionEntity>,
    isExpense: Boolean,
    onItemClick: (TransactionEntity) -> Unit
) {
    _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(12.dp))
            if (data.isEmpty()) {
                Text(
                    text = stringResource(R.string.stat_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                data.forEachIndexed { index, tx ->
                    TopRow(index = index + 1, tx = tx, isExpense = isExpense, onClick = { onItemClick(tx) })
                    if (index != data.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun TopRow(
    index: Int,
    tx: TransactionEntity,
    isExpense: Boolean,
    onClick: () -> Unit
) {
    val rankColor = when (index) {
        1 -> Color(0xFFFFB86B)
        2 -> Color(0xFFA8D8EA)
        3 -> Color(0xFFB8A8EA)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(rankColor.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = rankColor
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.merchant ?: tx.note ?: "账单",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = _root_ide_package_.com.xl.bill.mint.util.TimeUtil.format(tx.occurredAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(tx.amount),
            style = MaterialTheme.typography.titleMedium,
            color = if (isExpense) Color(0xFFFF8A9E) else Color(0xFF4ECDC4)
        )
    }
}

/** 周期趋势卡：周/月=每日柱状图，年=12 个月柱状图 */
@Composable
private fun TrendCard(period: com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod, trend: List<com.xl.bill.mint.util.StatisticsCalculator.MonthPoint>) {
    val title = when (period) {
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.DAY -> stringResource(R.string.stat_month_trend)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.WEEK -> stringResource(R.string.stat_week_trend)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.MONTH -> stringResource(R.string.stat_month_trend)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.YEAR -> stringResource(R.string.stat_year_trend)
    }
    val noData = when (period) {
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.DAY -> stringResource(R.string.stat_no_data)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.WEEK -> stringResource(R.string.stat_no_data_week)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.MONTH -> stringResource(R.string.stat_no_data)
        _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.ReportPeriod.YEAR -> stringResource(R.string.stat_no_data_year)
    }
    _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))
            if (trend.isEmpty()) {
                Text(
                    text = noData,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                _root_ide_package_.com.xl.bill.mint.ui.components.BarChart(data = trend)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(Color(0xFFFF8A9E))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.stat_expense),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.width(16.dp))
                    LegendDot(Color(0xFF4ECDC4))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.stat_income),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

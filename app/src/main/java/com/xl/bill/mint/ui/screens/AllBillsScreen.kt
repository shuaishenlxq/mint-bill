package com.xl.bill.mint.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xl.bill.mint.R
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint

/**
 * 全部账单列表页：全屏独立页面（带返回），展示 时间范围 × 收支类型 × 支付渠道 × 分类 四条件交集。
 * 滚动加载分页（每页 [com.xl.bill.mint.ui.filter.PAGE_SIZE] 条），初始筛选继承首页快照。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllBillsScreen(
    initialFilters: com.xl.bill.mint.ui.filter.BillFilters?,
    onBack: () -> Unit,
    viewModel: com.xl.bill.mint.ui.viewmodel.AllBillsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    // 筛选状态（跨旋转保留；初值继承首页快照）
    var typeFilter by rememberSaveable { mutableStateOf(initialFilters?.type) }
    var channelFilter by rememberSaveable { mutableStateOf(initialFilters?.channel) }
    var rangeFilter by rememberSaveable { mutableStateOf(initialFilters?.range) }
    var categoryFilter by rememberSaveable { mutableStateOf(initialFilters?.categoryId) }
    var sortFilter by rememberSaveable { mutableStateOf(initialFilters?.sort ?: _root_ide_package_.com.xl.bill.mint.ui.filter.BillSort.TIME_DESC) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    // 备注保存成功提示
    var showSaveSuccess by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.noteSaved.collect { showSaveSuccess = true }
    }

    val catById = remember(categories) { categories.associateBy { it.id } }
    var selectedTxId by remember { mutableStateOf<Long?>(null) }
    val selectedTx = remember(state.items, selectedTxId) {
        selectedTxId?.let { id -> state.items.firstOrNull { it.id == id } }
    }
    val listState = rememberLazyListState()

    // 筛选变化（含首次进入）→ 回顶并重置分页
    LaunchedEffect(typeFilter, channelFilter, rangeFilter, categoryFilter, sortFilter) {
        listState.scrollToItem(0)
        viewModel.refresh(
            _root_ide_package_.com.xl.bill.mint.ui.filter.BillFilters(
                typeFilter,
                channelFilter,
                rangeFilter,
                sortFilter,
                categoryFilter
            )
        )
    }

    // 滚动到底（倒数 3 条内）触发加载下一页
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.items.size) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    // 下拉选项与当前标签
    val typeExpenseLabel = stringResource(R.string.filter_type_expense)
    val typeIncomeLabel = stringResource(R.string.filter_type_income)
    val typeAllLabel = stringResource(R.string.filter_type_all)
    val channelWechatLabel = stringResource(R.string.filter_channel_wechat)
    val channelAlipayLabel = stringResource(R.string.filter_channel_alipay)
    val channelBankLabel = stringResource(R.string.filter_channel_bank)
    val channelSmsLabel = stringResource(R.string.filter_channel_sms)
    val channelManualLabel = stringResource(R.string.filter_channel_manual)
    val channelAllLabel = stringResource(R.string.filter_channel_all)
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
    val sortLatestLabel = stringResource(R.string.filter_sort_time_desc)
    val sortOldestLabel = stringResource(R.string.filter_sort_time_asc)
    val sortAmountDescLabel = stringResource(R.string.filter_sort_amount_desc)
    val sortAmountAscLabel = stringResource(R.string.filter_sort_amount_asc)
    val sortOpts = remember {
        _root_ide_package_.com.xl.bill.mint.ui.filter.sortOptions(
            sortLatestLabel,
            sortOldestLabel,
            sortAmountDescLabel,
            sortAmountAscLabel
        )
    }
    val rangeLabel = _root_ide_package_.com.xl.bill.mint.util.TimeRange.label(
        rangeFilter?.first,
        rangeFilter?.second,
        homeDefault = false,
        recentLabel = stringResource(R.string.time_range_recent3),
        allTimeLabel = stringResource(R.string.time_range_all_time),
        todayLabel = stringResource(R.string.time_range_today)
    )
    // 分类 pill 标签：选中分类名（分类被删/不存在时回退「全部分类」）
    val categoryLabel = categoryFilter?.let { id -> catById[id]?.name }
        ?: stringResource(R.string.filter_category_all)

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                text = stringResource(R.string.all_bills_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
        }

        // 筛选行：时间范围 + 收支类型 + 支付渠道 + 分类 + 排序
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            _root_ide_package_.com.xl.bill.mint.ui.components.SoftSurface(
                cornerRadius = 16.dp,
                elevation = 6.dp,
                onClick = { showTimePicker = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rangeLabel,
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
            _root_ide_package_.com.xl.bill.mint.ui.components.FilterDropdown(
                label = typeOpts.firstOrNull { it.value == typeFilter }?.label
                    ?: stringResource(R.string.filter_type_all),
                options = typeOpts,
                selected = typeFilter,
                onSelect = { typeFilter = it }
            )
            _root_ide_package_.com.xl.bill.mint.ui.components.FilterDropdown(
                label = channelOpts.firstOrNull { it.value == channelFilter }?.label
                    ?: stringResource(R.string.filter_channel_all),
                options = channelOpts,
                selected = channelFilter,
                onSelect = { channelFilter = it }
            )
            // 分类筛选 pill（SoftSurface 弹层样式，与时间范围 pill 一致；分类多，用底部弹窗选择）
            _root_ide_package_.com.xl.bill.mint.ui.components.SoftSurface(
                cornerRadius = 16.dp,
                elevation = 6.dp,
                onClick = { showCategoryPicker = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = categoryLabel,
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
            _root_ide_package_.com.xl.bill.mint.ui.components.FilterDropdown(
                label = sortOpts.first { it.value == sortFilter }.label,
                options = sortOpts,
                selected = sortFilter,
                onSelect = { sortFilter = it }
            )
        }

        // 统计栏：共 n 笔（左）+ 收入/支出合计（右）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.all_bills_total, state.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    R.string.all_bills_income,
                    com.xl.bill.mint.util.MoneyFormatter.yuan(state.income)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = IncomeMint
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(
                    R.string.all_bills_expense,
                    com.xl.bill.mint.util.MoneyFormatter.yuan(state.expense)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = ExpenseRose
            )
        }

        // 列表（滚动加载）
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.loading && state.items.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    }
                }
            } else if (state.items.isEmpty()) {
                item {
                    _root_ide_package_.com.xl.bill.mint.ui.components.EmptyState(
                        title = stringResource(R.string.all_bills_empty_title),
                        description = stringResource(R.string.all_bills_empty_desc)
                    )
                }
            } else {
                items(state.items, key = { it.id }) { tx ->
                    _root_ide_package_.com.xl.bill.mint.ui.components.BillCard(
                        tx = tx,
                        category = catById[tx.categoryId],
                        onClick = { selectedTxId = tx.id }
                    )
                }
                item {
                    when {
                        state.loadingMore -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }

                        !state.hasMore -> Text(
                            text = stringResource(R.string.all_bills_loaded_all),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }

    // 账单详情弹窗（改分类/备注/删除后 VM 内自动刷新列表）
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

    // 时间范围选择弹窗（不限 = 全部时间）
    if (showTimePicker) {
        TimeRangePickerSheet(
            currentStart = rangeFilter?.first,
            currentEnd = rangeFilter?.second,
            homeDefault = false,
            onConfirm = { range ->
                rangeFilter = range
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    // 分类筛选弹窗（支出/收入分区 + 全部分类）
    if (showCategoryPicker) {
        CategoryFilterSheet(
            categories = categories,
            currentType = typeFilter,
            selectedId = categoryFilter,
            onSelect = {
                categoryFilter = it
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }
}

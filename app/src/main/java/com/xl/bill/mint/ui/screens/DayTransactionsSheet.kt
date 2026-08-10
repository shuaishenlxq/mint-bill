package com.xl.bill.mint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.R
import com.xl.bill.mint.data.db.CategoryEntity
import com.xl.bill.mint.data.db.TransactionEntity
import com.xl.bill.mint.di.ServiceLocator
import com.xl.bill.mint.ui.components.BillCard
import com.xl.bill.mint.ui.filter.BillSort
import com.xl.bill.mint.util.StatisticsCalculator
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 日账单列表弹窗（报表页日视图入口）：当日账单，每 5 条一页滚动加载，按支付时间正序 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayTransactionsSheet(
    day: StatisticsCalculator.DayBalance,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit
) {
    val repo = ServiceLocator.transactionRepository
    val scope = rememberCoroutineScope()
    val catById = remember(categories) { categories.associateBy { it.id } }
    val listState = rememberLazyListState()

    // 分页状态（弹窗生命周期内自管理；编辑后 refreshTick 触发重拉第一页）
    var items by remember(day.date) { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    var hasMore by remember(day.date) { mutableStateOf(true) }
    var loading by remember(day.date) { mutableStateOf(false) } // 首屏/触底统一加载锁（防并发重复）
    var refreshTick by remember(day.date) { mutableIntStateOf(0) }

    // 内嵌账单详情弹窗状态
    var detailTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var showSaveSuccess by remember { mutableStateOf(false) }

    // 拉第一页（进入 / 编辑后刷新），sortMode=3 时间正序
    LaunchedEffect(day.date, refreshTick) {
        loading = true
        try {
            val (start, end) = StatisticsCalculator.dayRange(day.date)
            items = repo.getFiltered(start, end, null, null, null, BillSort.TIME_ASC.sortMode, PAGE_SIZE_DAY, 0)
            hasMore = items.size == PAGE_SIZE_DAY
        } finally {
            loading = false
        }
    }

    // 触底加载下一页（与全部账单页一致：剩余 3 条内触发；空列表不触发，loading 锁防与首屏并发）
    val shouldLoadMore by remember {
        derivedStateOf {
            if (items.isEmpty()) return@derivedStateOf false
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, items.size, hasMore) {
        if (shouldLoadMore && hasMore && !loading) {
            loading = true
            try {
                val (start, end) = StatisticsCalculator.dayRange(day.date)
                val page = repo.getFiltered(
                    start, end, null, null, null, BillSort.TIME_ASC.sortMode, PAGE_SIZE_DAY, items.size
                )
                items = (items + page).distinctBy { it.id }
                hasMore = page.size == PAGE_SIZE_DAY
            } finally {
                loading = false
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = day.date.format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINESE)),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (items.isEmpty()) {
                // 首屏加载中显示居中 spinner，避免闪「暂无」；加载完成且无数据才显示空态
                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.day_bills_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { tx ->
                        BillCard(
                            tx = tx,
                            category = catById[tx.categoryId],
                            onClick = { detailTx = tx }
                        )
                    }
                    if (loading) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 内嵌账单详情弹窗（编辑/删除后重拉第一页刷新列表）
    detailTx?.let { tx ->
        BillDetailSheet(
            tx = tx,
            categories = categories,
            onDismiss = { detailTx = null },
            onDelete = {
                scope.launch {
                    repo.delete(tx.id)
                    detailTx = null
                    refreshTick++
                }
            },
            onUpdateCategory = { categoryId ->
                scope.launch {
                    repo.updateCategory(tx.id, categoryId)
                    refreshTick++
                }
            },
            onUpdateNote = { note ->
                scope.launch {
                    repo.updateNote(tx.id, note)
                    showSaveSuccess = true
                    refreshTick++
                }
            },
            onAddCategory = { name, icon, type, keywords ->
                scope.launch {
                    val newId = ServiceLocator.categoryRepository.addCategory(name, icon, type, keywords)
                    repo.updateCategory(tx.id, newId)
                    refreshTick++
                }
            },
            showSaveSuccess = showSaveSuccess,
            onSaveSuccessDismiss = { showSaveSuccess = false }
        )
    }
}

/** 日账单弹窗每页条数 */
private const val PAGE_SIZE_DAY = 5

package com.xl.bill.mint.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.R
import com.xl.bill.mint.data.db.CategoryEntity
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint

/**
 * 分类筛选弹窗（全部账单页入口）：支出/收入分区 + 「全部分类」置顶取消筛选。
 * 单选：点击行即回调 [onSelect]（Long?，null=全部）并关闭。
 * 样式对齐 [CategoryManageSheet]（ModalBottomSheet + SegmentedButton 分区 + 列表行）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterSheet(
    categories: List<com.xl.bill.mint.data.db.CategoryEntity>,
    currentType: Int?,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    // 分区 tab 跟随当前类型筛选（全部时默认支出分区）
    var tab by remember(currentType) { mutableStateOf(currentType ?: CategoryEntity.TYPE_EXPENSE) }
    val tabCats = remember(categories, tab) { categories.filter { it.type == tab } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
        ) {
            Text(
                text = stringResource(R.string.filter_category_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                SegmentedButton(
                    selected = tab == CategoryEntity.TYPE_EXPENSE,
                    onClick = { tab = CategoryEntity.TYPE_EXPENSE },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = ExpenseRose,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    label = { Text(stringResource(R.string.filter_type_expense)) }
                )
                SegmentedButton(
                    selected = tab == CategoryEntity.TYPE_INCOME,
                    onClick = { tab = CategoryEntity.TYPE_INCOME },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = IncomeMint,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    label = { Text(stringResource(R.string.filter_type_income)) }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                // 全部分类：取消分类筛选
                item(key = "all") {
                    CategoryFilterRow(
                        icon = "🗂️",
                        name = stringResource(R.string.filter_category_all),
                        selected = selectedId == null,
                        onClick = { onSelect(null) }
                    )
                }
                items(tabCats, key = { it.id }) { cat ->
                    CategoryFilterRow(
                        icon = cat.icon,
                        name = cat.name,
                        selected = selectedId == cat.id,
                        onClick = { onSelect(cat.id) }
                    )
                }
            }
        }
    }
}

/** 分类筛选单选行：icon + 名称，选中项薄荷高亮 + Check（对齐 FilterDropdown 选中态） */
@Composable
private fun CategoryFilterRow(
    icon: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

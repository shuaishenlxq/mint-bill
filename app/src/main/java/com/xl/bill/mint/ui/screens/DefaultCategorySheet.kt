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
import androidx.compose.material.icons.rounded.RestartAlt
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xl.bill.mint.R
import com.xl.bill.mint.data.db.CategoryEntity
import com.xl.bill.mint.di.ServiceLocator
import com.xl.bill.mint.parser.CategoryMatcher
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint

/**
 * 默认分类设置弹窗（设置页入口）：支出/收入分区，单选打勾（样式对齐 [CategoryFilterSheet]）。
 * - 选中态 = 配置的分类 id；未配置时显示当前生效的初始默认（支出→餐饮、收入→其他收入）。
 * - 「恢复默认」清除该分区配置，回落初始默认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultCategorySheet(
    defaults: com.xl.bill.mint.parser.Defaults,
    onSelect: (type: Int, categoryId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val categories by ServiceLocator.appDatabase.categoryDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by remember { mutableStateOf(CategoryEntity.TYPE_EXPENSE) }
    val tabCats = remember(categories, tab) { categories.filter { it.type == tab } }

    // 当前分区生效的初始默认分类（按名称查，被改名/删除则为 null）
    val initialDefaultName = remember(tab) {
        if (tab == CategoryEntity.TYPE_EXPENSE) CategoryMatcher.INITIAL_DEFAULT_EXPENSE_NAME
        else CategoryMatcher.DEFAULT_INCOME_NAME
    }
    val initialDefaultId = remember(categories, tab, initialDefaultName) {
        categories.firstOrNull { it.type == tab && it.name == initialDefaultName }?.id
    }
    // 选中态：配置值优先，未配置回落初始默认
    val selectedId = when (tab) {
        CategoryEntity.TYPE_EXPENSE -> defaults.expenseId ?: initialDefaultId
        else -> defaults.incomeId ?: initialDefaultId
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
                .heightIn(max = 620.dp)
        ) {
            Text(
                text = stringResource(R.string.default_category_title),
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

            // 当前分区生效的初始默认提示（未配置时置顶高亮）
            if (initialDefaultId != null) {
                Text(
                    text = stringResource(R.string.default_category_current, initialDefaultName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                items(tabCats, key = { it.id }) { cat ->
                    DefaultCategoryRow(
                        icon = cat.icon,
                        name = cat.name,
                        selected = selectedId == cat.id,
                        onClick = {
                            // 点击「当前生效分类」（未配置时点餐饮/其他收入）→ 显式配置该 id
                            onSelect(tab, cat.id)
                            onDismiss()
                        }
                    )
                }
            }

            // 恢复默认：清除配置，回落初始默认（餐饮/其他收入）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelect(tab, null)
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.default_category_reset),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 默认分类单选行：icon + 名称，选中项薄荷高亮 + Check（对齐 CategoryFilterRow） */
@Composable
private fun DefaultCategoryRow(
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

package com.xl.bill.mint.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.R

/**
 * 分类选择器：可爱 emoji 圆角标签流式排列。
 *
 * @param onAddClick 非 null 时在末尾渲染「＋ 新建」chip（快捷新增分类入口）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    categories: List<com.xl.bill.mint.data.db.CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onAddClick: (() -> Unit)? = null
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = selectedId == category.id,
                onClick = { onSelect(category.id) },
                label = { Text("${category.icon} ${category.name}") },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
        if (onAddClick != null) {
            FilterChip(
                selected = false,
                onClick = onAddClick,
                label = { Text(stringResource(R.string.category_quick_add)) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

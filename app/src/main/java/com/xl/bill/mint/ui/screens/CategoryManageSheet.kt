package com.xl.bill.mint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xl.bill.mint.R
import com.xl.bill.mint.data.db.CategoryEntity
import com.xl.bill.mint.di.ServiceLocator
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint
import kotlinx.coroutines.launch

/**
 * 分类管理弹窗（设置页入口）：支出/收入分区，支持新建、编辑、删除（自定义分类）。
 * 预置分类（isCustom=false）显示「预置」角标、隐藏删除按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageSheet(onDismiss: () -> Unit) {
    val categories by ServiceLocator.appDatabase.categoryDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(CategoryEntity.TYPE_EXPENSE) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<CategoryEntity?>(null) }
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
                text = stringResource(R.string.category_manage_title),
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
                    label = { Text(stringResource(R.string.add_bill_expense)) }
                )
                SegmentedButton(
                    selected = tab == CategoryEntity.TYPE_INCOME,
                    onClick = { tab = CategoryEntity.TYPE_INCOME },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = IncomeMint,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    label = { Text(stringResource(R.string.add_bill_income)) }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(tabCats, key = { it.id }) { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cat.icon, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = cat.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        if (!cat.isCustom) {
                            Text(
                                text = stringResource(R.string.category_preset),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(onClick = { editing = cat; showForm = true }) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.category_edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (cat.isCustom) {
                            IconButton(onClick = { deleteTarget = cat }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.category_delete),
                                    tint = ExpenseRose
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    editing = null
                    showForm = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(R.string.category_add))
            }
        }
    }

    if (showForm) {
        CategoryFormSheet(
            onDismiss = { showForm = false },
            initialName = editing?.name ?: "",
            initialIcon = editing?.icon ?: "🏷️",
            initialKeywords = editing?.keywords ?: "",
            onSave = { name, icon, type, keywords ->
                scope.launch {
                    val cat = editing
                    if (cat != null) {
                        ServiceLocator.categoryRepository.updateCategory(cat.id, name, icon, keywords)
                    } else {
                        ServiceLocator.categoryRepository.addCategory(name, icon, type, keywords)
                    }
                }
                showForm = false
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.category_delete)) },
            text = { Text(stringResource(R.string.category_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { ServiceLocator.categoryRepository.deleteCategory(target.id, target.type) }
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.delete), color = ExpenseRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

package com.xl.bill.mint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.R
import com.xl.bill.mint.data.db.CategoryEntity
import com.xl.bill.mint.data.db.TransactionEntity
import com.xl.bill.mint.ui.components.CategoryPicker
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint
import com.xl.bill.mint.ui.theme.MintPrimary
import com.xl.bill.mint.ui.theme.SoftRed
import com.xl.bill.mint.util.MoneyFormatter
import com.xl.bill.mint.util.TimeUtil

/**
 * 账单详情底部弹窗：展示详情 + 修改分类/备注 + 删除（纠错能力）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailSheet(
    tx: TransactionEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onUpdateCategory: (Long) -> Unit,
    onUpdateNote: (String?) -> Unit,
    onAddCategory: (name: String, icon: String, type: Int, keywords: String) -> Unit,
    showSaveSuccess: Boolean = false,
    onSaveSuccessDismiss: () -> Unit = {}
) {
    val isIncome = tx.type == TransactionEntity.TYPE_INCOME
    var note by remember(tx.id) { mutableStateOf(tx.note.orEmpty()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    // 本地选中态：chip 点击立即高亮（Room 写入 + Flow 传播是异步的）；
    // tx.categoryId 变化（外部/快捷新增完成）时 key 变化自动重新初始化
    var localCategoryId by remember(tx.id, tx.categoryId) { mutableStateOf(tx.categoryId) }
    val currentCategory = remember(categories, localCategoryId) {
        categories.firstOrNull { it.id == localCategoryId }
    }
    val typeCats = remember(categories, tx.type) { categories.filter { it.type == tx.type } }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 头部：分类图标 + 商户 + 金额
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentCategory?.icon ?: "🏷️",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.merchant ?: tx.note ?: currentCategory?.name ?: "账单",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${TimeUtil.channelDisplay(tx.channel)} · ${TimeUtil.format(tx.occurredAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = MoneyFormatter.signed(tx.amount, isIncome),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isIncome) IncomeMint else ExpenseRose
                )
            }

            Text(
                text = stringResource(R.string.bill_edit_category),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            CategoryPicker(
                categories = typeCats,
                selectedId = localCategoryId,
                onSelect = { id ->
                    localCategoryId = id
                    onUpdateCategory(id)
                },
                onAddClick = { showAddCategory = true }
            )

            Text(
                text = stringResource(R.string.bill_edit_note),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
            Button(
                onClick = { onUpdateNote(note.trim().ifEmpty { null }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(R.string.add_bill_save))
            }

            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = SoftRed)
            ) {
                Text(stringResource(R.string.bill_delete))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.bill_delete)) },
            text = { Text(stringResource(R.string.bill_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text(stringResource(R.string.confirm), color = SoftRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    // 备注保存成功提示框：风格与删除确认框一致（extraLarge 圆角 + TextButton），薄荷绿对勾表示成功
    if (showSaveSuccess) {
        AlertDialog(
            onDismissRequest = onSaveSuccessDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = IncomeMint
                )
            },
            title = { Text(stringResource(R.string.saved_success)) },
            confirmButton = {
                TextButton(onClick = onSaveSuccessDismiss) {
                    Text(stringResource(R.string.confirm), color = MintPrimary)
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    // 快捷新建分类：类型固定为当前账单类型，保存后由上层自动把账单改到新分类
    if (showAddCategory) {
        CategoryFormSheet(
            fixedType = tx.type,
            onDismiss = { showAddCategory = false },
            onSave = { name, icon, type, keywords ->
                onAddCategory(name, icon, type, keywords)
                showAddCategory = false
            }
        )
    }
}

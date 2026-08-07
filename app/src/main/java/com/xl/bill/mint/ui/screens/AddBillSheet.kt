package com.xl.bill.mint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.R
import com.xl.bill.mint.data.db.CategoryEntity
import com.xl.bill.mint.data.db.TransactionEntity
import com.xl.bill.mint.di.ServiceLocator
import com.xl.bill.mint.ui.components.CategoryPicker
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint
import com.xl.bill.mint.util.MoneyFormatter
import kotlinx.coroutines.launch

/**
 * 手动记账底部弹窗（毛玻璃感：ModalBottomSheet 自带模糊容器 + 圆角）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBillSheet(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (type: Int, amountFen: Long, categoryId: Long, note: String?) -> Unit
) {
    var type by remember { mutableStateOf(TransactionEntity.TYPE_EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            Text(
                text = stringResource(R.string.add_bill_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = type == TransactionEntity.TYPE_EXPENSE,
                    onClick = {
                        type = TransactionEntity.TYPE_EXPENSE
                        categoryId = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = ExpenseRose,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    label = { Text(stringResource(R.string.add_bill_expense)) }
                )
                SegmentedButton(
                    selected = type == TransactionEntity.TYPE_INCOME,
                    onClick = {
                        type = TransactionEntity.TYPE_INCOME
                        categoryId = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = IncomeMint,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    label = { Text(stringResource(R.string.add_bill_income)) }
                )
            }

            OutlinedTextField(
                value = amount,
                onValueChange = {
                    amount = it.filter { c -> c.isDigit() || c == '.' }
                    error = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.add_bill_amount_hint)) },
                prefix = { Text("¥") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = error,
                supportingText = if (error) {
                    { Text(stringResource(R.string.invalid_amount)) }
                } else null,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.add_bill_note_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            Text(
                text = stringResource(R.string.add_bill_category),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            val typeCats = remember(categories, type) { categories.filter { it.type == type } }
            CategoryPicker(
                categories = typeCats,
                selectedId = categoryId,
                onSelect = { categoryId = it },
                onAddClick = { showAddCategory = true }
            )

            Button(
                onClick = {
                    val fen = MoneyFormatter.fenFromYuanInput(amount)
                    val catId = categoryId
                    if (fen == null || catId == null) {
                        error = true
                        return@Button
                    }
                    onSave(type, fen, catId, note.trim().ifEmpty { null })
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.add_bill_save),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    // 快捷新建分类：类型跟随当前选中的支出/收入；保存后自动选中新分类
    if (showAddCategory) {
        CategoryFormSheet(
            fixedType = type,
            onDismiss = { showAddCategory = false },
            onSave = { name, icon, newType, keywords ->
                scope.launch {
                    val newId = ServiceLocator.categoryRepository.addCategory(name, icon, newType, keywords)
                    categoryId = newId
                }
                showAddCategory = false
            }
        )
    }
}

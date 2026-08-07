package com.xl.bill.mint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.R
import com.xl.bill.mint.data.db.CategoryEntity
import com.xl.bill.mint.ui.components.CATEGORY_EMOJIS
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint

/**
 * 分类新建/编辑表单（ModalBottomSheet）。
 *
 * @param fixedType 非 null 时隐藏支出/收入切换（账单详情弹窗快捷添加用，类型固定为当前账单类型）；
 *                  null 时显示切换（分类管理页用）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryFormSheet(
    onDismiss: () -> Unit,
    fixedType: Int? = null,
    initialName: String = "",
    initialIcon: String = "🏷️",
    initialKeywords: String = "",
    onSave: (name: String, icon: String, type: Int, keywords: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    var keywords by remember { mutableStateOf(initialKeywords) }
    var type by remember { mutableStateOf(fixedType ?: CategoryEntity.TYPE_EXPENSE) }
    var nameError by remember { mutableStateOf(false) }

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
                text = stringResource(if (initialName.isNotEmpty()) R.string.category_edit else R.string.category_add),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (fixedType == null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == CategoryEntity.TYPE_EXPENSE,
                        onClick = { type = CategoryEntity.TYPE_EXPENSE },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = ExpenseRose,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        label = { Text(stringResource(R.string.add_bill_expense)) }
                    )
                    SegmentedButton(
                        selected = type == CategoryEntity.TYPE_INCOME,
                        onClick = { type = CategoryEntity.TYPE_INCOME },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = IncomeMint,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        label = { Text(stringResource(R.string.add_bill_income)) }
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.category_name_hint)) },
                singleLine = true,
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.category_name_required)) }
                } else null,
                shape = MaterialTheme.shapes.large
            )

            Text(
                text = stringResource(R.string.category_icon_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            // emoji 网格：用 FlowRow（勿用 LazyVerticalGrid，避免与外层 verticalScroll 嵌套滚动冲突）
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CATEGORY_EMOJIS.forEach { e ->
                    FilterChip(
                        selected = icon == e,
                        onClick = { icon = e },
                        label = { Text(e) },
                        shape = MaterialTheme.shapes.small
                    )
                }
            }

            OutlinedTextField(
                value = keywords,
                onValueChange = { keywords = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.category_keywords_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    onSave(name.trim(), icon, type, keywords.trim())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(R.string.add_bill_save))
            }
        }
    }
}

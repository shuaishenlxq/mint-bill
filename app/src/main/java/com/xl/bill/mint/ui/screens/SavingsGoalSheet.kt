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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.R

/**
 * 存款目标设置弹窗（ModalBottomSheet）：
 * 目标总存款（必填）、当前存款（初始化基准，选填默认 0）、每月目标存款（选填）。
 * 金额以「元」输入，「分」存储；回显现有值供编辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalSheet(
    onDismiss: () -> Unit,
    initialGoal: com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal,
    onSave: (com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal) -> Unit
) {
    var total by remember { mutableStateOf(initialGoal.total?.let(_root_ide_package_.com.xl.bill.mint.util.MoneyFormatter::yuan) ?: "") }
    var initial by remember { mutableStateOf(_root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(initialGoal.initial)) }
    var monthly by remember { mutableStateOf(initialGoal.monthly?.let(_root_ide_package_.com.xl.bill.mint.util.MoneyFormatter::yuan) ?: "") }
    var totalError by remember { mutableStateOf(false) }
    var initialError by remember { mutableStateOf(false) }
    var monthlyError by remember { mutableStateOf(false) }

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
                text = stringResource(
                    if (initialGoal.total != null) R.string.savings_goal_edit else R.string.savings_goal_set
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            OutlinedTextField(
                value = total,
                onValueChange = {
                    total = it.filter { c -> c.isDigit() || c == '.' }
                    totalError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.savings_total)) },
                prefix = { Text("¥") },
                singleLine = true,
                isError = totalError,
                supportingText = if (totalError) {
                    { Text(stringResource(R.string.savings_total_required)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.large
            )

            OutlinedTextField(
                value = initial,
                onValueChange = {
                    initial = it.filter { c -> c.isDigit() || c == '.' }
                    initialError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.savings_initial)) },
                prefix = { Text("¥") },
                singleLine = true,
                isError = initialError,
                supportingText = {
                    Text(
                        if (initialError) stringResource(R.string.invalid_amount)
                        else stringResource(R.string.savings_initial_hint)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.large
            )

            OutlinedTextField(
                value = monthly,
                onValueChange = {
                    monthly = it.filter { c -> c.isDigit() || c == '.' }
                    monthlyError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.savings_monthly)) },
                prefix = { Text("¥") },
                singleLine = true,
                isError = monthlyError,
                supportingText = {
                    Text(
                        if (monthlyError) stringResource(R.string.invalid_amount)
                        else stringResource(R.string.savings_monthly_hint)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.large
            )

            Button(
                onClick = {
                    val totalFen = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.fenFromYuanInput(total)
                    if (totalFen == null) {
                        totalError = true
                        return@Button
                    }
                    val initialFen = parseOptionalAmount(initial)
                    if (initialFen == null) {
                        initialError = true
                        return@Button
                    }
                    val monthlyFen = if (monthly.isBlank()) null else _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.fenFromYuanInput(monthly)
                    if (monthlyFen == null && monthly.isNotBlank()) {
                        monthlyError = true
                        return@Button
                    }
                    onSave(
                        _root_ide_package_.com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal(
                            total = totalFen,
                            initial = initialFen,
                            monthly = monthlyFen
                        )
                    )
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

/**
 * 当前存款解析：空串或 0 → 0L（fenFromYuanInput 拒收 0/负值，此处特判）；
 * 其余非法输入返回 null 由调用方标错。
 */
private fun parseOptionalAmount(input: String): Long? {
    val t = input.trim().replace("¥", "").replace("￥", "").replace(",", "")
    if (t.isEmpty()) return 0L
    if (t.toDoubleOrNull() == 0.0) return 0L
    return _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.fenFromYuanInput(input)
}

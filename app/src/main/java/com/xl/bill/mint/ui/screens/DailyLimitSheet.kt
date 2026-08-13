package com.xl.bill.mint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
 * 每日限额设置弹窗（ModalBottomSheet）：
 * 单个金额输入框，金额以「元」输入、「分」存储；回显现有限额供编辑。
 * 输入为空保存 = 清除限额（null，首页与报表不校验超额）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLimitSheet(
    onDismiss: () -> Unit,
    initialLimit: Long?,
    onSave: (Long?) -> Unit
) {
    var input by remember { mutableStateOf(initialLimit?.let(_root_ide_package_.com.xl.bill.mint.util.MoneyFormatter::yuan) ?: "") }
    var inputError by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(
                    if (initialLimit != null) R.string.daily_limit_edit else R.string.daily_limit_set
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it.filter { c -> c.isDigit() || c == '.' }
                    inputError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.daily_limit)) },
                prefix = { Text("¥") },
                singleLine = true,
                isError = inputError,
                supportingText = {
                    Text(
                        if (inputError) stringResource(R.string.invalid_amount)
                        else stringResource(R.string.daily_limit_hint)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.large
            )

            Button(
                onClick = {
                    // 空输入 = 清除限额；非空必须为合法正金额
                    if (input.isBlank()) {
                        onSave(null)
                        return@Button
                    }
                    val fen = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.fenFromYuanInput(input)
                    if (fen == null) {
                        inputError = true
                        return@Button
                    }
                    onSave(fen)
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

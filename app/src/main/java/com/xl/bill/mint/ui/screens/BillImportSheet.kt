package com.xl.bill.mint.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xl.bill.mint.R
import com.xl.bill.mint.billimport.DuplicatePolicy
import com.xl.bill.mint.parser.BillImportRow
import com.xl.bill.mint.parser.Channel
import com.xl.bill.mint.ui.components.SoftSurface
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint
import com.xl.bill.mint.ui.viewmodel.BillImportUiState
import com.xl.bill.mint.ui.viewmodel.BillImportViewModel
import com.xl.bill.mint.util.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 微信 xlsx MIME + * / * 兜底（部分文件管理器把 xlsx 识别为通用二进制） */
private val XLSX_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "*/*"
)

/** 支付宝 CSV MIME + * / * 兜底（部分文件管理器把 csv 识别为通用二进制） */
private val ALIPAY_CSV_MIME_TYPES = arrayOf(
    "text/csv",
    "application/csv",
    "text/comma-separated-values",
    "*/*"
)

/**
 * 「导入账单」底部弹窗（微信 Excel / 支付宝 CSV 双来源）：选择文件 → 解析 → 预览（逐条删除）→ 确认导入 → 结果。
 * 布局与视觉沿用 TransferDialogs 的薄荷毛玻璃风格，两来源仅标题/文案/图标/MIME 不同。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillImportSheet(
    source: Channel,
    onDismiss: () -> Unit,
    viewModel: BillImportViewModel = viewModel(factory = BillImportViewModel.factory(source))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickerTriggered by remember { mutableStateOf(false) }

    val isAlipay = source == Channel.ALIPAY
    val mimeTypes = remember(isAlipay) { if (isAlipay) ALIPAY_CSV_MIME_TYPES else XLSX_MIME_TYPES }
    val icon: ImageVector = if (isAlipay) Icons.Rounded.AccountBalanceWallet else Icons.Rounded.GridOn
    val titleRes = if (isAlipay) R.string.bill_import_title_alipay else R.string.bill_import_title
    val descRes = if (isAlipay) R.string.bill_import_desc_alipay else R.string.bill_import_desc
    val pickFileRes = if (isAlipay) R.string.bill_pick_file_alipay else R.string.bill_pick_file
    val pickingRes = if (isAlipay) R.string.bill_picking_alipay else R.string.bill_picking

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.analyze(uri)
    }

    // 打开弹窗后自动拉起系统文件选择器（SAF 免权限）
    LaunchedEffect(Unit) {
        if (!pickerTriggered) {
            pickerTriggered = true
            picker.launch(mimeTypes)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = stringResource(descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (val s = state) {
                is BillImportUiState.Idle -> {
                    Text(
                        text = stringResource(pickingRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { picker.launch(mimeTypes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(pickFileRes))
                    }
                }

                is BillImportUiState.Parsing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.bill_parsing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                is BillImportUiState.Preview -> {
                    PreviewContent(
                        rows = s.rows,
                        unrecognizedCount = s.unrecognizedCount,
                        onDelete = viewModel::deleteRow,
                        onConfirm = viewModel::confirmImport
                    )
                }

                is BillImportUiState.ConfirmDuplicate -> {
                    Text(
                        text = stringResource(R.string.bill_import_dup_desc, s.dupCount, s.newCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.resolveDuplicate(DuplicatePolicy.REPLACE) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.bill_import_dup_replace))
                        }
                        TextButton(
                            onClick = { viewModel.resolveDuplicate(DuplicatePolicy.SKIP) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.bill_import_dup_skip))
                        }
                        TextButton(
                            onClick = { viewModel.reset(); onDismiss() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.bill_close), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                is BillImportUiState.Importing -> {
                    Text(
                        text = stringResource(R.string.bill_importing, s.done, s.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    LinearProgressIndicator(
                        progress = { if (s.total > 0) s.done.toFloat() / s.total else 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                }

                is BillImportUiState.Success -> {
                    Text(
                        text = stringResource(R.string.bill_import_success, s.inserted, s.skipped),
                        style = MaterialTheme.typography.bodyLarge,
                        color = IncomeMint
                    )
                    Button(onClick = { viewModel.reset(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.bill_close))
                    }
                }

                is BillImportUiState.Error -> {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExpenseRose
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.reset(); picker.launch(mimeTypes) }) {
                            Text(stringResource(pickFileRes))
                        }
                        TextButton(onClick = { viewModel.reset(); onDismiss() }) {
                            Text(stringResource(R.string.bill_close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewContent(
    rows: List<BillImportRow>,
    unrecognizedCount: Int,
    onDelete: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val expense = rows.count { it.type == 0 }
    val income = rows.count { it.type == 1 }
    val neutral = rows.count { it.wasNeutral }

    Text(
        text = stringResource(R.string.bill_preview_stats, rows.size, expense, income, neutral),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (unrecognizedCount > 0) {
        Text(
            text = stringResource(R.string.bill_preview_unrecognized, unrecognizedCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (rows.isEmpty()) {
        Text(
            text = stringResource(R.string.bill_preview_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text(
            text = stringResource(R.string.capture_summary_new_prefix),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        rows.forEach { row ->
            BillImportRowView(row) { onDelete(row.notificationKey) }
        }
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.bill_confirm_import, rows.size))
        }
    }
}

@Composable
private fun BillImportRowView(item: BillImportRow, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.merchant ?: item.tradeType ?: item.rawText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                if (item.wasNeutral) {
                    Spacer(Modifier.width(6.dp))
                    SoftSurface(cornerRadius = 8.dp, elevation = 2.dp) {
                        Text(
                            text = stringResource(R.string.bill_neutral_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.occurredAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = MoneyFormatter.signed(item.amountFen, item.type == 1),
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.type == 1) IncomeMint else ExpenseRose
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onDelete) {
            Text(
                text = stringResource(R.string.capture_summary_delete),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

package com.xl.bill.mint.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

/** 预览日期格式（DateTimeFormatter 线程安全，顶层复用；避免每行重组新建 SimpleDateFormat） */
private val IMPORT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * 「导入账单」底部弹窗（微信 Excel / 支付宝 CSV 双来源）：选择文件 → 解析 → 预览（逐条删除）→ 确认导入 → 结果。
 * 布局与视觉沿用 TransferDialogs 的薄荷毛玻璃风格，两来源仅标题/文案/图标/MIME 不同。
 *
 * 性能与交互（2026-08-09）：
 * - 预览列表 LazyColumn 懒加载（900+ 条不卡顿），确认按钮固定在底部；
 * - 解析/写入期间全屏 scrim 遮罩（不允许其他操作），并禁用 sheet 拖拽/返回键/scrim 关闭。
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

    // 解析/写入中：禁止任何关闭途径（遮罩覆盖内容 + 拖拽/scrim/返回键拦截）
    val busy = state is BillImportUiState.Parsing || state is BillImportUiState.Importing
    val busyState by rememberUpdatedState(busy)
    val busyMessageRes = if (state is BillImportUiState.Parsing) R.string.bill_parsing else R.string.bill_writing

    ModalBottomSheet(
        onDismissRequest = {
            if (!busy) {
                viewModel.reset()
                onDismiss()
            }
            // busy 时忽略关闭请求（拖拽/scrim 已由 confirmValueChange 拦截，此处兜底）
        },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { value ->
                if (busyState) value != SheetValue.Hidden else true
            }
        ),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !busy),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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

                    // 解析/写入中：内容区占位，交互被全屏遮罩接管
                    is BillImportUiState.Parsing -> Unit
                    is BillImportUiState.Importing -> Unit

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

            // 覆盖式 loading：解析/写入期间盖住全部内容并拦截点击，不允许其他操作
            if (busy) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(busyMessageRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PreviewContent(
    rows: List<BillImportRow>,
    unrecognizedCount: Int,
    onDelete: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val stats = remember(rows) {
        Triple(
            rows.count { it.type == 0 },
            rows.count { it.type == 1 },
            rows.count { it.wasNeutral }
        )
    }

    Text(
        text = stringResource(R.string.bill_preview_stats, rows.size, stats.first, stats.second, stats.third),
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
        // 懒加载列表：900+ 条不卡顿；确认按钮固定在列表下方（底部常驻，无需滑到底）
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(rows, key = { it.notificationKey }) { row ->
                BillImportRowView(row) { onDelete(row.notificationKey) }
            }
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.bill_confirm_import, rows.size))
        }
    }
}

@Composable
private fun BillImportRowView(item: BillImportRow, onDelete: () -> Unit) {
    val titleText = remember(item.merchant, item.tradeType, item.rawText) {
        item.merchant ?: item.tradeType ?: item.rawText
    }
    val dateText = remember(item.occurredAt) {
        Instant.ofEpochMilli(item.occurredAt)
            .atZone(ZoneId.systemDefault())
            .format(IMPORT_DATE_FORMAT)
    }
    val amountText = remember(item.amountFen, item.type) {
        MoneyFormatter.signed(item.amountFen, item.type == 1)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = titleText,
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
                text = dateText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = amountText,
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

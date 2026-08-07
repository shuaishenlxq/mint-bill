package com.xl.bill.mint.ui.components

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xl.bill.mint.R
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint
import com.xl.bill.mint.ui.viewmodel.TransferUiState
import com.xl.bill.mint.ui.viewmodel.TransferViewModel

/** 蓝牙发送/接收所需的运行时权限 */
private val BT_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_SCAN
)

/**
 * 「蓝牙发送」底部弹窗：展示已配对设备，点击即发送本机全部账单数据。
 * 未配对时引导去系统蓝牙设置完成配对。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSendSheet(
    onDismiss: () -> Unit,
    viewModel: TransferViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var permissionGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> permissionGranted = grants.values.all { it } }

    // 打开弹窗时自动申请权限
    LaunchedEffect(Unit) {
        val missing = BT_PERMISSIONS.filter { permission ->
            context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) permissionGranted = true
        else permissionLauncher.launch(missing.toTypedArray())
    }

    val bondedDevices = remember(permissionGranted) {
        if (permissionGranted) {
            runCatching {
                (context.getSystemService(BluetoothManager::class.java))?.adapter?.bondedDevices?.toList()
                    .orEmpty()
            }.getOrDefault(emptyList())
        } else emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.cancel()
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
            Text(
                text = stringResource(R.string.bt_send_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.settings_bt_send_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (val s = state) {
                is TransferUiState.Idle -> {
                    if (!permissionGranted) {
                        Text(
                            text = stringResource(R.string.bt_permission_needed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ExpenseRose
                        )
                        Button(
                            onClick = { permissionLauncher.launch(BT_PERMISSIONS) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_permissions))
                        }
                    } else if (bondedDevices.isEmpty()) {
                        EmptyPairedDevices(onOpenSettings = { openBluetoothSettings(context) })
                    } else {
                        Text(
                            text = stringResource(R.string.bt_paired_devices),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        bondedDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.sendTo(context, device) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Devices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name ?: device.address,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { openBluetoothSettings(context) }) {
                            Text(stringResource(R.string.bt_open_settings))
                        }
                    }
                }

                is TransferUiState.Preparing -> BusyRow(stringResource(R.string.bt_preparing))
                is TransferUiState.Sending -> {
                    BusyRow(stringResource(R.string.bt_sending))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }

                is TransferUiState.Sent -> {
                    Text(
                        text = stringResource(R.string.bt_send_complete),
                        style = MaterialTheme.typography.bodyLarge,
                        color = IncomeMint
                    )
                    Button(onClick = { viewModel.cancel(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.bt_close))
                    }
                }

                is TransferUiState.Error -> {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExpenseRose
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.cancel() }) {
                            Text(stringResource(R.string.bt_close))
                        }
                        OutlinedRetryButton { viewModel.cancel() }
                    }
                }

                else -> Unit // Waiting/Receiving/Received/Success 不在发送端出现
            }
        }
    }
}

/**
 * 「蓝牙接收」底部弹窗：等待对方发送 → 进度 → 确认导入 → 完成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferReceiveSheet(
    onDismiss: () -> Unit,
    viewModel: TransferViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var permissionGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> permissionGranted = grants.values.all { it } }

    // 权限就绪后自动开始等待接收
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.startReceive(context)
    }
    LaunchedEffect(Unit) {
        val missing = BT_PERMISSIONS.filter { permission ->
            context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) permissionGranted = true
        else permissionLauncher.launch(missing.toTypedArray())
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.cancel()
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
            Text(
                text = stringResource(R.string.bt_receive_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.settings_bt_receive_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (val s = state) {
                is TransferUiState.Idle -> {
                    if (!permissionGranted) {
                        Text(
                            text = stringResource(R.string.bt_permission_needed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ExpenseRose
                        )
                        Button(
                            onClick = { permissionLauncher.launch(BT_PERMISSIONS) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_permissions))
                        }
                    }
                }

                is TransferUiState.Waiting -> {
                    BusyRow(stringResource(R.string.bt_waiting))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is TransferUiState.Receiving -> {
                    BusyRow(stringResource(R.string.bt_receiving))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }

                is TransferUiState.Received -> {
                    Text(
                        text = stringResource(R.string.bt_receive_complete),
                        style = MaterialTheme.typography.bodyLarge,
                        color = IncomeMint
                    )
                }

                is TransferUiState.Success -> {
                    Text(
                        text = stringResource(R.string.import_success, s.transactionCount),
                        style = MaterialTheme.typography.bodyLarge,
                        color = IncomeMint
                    )
                    Button(onClick = { viewModel.cancel(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.bt_close))
                    }
                }

                is TransferUiState.Error -> {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExpenseRose
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.cancel() }) {
                            Text(stringResource(R.string.bt_close))
                        }
                        OutlinedRetryButton {
                            viewModel.cancel()
                            viewModel.startReceive(context)
                        }
                    }
                }

                else -> Unit
            }
        }
    }

    // 接收完成后弹导入确认框（清空现有数据）
    if (state is TransferUiState.Received) {
        AlertDialog(
            onDismissRequest = { viewModel.cancel() },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport() }) {
                    Text(stringResource(R.string.confirm), color = ExpenseRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancel() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

/** 已配对设备为空时的引导卡片 */
@Composable
private fun EmptyPairedDevices(onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.bt_no_devices),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.bt_open_settings))
        }
    }
}

@Composable
private fun BusyRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun OutlinedRetryButton(onRetry: () -> Unit) {
    TextButton(onClick = onRetry) {
        Text(stringResource(R.string.bt_retry))
    }
}

/** 跳转系统蓝牙设置 */
private fun openBluetoothSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }
}

package com.xl.bill.mint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xl.bill.mint.R
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint

/**
 * 设置页：总开关、权限与保活引导、记账渠道、数据管理、保活说明。
 */
@Composable
fun SettingsScreen(viewModel: com.xl.bill.mint.ui.viewmodel.SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val autoRecord by viewModel.autoRecord.collectAsStateWithLifecycle()
    val transferHintEnabled by viewModel.transferHintEnabled.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val channelEnabled by viewModel.channelEnabled.collectAsStateWithLifecycle()
    val savingsGoal by viewModel.savingsGoal.collectAsStateWithLifecycle()

    // 从系统设置返回后自动刷新权限状态
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val listenerEnabled = remember(refreshKey) { _root_ide_package_.com.xl.bill.mint.util.PermissionChecker.isNotificationListenerEnabled(context) }
    val accessibilityEnabled = remember(refreshKey) { _root_ide_package_.com.xl.bill.mint.util.PermissionChecker.isAccessibilityServiceEnabled(context) }
    val smsGranted = remember(refreshKey) { _root_ide_package_.com.xl.bill.mint.util.PermissionChecker.hasSmsPermission(context) }
    val batteryWhitelisted = remember(refreshKey) { _root_ide_package_.com.xl.bill.mint.util.PermissionChecker.isIgnoringBatteryOptimizations(context) }
    val fgAlive = remember(refreshKey) { _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.isForegroundServiceAlive() }

    // 短信权限申请（结果由 ON_RESUME refreshKey 自动刷新状态）
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 授权与否由 refreshKey 刷新展示 */ }

    var showClearConfirm by remember { mutableStateOf(false) }
    var showSendSheet by remember { mutableStateOf(false) }
    var showReceiveSheet by remember { mutableStateOf(false) }
    var showBillImportSheet by remember { mutableStateOf(false) }
    var showAlipayImportSheet by remember { mutableStateOf(false) }
    var showCategoryManage by remember { mutableStateOf(false) }
    var showSavingsGoal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // 自动记账总开关
        item {
            _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_auto_record),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_auto_record_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoRecord,
                        onCheckedChange = viewModel::setAutoRecord,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // 转账记录提示开关
        item {
            _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_transfer_hint),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_transfer_hint_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = transferHintEnabled,
                        onCheckedChange = viewModel::setTransferHintEnabled,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // 无障碍使用说明（微信转账自动记账的正确用法）
        item {
            _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.settings_accessibility_usage),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_accessibility_usage_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 权限与保活
        item {
            SectionTitle(stringResource(R.string.settings_permissions))
        }
        item {
            _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    StatusRow(
                        icon = Icons.Rounded.Notifications,
                        title = stringResource(R.string.settings_notification_access),
                        desc = stringResource(R.string.settings_notification_access_desc),
                        enabled = listenerEnabled,
                        enabledText = stringResource(R.string.settings_notification_access_enabled),
                        disabledText = stringResource(R.string.settings_notification_access_disabled),
                        onClick = {
                            _root_ide_package_.com.xl.bill.mint.util.PermissionChecker.openNotificationListenerSettings(
                                context
                            )
                        }
                    )
                    DividerLine()
                    StatusRow(
                        icon = Icons.Rounded.Accessibility,
                        title = stringResource(R.string.settings_accessibility),
                        desc = stringResource(R.string.settings_accessibility_desc),
                        enabled = accessibilityEnabled,
                        enabledText = stringResource(R.string.settings_accessibility_enabled),
                        disabledText = stringResource(R.string.settings_accessibility_disabled),
                        onClick = {
                            _root_ide_package_.com.xl.bill.mint.util.PermissionChecker.openAccessibilitySettings(
                                context
                            )
                        }
                    )
                    DividerLine()
                    StatusRow(
                        icon = Icons.Rounded.BatteryFull,
                        title = stringResource(R.string.settings_battery_whitelist),
                        desc = stringResource(R.string.settings_battery_whitelist_desc),
                        enabled = batteryWhitelisted,
                        enabledText = stringResource(R.string.settings_battery_whitelist_done),
                        disabledText = stringResource(R.string.settings_battery_whitelist_add),
                        onClick = {
                            _root_ide_package_.com.xl.bill.mint.util.PermissionChecker.requestIgnoreBatteryOptimizations(
                                context
                            )
                        }
                    )
                    DividerLine()
                    StatusRow(
                        icon = Icons.Rounded.Bolt,
                        title = stringResource(R.string.settings_rom_autostart),
                        desc = stringResource(R.string.settings_rom_autostart_desc),
                        enabled = false,
                        enabledText = "",
                        disabledText = stringResource(R.string.settings_rom_autostart_open),
                        onClick = {
                            if (!_root_ide_package_.com.xl.bill.mint.util.RomGuideHelper.openAutostartSettings(
                                    context
                                )
                            ) {
                                _root_ide_package_.com.xl.bill.mint.util.PermissionChecker.openBatteryOptimizationSettings(
                                    context
                                )
                            }
                        }
                    )
                    DividerLine()
                    // 服务状态
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.ensureRunning(
                                    context
                                )
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (fgAlive) IncomeMint else ExpenseRose,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (fgAlive) {
                                stringResource(R.string.settings_service_status)
                            } else {
                                stringResource(R.string.settings_service_status_stopped)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 记账渠道
        item {
            SectionTitle(stringResource(R.string.settings_channels))
        }
        item {
            _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    _root_ide_package_.com.xl.bill.mint.parser.PaymentApps.ALL_CHANNELS.forEachIndexed { index, channel ->
                        if (index > 0) DividerLine()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = channel.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = channelEnabled[channel] ?: true,
                                onCheckedChange = { viewModel.setChannelEnabled(channel, it) },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }

        // 短信渠道：未授予 RECEIVE_SMS 时显示权限引导
        if (!smsGranted) {
            item {
                _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_sms),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_sms_permission_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = {
                            smsPermissionLauncher.launch(android.Manifest.permission.RECEIVE_SMS)
                        }) {
                            Text(stringResource(R.string.settings_sms_permission_grant))
                        }
                    }
                }
            }
        }

        // 存款目标
        item {
            SectionTitle(stringResource(R.string.savings_goal))
        }
        item {
            _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSavingsGoal = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Savings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.savings_goal),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = savingsGoalSubtitle(savingsGoal),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 隐私安全
        item {
            SectionTitle(stringResource(R.string.settings_security))
        }
        item {
            _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_app_lock),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_app_lock_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appLockEnabled,
                        onCheckedChange = viewModel::setAppLockEnabled,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // 数据管理
        item {
            SectionTitle(stringResource(R.string.settings_data))
        }
        item {
            _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    // 分类管理
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCategoryManage = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_category_manage),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.settings_category_manage_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DividerLine()
                    // 蓝牙发送
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSendSheet = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_bt_send),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.settings_bt_send_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DividerLine()
                    // 蓝牙接收
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showReceiveSheet = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_bt_receive),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.settings_bt_receive_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DividerLine()
                    // 导入微信账单 Excel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBillImportSheet = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_bill_import),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.settings_bill_import_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DividerLine()
                    // 导入支付宝账单 CSV
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAlipayImportSheet = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_alipay_import),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.settings_alipay_import_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DividerLine()
                    // 清空全部账单
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearConfirm = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = ExpenseRose,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_clear_all),
                                style = MaterialTheme.typography.bodyLarge,
                                color = ExpenseRose
                            )
                            Text(
                                text = stringResource(R.string.settings_clear_confirm),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 保活说明
        item {
            _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.settings_keepalive_guide),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_about_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_all)) },
            text = { Text(stringResource(R.string.settings_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.confirm), color = ExpenseRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showSendSheet) {
        _root_ide_package_.com.xl.bill.mint.ui.components.TransferSendSheet(onDismiss = {
            showSendSheet = false
        })
    }
    if (showReceiveSheet) {
        _root_ide_package_.com.xl.bill.mint.ui.components.TransferReceiveSheet(onDismiss = {
            showReceiveSheet = false
        })
    }
    if (showBillImportSheet) {
        BillImportSheet(source = _root_ide_package_.com.xl.bill.mint.parser.Channel.WECHAT, onDismiss = { showBillImportSheet = false })
    }
    if (showAlipayImportSheet) {
        BillImportSheet(source = _root_ide_package_.com.xl.bill.mint.parser.Channel.ALIPAY, onDismiss = { showAlipayImportSheet = false })
    }
    if (showCategoryManage) {
        CategoryManageSheet(onDismiss = { showCategoryManage = false })
    }

    if (showSavingsGoal) {
        SavingsGoalSheet(
            onDismiss = { showSavingsGoal = false },
            initialGoal = savingsGoal,
            onSave = { goal ->
                viewModel.setSavingsGoal(goal)
                showSavingsGoal = false
            }
        )
    }
}

/** 存款目标摘要：未设置显示引导文案；已设置显示「目标 ¥x · 每月目标 ¥y」 */
@Composable
private fun savingsGoalSubtitle(goal: com.xl.bill.mint.data.repo.SettingsRepository.SavingsGoal): String {
    val total = goal.total ?: return stringResource(R.string.savings_goal_desc)
    val monthlyPart = goal.monthly?.let {
        " · " + stringResource(R.string.savings_summary_monthly, _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(it))
    } ?: ""
    return stringResource(R.string.savings_summary_goal, _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(total)) + monthlyPart
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    title: String,
    desc: String,
    enabled: Boolean,
    enabledText: String,
    disabledText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (enabled) enabledText else disabledText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) IncomeMint else ExpenseRose
        )
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 34.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

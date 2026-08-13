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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tag
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
import com.xl.bill.mint.transfer.BackupFileManager
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint
import com.xl.bill.mint.ui.viewmodel.BackupOpState
import android.widget.Toast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import android.app.AlarmManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import com.xl.bill.mint.util.DiagLog

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
    val smsWindowMs by viewModel.smsWindowMs.collectAsStateWithLifecycle()
    val savingsGoal by viewModel.savingsGoal.collectAsStateWithLifecycle()
    val dailyLimit by viewModel.dailyLimit.collectAsStateWithLifecycle()
    val categoryDefaults by viewModel.categoryDefaults.collectAsStateWithLifecycle()
    val categories by _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appDatabase.categoryDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

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
    val fgAlive = remember(refreshKey) { _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.isForegroundServiceRecentlyActive(context) }
    val exactAlarmEnabled = remember(refreshKey) {
        (context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

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
    var showDailyLimit by remember { mutableStateOf(false) }
    var showDefaultCategory by remember { mutableStateOf(false) }
    var showAdBlock by remember { mutableStateOf(false) }
    var showCustomMatch by remember { mutableStateOf(false) }
    var showDiagLog by remember { mutableStateOf(false) }
    var diagLogContent by remember { mutableStateOf("") }
    var showSmsWindowDialog by remember { mutableStateOf(false) }

    // 备份/恢复（文件级，扛卸载重装）
    var showRestoreConfirm by remember { mutableStateOf(false) }
    val backupOpState by viewModel.backupOpState.collectAsStateWithLifecycle()

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.backupToUri(it) } }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.restoreFromUri(it) } }

    // 备份/恢复结果：Toast 提示后复位
    LaunchedEffect(backupOpState) {
        when (val s = backupOpState) {
            is BackupOpState.Success -> {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                viewModel.resetBackupOpState()
            }
            is BackupOpState.Error -> {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                viewModel.resetBackupOpState()
            }
            else -> {}
        }
    }

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
                        title = stringResource(R.string.settings_exact_alarm),
                        desc = stringResource(R.string.settings_exact_alarm_desc),
                        enabled = exactAlarmEnabled,
                        enabledText = stringResource(R.string.settings_exact_alarm_on),
                        disabledText = stringResource(R.string.settings_exact_alarm_off),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure {
                                _root_ide_package_.com.xl.bill.mint.util.PermissionChecker.openBatteryOptimizationSettings(
                                    context
                                )
                            }
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
                        neutral = true,
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
                    DividerLine()
                    // 诊断日志：漏记时先看这里（本地记录，不上传）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                diagLogContent = DiagLog.readAll()
                                showDiagLog = true
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_diag_log),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.settings_diag_log_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    DividerLine()
                    // 短信去重窗口（短信延迟超过窗口可能双记，可调大）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSmsWindowDialog = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_sms_window),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.settings_sms_window_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_sms_window_value, smsWindowMs / 1000),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 智能记账：默认分类 + 广告过滤
        item {
            _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDefaultCategory = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_default_category),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = defaultCategorySubtitle(categoryDefaults, categories),
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
                    DividerLine()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdBlock = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_ad_block),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.settings_ad_block_desc),
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
                    DividerLine()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCustomMatch = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_custom_match),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.settings_custom_match_desc),
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

        // 每日限额
        item {
            SectionTitle(stringResource(R.string.daily_limit))
        }
        item {
            _root_ide_package_.com.xl.bill.mint.ui.components.GlassCard {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDailyLimit = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.daily_limit),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = dailyLimitSubtitle(dailyLimit),
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
                    // 备份到文件（SAF 选择器，落下载目录/云盘，卸载不丢）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { backupLauncher.launch(BackupFileManager.defaultBackupName()) }
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
                                text = stringResource(R.string.settings_backup),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.settings_backup_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DividerLine()
                    // 从文件恢复（先确认，再选文件）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRestoreConfirm = true }
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
                                text = stringResource(R.string.settings_restore),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.settings_restore_desc),
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

    if (showSmsWindowDialog) {
        val options = listOf(3_000L, 5_000L, 10_000L, 30_000L, 60_000L)
        AlertDialog(
            onDismissRequest = { showSmsWindowDialog = false },
            title = { Text(stringResource(R.string.settings_sms_window_title)) },
            text = {
                Column {
                    options.forEach { ms ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSmsWindowMs(ms)
                                    showSmsWindowDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_sms_window_value, ms / 1000),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (ms == smsWindowMs) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground,
                                fontWeight = if (ms == smsWindowMs) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (ms == smsWindowMs) {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSmsWindowDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showDiagLog) {
        AlertDialog(
            onDismissRequest = { showDiagLog = false },
            title = { Text(stringResource(R.string.diag_dialog_title)) },
            text = {
                if (diagLogContent.isBlank()) {
                    Text(stringResource(R.string.diag_empty))
                } else {
                    SelectionContainer {
                        Text(
                            text = diagLogContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .height(320.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                context.getString(R.string.diag_share_title) + "\n\n" + diagLogContent
                            )
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                ) {
                    Text(stringResource(R.string.diag_share))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        DiagLog.clear()
                        diagLogContent = ""
                    }
                ) {
                    Text(stringResource(R.string.diag_clear), color = ExpenseRose)
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        restoreLauncher.launch(arrayOf("application/json"))
                    }
                ) {
                    Text(stringResource(R.string.confirm), color = ExpenseRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
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

    if (showDailyLimit) {
        DailyLimitSheet(
            onDismiss = { showDailyLimit = false },
            initialLimit = dailyLimit,
            onSave = { fen ->
                viewModel.setDailyLimit(fen)
                showDailyLimit = false
            }
        )
    }

    if (showDefaultCategory) {
        DefaultCategorySheet(
            defaults = categoryDefaults,
            onSelect = { type, id -> viewModel.setDefaultCategory(type, id) },
            onDismiss = { showDefaultCategory = false }
        )
    }

    if (showAdBlock) {
        AdBlockSheet(
            onDismiss = { showAdBlock = false }
        )
    }

    if (showCustomMatch) {
        CustomMatchSheet(
            onDismiss = { showCustomMatch = false }
        )
    }

    // 备份/恢复进行中：全屏遮罩禁止操作
    if (backupOpState is BackupOpState.Busy) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

/**
 * 默认分类摘要：显示支出/收入当前生效分类名（配置优先，未配置显示初始默认）。
 */
@Composable
private fun defaultCategorySubtitle(
    defaults: com.xl.bill.mint.parser.Defaults,
    categories: List<com.xl.bill.mint.data.db.CategoryEntity>
): String {
    fun effectiveName(type: Int, configuredId: Long?, initialName: String): String {
        val configured = configuredId?.let { id ->
            categories.firstOrNull { it.id == id && it.type == type }?.name
        }
        return configured
            ?: categories.firstOrNull { it.type == type && it.name == initialName }?.name
            ?: "—"
    }
    val expense = effectiveName(
        com.xl.bill.mint.data.db.CategoryEntity.TYPE_EXPENSE,
        defaults.expenseId,
        com.xl.bill.mint.parser.CategoryMatcher.INITIAL_DEFAULT_EXPENSE_NAME
    )
    val income = effectiveName(
        com.xl.bill.mint.data.db.CategoryEntity.TYPE_INCOME,
        defaults.incomeId,
        com.xl.bill.mint.parser.CategoryMatcher.DEFAULT_INCOME_NAME
    )
    return stringResource(R.string.default_category_subtitle, expense, income)
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

/** 每日限额摘要：未设置显示引导文案；已设置显示「¥x / 天」 */
@Composable
private fun dailyLimitSubtitle(limit: Long?): String {
    val fen = limit ?: return stringResource(R.string.daily_limit_desc)
    return stringResource(R.string.daily_limit_value, _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(fen))
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
    onClick: () -> Unit,
    neutral: Boolean = false
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
            color = when {
                neutral -> MaterialTheme.colorScheme.primary
                enabled -> IncomeMint
                else -> ExpenseRose
            }
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

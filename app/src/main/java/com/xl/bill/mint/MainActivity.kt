package com.xl.bill.mint

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : androidx.fragment.app.FragmentActivity() {

    /** 通知点击携带的目标账单 id（直达补备注详情弹窗），null 表示无直达目标 */
    private var notePromptTxId by mutableStateOf<Long?>(null)

    /** 应用锁：是否已通过验证（false 时渲染锁屏遮罩，不渲染任何账单数据） */
    private var appUnlocked by mutableStateOf(false)

    /** 应用锁开关缓存（避免每次 onStart 都读 DataStore） */
    private var lockEnabled = false

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 拉起记账守护服务（用户可见前台通知）
        _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.ensureRunning(this)
        requestNotificationPermissionIfNeeded()
        maybeShowOnboarding()

        notePromptTxId = extractNotePromptTxId(intent)

        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    appUnlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // 验证取消/失败：保持锁定，锁屏页可再次点击触发验证
                }

                override fun onAuthenticationFailed() {
                    // 指纹不匹配等：保持锁定
                }
            }
        )
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(_root_ide_package_.com.xl.bill.mint.R.string.app_lock_title))
            .setSubtitle(getString(_root_ide_package_.com.xl.bill.mint.R.string.app_lock_desc))
            // 注意：允许 DEVICE_CREDENTIAL 时禁止 setNegativeButtonText（androidx.biometric 约束，
            // 否则 build() 抛 IllegalArgumentException 导致启动崩溃）；取消按钮由系统提供。
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        // 离开前台（切后台/锁屏）即重锁；回前台由 repeatOnLifecycle(STARTED) 触发验证
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_STOP && lockEnabled) {
                    appUnlocked = false
                }
            }
        })

        setContent {
            _root_ide_package_.com.xl.bill.mint.ui.theme.MintBillTheme {
                if (appUnlocked) {
                    MainScreen(
                        notePromptTxId = notePromptTxId,
                        onNotePromptConsumed = { notePromptTxId = null }
                    )
                } else {
                    LockScreen(onUnlockClick = ::showBiometricPrompt)
                }
            }
        }

        // 每次进入 STARTED（含首次启动与后台返回）读取开关并视情况弹验证
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val enabled = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository.appLockEnabled.first()
                lockEnabled = enabled
                if (!enabled) {
                    appUnlocked = true
                } else if (!appUnlocked) {
                    showBiometricPrompt()
                }
            }
        }
    }

    private fun showBiometricPrompt() {
        if (appUnlocked || !lockEnabled) return
        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (_: Exception) {
            // 极端时序下尚未处于可弹窗状态，等待下一次 STARTED 重试
        }
    }

    /** App 已在后台时收到通知点击，刷新直达目标 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notePromptTxId = extractNotePromptTxId(intent)
    }

    private fun extractNotePromptTxId(intent: Intent): Long? =
        intent.getLongExtra(_root_ide_package_.com.xl.bill.mint.util.NotificationHelper.EXTRA_NOTE_TX_ID, -1L).takeIf { it > 0 }

    /** Android 13+ 常驻通知需要 POST_NOTIFICATIONS 运行时权限 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 用户可拒绝，不影响记账核心功能 */ }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun maybeShowOnboarding() {
        lifecycleScope.launch {
            val done = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.settingsRepository.firstLaunchDone.first()
            if (!done) {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
            }
        }
    }
}

/** 应用锁遮罩：未验证前不渲染任何账单数据 */
@Composable
private fun LockScreen(onUnlockClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(_root_ide_package_.com.xl.bill.mint.R.string.app_lock_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(_root_ide_package_.com.xl.bill.mint.R.string.app_lock_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlockClick) {
                Text(stringResource(_root_ide_package_.com.xl.bill.mint.R.string.app_lock_unlock))
            }
        }
    }
}

private enum class MainTab(val labelRes: Int, val icon: ImageVector) {
    Dashboard(_root_ide_package_.com.xl.bill.mint.R.string.tab_dashboard, Icons.Rounded.Home),
    Statistics(_root_ide_package_.com.xl.bill.mint.R.string.tab_statistics, Icons.Rounded.PieChart),
    Settings(_root_ide_package_.com.xl.bill.mint.R.string.tab_settings, Icons.Rounded.Settings)
}

@Composable
private fun MainScreen(
    notePromptTxId: Long?,
    onNotePromptConsumed: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Dashboard) }
    var showAddBill by remember { mutableStateOf(false) }
    // 全部账单列表页路由（全屏覆盖，隐藏底部导航与 FAB）
    var showAllBills by rememberSaveable { mutableStateOf(false) }
    var allBillsInit by rememberSaveable { mutableStateOf<com.xl.bill.mint.ui.filter.BillFilters?>(null) }
    val dashboardViewModel: com.xl.bill.mint.ui.viewmodel.DashboardViewModel = viewModel()
    val categories by _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.appDatabase.categoryDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!showAllBills) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    MainTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!showAllBills && tab == MainTab.Dashboard) {
                FloatingActionButton(
                    onClick = { showAddBill = true },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(_root_ide_package_.com.xl.bill.mint.R.string.manual_add))
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showAllBills) {
                _root_ide_package_.com.xl.bill.mint.ui.screens.AllBillsScreen(
                    initialFilters = allBillsInit,
                    onBack = { showAllBills = false }
                )
            } else {
                when (tab) {
                    MainTab.Dashboard -> _root_ide_package_.com.xl.bill.mint.ui.screens.DashboardScreen(
                        onAddClick = { showAddBill = true },
                        onViewAll = {
                            allBillsInit = dashboardViewModel.filtersSnapshot()
                            showAllBills = true
                        },
                        notePromptTxId = notePromptTxId,
                        onNotePromptConsumed = onNotePromptConsumed
                    )
                    MainTab.Statistics -> _root_ide_package_.com.xl.bill.mint.ui.screens.StatisticsScreen()
                    MainTab.Settings -> _root_ide_package_.com.xl.bill.mint.ui.screens.SettingsScreen()
                }
            }
        }
    }

    if (showAddBill) {
        _root_ide_package_.com.xl.bill.mint.ui.screens.AddBillSheet(
            categories = categories,
            onDismiss = { showAddBill = false },
            onSave = { type, amountFen, categoryId, note ->
                dashboardViewModel.addManual(type, amountFen, categoryId, note)
                showAddBill = false
            }
        )
    }
}

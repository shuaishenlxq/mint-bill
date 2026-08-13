package com.xl.bill.mint

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.xl.bill.mint.di.ServiceLocator
import com.xl.bill.mint.ui.components.GlassCard
import com.xl.bill.mint.ui.components.Mascot
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint
import com.xl.bill.mint.ui.theme.MintBillTheme
import com.xl.bill.mint.util.KeepAliveHelper
import com.xl.bill.mint.util.PermissionChecker
import kotlinx.coroutines.launch

/**
 * 首次启动引导：通知使用权 → 无障碍 → 后台保活 → 短信权限，四步教会小薄荷记好账。
 */
class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MintBillTheme {
                OnboardingScreen(
                    onStart = {
                        lifecycleScope.launch {
                            ServiceLocator.settingsRepository.markFirstLaunchDone()
                            KeepAliveHelper.ensureRunning(this@OnboardingActivity)
                            startActivity(
                                Intent(this@OnboardingActivity, MainActivity::class.java)
                            )
                            finish()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OnboardingScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val listenerEnabled = remember(refreshKey) { PermissionChecker.isNotificationListenerEnabled(context) }
    val accessibilityEnabled = remember(refreshKey) { PermissionChecker.isAccessibilityServiceEnabled(context) }
    val batteryWhitelisted = remember(refreshKey) { PermissionChecker.isIgnoringBatteryOptimizations(context) }
    val smsGranted = remember(refreshKey) { PermissionChecker.hasSmsPermission(context) }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Mascot(modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        StepCard(
            step = 1,
            title = stringResource(R.string.onboarding_step1_title),
            desc = stringResource(R.string.onboarding_step1_desc),
            done = listenerEnabled,
            buttonText = stringResource(R.string.settings_notification_access_disabled),
            onClick = { PermissionChecker.openNotificationListenerSettings(context) }
        )

        Spacer(Modifier.height(12.dp))

        StepCard(
            step = 2,
            title = stringResource(R.string.onboarding_step2_title),
            desc = stringResource(R.string.onboarding_step2_desc),
            done = accessibilityEnabled,
            buttonText = stringResource(R.string.settings_accessibility_disabled),
            onClick = { PermissionChecker.openAccessibilitySettings(context) }
        )

        Spacer(Modifier.height(12.dp))

        StepCard(
            step = 3,
            title = stringResource(R.string.onboarding_step3_title),
            desc = stringResource(R.string.onboarding_step3_desc),
            done = batteryWhitelisted,
            buttonText = stringResource(R.string.settings_battery_whitelist_add),
            onClick = { PermissionChecker.requestIgnoreBatteryOptimizations(context) }
        )

        Spacer(Modifier.height(12.dp))

        StepCard(
            step = 4,
            title = stringResource(R.string.onboarding_step4_title),
            desc = stringResource(R.string.onboarding_step4_desc),
            done = smsGranted,
            buttonText = stringResource(R.string.settings_sms_permission_grant),
            onClick = { smsPermissionLauncher.launch(android.Manifest.permission.RECEIVE_SMS) }
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                text = stringResource(R.string.onboarding_start),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_skip),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(8.dp)
                .clickable(onClick = onStart),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StepCard(
    step: Int,
    title: String,
    desc: String,
    done: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "STEP $step",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(done)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!done) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(done: Boolean) {
    Text(
        text = if (done) {
            stringResource(R.string.onboarding_status_ok)
        } else {
            stringResource(R.string.onboarding_status_pending)
        },
        style = MaterialTheme.typography.labelMedium,
        color = if (done) IncomeMint else ExpenseRose,
        fontWeight = FontWeight.SemiBold
    )
}

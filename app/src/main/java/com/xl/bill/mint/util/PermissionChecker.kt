package com.xl.bill.mint.util

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 关键权限/服务状态检查与跳转。
 */
object PermissionChecker {

    /** 通知使用权是否已开启（自动记账主数据源） */
    fun isNotificationListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun openNotificationListenerSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 无障碍服务是否已开启 */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, _root_ide_package_.com.xl.bill.mint.service.BillAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabled?.split(':')?.any {
            ComponentName.unflattenFromString(it) == expected
        } ?: false
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 是否已加入电池优化白名单 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 申请电池白名单（ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 直跳，部分机型需手动） */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            openBatteryOptimizationSettings(context)
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // 部分定制 ROM 无此入口，忽略
        }
    }

    /** 运行时通知权限（Android 13+ POST_NOTIFICATIONS） */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** 运行时短信权限（RECEIVE_SMS，Android 6+ 危险权限） */
    fun hasSmsPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean =
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
}

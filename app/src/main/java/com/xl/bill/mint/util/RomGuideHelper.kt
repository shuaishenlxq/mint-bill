package com.xl.bill.mint.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * 国产 ROM 自启动设置引导。
 * Force Stop 后一切保活手段都会失效，唯一可靠方式是把 App 加入厂商白名单。
 */
object RomGuideHelper {

    data class RomEntry(val name: String, val component: String?)

    private fun detect(context: Context): RomEntry? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                RomEntry("小米 MIUI", "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity")

            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                RomEntry("华为/荣耀 EMUI", "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity")

            manufacturer.contains("oppo") || manufacturer.contains("realme") ->
                RomEntry("OPPO ColorOS", "com.coloros.safecenter/.startupapp.StartupAppListActivity")

            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
                RomEntry("vivo OriginOS", "com.vivo.permissionmanager/.activity.BgStartUpManagerActivity")

            else -> null
        }
    }

    /** 一键跳转自启动设置页，返回是否成功 */
    fun openAutostartSettings(context: Context): Boolean {
        val entry = detect(context) ?: return false
        val component = entry.component ?: return false
        val (pkg, cls) = component.split("/").let {
            if (it.size == 2) it[0] to it[1] else return false
        }
        val intent = Intent().apply {
            setClassName(pkg, cls)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                // 兜底：打开应用详情
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun isInstalled(context: Context, pkg: String): Boolean =
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}

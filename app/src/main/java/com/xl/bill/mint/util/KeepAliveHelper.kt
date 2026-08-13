package com.xl.bill.mint.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.xl.bill.mint.receiver.HeartbeatWorker
import com.xl.bill.mint.service.BillForegroundService
import com.xl.bill.mint.service.BillNotificationListenerService
import java.util.concurrent.TimeUnit

/**
 * 保活/拉活统一入口（App 不拉起，仅拉起记账服务）：
 * 1. 前台服务（常驻通知 + START_STICKY + stopWithTask=false + onTaskRemoved 重排）；
 * 2. AlarmManager 心跳（优先精确闹钟——Android 12+ 后台启动 FGS 的合法豁免通道）；
 * 3. WorkManager 周期任务（存活检查补充）；
 * 4. BootReceiver 开机拉活；
 * 5. NLS 断连自救（requestRebind + 组件 toggle 强制系统重绑）。
 *
 * 诚实边界：Android 12+ 无法 100% 保活——
 * 用户 Force Stop、开启超级省电、系统未加白名单、系统更新重置权限时，
 * 上述机制全部失效，需在 UI 上引导用户加入厂商白名单。
 */
object KeepAliveHelper {

    private const val SP_NAME = "keepalive_state"
    private const val KEY_NLS_DISCONNECTED = "nls_disconnected"
    private const val KEY_FG_ALIVE_TS = "fg_alive_ts"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun ensureRunning(context: Context) {
        startForegroundServiceSafely(context)
        rearmTimers(context)
    }

    /**
     * 只重挂计时链（闹钟 + WorkManager），不启动前台服务。
     * 供 Application.onCreate 等后台上下文调用（后台启动 FGS 必撞限制，由闹钟豁免链负责）。
     */
    fun rearmTimers(context: Context) {
        HeartbeatScheduler.scheduleNext(context)
        enqueueHeartbeatWork(context)
    }

    private fun startForegroundServiceSafely(context: Context) {
        // 进程内标记：同进程内服务活着才跳过；进程重建后必为 false → 允许尝试重启
        if (fgAlive) return
        try {
            context.startForegroundService(Intent(context, BillForegroundService::class.java))
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException / SecurityException：
            // Android 12+ 后台启动受限时的正常失败，由精确闹钟豁免链 / START_STICKY / NLS 绑定兜底
            DiagLog.log(DiagEvent.FGS_START_FAIL, e.javaClass.simpleName)
        }
    }

    private fun enqueueHeartbeatWork(context: Context) {
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "heartbeat",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    // ---------------- 前台服务存活标记 ----------------

    /** 进程内真实标记（本进程生命周期内服务是否起来过），重启判定用 */
    @Volatile
    private var fgAlive = false

    fun markForegroundAlive(context: Context) {
        fgAlive = true
        sp(context).edit().putLong(KEY_FG_ALIVE_TS, System.currentTimeMillis()).apply()
    }

    fun markForegroundDead(context: Context) {
        fgAlive = false
        sp(context).edit().putLong(KEY_FG_ALIVE_TS, 0L).apply()
    }

    /**
     * 服务是否「近期活跃」（设置页展示用）：SP 时间戳在 20 分钟内（覆盖一次心跳间隔）。
     * 进程内 bool 在进程重建后失真，SP 持久化可跨进程读。
     */
    fun isForegroundServiceRecentlyActive(context: Context): Boolean {
        if (fgAlive) return true
        val ts = sp(context).getLong(KEY_FG_ALIVE_TS, 0L)
        return ts > 0 && System.currentTimeMillis() - ts < 20 * 60_000L
    }

    // ---------------- NLS 断连自救 ----------------

    fun markNlsDisconnected(context: Context) {
        sp(context).edit().putBoolean(KEY_NLS_DISCONNECTED, true).apply()
    }

    fun markNlsConnected(context: Context) {
        sp(context).edit().putBoolean(KEY_NLS_DISCONNECTED, false).apply()
    }

    /**
     * NLS 组件 toggle 重绑：系统授权仍在但绑定被 ROM 杀掉时，强制系统重新绑定。
     *
     * 严格限定「观测到断连标记后才执行一次」——toggle 的 disable→enable 之间有
     * 瞬间通知空窗，绝不周期无差别执行；状态持久化在 SP，进程死了标记不丢。
     */
    fun rebindNotificationListenerIfNeeded(context: Context) {
        val prefs = sp(context)
        if (!prefs.getBoolean(KEY_NLS_DISCONNECTED, false)) return
        if (!PermissionChecker.isNotificationListenerEnabled(context)) {
            // 用户主动关闭了通知使用权：清标记，尊重用户
            prefs.edit().remove(KEY_NLS_DISCONNECTED).apply()
            return
        }
        runCatching {
            val cn = ComponentName(context, BillNotificationListenerService::class.java)
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            prefs.edit().putBoolean(KEY_NLS_DISCONNECTED, false).apply()
            DiagLog.log(DiagEvent.NLS_REBIND_TOGGLE)
        }
    }
}

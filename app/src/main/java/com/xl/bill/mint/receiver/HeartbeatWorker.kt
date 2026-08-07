package com.xl.bill.mint.receiver

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager 周期存活检查（补充手段，不承载核心逻辑）。
 * 进程被杀后由系统在合适时机调度本 Worker；后台启动前台服务受限时静默失败。
 */
class HeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.ensureRunning(applicationContext)
        _root_ide_package_.com.xl.bill.mint.util.HeartbeatScheduler.scheduleNext(applicationContext)
        return Result.success()
    }
}

package com.xl.bill.mint.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 小组件定时刷新：每 30 分钟触发一次小部件重渲染，
 * 兜底「进程被杀/无新账单事件」期间的展示数据保鲜。
 * 与数据写库后的事件刷新互补，构成双通道刷新闭环。
 */
class BudgetWidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        BudgetWidgetReceiver.notifyDataChanged(applicationContext)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}

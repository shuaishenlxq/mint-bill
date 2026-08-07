package com.xl.bill.mint.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 小组件周期刷新调度（WorkManager）。
 *
 * unique name 独立于心跳任务的 "heartbeat"：心跳是 15 分钟保活语义，
 * 小组件是 30 分钟展示刷新语义，两者策略互不干扰。
 */
object BudgetWidgetScheduler {

    private const val UNIQUE_NAME = "widget-refresh"
    private const val INTERVAL_MINUTES = 30L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BudgetWidgetRefreshWorker>(
            INTERVAL_MINUTES,
            TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

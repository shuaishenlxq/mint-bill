package com.xl.bill.mint.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.xl.bill.mint.widget.BudgetWidgetReceiver.Companion.notifyDataChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 小组件接收器：注册表入口，并对外提供「数据变化 → 即时刷新」的进程内调用入口。
 *
 * 刷新走进程内直接调用而非广播：小部件与 App 同进程，广播无跨进程收益，
 * 反而需要自定义 action + 额外 receiver，因此采用 [notifyDataChanged] 直调。
 */
class BudgetWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = BudgetWidget()

    companion object {

        /** 记账数据写入/变更后调用，所有已添加的小部件实例立即重新渲染 */
        fun notifyDataChanged(context: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                runCatching { BudgetWidget().updateAll(context.applicationContext) }
            }
        }
    }
}

package com.xl.bill.mint.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.xl.bill.mint.R
import java.time.LocalDate
import kotlin.math.abs

/**
 * 桌面收支小组件（薄荷清新 · 毛玻璃风格）。
 *
 * 展示逻辑：默认展示【今日】收入/支出/结余三大数字；
 * 今日无任何记录时，回退展示【自然周（周一~周日）】的收支总览。
 *
 * 视觉：毛玻璃渐变卡片（res/drawable(-night)/budget_widget_bg，圆角+描边+半透明渐变，
 * 明暗随系统切换）+ emoji 标题 + 圆角胶囊标签，呼应 App 内薄荷清新毛玻璃风。
 * 多尺寸：宽度 < 200dp → 2x2 竖排；宽度 >= 200dp → 4x2 横排三列。
 * 点击整卡 → 打开 App 首页。
 */
class BudgetWidget : GlanceAppWidget() {

    /** 跟随实际可用尺寸，尺寸变化时重新渲染（内容轻量，重建成本低） */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData()
        provideContent {
            val cardWidth = LocalSize.current.width
            val wide = cardWidth >= WIDE_MIN_WIDTH
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.budget_widget_bg))
                    .clickable(onClick = actionStartActivity<com.xl.bill.mint.MainActivity>())
            ) {
                if (wide) HorizontalLayout(data, cardWidth) else VerticalLayout(data, cardWidth)
            }
        }
    }

    // ==================== 数据 ====================

    private data class WidgetData(
        val isToday: Boolean,
        val income: Long,
        val expense: Long,
        val balance: Long
    )

    /** 今日 → 无记录回退自然周。两段均为单次小范围查询，满足 provideGlance 轻量要求 */
    private suspend fun loadData(): WidgetData {
        val repo = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.transactionRepository
        val today = LocalDate.now()

        val day = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.dayRange(today)
        val dayList = repo.getByRange(day.first, day.second)
        if (dayList.isNotEmpty()) {
            val o = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.overview(dayList, day.first, day.second)
            return WidgetData(isToday = true, income = o.income, expense = o.expense, balance = o.balance)
        }

        val week = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.weekRange(today)
        val weekList = repo.getByRange(week.first, week.second)
        val o = _root_ide_package_.com.xl.bill.mint.util.StatisticsCalculator.overview(weekList, week.first, week.second)
        return WidgetData(isToday = false, income = o.income, expense = o.expense, balance = o.balance)
    }

    // ==================== 布局：2x2 竖排 ====================

    @Composable
    private fun VerticalLayout(data: WidgetData, cardWidth: Dp) {
        val incomeText = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.signed(data.income, income = true)
        val expenseText = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.signed(data.expense, income = false)
        val balanceText = signedBalance(data.balance)
        // 收/支胶囊确定性定宽：cardWidth 减去 Column 水平 padding 与胶囊间距后对半
        val padH = 12.dp
        val gap = 6.dp
        val pillW = (cardWidth - padH * 2 - gap) / 2

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = padH, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = if (data.isToday) "🌿 今日收支" else "🌿 本周收支",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(R.color.widget_text_secondary),
                    textAlign = TextAlign.Center
                )
            )
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = balanceText,
                style = TextStyle(
                    fontSize = if (balanceText.length >= 8) 18.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(R.color.widget_balance),
                    textAlign = TextAlign.Center
                )
            )
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Pill(
                    modifier = GlanceModifier.width(pillW),
                    text = "💰 收 $incomeText",
                    colorRes = R.color.widget_income
                )
                Spacer(GlanceModifier.width(gap))
                Pill(
                    modifier = GlanceModifier.width(pillW),
                    text = "🛒 支 $expenseText",
                    colorRes = R.color.widget_expense
                )
            }
            Spacer(GlanceModifier.defaultWeight())
        }
    }

    // ==================== 布局：4x2 横排 ====================

    @Composable
    private fun HorizontalLayout(data: WidgetData, cardWidth: Dp) {
        // 三列确定性定宽：cardWidth 减去 Row 水平 padding 与两处列间距后三等分，保证对称居中
        val padH = 8.dp
        val gap = 6.dp
        val colW = (cardWidth - padH * 2 - gap * 2) / 3

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = padH, vertical = 10.dp)
        ) {
            MetricColumn(
                modifier = GlanceModifier.width(colW).fillMaxHeight(),
                label = "💰 收入",
                value = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.signed(data.income, income = true),
                colorRes = R.color.widget_income
            )
            Spacer(GlanceModifier.width(gap))
            MetricColumn(
                modifier = GlanceModifier.width(colW).fillMaxHeight(),
                label = "🛍️ 支出",
                value = _root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.signed(data.expense, income = false),
                colorRes = R.color.widget_expense
            )
            Spacer(GlanceModifier.width(gap))
            MetricColumn(
                modifier = GlanceModifier.width(colW).fillMaxHeight(),
                label = "💎 结余",
                value = signedBalance(data.balance),
                colorRes = R.color.widget_balance
            )
        }
    }

    @Composable
    private fun MetricColumn(modifier: GlanceModifier, label: String, value: String, colorRes: Int) {
        // 宽度由调用方确定性指定（width(colW)），高度填满；内容水平居中
        Column(
            modifier = modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(R.color.widget_container))
                    .cornerRadius(9.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(R.color.widget_text_secondary),
                        textAlign = TextAlign.Center
                    )
                )
            }
            Spacer(GlanceModifier.height(5.dp))
            Text(
                text = value,
                style = TextStyle(
                    fontSize = if (value.length >= 8) 15.sp else 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(colorRes),
                    textAlign = TextAlign.Center
                )
            )
            Spacer(GlanceModifier.defaultWeight())
        }
    }

    // ==================== 通用组件 ====================

    /** 圆角胶囊标签：容器色底 + 语义色文字 */
    @Composable
    private fun Pill(modifier: GlanceModifier, text: String, colorRes: Int) {
        Box(
            modifier = modifier
                .background(ColorProvider(R.color.widget_container))
                .cornerRadius(10.dp)
                .padding(vertical = 5.dp, horizontal = 2.dp)
        ) {
            Text(
                text = text,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(colorRes),
                    textAlign = TextAlign.Center
                )
            )
        }
    }

    private fun signedBalance(balance: Long): String {
        val sign = if (balance < 0) "-" else ""
        return "$sign¥${_root_ide_package_.com.xl.bill.mint.util.MoneyFormatter.yuan(abs(balance))}"
    }

    companion object {
        private val WIDE_MIN_WIDTH = 200.dp
    }
}

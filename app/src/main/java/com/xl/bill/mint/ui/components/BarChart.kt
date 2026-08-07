package com.xl.bill.mint.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint
import com.xl.bill.mint.util.StatisticsCalculator
import kotlin.math.ceil
import kotlin.math.max

/**
 * 收支柱状图（布局实现，无 Canvas 依赖）：
 * 每期两根圆角柱——支出（暖红）与收入（薄荷）。
 *
 * 支持任意数量数据点：柱宽随数据量自适应（2..12dp），
 * 标签超过 [maxLabels] 个时自动抽样，避免月视图 31 天标签重叠。
 */
@Composable
fun BarChart(
    data: List<StatisticsCalculator.MonthPoint>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 150.dp,
    maxLabels: Int = 8
) {
    if (data.isEmpty()) return
    val max = (data.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1L).coerceAtLeast(1L)
    val labelStep = max(1, ceil(data.size.toFloat() / maxLabels.coerceAtLeast(1)).toInt())
    // 抽样后的实际标签：数据点超过 maxLabels 时按 labelStep 均匀抽样，末点必显。
    // 标签行按实际标签数等分宽度，保证每个标签（如 "8/1"）有足够空间完整显示。
    val shownLabels = data.mapIndexedNotNull { index, point ->
        if (index % labelStep == 0 || index == data.lastIndex) point else null
    }

    BoxWithConstraints(modifier = modifier) {
        val slotWidth = with(LocalDensity.current) { constraints.maxWidth.toDp() } / data.size
        val barWidth = (slotWidth * 0.55f).coerceIn(2.dp, 12.dp)

        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEach { point ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val expenseH = chartHeight * (point.expense.toFloat() / max)
                        val incomeH = chartHeight * (point.income.toFloat() / max)
                        Bar(expenseH, ExpenseRose, barWidth)
                        Spacer(Modifier.width(2.dp))
                        Bar(incomeH, IncomeMint, barWidth)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                shownLabels.forEach { point ->
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Bar(height: Dp, color: Color, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height.coerceAtLeast(2.dp))
            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
            .background(
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.45f))
                )
            )
    )
}

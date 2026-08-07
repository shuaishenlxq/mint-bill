package com.xl.bill.mint.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 折线趋势图（Compose Canvas 自绘）：平滑曲线 + 数据点 + 底部月份标签。
 */
@Composable
fun LineChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF4ECDC4),
    chartHeight: androidx.compose.ui.unit.Dp = 150.dp
) {
    if (values.isEmpty() || values.size != labels.size) return
    val max = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            if (values.size >= 2) {
                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = size.width * i / (values.size - 1)
                    val y = size.height * (1f - v / max)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.cornerPathEffect(10.dp.toPx())
                    )
                )
                values.forEachIndexed { i, v ->
                    val x = size.width * i / (values.size - 1)
                    val y = size.height * (1f - v / max)
                    drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(x, y))
                    drawCircle(color = color, radius = 3.5.dp.toPx(), center = Offset(x, y))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

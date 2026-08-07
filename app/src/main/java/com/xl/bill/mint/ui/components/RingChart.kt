package com.xl.bill.mint.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 环形占比图（Compose Canvas 自绘，零依赖）。
 * segments: 颜色 + 占比(0..1)，占比和 ≤ 1，剩余部分显示为轨道色。
 */
@Composable
fun RingChart(
    segments: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 18.dp,
    trackColor: Color = Color(0x33FFFFFF),
    centerContent: @Composable () -> Unit = {}
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            var start = -90f
            segments.forEach { (color, fraction) ->
                if (fraction > 0.001f) {
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = fraction * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                start += fraction * 360f
            }
        }
        centerContent()
    }
}

// 便捷占位：供预览/布局测量使用
@Composable
fun RingChartPreview(modifier: Modifier = Modifier) {
    RingChart(
        segments = listOf(Color(0xFFFF8A9E) to 0.62f, Color(0xFF4ECDC4) to 0.38f),
        modifier = modifier.size(120.dp)
    ) {
        Box(Modifier.size(1.dp))
    }
}

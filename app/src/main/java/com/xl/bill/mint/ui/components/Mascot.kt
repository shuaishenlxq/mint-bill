package com.xl.bill.mint.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 小薄荷吉祥物：嫩芽 + 圆脸 + 腮红 + 微笑。
 * 纯 Canvas 绘制，任何界面/明暗主题下都可用，是"可爱"风格的核心元素。
 */
@Composable
fun Mascot(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    faceColor: Color = Color.White,
    leafColor: Color = Color(0xFF2BA89E),
    eyeColor: Color = Color(0xFF1C3A38),
    blushColor: Color = Color(0xFFFFC7D3)
) {
    Canvas(modifier = modifier) {
        val s = size.toPx()
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        // 嫩芽（两片叶子 + 茎）
        val leafPath = Path().apply {
            moveTo(cx - s * 0.14f, cy - s * 0.30f)
            cubicTo(cx - s * 0.30f, cy - s * 0.42f, cx - s * 0.10f, cy - s * 0.50f, cx + s * 0.02f, cy - s * 0.38f)
            cubicTo(cx + s * 0.10f, cy - s * 0.30f, cx + s * 0.02f, cy - s * 0.22f, cx - s * 0.02f, cy - s * 0.26f)
            close()
        }
        drawPath(leafPath, color = leafColor)

        // 脸
        drawCircle(
            color = faceColor,
            radius = s * 0.42f,
            center = Offset(cx, cy + s * 0.06f)
        )

        // 腮红
        drawCircle(color = blushColor, radius = s * 0.09f, center = Offset(cx - s * 0.24f, cy + s * 0.18f))
        drawCircle(color = blushColor, radius = s * 0.09f, center = Offset(cx + s * 0.24f, cy + s * 0.18f))

        // 眼睛
        drawCircle(color = eyeColor, radius = s * 0.055f, center = Offset(cx - s * 0.13f, cy + s * 0.02f))
        drawCircle(color = eyeColor, radius = s * 0.055f, center = Offset(cx + s * 0.13f, cy + s * 0.02f))

        // 微笑
        val smilePath = Path().apply {
            moveTo(cx - s * 0.16f, cy + s * 0.14f)
            quadraticBezierTo(cx, cy + s * 0.26f, cx + s * 0.16f, cy + s * 0.14f)
        }
        drawPath(
            path = smilePath,
            color = eyeColor,
            style = Stroke(width = s * 0.045f, cap = StrokeCap.Round)
        )
    }
}

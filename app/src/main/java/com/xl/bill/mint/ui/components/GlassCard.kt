package com.xl.bill.mint.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.ui.theme.GlassHighlight
import com.xl.bill.mint.ui.theme.GlassStrokeDark
import com.xl.bill.mint.ui.theme.GlassStrokeLight
import com.xl.bill.mint.ui.theme.ShadowSoft

/**
 * 毛玻璃（模拟实现）卡片。
 *
 * 说明：Compose 的 Modifier.blur 只能模糊自身内容、无法模糊其后的背景，
 * 因此采用业界通用的"模拟玻璃"方案：半透明表面 + 顶部高光渐变 +
 * 1dp 白色描边 + 柔和阴影，在浅色/深色主题下均呈现通透的玻璃质感。
 * MVP 不引入 backdrop blur（RenderEffect）以控制复杂度与兼容性。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val strokeColor = if (isDark) GlassStrokeDark else GlassStrokeLight

    val shape = RoundedCornerShape(cornerRadius)
    val base = Modifier
        .shadow(10.dp, shape, ambientColor = ShadowSoft, spotColor = ShadowSoft)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0f to surface.copy(alpha = 0.78f),
                0.5f to surface.copy(alpha = 0.62f),
                1f to surface.copy(alpha = 0.55f)
            )
        )
        .border(1.dp, strokeColor, shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to GlassHighlight.copy(alpha = 0.5f),
                        0.35f to Color.Transparent
                    )
                )
        )
        Column(modifier = base.fillMaxWidth()) { content() }
    }
}

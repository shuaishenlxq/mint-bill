package com.xl.bill.mint.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
 * 柔和凸起 surface（方圆 · 新拟物微立体手法，与 [GlassCard] 同语言）。
 *
 * 复刻 GlassCard 的「柔和阴影 + 半透明渐变 + 细描边 + 顶部高光」，
 * 用于小面积 pill / 卡片，营造轻凸起立体感。
 * 展开/按压态通过降低阴影近似「内凹」（Compose 1.7 无 innerShadow，`shadow` 无 offset）。
 *
 * 尺寸完全由内容与调用方 [modifier] 决定（不强制 fillMaxWidth/fillMaxSize，
 * 保证在 Row/FlowRow 内按内容宽度排布）；需要撑满时由调用方传 fillMaxWidth。
 *
 * @param pressed 外部控制内凹态（如下拉展开时）
 * @param onClick 非 null 时带 ripple 与瞬时按压内凹；null 为不可交互的纯展示容器
 */
@Composable
fun SoftSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 6.dp,
    pressed: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val strokeColor = if (isDark) GlassStrokeDark else GlassStrokeLight

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val effectivePressed = pressed || isPressed
    val effectiveElevation = if (effectivePressed) {
        (elevation * 0.25f).coerceAtLeast(1.dp)
    } else {
        elevation
    }

    val shape = RoundedCornerShape(cornerRadius)
    val base = Modifier
        .shadow(effectiveElevation, shape, ambientColor = ShadowSoft, spotColor = ShadowSoft)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0f to surface.copy(alpha = 0.95f),
                0.5f to surface.copy(alpha = 0.82f),
                1f to surface.copy(alpha = 0.72f)
            )
        )
        .drawWithContent {
            drawContent()
            // 顶部高光叠加在内容之上（clip 已约束圆角形状；尺寸不受影响）
            drawRect(
                brush = Brush.verticalGradient(
                    0f to GlassHighlight.copy(alpha = 0.4f),
                    0.3f to Color.Transparent
                )
            )
        }
        .border(1.dp, strokeColor, shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Box(modifier = modifier.then(base)) { content() }
}

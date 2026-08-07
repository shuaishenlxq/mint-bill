package com.xl.bill.mint.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆润规范：卡片 24dp、按钮 16–20dp、对话框/弹窗 28dp。
 * 全部使用大圆角塑造可爱治愈的视觉基调。
 */
val MintShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

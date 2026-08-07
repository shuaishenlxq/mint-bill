package com.xl.bill.mint.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.ui.filter.SelectOption
import com.xl.bill.mint.ui.theme.GlassStrokeDark
import com.xl.bill.mint.ui.theme.GlassStrokeLight

/**
 * 通用筛选下拉 pill（方圆 · 新拟物微立体）：
 * [SoftSurface] 凸起 pill + 展开态内凹 + 箭头旋转 + 定制 [DropdownMenu]
 * （圆角 surface 容器 + 柔和阴影 + 细描边 + 选中项薄荷高亮 + Check 图标）。
 */
@Composable
fun <T> FilterDropdown(
    label: String,
    options: List<SelectOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "filterDropdownArrow"
    )
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val strokeColor = if (isDark) GlassStrokeDark else GlassStrokeLight

    Box(modifier) {
        SoftSurface(
            cornerRadius = 16.dp,
            elevation = 6.dp,
            pressed = expanded,
            onClick = { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, strokeColor)
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    }
                )
            }
        }
    }
}

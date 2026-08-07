package com.xl.bill.mint.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.R
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint
import com.xl.bill.mint.util.MoneyFormatter
import com.xl.bill.mint.util.StatisticsCalculator
import java.util.Locale

/**
 * 顶部总览毛玻璃卡：本月支出/收入/结余 + 收支占比环形图 + 小薄荷。
 */
@Composable
fun OverviewCard(
    overview: StatisticsCalculator.MonthOverview,
    monthLabel: String,
    modifier: Modifier = Modifier
) {
    val total = (overview.income + overview.expense).toFloat().coerceAtLeast(1f)
    val expensePct = overview.expense.toFloat() / total
    val incomePct = overview.income.toFloat() / total

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Mascot(modifier = Modifier.size(30.dp))
                    Text(
                        text = monthLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.month_expense),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedMoney(overview.expense.toFloat(), style = MaterialTheme.typography.displaySmall, color = ExpenseRose)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.month_income),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "¥" + MoneyFormatter.yuan(overview.income),
                        style = MaterialTheme.typography.bodySmall,
                        color = IncomeMint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.month_balance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "¥" + MoneyFormatter.yuan(overview.balance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(12.dp))
            RingChart(
                segments = listOf(ExpenseRose to expensePct, IncomeMint to incomePct),
                modifier = Modifier.size(110.dp),
                strokeWidth = 16.dp,
                trackColor = Color(0x26FFFFFF)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.month_balance),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = MoneyFormatter.yuan(overview.balance),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

/** 金额数字滚动动画 */
@Composable
private fun AnimatedMoney(
    targetFen: Float,
    style: TextStyle,
    color: Color
) {
    val animated by animateFloatAsState(
        targetValue = targetFen,
        animationSpec = tween(durationMillis = 600),
        label = "money"
    )
    val yuanText = if (animated % 100f == 0f) {
        String.format(Locale.US, "%.0f", animated / 100f)
    } else {
        String.format(Locale.US, "%.2f", animated / 100f)
    }
    Text(text = "¥$yuanText", style = style, color = color)
}

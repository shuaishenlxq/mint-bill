package com.xl.bill.mint.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xl.bill.mint.R
import com.xl.bill.mint.data.db.CategoryEntity
import com.xl.bill.mint.data.db.TransactionEntity
import com.xl.bill.mint.ui.theme.ExpenseRose
import com.xl.bill.mint.ui.theme.IncomeMint
import com.xl.bill.mint.util.MoneyFormatter
import com.xl.bill.mint.util.TimeUtil

/**
 * 账单卡片：分类 emoji + 商户/备注 + 渠道时间 + 金额（收入薄荷/支出暖红）。
 * 自动记录且未填备注时，金额下方显示「补备注」角标。
 */
@Composable
fun BillCard(
    tx: TransactionEntity,
    category: CategoryEntity?,
    onClick: () -> Unit
) {
    val isIncome = tx.type == TransactionEntity.TYPE_INCOME
    val needsNote = tx.channel != "manual" && tx.note.isNullOrBlank()
    val title = tx.merchant?.takeIf { it.isNotBlank() }
        ?: tx.note?.takeIf { it.isNotBlank() }
        ?: category?.name
        ?: "账单"
    // 备注行：note 非空且与标题（商户名兜底）不重复时展示，避免同文本出现两遍
    val noteText = tx.note?.trim().orEmpty()
    val showNote = noteText.isNotEmpty() && noteText != title

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category?.icon ?: "🏷️",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showNote) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = noteText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    text = "${TimeUtil.channelDisplay(tx.channel)} · ${TimeUtil.format(tx.occurredAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = MoneyFormatter.signed(tx.amount, isIncome),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isIncome) IncomeMint else ExpenseRose
                )
                if (needsNote) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.bill_needs_note_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

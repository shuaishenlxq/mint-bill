package com.xl.bill.mint.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账单流水。
 * amount 统一以「分」(Long) 存储，避免浮点误差。
 * type: 0=支出 1=收入
 * notificationKey：防重复记账的唯一键（通知类 = pkg|通知key，无障碍类 = acc-...，手动 = manual-...）
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["notificationKey"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["type"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val channel: String,           // alipay / wechat / bank / manual
    val rawTitle: String? = null,
    val rawText: String? = null,
    val amount: Long,
    val type: Int,
    val categoryId: Long,
    val accountId: Long,
    val merchant: String? = null,
    val occurredAt: Long,
    val notificationKey: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1
    }
}

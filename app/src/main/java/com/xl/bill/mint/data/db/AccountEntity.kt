package com.xl.bill.mint.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记账渠道（账户）。packageName 为对应支付 App 包名，手动记账为 null。
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val packageName: String? = null
)

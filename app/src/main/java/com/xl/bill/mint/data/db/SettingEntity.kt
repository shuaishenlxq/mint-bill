package com.xl.bill.mint.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 键值设置表。当前用于各记账渠道（支付宝/微信/银行）的开关状态。
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

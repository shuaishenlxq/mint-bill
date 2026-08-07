package com.xl.bill.mint.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时间展示工具。
 */
object TimeUtil {

    private val shortFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private val dayFmt = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())

    fun format(millis: Long): String = shortFmt.format(Date(millis))

    fun formatDay(millis: Long): String = dayFmt.format(Date(millis))

    fun channelDisplay(channel: String): String = when (channel) {
        "alipay" -> "支付宝"
        "wechat" -> "微信"
        "bank" -> "银行卡"
        "sms" -> "短信"
        else -> "手动记账"
    }
}

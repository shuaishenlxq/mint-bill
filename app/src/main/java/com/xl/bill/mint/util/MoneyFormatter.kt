package com.xl.bill.mint.util

import java.util.Locale

/**
 * 金额工具：内部统一使用「分」(Long) 存储，展示时转为「元」。
 */
object MoneyFormatter {

    /** 分 → 元字符串，去掉多余的 .00 */
    fun yuan(fen: Long): String {
        val yuan = fen / 100.0
        return if (fen % 100 == 0L) {
            String.format(Locale.US, "%.0f", yuan)
        } else {
            String.format(Locale.US, "%.2f", yuan)
        }
    }

    /** 带符号展示：支出 "-¥xx" / 收入 "+¥xx"（中式记账：支出暖红、收入正向） */
    fun signed(fen: Long, income: Boolean): String {
        val sign = if (income) "+" else "-"
        return "$sign¥${yuan(fen)}"
    }

    /** 输入「元」字符串 → 分，非法返回 null */
    fun fenFromYuanInput(input: String): Long? {
        val t = input.trim().replace("¥", "").replace("￥", "").replace(",", "")
        if (t.isEmpty()) return null
        val v = t.toDoubleOrNull() ?: return null
        if (v <= 0.0 || v > 100_000_000.0) return null
        return (v * 100).toLong()
    }
}

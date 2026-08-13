package com.xl.bill.mint.util

/**
 * 环形日志缓冲（纯 Kotlin，JVM 可单测）。
 *
 * 语义：追加行；内容超过容量时从头部截断，仅保留末尾 [keepChars] 个字符，
 * 截断点对齐到换行边界，保证保留部分的首行是完整行。
 * 容量按「字符数」近似（UTF-8 中文 1 字符 ≈ 3 字节），诊断日志场景足够。
 */
class RingLogBuffer(
    private val capacityChars: Int,
    private val keepChars: Int = capacityChars / 2
) {

    private val sb = StringBuilder()

    @Synchronized
    fun append(line: String) {
        sb.append(line).append('\n')
        if (sb.length > capacityChars) {
            val trimmed = trim(sb.toString(), keepChars)
            sb.setLength(0)
            sb.append(trimmed)
        }
    }

    @Synchronized
    fun content(): String = sb.toString()

    @Synchronized
    fun clear() {
        sb.setLength(0)
    }

    companion object {
        /**
         * 从头部截断 [content]，保留末尾至多 [keepChars] 个字符且对齐行边界。
         * 内容本就不超限时原样返回。
         */
        fun trim(content: String, keepChars: Int): String {
            if (content.length <= keepChars) return content
            var start = content.length - keepChars
            val nl = content.indexOf('\n', start)
            if (nl >= 0 && nl + 1 < content.length) start = nl + 1
            return content.substring(start)
        }
    }
}

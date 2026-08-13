package com.example.bill.util

import com.xl.bill.mint.util.RingLogBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 环形日志缓冲 JVM 单测：追加、超容量头部截断（保留末尾 + 行边界对齐）、清空。 */
class RingLogBufferTest {

    @Test
    fun appendBelowCapacityKeepsAllLines() {
        val buf = RingLogBuffer(capacityChars = 1024, keepChars = 512)
        buf.append("aaa")
        buf.append("bbb")
        assertEquals("aaa\nbbb\n", buf.content())
    }

    @Test
    fun overCapacityTrimsHeadKeepsTailAligned() {
        // 容量 50、保留 25：每行 10 字符（9+换行），追加 10 行必然多次触发截断
        val buf = RingLogBuffer(capacityChars = 50, keepChars = 25)
        val lines = (1..10).map { "L%08d".format(it) } // L00000001 ... 每行 9 字符
        lines.forEach { buf.append(it) }

        val content = buf.content()
        // 末尾最后一行必须在
        assertTrue(content.endsWith("L00000010\n"))
        // 截断后长度不超过 keepChars + 一行（截断对齐到行边界，保留部分 ≤ keepChars）
        assertTrue("截断后长度应 <= 25+10，实际 ${content.length}", content.length <= 25 + 10)
        // 首行必须是完整行（行边界对齐，不出现半行）
        val firstLine = content.substringBefore('\n')
        assertTrue("首行必须是完整行，实际=$firstLine", lines.contains(firstLine))
    }

    @Test
    fun trimStaticBelowKeepReturnsOriginal() {
        val s = "abc\ndef\n"
        assertEquals(s, RingLogBuffer.trim(s, 100))
    }

    @Test
    fun trimStaticAlignsToNewline() {
        // 保留 4 个字符：从 "def\n" 前截断，对齐后应为 "def\n"
        val s = "abc\ndef\n"
        assertEquals("def\n", RingLogBuffer.trim(s, 4))
    }

    @Test
    fun trimStaticSingleLongLineKeepsExactTail() {
        val s = "0123456789" // 无换行
        assertEquals("6789", RingLogBuffer.trim(s, 4))
    }

    @Test
    fun clearEmptiesBuffer() {
        val buf = RingLogBuffer(capacityChars = 100)
        buf.append("abc")
        buf.clear()
        assertEquals("", buf.content())
    }
}

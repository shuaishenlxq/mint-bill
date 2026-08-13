package com.example.bill.parser

import com.xl.bill.mint.parser.ImportDuplicateDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 导入疑似重复判定（ImportDuplicateDetector）JVM 单测：分钟级时间窗 + 金额/方向由调用方预筛。 */
class ImportDuplicateDetectorTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun sameMinuteSameAmountSuspected() {
        assertTrue(ImportDuplicateDetector.isSuspected(t0, listOf(t0 + 10_000)))
    }

    @Test
    fun within60sToleranceSuspected() {
        // 边界：差 60s 内 → 疑似
        assertTrue(ImportDuplicateDetector.isSuspected(t0, listOf(t0 + 60_000)))
        assertTrue(ImportDuplicateDetector.isSuspected(t0, listOf(t0 - 60_000)))
    }

    @Test
    fun beyond60sNotSuspected() {
        assertFalse(ImportDuplicateDetector.isSuspected(t0, listOf(t0 + 61_000)))
        assertFalse(ImportDuplicateDetector.isSuspected(t0, listOf(t0 - 61_000)))
    }

    @Test
    fun noCandidatesNotSuspected() {
        assertFalse(ImportDuplicateDetector.isSuspected(t0, emptyList()))
    }

    @Test
    fun anyCandidateMatches() {
        assertTrue(ImportDuplicateDetector.isSuspected(t0, listOf(t0 - 120_000, t0 + 30_000)))
    }
}

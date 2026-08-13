package com.xl.bill.mint.parser

import kotlin.math.abs

/**
 * 导入疑似重复判定（纯 Kotlin，JVM 可单测）。
 *
 * 去重指标：**时间（分钟级）+ 金额 + 方向**——同一笔若先被自动记账、
 * 再从账单文件导入，两条记录的时间/金额/方向一致（key 体系不同：sbn.key vs pdf-*）。
 * 仅做「标记」，不自动删除：由用户决定保留 app 记录还是导入文件的记录。
 */
object ImportDuplicateDetector {

    /** 分钟级容差：±60s 覆盖同一分钟（与自动记账 KeyDeduper 的 60s 容差一致） */
    const val WINDOW_MS = 60_000L

    /**
     * @param rowOccurredAt 导入行时间（毫秒）
     * @param candidateTimes 已按 同金额+同方向+±60s 预筛的既有记录时间列表
     * @return true = 与既有记录可能重复
     */
    fun isSuspected(rowOccurredAt: Long, candidateTimes: List<Long>): Boolean =
        candidateTimes.any { abs(it - rowOccurredAt) <= WINDOW_MS }
}

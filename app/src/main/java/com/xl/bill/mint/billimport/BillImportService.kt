package com.xl.bill.mint.billimport

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.xl.bill.mint.data.db.TransactionEntity
import com.xl.bill.mint.di.ServiceLocator
import com.xl.bill.mint.parser.AlipayCsvParser
import com.xl.bill.mint.parser.BillImportRow
import com.xl.bill.mint.parser.BillParseResult
import com.xl.bill.mint.parser.Channel
import com.xl.bill.mint.parser.ExcelBillParser

/**
 * 重复记录处理策略（确认导入时由用户选择）：
 * - [SKIP]：忽略已存在的记录，仅导入新记录；
 * - [REPLACE]：覆盖原记录——先按 notificationKey 删除旧行，再整体重新插入。
 */
enum class DuplicatePolicy { SKIP, REPLACE }

/**
 * 账单导入编排（Android 绑定层）：解析预览与确认入库。支持微信 Excel 与支付宝 CSV 双来源。
 *
 * - [analyze]：读微信 xlsx → 还原单元格矩阵 → 解析，产出预览行（不落库）；
 * - [analyzeAlipay]：读支付宝 CSV（GB18030 编码）→ 还原单元格矩阵 → 解析；
 * - [detectDuplicates]：检测预览行中已存在的去重键（重复导入询问前置）；
 * - [commit]：预览确认后批量入库（走 repository 直接插入，不经过自动记账 pipeline，
 *   不受自动记账开关/120s 内存指纹影响），重复记录按 [DuplicatePolicy] 覆盖或跳过。
 */
class BillImportService(private val context: Context) {

    private companion object {
        const val TAG = "BillImport"

        /** 渠道字段落库值（与 TimeUtil.channelDisplay 规范一致） */
        fun channelDbValue(channel: Channel): String = when (channel) {
            Channel.ALIPAY -> "alipay"
            else -> "wechat"
        }

        /** 支付包名 → 账户（找不到回退「银行卡」） */
        fun accountPkg(channel: Channel): String = when (channel) {
            Channel.ALIPAY -> "com.eg.android.AlipayGphone"
            else -> "com.tencent.mm"
        }
    }

    /** 导入结果统计 */
    data class BillCommitResult(val inserted: Int, val skipped: Int)

    /** 微信 Excel：仅解析，不落库（预览用） */
    suspend fun analyze(uri: Uri): BillParseResult = try {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BillImportException("无法打开所选文件")
        input.use { stream ->
            val matrix = XlsxWorkbookReader.readSheetCells(stream)
            ExcelBillParser.parse(matrix)
        }
    } catch (e: BillImportException) {
        throw e
    } catch (e: SecurityException) {
        throw BillImportException("无法读取所选文件（权限受限）", e)
    } catch (e: Exception) {
        // 诊断日志：完整堆栈便于真机排障（用户看到的文案不受影响）
        Log.w(TAG, "Excel import analyze failed: $uri", e)
        throw BillImportException("Excel 解析失败，请确认文件为微信导出的账单 Excel", e)
    }

    /** 支付宝 CSV：仅解析，不落库（预览用） */
    suspend fun analyzeAlipay(uri: Uri): BillParseResult = try {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BillImportException("无法打开所选文件")
        input.use { stream ->
            val matrix = CsvReader.parse(stream)
            AlipayCsvParser.parse(matrix)
        }
    } catch (e: BillImportException) {
        throw e
    } catch (e: SecurityException) {
        throw BillImportException("无法读取所选文件（权限受限）", e)
    } catch (e: Exception) {
        Log.w(TAG, "Alipay CSV import analyze failed: $uri", e)
        throw BillImportException("CSV 解析失败，请确认文件为支付宝导出的账单 CSV", e)
    }

    /** 检测预览行中已存在的去重键（供「确认导入」前询问用户覆盖/忽略） */
    suspend fun detectDuplicates(rows: List<BillImportRow>): Set<String> {
        if (rows.isEmpty()) return emptySet()
        return ServiceLocator.transactionRepository.existingNotificationKeys(rows.map { it.notificationKey })
    }

    /**
     * 确认后批量入库，返回新增/跳过统计；channel 区分来源（默认微信）。
     * 重复记录按 [DuplicatePolicy] 处理：REPLACE = 删除旧行后整体重插；SKIP = 仅插新记录。
     *
     * @param existingKeys 已存在的去重键集合（detectDuplicates 结果复用，避免二次查库）；null 时自查。
     */
    suspend fun commit(
        rows: List<BillImportRow>,
        channel: Channel = Channel.WECHAT,
        policy: DuplicatePolicy = DuplicatePolicy.SKIP,
        existingKeys: Set<String>? = null
    ): BillCommitResult {
        if (rows.isEmpty()) return BillCommitResult(0, 0)

        val repo = ServiceLocator.transactionRepository
        val accountId = repo.resolveAccountId(accountPkg(channel))

        val existing = existingKeys ?: repo.existingNotificationKeys(rows.map { it.notificationKey })
        val dupRows = rows.filter { it.notificationKey in existing }
        val newRows = rows - dupRows
        val toInsertRows = if (policy == DuplicatePolicy.REPLACE) rows else newRows

        // 分类匹配（CPU 热点，放事务外）：批量预计算 + 同文本短路
        val parsedList = toInsertRows.map { it.toParsedBill(channel) }
        val defaults = ServiceLocator.settingsRepository.getCategoryDefaults()
        val categoryIds = ServiceLocator.categoryMatcher.resolveBatch(
            parsedList.map { it.type to it.rawText.orEmpty() },
            defaults
        )
        val toInsert = toInsertRows.zip(parsedList).mapIndexed { i, (row, parsed) ->
            TransactionEntity(
                channel = channelDbValue(channel),
                rawTitle = null,
                rawText = parsed.rawText,
                amount = parsed.amount,
                type = parsed.type,
                categoryId = categoryIds[i],
                accountId = accountId,
                merchant = parsed.merchant,
                occurredAt = parsed.occurredAt,
                notificationKey = parsed.notificationKey,
                note = row.note,
                createdAt = System.currentTimeMillis()
            )
        }

        // 单事务：REPLACE 先删旧再整体重插（原子，避免双写事务双 fsync/双全表刷新）
        val inserted = ServiceLocator.appDatabase.withTransaction {
            if (policy == DuplicatePolicy.REPLACE && dupRows.isNotEmpty()) {
                repo.deleteByNotificationKeys(dupRows.map { it.notificationKey })
            }
            repo.insertAllQuiet(toInsert)
        }
        // 事务提交后再刷新小组件（事务内广播会读到旧数据）
        if (inserted > 0) {
            repo.notifyWidgetDataChanged()
            // 首次导入成功 → 自动记录存款净结余起始日（幂等，先到先得）
            ServiceLocator.settingsRepository.ensureSavingsBaseTime(System.currentTimeMillis())
        }
        return BillCommitResult(inserted = inserted, skipped = rows.size - inserted)
    }
}

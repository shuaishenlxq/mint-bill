package com.xl.bill.mint.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xl.bill.mint.billimport.BillImportException
import com.xl.bill.mint.billimport.DuplicatePolicy
import com.xl.bill.mint.di.ServiceLocator
import com.xl.bill.mint.parser.BillImportRow
import com.xl.bill.mint.parser.Channel
import com.xl.bill.mint.parser.ImportDuplicateDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 账单导入状态机 */
sealed interface BillImportUiState {
    data object Idle : BillImportUiState
    /** 解析快，单阶段无进度 */
    data object Parsing : BillImportUiState
    /** suspiciousKeys：与已自动记账记录「时间分钟级+金额+方向」相近的疑似重复行（跨来源） */
    data class Preview(
        val rows: List<BillImportRow>,
        val unrecognizedCount: Int,
        val suspiciousKeys: Set<String> = emptySet()
    ) : BillImportUiState
    /** 检测到重复记录，等待用户选择覆盖/忽略（rows 暂存于 pendingRows） */
    data class ConfirmDuplicate(val dupCount: Int, val newCount: Int) : BillImportUiState
    /** 写入中（单事务不可分块，UI 用不确定进度遮罩） */
    data object Importing : BillImportUiState
    data class Success(val inserted: Int, val skipped: Int) : BillImportUiState
    data class Error(val message: String) : BillImportUiState
}

class BillImportViewModel(
    /** 账单来源：WECHAT = 微信 Excel，ALIPAY = 支付宝 CSV */
    private val source: Channel = Channel.WECHAT
) : ViewModel() {

    private val _state = MutableStateFlow<BillImportUiState>(BillImportUiState.Idle)
    val state: StateFlow<BillImportUiState> = _state.asStateFlow()

    private var activeJob: Job? = null

    /** ConfirmDuplicate 期间暂存预览行，用户选择覆盖/忽略后用于 commit */
    private var pendingRows: List<BillImportRow>? = null

    /** 查重结果缓存：confirmImport 检测出的已存在 key 集合，重复确认后传给 commit 复用（避免二次查库） */
    private var pendingDupKeys: Set<String>? = null

    /** 是否跳过「可能重复」行（与已自动记账记录时间/金额/方向相近），默认开 */
    private val _skipSuspicious = MutableStateFlow(true)
    val skipSuspicious: StateFlow<Boolean> = _skipSuspicious.asStateFlow()

    fun setSkipSuspicious(enabled: Boolean) {
        _skipSuspicious.value = enabled
    }

    /** 解析所选文件（按来源分流）→ Preview（或 Error） */
    fun analyze(uri: Uri) {
        activeJob?.cancel()
        val service = ServiceLocator.billImportService
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            _state.value = BillImportUiState.Parsing
            try {
                val result = if (source == Channel.ALIPAY) service.analyzeAlipay(uri) else service.analyze(uri)
                val rows = result.rows
                if (rows.isEmpty()) {
                    _state.value = BillImportUiState.Error(emptyMessage)
                } else {
                    val suspiciousKeys = detectSuspicious(rows)
                    _state.value = BillImportUiState.Preview(rows, result.unrecognizedCount, suspiciousKeys)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BillImportException) {
                _state.value = BillImportUiState.Error(e.message ?: failMessage)
            } catch (e: Exception) {
                _state.value = BillImportUiState.Error(e.message ?: failMessage)
            }
        }
    }

    /**
     * 疑似重复检测（跨来源）：导入行按 (金额, 方向) 分组，每组查既有自动记账记录
     * （source != 'import'）[min-60s, max+60s] 窗口 → 时间分钟级匹配的行标记为可能重复。
     */
    private suspend fun detectSuspicious(rows: List<BillImportRow>): Set<String> {
        val repo = ServiceLocator.transactionRepository
        val suspicious = mutableSetOf<String>()
        rows.groupBy { it.amountFen to it.type }.forEach { (key, group) ->
            val fromTs = group.minOf { it.occurredAt } - ImportDuplicateDetector.WINDOW_MS
            val toTs = group.maxOf { it.occurredAt } + ImportDuplicateDetector.WINDOW_MS
            val candidates = repo.findSuspectedDuplicateCandidates(key.first, key.second, fromTs, toTs)
            if (candidates.isEmpty()) return@forEach
            val candidateTimes = candidates.map { it.occurredAt }
            group.forEach { row ->
                if (ImportDuplicateDetector.isSuspected(row.occurredAt, candidateTimes)) {
                    suspicious += row.notificationKey
                }
            }
        }
        return suspicious
    }

    /** 预览中删除一条（按 notificationKey 过滤） */
    fun deleteRow(key: String) {
        val cur = _state.value as? BillImportUiState.Preview ?: return
        _state.value = cur.copy(rows = cur.rows.filter { it.notificationKey != key })
    }

    /**
     * 确认导入：先按「跳过可能重复」开关过滤疑似行 → 再检测精确重复（key）→
     * 无重复直接导入；有重复进入 [BillImportUiState.ConfirmDuplicate] 等待用户选择覆盖/忽略。
     */
    fun confirmImport() {
        if (activeJob?.isActive == true) return // 查重阶段 state 仍是 Preview，防连点重入
        val cur = _state.value as? BillImportUiState.Preview ?: return
        if (cur.rows.isEmpty()) return
        val totalRows = cur.rows
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 疑似重复行：按开关过滤（默认跳过，保留已自动记账的那条）
                val toImport = if (_skipSuspicious.value && cur.suspiciousKeys.isNotEmpty()) {
                    totalRows.filter { it.notificationKey !in cur.suspiciousKeys }
                } else {
                    totalRows
                }
                if (toImport.isEmpty()) {
                    // 全部被疑似重复跳过
                    _state.value = BillImportUiState.Success(0, totalRows.size)
                    return@launch
                }
                val dupKeys = ServiceLocator.billImportService.detectDuplicates(toImport)
                pendingDupKeys = dupKeys
                if (dupKeys.isEmpty()) {
                    executeCommit(toImport, totalRows.size, DuplicatePolicy.SKIP, dupKeys)
                } else {
                    pendingRows = toImport
                    _state.value = BillImportUiState.ConfirmDuplicate(
                        dupCount = dupKeys.size,
                        newCount = toImport.size - dupKeys.size
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = BillImportUiState.Error(e.message ?: "导入失败，请重试")
            }
        }
    }

    /** 用户在重复确认弹窗中选择处理策略（覆盖原记录 / 忽略跳过） */
    fun resolveDuplicate(policy: DuplicatePolicy) {
        if (activeJob?.isActive == true) return
        val rows = pendingRows ?: return
        val dupKeys = pendingDupKeys
        pendingRows = null
        pendingDupKeys = null
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            executeCommit(rows, rows.size, policy, dupKeys)
        }
    }

    /** 执行入库 → Importing → Success/Error；skipped = 全量行 - 实际插入（含疑似跳过 + key 精确重复跳过） */
    private suspend fun executeCommit(
        rows: List<BillImportRow>,
        totalRows: Int,
        policy: DuplicatePolicy,
        knownExisting: Set<String>?
    ) {
        _state.value = BillImportUiState.Importing
        try {
            val result = ServiceLocator.billImportService.commit(rows, source, policy, knownExisting)
            pendingRows = null
            pendingDupKeys = null
            _state.value = BillImportUiState.Success(result.inserted, totalRows - result.inserted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pendingRows = null
            pendingDupKeys = null
            _state.value = BillImportUiState.Error(e.message ?: "导入失败，请重试")
        }
    }

    /** 取消任务 → Idle（弹窗关闭时调用） */
    fun reset() {
        activeJob?.cancel()
        activeJob = null
        pendingRows = null
        pendingDupKeys = null
        _state.value = BillImportUiState.Idle
    }

    private val emptyMessage: String
        get() = if (source == Channel.ALIPAY) "未识别到支付宝账单记录，请确认所选文件为支付宝导出的账单 CSV"
        else "未识别到微信账单记录，请确认所选文件为微信导出的账单 Excel"

    private val failMessage: String
        get() = if (source == Channel.ALIPAY) "CSV 解析失败" else "Excel 解析失败"

    override fun onCleared() {
        activeJob?.cancel()
        super.onCleared()
    }

    companion object {
        /** 按来源构造（Sheet 弹窗 viewModel(factory) 传入） */
        fun factory(source: Channel): ViewModelProvider.Factory = viewModelFactory {
            initializer { BillImportViewModel(source) }
        }
    }
}

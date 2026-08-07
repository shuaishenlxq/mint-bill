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
    data class Preview(val rows: List<BillImportRow>, val unrecognizedCount: Int) : BillImportUiState
    /** 检测到重复记录，等待用户选择覆盖/忽略（rows 暂存于 pendingRows） */
    data class ConfirmDuplicate(val dupCount: Int, val newCount: Int) : BillImportUiState
    data class Importing(val done: Int, val total: Int) : BillImportUiState
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
                    _state.value = BillImportUiState.Preview(rows, result.unrecognizedCount)
                }
            } catch (e: BillImportException) {
                _state.value = BillImportUiState.Error(e.message ?: failMessage)
            } catch (e: Exception) {
                _state.value = BillImportUiState.Error(e.message ?: failMessage)
            }
        }
    }

    /** 预览中删除一条（按 notificationKey 过滤） */
    fun deleteRow(key: String) {
        val cur = _state.value as? BillImportUiState.Preview ?: return
        _state.value = cur.copy(rows = cur.rows.filter { it.notificationKey != key })
    }

    /**
     * 确认导入：先检测重复（不落库）→
     * 无重复直接导入；有重复进入 [BillImportUiState.ConfirmDuplicate] 等待用户选择覆盖/忽略。
     */
    fun confirmImport() {
        val cur = _state.value as? BillImportUiState.Preview ?: return
        if (cur.rows.isEmpty()) return
        val rows = cur.rows
        activeJob?.cancel()
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val dupKeys = ServiceLocator.billImportService.detectDuplicates(rows)
                if (dupKeys.isEmpty()) {
                    executeCommit(rows, DuplicatePolicy.SKIP)
                } else {
                    pendingRows = rows
                    _state.value = BillImportUiState.ConfirmDuplicate(
                        dupCount = dupKeys.size,
                        newCount = rows.size - dupKeys.size
                    )
                }
            } catch (e: Exception) {
                _state.value = BillImportUiState.Error(e.message ?: "导入失败，请重试")
            }
        }
    }

    /** 用户在重复确认弹窗中选择处理策略（覆盖原记录 / 忽略跳过） */
    fun resolveDuplicate(policy: DuplicatePolicy) {
        val rows = pendingRows ?: return
        pendingRows = null
        activeJob?.cancel()
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            executeCommit(rows, policy)
        }
    }

    /** 执行入库 → Importing → Success/Error */
    private suspend fun executeCommit(rows: List<BillImportRow>, policy: DuplicatePolicy) {
        _state.value = BillImportUiState.Importing(0, rows.size)
        try {
            val result = ServiceLocator.billImportService.commit(rows, source, policy)
            pendingRows = null
            _state.value = BillImportUiState.Success(result.inserted, result.skipped)
        } catch (e: Exception) {
            pendingRows = null
            _state.value = BillImportUiState.Error(e.message ?: "导入失败，请重试")
        }
    }

    /** 取消任务 → Idle（弹窗关闭时调用） */
    fun reset() {
        activeJob?.cancel()
        activeJob = null
        pendingRows = null
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

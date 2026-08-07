package com.xl.bill.mint.ui.viewmodel

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 蓝牙传输/导入导出的 UI 状态机 */
sealed interface TransferUiState {
    /** 空闲 */
    data object Idle : TransferUiState

    /** 正在导出本地数据（发送端准备 JSON） */
    data object Preparing : TransferUiState

    /** 发送中，progress ∈ [0,1] */
    data class Sending(val progress: Float) : TransferUiState

    /** 等待对方连接（接收端） */
    data object Waiting : TransferUiState

    /** 接收中，progress ∈ [0,1] */
    data class Receiving(val progress: Float) : TransferUiState

    /** 已收到数据，等待用户确认导入 */
    data class Received(val json: String) : TransferUiState

    /** 发送完成 */
    data object Sent : TransferUiState

    /** 导入完成 */
    data class Success(val transactionCount: Int) : TransferUiState

    /** 出错，message 为用户可读信息 */
    data class Error(val message: String) : TransferUiState
}

/**
 * 数据迁移（蓝牙发送/接收 + 导入）的 ViewModel。
 * 持有进行中的协程 Job，关闭 Sheet 时取消以释放蓝牙连接。
 */
class TransferViewModel : ViewModel() {

    private val _state = MutableStateFlow<TransferUiState>(TransferUiState.Idle)
    val state: StateFlow<TransferUiState> = _state.asStateFlow()

    private var activeJob: Job? = null

    /** 发送端：导出本地数据并通过蓝牙发送给目标设备 */
    fun sendTo(context: Context, device: BluetoothDevice) {
        if (activeJob?.isActive == true) return
        activeJob = viewModelScope.launch {
            _state.value = TransferUiState.Preparing
            try {
                val json = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.exportManager.export()
                _state.value = TransferUiState.Sending(0f)
                _root_ide_package_.com.xl.bill.mint.transfer.BluetoothTransfer.sendJson(context, device, json) { sent, total ->
                    _state.value = TransferUiState.Sending(
                        if (total > 0) sent.toFloat() / total else 0f
                    )
                }
                _state.value = TransferUiState.Sent
            } catch (e: Exception) {
                _state.value = TransferUiState.Error(userMessage(e, "发送失败"))
            }
        }
    }

    /** 接收端：开启蓝牙服务等待对方发送数据 */
    fun startReceive(context: Context) {
        if (activeJob?.isActive == true) return
        activeJob = viewModelScope.launch {
            _state.value = TransferUiState.Waiting
            try {
                val json = _root_ide_package_.com.xl.bill.mint.transfer.BluetoothTransfer.receiveJson(context) { received, total ->
                    _state.value = TransferUiState.Receiving(
                        if (total > 0) received.toFloat() / total else 0f
                    )
                }
                _state.value = TransferUiState.Received(json)
            } catch (e: Exception) {
                _state.value = TransferUiState.Error(userMessage(e, "接收失败"))
            }
        }
    }

    /** 用户确认后执行导入（清空现有数据） */
    fun confirmImport() {
        val json = (state.value as? TransferUiState.Received)?.json ?: return
        viewModelScope.launch {
            try {
                val result = _root_ide_package_.com.xl.bill.mint.di.ServiceLocator.importManager.import(json)
                _state.value = TransferUiState.Success(result.transactionCount)
            } catch (e: Exception) {
                _state.value = TransferUiState.Error(userMessage(e, "导入失败"))
            }
        }
    }

    /** 关闭 Sheet / 取消进行中的传输 */
    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        _state.value = TransferUiState.Idle
    }

    private fun userMessage(e: Exception, fallback: String): String =
        (e as? com.xl.bill.mint.transfer.TransferException)?.message ?: e.message ?: fallback
}

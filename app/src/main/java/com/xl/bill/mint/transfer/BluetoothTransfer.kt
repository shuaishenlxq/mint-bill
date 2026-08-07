package com.xl.bill.mint.transfer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/** 蓝牙传输异常；message 为用户可读信息 */
class BluetoothTransferException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 经典蓝牙 SPP 传输（App 内直连）：
 * - 服务端（新设备接收端）：listenUsingRfcommWithServiceRecord → accept → 读「4 字节大端长度 + UTF-8 JSON」
 * - 客户端（旧设备发送端）：createRfcommSocketToServiceRecord → connect → 写「4 字节大端长度 + UTF-8 JSON」，16KB 分块
 *
 * 阻塞调用均包在 Dispatchers.IO + runInterruptible 中，协程取消可中断（accept 由 withTimeoutOrNull 兜底 60s）。
 * 调用方（ViewModel）负责运行时权限（BLUETOOTH_SCAN / BLUETOOTH_CONNECT）与适配器可用性提示。
 */
object BluetoothTransfer {

    /** 固定自定义服务 UUID（MintBill 专用，避免与系统 SPP 默认 UUID 冲突） */
    val SERVICE_UUID: UUID = UUID.fromString("b1c9f6e2-7d3a-4e5f-9a2c-3d4e5f6a7b8c")

    private const val SERVICE_NAME = "MintBill"
    private const val ACCEPT_TIMEOUT_MS = 60_000L
    private const val MAX_JSON_BYTES = 32 * 1024 * 1024
    private const val CHUNK_SIZE = 16 * 1024

    /**
     * 等待并接收完整 JSON（新设备导入端）。
     * [onProgress] 回调 (已接收字节, 总字节)，供进度条展示。
     * 返回接收到的原始 JSON 字符串。
     */
    suspend fun receiveJson(
        context: Context,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val adapter = adapter(context)
        _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.ensureRunning(context)

        val serverSocket: BluetoothServerSocket = runInterruptible {
            adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
        }
        var socket: BluetoothSocket? = null
        try {
            // accept(timeout) 原生支持超时；withTimeoutOrNull + 线程中断作为协程取消兜底
            val accepted: BluetoothSocket = withTimeoutOrNull(ACCEPT_TIMEOUT_MS) {
                runInterruptible { serverSocket.accept(ACCEPT_TIMEOUT_MS.toInt()) }
            } ?: throw BluetoothTransferException("等待连接超时（60 秒），请重试")
            socket = accepted

            DataInputStream(accepted.inputStream).use { input ->
                val length = input.readInt() // 4 字节大端
                if (length <= 0 || length > MAX_JSON_BYTES) {
                    throw BluetoothTransferException("收到非法数据长度（${length} 字节），传输中止")
                }
                val buffer = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(buffer, read, length - read)
                    if (n == -1) throw IOException("连接中断")
                    read += n
                    onProgress(read.toLong(), length.toLong())
                }
                String(buffer, Charsets.UTF_8)
            }
        } catch (e: IOException) {
            throw BluetoothTransferException("接收失败：${e.message}", e)
        } catch (e: SecurityException) {
            throw BluetoothTransferException("缺少蓝牙权限，请授予后重试", e)
        } finally {
            runCatching { socket?.close() }
            runCatching { serverSocket.close() }
        }
    }

    /**
     * 连接目标设备并发送 JSON（旧设备导出端）。
     * [onProgress] 回调 (已发送字节, 总字节)，供进度条展示。
     */
    suspend fun sendJson(
        context: Context,
        device: BluetoothDevice,
        json: String,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val adapter = adapter(context)
        // 连接前必须停止发现，否则 connect 大概率失败
        runCatching { if (adapter.isDiscovering) adapter.cancelDiscovery() }
        _root_ide_package_.com.xl.bill.mint.util.KeepAliveHelper.ensureRunning(context)

        val data = json.toByteArray(Charsets.UTF_8)
        var socket: BluetoothSocket? = null
        try {
            socket = runInterruptible {
                val s = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                s.connect()
                s
            }
            DataOutputStream(socket!!.outputStream).use { output ->
                output.writeInt(data.size) // 4 字节大端长度前缀
                var sent = 0
                while (sent < data.size) {
                    val n = minOf(CHUNK_SIZE, data.size - sent)
                    output.write(data, sent, n)
                    sent += n
                    onProgress(sent.toLong(), data.size.toLong())
                }
                output.flush()
            }
        } catch (e: IOException) {
            throw BluetoothTransferException(
                "连接失败：请确认两台设备蓝牙均已开启、已配对并靠近设备（${e.message}）", e
            )
        } catch (e: SecurityException) {
            throw BluetoothTransferException("缺少蓝牙权限，请授予后重试", e)
        } finally {
            runCatching { socket?.close() }
        }
    }

    /** 获取可用 BluetoothAdapter，未开启蓝牙时抛出可读错误 */
    private fun adapter(context: Context): BluetoothAdapter {
        val manager = context.getSystemService(BluetoothManager::class.java)
            ?: throw BluetoothTransferException("设备不支持蓝牙")
        val adapter = manager.adapter
        if (adapter == null || !adapter.isEnabled) {
            throw BluetoothTransferException("请先开启蓝牙")
        }
        return adapter
    }
}

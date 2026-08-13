package com.xl.bill.mint.util

import android.util.Log
import com.xl.bill.mint.di.ServiceLocator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 诊断事件：覆盖「收到 → 解析 → 去重 → 落库」与保活链路的关键判定/拦截点。 */
enum class DiagEvent {
    // 通知监听（NLS）
    NLS_RECEIVED, NLS_DROPPED_EMPTY, NLS_CONNECTED, NLS_DISCONNECTED, NLS_REBIND_TOGGLE,
    // 解析
    PARSE_REJECTED,
    // 去重 / 落库
    DEDUP_FINGERPRINT, DEDUP_KEY, CROSS_DROP, CROSS_UPGRADE, DB_KEY_CONFLICT, RECORDED,
    // 短信
    SMS_NO_PERMISSION, SMS_RECEIVED,
    // 保活
    HEARTBEAT_TICK, HEARTBEAT_EXACT, HEARTBEAT_INEXACT,
    FGS_START_OK, FGS_START_FAIL, FGS_TIMEOUT, FGS_TASK_REMOVED
}

/**
 * 诊断日志：轻量本地环形缓冲（filesDir/diag.log），让用户无 adb 也能在设置页
 * 自查「为什么没有自动记账」。所有写入同时镜像到 Log.d（TAG=MintBill）。
 *
 * 隐私约定：仅本机存储、不上传；detail 由调用方预先截断（正文类 take(60)，
 * 沿用 BillRecordPipeline 惯例），此处再兜底截 200 字符。
 * IO 失败一律静默，绝不影响记账主链路。
 */
object DiagLog {

    private const val TAG = "MintBill"
    private const val FILE_NAME = "diag.log"

    /** 容量按字符近似：≈256K 字符（中文为主时约 0.5-0.75MB 字节） */
    private const val CAPACITY_CHARS = 256 * 1024
    private const val KEEP_CHARS = 128 * 1024

    private val lock = Any()
    private val tsFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(event: DiagEvent, detail: String? = null) {
        val line = buildString {
            synchronized(lock) { append(tsFormat.format(Date())) }
            append(" [").append(event.name).append(']')
            if (!detail.isNullOrEmpty()) append(' ').append(detail.take(200))
        }
        Log.d(TAG, line)
        runCatching {
            val file = File(ServiceLocator.appContext.filesDir, FILE_NAME)
            synchronized(lock) {
                file.appendText(line + "\n")
                if (file.length() > CAPACITY_CHARS) {
                    // 超容量：读出 → 截断保留末尾 → 重写（触发频率低，可接受）
                    file.writeText(RingLogBuffer.trim(file.readText(), KEEP_CHARS))
                }
            }
        }
    }

    /** 读取最近日志（设置页查看/分享），最多 [maxLines] 行；无日志返回空串 */
    fun readAll(maxLines: Int = 300): String = runCatching {
        val file = File(ServiceLocator.appContext.filesDir, FILE_NAME)
        if (!file.exists()) return ""
        synchronized(lock) {
            file.readLines().takeLast(maxLines).joinToString("\n")
        }
    }.getOrDefault("")

    fun clear() {
        runCatching {
            synchronized(lock) {
                File(ServiceLocator.appContext.filesDir, FILE_NAME).delete()
            }
        }
    }
}

package com.xl.bill.mint.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.xl.bill.mint.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃捕获：把未捕获异常完整堆栈追加写入 filesDir/crash.log，
 * 便于在无 adb 环境下定位启动崩溃。链式保留系统默认处理器（不吞异常）。
 */
object CrashLog {

    private const val TAG = "MintBillCrash"
    private const val FILE_NAME = "crash.log"
    private const val MAX_BYTES = 512 * 1024 // 超过 512KB 清空重写，防止无限膨胀

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(appContext, "uncaught on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 记录任意异常（不崩溃场景的兜底日志也走这里）；context 为 null 时仅输出 Logcat */
    fun record(context: Context?, message: String, throwable: Throwable? = null) {
        try {
            val sw = StringWriter()
            PrintWriter(sw).use {
                it.println("=== ${ts()} | $message")
                it.println("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL}")
                throwable?.printStackTrace(it) ?: it.println("(no throwable)")
            }
            Log.e(TAG, "$message", throwable)
            if (context != null) {
                writeFile(context.applicationContext, sw.toString())
            }
        } catch (_: Throwable) {
            // 日志本身失败不能再次崩溃
        }
    }

    /** 判断数据库文件是否已存在（崩溃信息上下文） */
    fun dbFileExists(context: Context): Boolean =
        context.getDatabasePath("mint_bill.db").exists()

    private fun writeFile(context: Context, content: String) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists() && file.length() > MAX_BYTES) {
            file.writeText("") // 过大则清空
        }
        file.appendText(content)
    }

    private fun ts(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}

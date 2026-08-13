package com.xl.bill.mint.transfer

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 备份文件读写（SAF / ContentResolver）。
 *
 * 用 SAF 选择器拿到的 Uri 读写，文件落在用户指定的下载目录或云盘，
 * 不受应用卸载影响 —— 这是「卸载重装不丢数据」的关键。
 * 应用私有目录（getExternalFilesDir）会随卸载删除，不可用。
 */
object BackupFileManager {

    private val NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** 默认备份文件名，如 MintBill-备份-20260811-203015.json */
    fun defaultBackupName(): String {
        val ts = LocalDateTime.now(ZoneId.systemDefault()).format(NAME_FORMATTER)
        return "MintBill-备份-$ts.json"
    }

    /** 把导出的 JSON 写入目标 Uri（"w" 模式会截断旧内容） */
    suspend fun writeBackup(context: Context, uri: Uri, json: String) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "w")
            ?: throw TransferException("无法写入备份文件（URI 无效或被拒绝）")
        pfd.use {
            FileOutputStream(it.fileDescriptor).use { fos ->
                fos.write(json.toByteArray(StandardCharsets.UTF_8))
            }
        }
    }

    /** 从目标 Uri 读取备份 JSON 文本 */
    suspend fun readBackup(context: Context, uri: Uri): String {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw TransferException("无法读取备份文件（URI 无效或被拒绝）")
        return pfd.use {
            FileInputStream(it.fileDescriptor).bufferedReader(StandardCharsets.UTF_8).use { reader ->
                reader.readText()
            }
        }
    }
}

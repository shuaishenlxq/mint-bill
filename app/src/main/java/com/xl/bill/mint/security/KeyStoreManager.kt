package com.xl.bill.mint.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 数据库加密密钥托管：
 * - SQLCipher 的 passphrase 是随机 32 字节，不直接落盘；
 * - passphrase 用 Android Keystore 中的 AES-256-GCM 密钥加密（IV + 密文 → Base64）后存 SharedPreferences；
 * - Keystore 密钥不参与系统备份/设备迁移 → 备份中的密文 passphrase 无法解密，等于备份里的库是死数据；
 * - 进程内缓存解密结果，避免每次打开数据库都做一次 Keystore 操作；
 * - **自愈**：Keystore key 丢失/损坏（卸载残留、ROM 异常、备份还原）导致解密失败时，
 *   备份并清走旧数据库文件后重新生成 passphrase，保证 app 可启动（由 AppDatabase 的降级逻辑接住）。
 */
object KeyStoreManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "mintbill_db_passphrase_key"
    private const val PREFS_NAME = "mintbill_secure"
    private const val PREFS_WRAPPED = "wrapped_db_passphrase"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    /** 数据库文件名（与 AppDatabase 保持一致；rekey 时备份/清理用） */
    private const val DB_FILE_NAME = "mint_bill.db"
    private const val DB_BACKUP_SUFFIX = ".rekey.bak"

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    /**
     * 获取（或首次生成并托管）SQLCipher passphrase。
     * 返回的是原始字节数组，调用方负责及时用完（可 copy 后置空）。
     */
    fun getOrCreatePassphrase(context: Context): ByteArray {
        cachedPassphrase?.let { return it }
        synchronized(this) {
            cachedPassphrase?.let { return it }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val wrapped = prefs.getString(PREFS_WRAPPED, null)
            val passphrase = if (wrapped != null) {
                try {
                    decrypt(wrapped)
                } catch (t: Throwable) {
                    // Keystore key 丢失/损坏：旧 passphrase 永久丢失，旧库已无法解密 →
                    // 备份并清走旧库文件（数据留档），重新生成 passphrase，保证可启动。
                    _root_ide_package_.com.xl.bill.mint.util.CrashLog.record(context, "keystore decrypt failed, regenerate passphrase", t)
                    quarantineDb(context)
                    generateAndStore(prefs)
                }
            } else {
                generateAndStore(prefs)
            }
            cachedPassphrase = passphrase
            return passphrase
        }
    }

    private fun generateAndStore(prefs: android.content.SharedPreferences): ByteArray {
        val fresh = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(PREFS_WRAPPED, encrypt(fresh)).apply()
        return fresh
    }

    /** 旧库文件（若存在）备份为 *.rekey.bak 后删除，避免用新 passphrase 打不开旧文件 */
    private fun quarantineDb(context: Context) {
        runCatching {
            val dbFile = context.getDatabasePath(DB_FILE_NAME)
            if (dbFile.exists()) {
                dbFile.copyTo(
                    java.io.File(dbFile.path + DB_BACKUP_SUFFIX),
                    overwrite = true
                )
                context.deleteDatabase(DB_FILE_NAME)
            }
        }
    }

    // ---------- Keystore AES-GCM 加解密 ----------

    private fun getOrCreateKeystoreKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        } catch (t: Throwable) {
            // 读取失败（ROM 异常等）：忽略，走生成新 key 路径
            _root_ide_package_.com.xl.bill.mint.util.CrashLog.record(null, "keystore read failed, will recreate key", t)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        val encrypted = cipher.doFinal(plain)
        val out = ByteArray(GCM_IV_BYTES + encrypted.size)
        System.arraycopy(cipher.iv, 0, out, 0, GCM_IV_BYTES)
        System.arraycopy(encrypted, 0, out, GCM_IV_BYTES, encrypted.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(wrapped: String): ByteArray {
        val raw = Base64.decode(wrapped, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, GCM_IV_BYTES)
        val encrypted = raw.copyOfRange(GCM_IV_BYTES, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }
}

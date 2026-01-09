package com.vibe.terminal.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据库加密密钥管理器
 *
 * 使用 Android Keystore 安全存储数据库加密密钥
 * 密钥永远不会以明文形式存储在设备上
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_FILE_NAME = "secure_db_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val PASSPHRASE_LENGTH = 32  // 256 bits
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 获取数据库加密密钥
     *
     * 如果密钥不存在，则生成新密钥并安全存储
     * 密钥使用 Android Keystore 加密后存储在 EncryptedSharedPreferences 中
     *
     * @return 用于 SQLCipher 的密钥字节数组
     */
    fun getOrCreateDatabaseKey(): ByteArray {
        val existingKey = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)

        return if (existingKey != null) {
            // 解码已存储的密钥
            hexStringToByteArray(existingKey)
        } else {
            // 生成新的随机密钥
            val newKey = generateSecureKey()
            // 存储密钥（加密存储）
            encryptedPrefs.edit()
                .putString(KEY_DB_PASSPHRASE, byteArrayToHexString(newKey))
                .apply()
            newKey
        }
    }

    /**
     * 检查数据库密钥是否已存在
     */
    fun hasDatabaseKey(): Boolean {
        return encryptedPrefs.getString(KEY_DB_PASSPHRASE, null) != null
    }

    /**
     * 生成安全的随机密钥
     */
    private fun generateSecureKey(): ByteArray {
        val secureRandom = SecureRandom()
        val key = ByteArray(PASSPHRASE_LENGTH)
        secureRandom.nextBytes(key)
        return key
    }

    /**
     * 字节数组转十六进制字符串
     */
    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 十六进制字符串转字节数组
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        return ByteArray(hex.length / 2) {
            hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
}

package com.vibe.terminal.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 凭证加密管理器
 *
 * 使用 Android Keystore 对单个凭证（密码、私钥、密码短语）进行加密
 * 提供数据库加密之上的额外保护层
 *
 * 加密方案：AES-256-GCM
 * 密钥存储：Android Keystore（硬件支持时使用 TEE/SE）
 *
 * 安全特性：
 * - 密钥永远不会离开安全硬件
 * - 每次加密使用随机 IV
 * - 认证加密（防篡改）
 */
@Singleton
class CredentialEncryptionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "vibe_credential_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12

        // 加密数据格式：IV (12 bytes) + 加密数据
        private const val ENCRYPTED_PREFIX = "ENC:"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
    }

    /**
     * 获取或创建加密密钥
     */
    private fun getOrCreateKey(): SecretKey {
        // 检查密钥是否已存在
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        // 生成新密钥
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )

        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)  // 强制每次使用随机 IV
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    /**
     * 加密凭证
     *
     * @param plaintext 明文凭证
     * @return 加密后的字符串（Base64 编码），如果输入为 null 则返回 null
     */
    fun encrypt(plaintext: String?): String? {
        if (plaintext == null) return null
        if (plaintext.isEmpty()) return ""

        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            // 组合 IV 和加密数据
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // 加密失败时记录错误但不暴露敏感信息
            android.util.Log.e("CredentialEncryption", "Encryption failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * 解密凭证
     *
     * @param encrypted 加密的字符串
     * @return 解密后的明文，如果解密失败或输入为 null 则返回 null
     */
    fun decrypt(encrypted: String?): String? {
        if (encrypted == null) return null
        if (encrypted.isEmpty()) return ""

        // 检查是否为加密格式
        if (!encrypted.startsWith(ENCRYPTED_PREFIX)) {
            // 可能是未加密的旧数据，直接返回（向后兼容）
            return encrypted
        }

        return try {
            val key = getOrCreateKey()
            val combined = Base64.decode(encrypted.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)

            // 提取 IV 和加密数据
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("CredentialEncryption", "Decryption failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * 加密凭证（使用 CharArray 以提高安全性）
     * CharArray 可以在使用后被清零，而 String 在 JVM 中是不可变的
     *
     * @param plaintext 明文凭证
     * @return 加密后的字符串
     */
    fun encrypt(plaintext: CharArray?): String? {
        if (plaintext == null) return null
        if (plaintext.isEmpty()) return ""

        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv
            val plaintextBytes = String(plaintext).toByteArray(Charsets.UTF_8)
            val encryptedBytes = cipher.doFinal(plaintextBytes)

            // 清零明文字节
            plaintextBytes.fill(0)

            // 组合 IV 和加密数据
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("CredentialEncryption", "Encryption failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * 解密凭证到 CharArray
     *
     * @param encrypted 加密的字符串
     * @return 解密后的 CharArray，使用后应调用 fill('\u0000') 清零
     */
    fun decryptToCharArray(encrypted: String?): CharArray? {
        val decrypted = decrypt(encrypted) ?: return null
        return decrypted.toCharArray()
    }

    /**
     * 检查凭证是否已加密
     */
    fun isEncrypted(value: String?): Boolean {
        return value?.startsWith(ENCRYPTED_PREFIX) == true
    }

    /**
     * 安全清除 CharArray
     */
    fun secureWipe(data: CharArray?) {
        data?.fill('\u0000')
    }

    /**
     * 安全清除 ByteArray
     */
    fun secureWipe(data: ByteArray?) {
        data?.fill(0)
    }
}
